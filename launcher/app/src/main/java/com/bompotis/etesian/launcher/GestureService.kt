package com.bompotis.etesian.launcher

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Etesian system gestures on top of other apps, via edge overlay strips:
 *  - swipe in from the left/right edge → minimise to Open applications
 *  - swipe down from the top edge (centre) → close the application
 * It also tracks the foreground app and screenshots it for the switcher thumbnails (API 30+).
 */
class GestureService : AccessibilityService() {

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, GestureService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }

    private enum class Edge { LEFT, RIGHT, TOP }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private val strips = mutableMapOf<Edge, View>()
    private var feedback: Feedback? = null
    private var foreground: String? = null
    private var lastShot = 0L
    private val shoot = Runnable { capture() }

    override fun onServiceConnected() {
        // Built into the ROM, the real gestures run in platform.EdgeGestures instead.
        if (SystemTasks.available(this)) { disableSelf(); return }
        wm = getSystemService(WindowManager::class.java)
        RunningApps.ensureLoaded(this)
        addStrip(Edge.LEFT)
        addStrip(Edge.RIGHT)
        addStrip(Edge.TOP)
        setStripsVisible(false)
    }

    override fun onDestroy() {
        strips.values.forEach { runCatching { wm.removeView(it) } }
        feedback?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == packageName || isHomePackage(pkg)) {
                    foreground = null
                    setStripsVisible(false)
                } else if (isApp(pkg)) {
                    if (foreground != pkg) {
                        foreground = pkg
                        RunningApps.touch(this, pkg)
                    }
                    setStripsVisible(true)
                    scheduleCapture(700)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (pkg == foreground) scheduleCapture(1500)
        }
    }

    private fun isApp(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android") return false
        val ime = getSystemService(InputMethodManager::class.java).enabledInputMethodList
        if (ime.any { it.packageName == pkg }) return false
        return packageManager.getLaunchIntentForPackage(pkg) != null
    }

    private fun isHomePackage(pkg: String): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, 0).any { it.activityInfo.packageName == pkg }
    }

    // ---- thumbnails ----

    private fun scheduleCapture(delay: Long) {
        if (Build.VERSION.SDK_INT < 30) return
        val since = SystemClock.uptimeMillis() - lastShot
        main.removeCallbacks(shoot)
        main.postDelayed(shoot, max(delay, 2000 - since))
    }

    @SuppressLint("NewApi")
    private fun capture() {
        val pkg = foreground ?: return
        if (feedback != null) return
        // In the ROM the system keeps task snapshots; no need for screenshots.
        if (SystemTasks.available(this)) return
        lastShot = SystemClock.uptimeMillis()
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                result.hardwareBuffer.close()
                if (hw == null || foreground != pkg) return
                // Drop the status bar and scale down to card size.
                val top = statusBarHeight()
                val sw = hw.copy(Bitmap.Config.ARGB_8888, false)
                hw.recycle()
                val w = 360
                val cropped = Bitmap.createBitmap(sw, 0, top, sw.width, sw.height - top)
                val scaled = Bitmap.createScaledBitmap(cropped, w, (cropped.height * w.toFloat() / cropped.width).toInt(), true)
                if (cropped !== sw) cropped.recycle()
                sw.recycle()
                RunningApps.setThumb(this@GestureService, pkg, scaled)
            }
            override fun onFailure(errorCode: Int) {}
        })
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    // ---- edge gestures ----

    private fun setStripsVisible(on: Boolean) {
        strips.values.forEach { it.visibility = if (on) View.VISIBLE else View.GONE }
    }

    private fun overlayParams(w: Int, h: Int, gravity: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addStrip(edge: Edge) {
        val screenW = resources.displayMetrics.widthPixels
        val params = when (edge) {
            Edge.LEFT -> overlayParams(dp(12f), WindowManager.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.TOP, true)
            Edge.RIGHT -> overlayParams(dp(12f), WindowManager.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.TOP, true)
            // Centre third only, so the notification shade stays reachable from the corners.
            Edge.TOP -> overlayParams(screenW / 3, dp(18f), Gravity.TOP or Gravity.CENTER_HORIZONTAL, true)
        }
        val v = View(this)
        v.setOnTouchListener(EdgeTouch(edge))
        wm.addView(v, params)
        strips[edge] = v
    }

    private inner class EdgeTouch(val edge: Edge) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var downT = 0L

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val screen = resources.displayMetrics
            val dist = when (edge) {
                Edge.LEFT -> e.rawX - downX
                Edge.RIGHT -> downX - e.rawX
                Edge.TOP -> e.rawY - downY
            }
            val span = if (edge == Edge.TOP) screen.heightPixels else screen.widthPixels
            val progress = min(1f, max(0f, dist / span))
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; downT = SystemClock.uptimeMillis()
                    showFeedback(edge)
                }
                MotionEvent.ACTION_MOVE -> feedback?.setProgress(progress)
                MotionEvent.ACTION_UP -> {
                    val fast = dist > dp(60f) && SystemClock.uptimeMillis() - downT < 250
                    if (progress > 0.3f || fast) commit(edge) else feedback?.dismiss()
                }
                MotionEvent.ACTION_CANCEL -> feedback?.dismiss()
            }
            return true
        }
    }

    private fun showFeedback(edge: Edge) {
        feedback?.let { runCatching { wm.removeView(it) } }
        val f = Feedback(this, edge)
        feedback = f
        wm.addView(f, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.START, false))
    }

    private fun removeFeedback() {
        feedback?.let { runCatching { wm.removeView(it) } }
        feedback = null
    }

    private fun commit(edge: Edge) {
        val pkg = foreground
        feedback?.finish {
            goHome()
            if (edge == Edge.TOP && pkg != null) RunningApps.close(this, pkg)
            main.postDelayed({ removeFeedback() }, 250)
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClass(this, HomeActivity::class.java)
            .putExtra(HomeActivity.EXTRA_PAGE, HomeActivity.PAGE_SWITCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The swipe feedback has already slid the app away: no second (system) window animation.
        val still = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
        if (runCatching { startActivity(home, still) }.isFailure) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Visual response while swiping: the app is pushed away from the swiped edge,
     * revealing black, as the N9 window slides off.
     */
    private inner class Feedback(ctx: Context, val edge: Edge) : View(ctx) {
        private var progress = 0f
        private val paint = Paint()
        private val shade = Paint()
        private var anim: ValueAnimator? = null

        fun setProgress(p: Float) { progress = p; invalidate() }

        fun dismiss() = animateTo(0f) { removeFeedback() }
        fun finish(then: () -> Unit) = animateTo(1f, then)

        private fun animateTo(target: Float, end: () -> Unit) {
            anim?.cancel()
            anim = ValueAnimator.ofFloat(progress, target).apply {
                duration = (200 * abs(target - progress) + 60).toLong()
                addUpdateListener { setProgress(it.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = end()
                })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            paint.color = Color.BLACK
            val shadow = dp(24f).toFloat()
            when (edge) {
                Edge.LEFT -> {
                    val x = w * progress
                    canvas.drawRect(0f, 0f, x, h, paint)
                    shade.shader = LinearGradient(x, 0f, x + shadow, 0f, 0x99000000.toInt(), 0, Shader.TileMode.CLAMP)
                    canvas.drawRect(x, 0f, x + shadow, h, shade)
                }
                Edge.RIGHT -> {
                    val x = w * (1 - progress)
                    canvas.drawRect(x, 0f, w, h, paint)
                    shade.shader = LinearGradient(x, 0f, x - shadow, 0f, 0x99000000.toInt(), 0, Shader.TileMode.CLAMP)
                    canvas.drawRect(x - shadow, 0f, x, h, shade)
                }
                Edge.TOP -> {
                    // Closing: the window dims and drops away.
                    val y = h * progress
                    canvas.drawColor(Color.argb((200 * progress).toInt(), 0, 0, 0))
                    canvas.drawRect(0f, 0f, w, y, paint)
                }
            }
        }
    }
}
