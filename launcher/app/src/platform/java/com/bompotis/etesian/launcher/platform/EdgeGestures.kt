package com.bompotis.etesian.launcher.platform

import android.app.ActivityTaskManager
import android.app.KeyguardManager
import android.app.WindowConfiguration
import android.content.Context
import android.hardware.input.InputManager
import android.hardware.input.InputManagerGlobal
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import com.bompotis.etesian.launcher.Platform
import com.bompotis.etesian.launcher.Prefs
import kotlin.math.abs

/**
 * N9 swipe gestures, read straight from the input system: swiping in from the left, right or
 * bottom edge minimises the application into Open applications with the window following the
 * finger; swiping down from the top edge closes it. Letting go early cancels.
 *
 * Android has no N9-style back, so Back is configurable (see [Prefs.backGesture]).
 */
class EdgeGestures(private val ctx: Context) {

    enum class Edge { LEFT, RIGHT, TOP, BOTTOM }
    enum class Action { MINIMISE, CLOSE, BACK, NONE }

    /** Latest SystemUI state flags (notification shade, keyguard, ...). */
    @Volatile var systemUiState = 0L

    private val density = ctx.resources.displayMetrics.density
    private val edgeWidth = 18 * density
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
    private val backMin = 40 * density
    /** Past this much of the minimise slide a curve no longer counts as Back. */
    private val curveMaxProgress = 0.25f
    private val curveDown = 56 * density

    private var monitor: InputMonitor? = null
    private var receiver: InputEventReceiver? = null

    // Current gesture.
    private var edge: Edge? = null
    private var downX = 0f
    private var downY = 0f
    private var started = false
    private var curved = false
    private var velocity: VelocityTracker? = null
    private var drag: WindowDrag? = null
    private var backHint: BackHint? = null
    private var taskId = -1

    // Bottom swipe and hold: the first row of applications peeks in under the window.
    private val holdDelay = 350L
    private val main = android.os.Handler(Looper.getMainLooper())
    private var peek: AppPeek? = null
    private var holdX = 0f
    private var holdY = 0f
    private var lastInward = 0f
    private val holdCheck = Runnable {
        if (edge == Edge.BOTTOM && drag != null && peek == null) {
            peek = AppPeek(ctx).takeUnless { it.isEmpty }?.also { it.show(lastInward.toInt()) }
        }
    }

