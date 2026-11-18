package com.bompotis.etesian.launcher.platform

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.bompotis.etesian.launcher.IconFactory
import com.bompotis.etesian.launcher.N9
import com.bompotis.etesian.launcher.N9Sounds
import com.bompotis.etesian.launcher.Prefs
import com.bompotis.etesian.launcher.R
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Breeze, the one-hand control: a handle on a screen edge, or, dragged away from the edges, a see-through
 * squircle button with Etesian's logo that floats over everything. Taps, double taps and short flicks run the
 * actions picked in settings (one can be Breeze's menu, [BreezeMenu]); every one of them is felt. Long-press it
 * and drag: away from the edges it slowly becomes the button, back at an edge the handle again.
 *
 * Touches reach it through the input monitor ([EdgeGestures]), its windows never take any. This phone's touch
 * controller drops a finger held still on its curved sides (a palm, it thinks: 60 px each side, ver_grip_x in the
 * s6sy761 driver), so the handle is drawn clear of that strip and the touch area reaches well past it.
 */
class SideBar(private val ctx: Context, private val perform: (String) -> Unit) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val vibrator = ctx.getSystemService(Vibrator::class.java)
    private var view: Handle? = null
    private var drag: DragLayer? = null
    private var onLeft = false
    /** The floating button, not the edge handle. */
    private var free = false
    /** Long-pressed: the stream moves Breeze instead of making gestures. */
    private var moving = false
    private val menu = BreezeMenu(ctx)

    /** Settings want it shown; the lock screen still hides it. */
    private var wanted = false
    private var locked = false

    /** Screen area Breeze takes, so touches starting on it are Breeze's. */
    val bounds = Rect()
    /** Where it is drawn (the handle or the button), for the menu to line up with. */
    private val drawn = Rect()

    private fun dp(v: Float) = (v * density).toInt()
    private val handleW get() = dp(12f)
    /** Handle's distance from the screen edge: outside the controller's 60 px grip strip. */
    private val handleInset get() = dp(20f)
    private val barWindow get() = handleInset + handleW + dp(6f)
    private val barH get() = dp(140f)
    private val button get() = dp(56f)
    /** Touch area of the handle, from the screen edge in. */
    private val reach get() = dp(64f)
    /** This close to the left or right edge it is the handle there; further in, the button. */
    private val edgeSnap get() = dp(48f).toFloat()

    private val screenW get() = ctx.resources.displayMetrics.widthPixels
    private val screenH get() = ctx.resources.displayMetrics.heightPixels

    init {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        locked = km.isKeyguardLocked
        // The lock screen hides Breeze: the system's own listener, with the screen broadcasts as a backstop.
        runCatching {
            km.addKeyguardLockedStateListener(ctx.mainExecutor) { l -> setLocked(l) }
        }
        runCatching {
            ctx.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> setLocked(true)
                    else -> setLocked(km.isKeyguardLocked)
                }
            }, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }, Context.RECEIVER_EXPORTED)
        }
    }

    private fun setLocked(l: Boolean) {
        if (l == locked) return
        locked = l
        refresh()
    }

    fun show() { wanted = true; refresh() }

    fun hide() { wanted = false; refresh() }

    private fun refresh() {
        if (wanted && !locked) place() else remove()
    }

    private fun remove() {
        menu.dismiss()
        endDrag()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        bounds.setEmpty(); drawn.setEmpty()
    }

    private fun place(appear: Boolean = false) {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        val side = Prefs.sideBarSide(ctx)
        free = side == Prefs.SIDE_FREE
        onLeft = side == "left"
        val w = if (free) button else barWindow
        val h = if (free) button else barH
        val y = (screenH * Prefs.sideBarPosition(ctx) / 100f - h / 2).toInt().coerceIn(0, screenH - h)
        val x = if (free) (screenW * Prefs.sideBarX(ctx) / 100f - w / 2).toInt().coerceIn(0, screenW - w) else 0
        val v = Handle(ctx)
        val lp = overlay(w, h).apply {
            gravity = Gravity.TOP or (if (free || onLeft) Gravity.LEFT else Gravity.RIGHT)
            this.x = x; this.y = y
            title = "EtesianBreeze"
        }
        if (appear) { v.alpha = 0f; v.scaleX = 0.6f; v.scaleY = 0.6f }
        runCatching { wm.addView(v, lp); view = v }
        if (appear) v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        when {
            free -> { bounds.set(x, y, x + w, y + h); drawn.set(bounds) }
            onLeft -> { bounds.set(0, y, reach, y + h); drawn.set(handleInset, y, handleInset + handleW, y + h) }
            else -> {
                bounds.set(screenW - reach, y, screenW, y + h)
                drawn.set(screenW - handleInset - handleW, y, screenW - handleInset, y + h)
            }
        }
    }

    /** A window that draws over everything and never takes a touch. */
    private fun overlay(w: Int, h: Int) = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT)

    /** Felt on every gesture Breeze takes. */
    private fun buzz(heavy: Boolean = false) {
        runCatching {
            vibrator.vibrate(VibrationEffect.createPredefined(
                if (heavy) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK))
        }
    }

    private fun run(action: String) {
        buzz()
        if (action == Prefs.ACTION_MENU) {
            if (menu.showing) menu.dismiss()
            else {
                // Out of the side the handle is on; the button's from the nearer side.
                val left = if (free) drawn.centerX() < screenW / 2 else onLeft
                menu.show(drawn.top, left)
            }
            return
        }
        menu.dismiss()
        perform(action)
    }

    private val flick = 36 * density
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            run(Prefs.sideBarAction(ctx, Prefs.SIDEBAR_TAP)); return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            run(Prefs.sideBarAction(ctx, Prefs.SIDEBAR_DOUBLE_TAP)); return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val start = e1 ?: return false
            val dx = e2.rawX - start.rawX
            val dy = e2.rawY - start.rawY
            // Inwards: away from the handle's edge; for the button, towards the middle of the screen.
            val inward = if (free) (if (start.rawX < screenW / 2f) dx else -dx) else dx * (if (onLeft) 1 else -1)
            val gesture = when {
                inward > flick && abs(dx) > abs(dy) -> Prefs.SIDEBAR_FLICK_IN
                dy < -flick -> Prefs.SIDEBAR_FLICK_UP
                dy > flick -> Prefs.SIDEBAR_FLICK_DOWN
                else -> return false
            }
            run(Prefs.sideBarAction(ctx, gesture)); return true
        }
    }).apply { setIsLongpressEnabled(false) }

    // Long press, measured here rather than by the detector: a finger still for the long-press time, wherever it
    // came to rest (the controller may hand the touch over only once it has crept in from the curved edge).
    private var downX = 0f
    private var downY = 0f
    private var stillX = 0f
    private var stillY = 0f
    private var lastEvent: MotionEvent? = null
    private val hold = Runnable {
        moving = true
        buzz(heavy = true)
        N9Sounds.play(ctx, N9Sounds.EDIT)
        menu.dismiss()
        // The detector saw the press start: end it, so the release is no tap or flick.
        lastEvent?.let { MotionEvent.obtain(it).apply { action = MotionEvent.ACTION_CANCEL; detector.onTouchEvent(this); recycle() } }
        startDrag()
    }

    /** Touches that start on Breeze, fed in from the input monitor. */
    fun onTouch(e: MotionEvent) {
        view?.active = e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE
        view?.invalidate()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                moving = false
                downX = e.rawX; downY = e.rawY; stillX = downX; stillY = downY
                main.removeCallbacks(hold)
                main.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (!moving) {
                if (hypot(e.rawX - downX, e.rawY - downY) > flick) main.removeCallbacks(hold)
                else if (hypot(e.rawX - stillX, e.rawY - stillY) > slop) {
                    // Crept a little: the hold counts from where it is now.
                    stillX = e.rawX; stillY = e.rawY
                    main.removeCallbacks(hold)
                    main.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> main.removeCallbacks(hold)
        }
        lastEvent?.recycle()
        lastEvent = MotionEvent.obtain(e)
        if (moving) { move(e); return }
        detector.onTouchEvent(e)
    }

    /** A touch outside Breeze and its menu closes the menu. */
    fun onOutsideDown(x: Float, y: Float) { if (menu.showing && !menu.contains(x, y)) menu.dismiss() }

    // ----- moving it -----

    private fun startDrag() {
        endDrag()
        val layer = DragLayer(ctx, startFree = free)
        layer.fingerX = stillX; layer.fingerY = stillY
        val lp = overlay(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            title = "EtesianBreezeDrag"
        }
        runCatching { wm.addView(layer, lp); drag = layer }
        // The handle itself is now the one under the finger.
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun endDrag() {
        drag?.let { it.stop(); runCatching { wm.removeView(it) } }
        drag = null
    }

    /**
     * Follows the finger: near an edge the handle there, further in the button, changing slowly between them;
     * on release the place is saved and Breeze shown there.
     */
    private fun move(e: MotionEvent) {
        val layer = drag ?: return
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> layer.follow(e.rawX, e.rawY, nearEdge(e.rawX))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moving = false
                val side = when {
                    e.rawX < edgeSnap -> "left"
                    e.rawX > screenW - edgeSnap -> "right"
                    else -> Prefs.SIDE_FREE
                }
                Prefs.setSideBarPlace(ctx, side, (e.rawY * 100 / screenH).toInt(), (e.rawX * 100 / screenW).toInt())
                buzz()
                endDrag()
                // Saving the same place as before changes no setting: place it here either way.
                if (wanted && !locked) place(appear = true)
            }
        }
    }

    /** -1 near the left edge, 1 near the right one, 0 in between. */
    private fun nearEdge(x: Float) = when {
        x < edgeSnap -> -1
        x > screenW - edgeSnap -> 1
        else -> 0
    }

    // ----- drawing -----

    private val dark get() = Prefs.breezeDark(ctx)

    private fun handleFill(active: Boolean) = when {
        active -> N9.ACCENT
        dark -> 0xE6262626.toInt()
        else -> 0xE6F4F4F4.toInt()
    }
    private fun handleRim() = if (dark) 0x80FFFFFF.toInt() else 0x66000000

    private fun buttonFill(active: Boolean) = when {
        active -> (N9.ACCENT and 0x00FFFFFF) or 0xCC000000.toInt()
        dark -> 0x8C000000.toInt()
        else -> 0xA6FFFFFF.toInt()
    }

    /** The handle at an edge, or the squircle button with the logo. */
    @SuppressLint("ViewConstructor")
    private inner class Handle(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val logo = ctx.getDrawable(R.drawable.ic_launcher_foreground)
        var active = false

        override fun onDraw(canvas: Canvas) {
            if (free) {
                drawButton(canvas, paint, logo, RectF(0f, 0f, width.toFloat(), height.toFloat()), active, 1f)
                return
            }
            val x = if (onLeft) handleInset.toFloat() else (width - handleInset - handleW).toFloat()
            drawHandle(canvas, paint, RectF(x, 0f, x + handleW, height.toFloat()), active, 1f)
        }
    }

    private fun drawHandle(c: Canvas, p: Paint, r: RectF, active: Boolean, alpha: Float) {
        val rad = r.width() / 2
        p.style = Paint.Style.FILL; p.color = handleFill(active); p.alpha = (p.alpha * alpha).toInt()
        c.drawRoundRect(r, rad, rad, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = density; p.color = handleRim(); p.alpha = (p.alpha * alpha).toInt()
        c.drawRoundRect(r, rad, rad, p)
    }

    private fun drawButton(c: Canvas, p: Paint, logo: android.graphics.drawable.Drawable?, r: RectF, active: Boolean, alpha: Float) {
        val s = minOf(r.width(), r.height())
        val path = Path(IconFactory.shape(s)).apply { offset(r.left, r.top) }
        p.style = Paint.Style.FILL; p.color = buttonFill(active); p.alpha = (p.alpha * alpha).toInt()
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = density; p.color = handleRim(); p.alpha = (p.alpha * alpha).toInt()
        c.drawPath(path, p)
        logo?.apply {
            this.alpha = ((if (active) 255 else 210) * alpha).toInt()
            setBounds(r.left.toInt(), r.top.toInt(), (r.left + s).toInt(), (r.top + s).toInt()); draw(c)
        }
    }

    /**
     * The whole screen while Breeze is moved: draws it under the finger, turning from the handle into the button
     * (and back) over a third of a second once the finger leaves the edge.
     */
    @SuppressLint("ViewConstructor")
    private inner class DragLayer(ctx: Context, startFree: Boolean) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val logo = ctx.getDrawable(R.drawable.ic_launcher_foreground)
        var fingerX = 0f
        var fingerY = 0f
        /** 0: the handle, 1: the button. */
        private var morph = if (startFree) 1f else 0f
        private var target = morph
        /** The edge the handle shows on (-1 left, 1 right). */
        private var edge = if (startFree) 0 else if (onLeft) -1 else 1
        private var anim: ValueAnimator? = null

        fun follow(x: Float, y: Float, near: Int) {
            fingerX = x; fingerY = y
            if (near != 0) edge = near
            val t = if (near == 0) 1f else 0f
            if (t != target) {
                target = t
                anim?.cancel()
                anim = ValueAnimator.ofFloat(morph, t).apply {
                    duration = (350 * abs(t - morph)).toLong().coerceAtLeast(1)
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { morph = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            invalidate()
        }

        fun stop() { anim?.cancel() }

        override fun onDraw(canvas: Canvas) {
            if (edge == 0) edge = if (fingerX < width / 2f) -1 else 1
            // The handle: at its edge, centred on the finger's height.
            val hx = if (edge < 0) handleInset.toFloat() else (width - handleInset - handleW).toFloat()
            val bar = RectF(hx, fingerY - barH / 2f, hx + handleW, fingerY + barH / 2f)
            // The button: centred on the finger.
            val b = button / 2f
            val btn = RectF(fingerX - b, fingerY - b, fingerX + b, fingerY + b)
            val m = morph
            if (m < 1f) {
                // The handle shrinks into the button's place as the button grows.
                val r = RectF(lerp(bar.left, btn.left, m), lerp(bar.top, btn.top, m), lerp(bar.right, btn.right, m), lerp(bar.bottom, btn.bottom, m))
                drawHandle(canvas, paint, r, true, 1f - m)
            }
            if (m > 0f) {
                val s = b * (0.4f + 0.6f * m)
                drawButton(canvas, paint, logo, RectF(fingerX - s, fingerY - s, fingerX + s, fingerY + s), true, m)
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