    private val sideBar = SideBar(ctx) { perform(it) }
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateSideBar()
    }
    private val modeObserver = object : android.database.ContentObserver(android.os.Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = updateSideBar()
    }

    private fun updateSideBar() {
        if (n9Mode() && Prefs.backGesture(ctx) == Prefs.BACK_SIDEBAR) sideBar.show() else sideBar.hide()
    }

    /** An action picked in settings, run from the edge bar. */
    private fun perform(action: String) {
        val top = runCatching { ActivityTaskManager.getService().focusedRootTaskInfo }.getOrNull()
        val inApp = top != null && top.activityType != WindowConfiguration.ACTIVITY_TYPE_HOME
        val id = top?.childTaskIds?.lastOrNull() ?: top?.taskId ?: -1
        when (action) {
            Prefs.ACTION_BACK -> sendBack()
            Prefs.ACTION_MINIMISE -> if (inApp) WindowDrag(ctx, Edge.RIGHT, id, closing = false).apply { begin(); commit() }
            Prefs.ACTION_CLOSE -> if (inApp) WindowDrag(ctx, Edge.TOP, id, closing = true).apply { begin(); commit() }
            Prefs.ACTION_SWITCHER -> ctx.startActivity(com.bompotis.etesian.launcher.HomeActivity.switcherIntent(ctx))
        }
    }

    fun start() {
        Prefs.of(ctx).registerOnSharedPreferenceChangeListener(prefsListener)
        ctx.contentResolver.registerContentObserver(Settings.Secure.getUriFor("etesian_gestures"), false, modeObserver)
        updateSideBar()
        val im = ctx.getSystemService(InputManager::class.java)
        monitor = im.monitorGestureInput("etesian-edges", Display.DEFAULT_DISPLAY).also { m ->
            receiver = object : InputEventReceiver(m.inputChannel, Looper.getMainLooper()) {
                override fun onInputEvent(event: InputEvent) {
                    if (event is MotionEvent) onMotion(event)
                    finishInputEvent(event, false)
                }
            }
        }
    }

    fun stop() {
        Prefs.of(ctx).unregisterOnSharedPreferenceChangeListener(prefsListener)
        ctx.contentResolver.unregisterContentObserver(modeObserver)
        sideBar.hide()
        receiver?.dispose(); receiver = null
        monitor?.dispose(); monitor = null
    }

    private fun n9Mode() = Settings.Secure.getInt(ctx.contentResolver, "etesian_gestures", 1) == 1

    private var onBar = false

    private fun onMotion(e: MotionEvent) {
        // Streams that start on the edge bar belong to the bar.
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            onBar = sideBar.bounds.contains(e.rawX.toInt(), e.rawY.toInt())
            if (onBar) monitor?.pilferPointers()
        }
        if (onBar) { sideBar.onTouch(e); return }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(e)
            MotionEvent.ACTION_MOVE -> if (edge != null) onMove(e)
            MotionEvent.ACTION_UP -> if (edge != null) onUp(e, cancelled = false)
            MotionEvent.ACTION_CANCEL -> if (edge != null) onUp(e, cancelled = true)
            MotionEvent.ACTION_POINTER_DOWN -> if (edge != null && !started) reset()
        }
    }

    private val statusBarHeight = ctx.resources.getDimensionPixelSize(
        ctx.resources.getIdentifier("status_bar_height", "dimen", "android"))

    /** True while a touch on the status bar might still turn out to be a tap. */
    private var statusTap = false
    private var downTime = 0L
    /** Home is in front: a status bar swipe is swallowed (no shade) but closes nothing. */
    private var onHome = false

    private fun onDown(e: MotionEvent) {
        reset()
        val w = ctx.resources.displayMetrics.widthPixels
        val h = ctx.resources.displayMetrics.heightPixels
        val x = e.rawX
        val y = e.rawY
        val n9 = n9Mode()
        val candidate = when {
            // N9: the whole status bar belongs to Etesian; no notification shade.
            n9 && y < statusBarHeight -> Edge.TOP
            x < edgeWidth -> Edge.LEFT
            x > w - edgeWidth -> Edge.RIGHT
            y > h - edgeWidth -> Edge.BOTTOM
            else -> return
        }
        // Android mode keeps its own back gesture and shade; Etesian still answers the bottom edge.
        if (!n9 && candidate != Edge.BOTTOM) return
        if (ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            // N9: no shade on the lock screen either.
            if (n9 && candidate == Edge.TOP) monitor?.pilferPointers()
            // Unlocking is SystemUI's sideways swipe (EtesianKeyguardSwipe); edges stay its.
            return
        }
        val top = runCatching { ActivityTaskManager.getService().focusedRootTaskInfo }.getOrNull()
        onHome = top == null || top.activityType == WindowConfiguration.ACTIVITY_TYPE_HOME
        // Nothing to minimise on the home screen itself; only the status bar still matters there.
        if (onHome && candidate != Edge.TOP) return
        taskId = top?.childTaskIds?.lastOrNull() ?: top?.taskId ?: -1
        edge = candidate
        statusTap = candidate == Edge.TOP
        // N9: nothing on the status bar reaches Android's shade, not even a long press.
        if (candidate == Edge.TOP) monitor?.pilferPointers()
        downTime = e.eventTime
        downX = x
        downY = y
        velocity = VelocityTracker.obtain().also { it.addMovement(e) }
    }

    /** N9 status menu: a tap on the status bar opens Etesian's status menu, never notifications. */
    private fun openStatusMenu() {
        // While recording, the red dot sits on the right of the status bar (with the system
        // icons): a tap there asks "Stop recording?" instead (approved "Recording dot").
        if (com.bompotis.etesian.launcher.Recorder.on(ctx) && downX > ctx.resources.displayMetrics.widthPixels / 2f) {
            com.bompotis.etesian.launcher.Recorder.askToStop(ctx)
            return
        }
        ctx.startActivity(android.content.Intent(ctx, com.bompotis.etesian.launcher.StatusMenuActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    /** How far the finger has travelled inward from its edge. */
    private fun inward(e: MotionEvent) = when (edge!!) {
        Edge.LEFT -> e.rawX - downX
        Edge.RIGHT -> downX - e.rawX
        Edge.TOP -> e.rawY - downY
        Edge.BOTTOM -> downY - e.rawY
    }

    private fun across(e: MotionEvent) = when (edge!!) {
        Edge.LEFT, Edge.RIGHT -> e.rawY - downY
        Edge.TOP, Edge.BOTTOM -> e.rawX - downX
    }

    /** What finishing this edge's swipe does, before back variants are considered. */
    private fun edgeAction(): Action {
        if (!n9Mode()) return if (edge == Edge.BOTTOM) Action.MINIMISE else Action.NONE
        val back = Prefs.backGesture(ctx)
        return when (edge!!) {
            Edge.TOP -> Action.CLOSE
            Edge.LEFT -> if (back == Prefs.BACK_LEFT) Action.BACK else Action.MINIMISE
            Edge.RIGHT -> if (back == Prefs.BACK_RIGHT) Action.BACK else Action.MINIMISE
            Edge.BOTTOM -> when (Prefs.bottomAction(ctx)) {
                Prefs.ACTION_BACK -> Action.BACK
                Prefs.ACTION_CLOSE -> Action.CLOSE
                Prefs.ACTION_NONE -> Action.NONE
                else -> Action.MINIMISE
            }
        }
    }

    private fun onMove(e: MotionEvent) {
        velocity?.addMovement(e)
        val d = inward(e)
        if (statusTap && (abs(d) > slop || abs(across(e)) > slop)) statusTap = false
        if (!started) {
            if (d > slop && d > abs(across(e))) {
                if (onHome) {
                    // Swallow it so the shade doesn't open; there's no application to close.
                    monitor?.pilferPointers()
                    started = true
                    return
                }
                val action = edgeAction()
                if (action == Action.NONE) { reset(); return }
                started = true
                Platform.edgeGestureActive = true
                // From here on the gesture is ours: the app gets a cancel.
                monitor?.pilferPointers()
                if (action == Action.BACK) backHint = BackHint(ctx, edge!!).also { it.show(downX, downY) }
                else drag = WindowDrag(ctx, edge!!, taskId, closing = action == Action.CLOSE).also { it.begin() }
            } else if (abs(across(e)) > slop * 2 && edge != Edge.TOP) {
                reset()
            }
            return
        }
        val travel = if (edge == Edge.LEFT || edge == Edge.RIGHT) ctx.resources.displayMetrics.widthPixels
            else ctx.resources.displayMetrics.heightPixels
        val progress = (d / travel).coerceIn(0f, 1f)
        drag?.update(progress, across(e))
        // Curved back: in from the side, then down.
        if (Prefs.backGesture(ctx) == Prefs.BACK_CURVED && (edge == Edge.LEFT || edge == Edge.RIGHT)) {
            // A curve is a short move in and then mostly down. The drift of a long, slow minimise
            // swipe is not: it armed Back at about half way (N9 gestures come first).
            curved = d > slop * 2 && across(e) > curveDown && across(e) > d && progress < curveMaxProgress
            // Only promise Back when letting go now would really go Back: a fast or long swipe
            // still sends the application away, so the chevron stays hidden for it.
            showArmedBack(curved && drag?.shouldCommit(inwardVelocity() / density, Prefs.closeDistance(ctx)) != true, e)
        } else {
            backHint?.update(d)
        }
        if (edge == Edge.BOTTOM && drag != null) {
            peek?.hover(e.rawX, e.rawY)
            // Restart the hold timer whenever the finger really moves.
            if (peek == null && (abs(e.rawX - holdX) > slop || abs(e.rawY - holdY) > slop)) {
                holdX = e.rawX; holdY = e.rawY
                lastInward = d
                main.removeCallbacks(holdCheck)
                main.postDelayed(holdCheck, holdDelay)
            }
        }
    }

    /** While a minimise swipe could still end as Back, the chevron says so. */
    private var sideHint: BackHint? = null
    private fun showArmedBack(armed: Boolean, e: MotionEvent) {
        if (armed && sideHint == null) sideHint = BackHint(ctx, edge!!).also { it.show(downX, e.rawY); it.update(backMin * 2) }
        if (!armed) { sideHint?.hide(); sideHint = null }
    }

    /** The finger's speed away from the edge, in px/s. */
    private fun inwardVelocity(): Float {
        val vt = velocity ?: return 0f
        vt.computeCurrentVelocity(1000)
        return when (edge!!) {
            Edge.LEFT -> vt.xVelocity
            Edge.RIGHT -> -vt.xVelocity
            Edge.TOP -> vt.yVelocity
            Edge.BOTTOM -> -vt.yVelocity
        }
    }

    private fun onUp(e: MotionEvent, cancelled: Boolean) {
        if (!started) {
            if (!cancelled && statusTap && e.eventTime - downTime < ViewConfiguration.getLongPressTimeout()) openStatusMenu()
            reset(); return
        }
        if (onHome) { reset(); return }
        val v = inwardVelocity()
        val d = inward(e)
        val hint = backHint
        val window = drag
        val peeked = peek
        when {
            cancelled -> { window?.cancel(); hint?.hide() }
            peeked != null -> {
                // Let go over an icon: open it. Elsewhere: the application stays.
                if (peeked.launchAt(e.rawX, e.rawY)) window?.commitQuiet() else window?.cancel()
            }
            hint != null -> { hint.hide(); if (d > backMin) sendBack() }
            window != null -> {
                val side = edge == Edge.LEFT || edge == Edge.RIGHT
                val back = Prefs.backGesture(ctx)
                when {
                    window.shouldCommit(v / density, Prefs.closeDistance(ctx)) -> { sideHint?.hideNow(); sideHint = null; window.commit() }
                    side && back == Prefs.BACK_CURVED && curved -> { window.cancel(); sendBack() }
                    else -> window.cancel()
                }
            }
        }
        // The drag and hint finish their own animations.
        drag = null
        backHint = null
        reset()
    }

    private fun reset() {
        Platform.edgeGestureActive = false
        statusTap = false
        onHome = false
        main.removeCallbacks(holdCheck)
        peek?.hide()
        peek = null
        edge = null
        started = false
        curved = false
        velocity?.recycle()
        velocity = null
        drag?.cancel()
        drag = null
        backHint?.hide()
        backHint = null
        sideHint?.hide()
        sideHint = null
    }

    private fun sendBack() {
        val now = SystemClock.uptimeMillis()
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            val ev = KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD)
            InputManagerGlobal.getInstance().injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
        }
    }
}
