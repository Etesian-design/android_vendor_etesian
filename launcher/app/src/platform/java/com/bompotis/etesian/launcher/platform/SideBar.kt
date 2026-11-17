package com.bompotis.etesian.launcher.platform

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.bompotis.etesian.launcher.IconFactory
import com.bompotis.etesian.launcher.N9
import com.bompotis.etesian.launcher.N9Sounds
import com.bompotis.etesian.launcher.Prefs
import com.bompotis.etesian.launcher.R
import kotlin.math.abs

/**
 * Breeze, the one-hand control: a slim bar on a screen edge, or, dragged away from the edges, a see-through
 * squircle button with Etesian's logo that floats over everything. Both answer taps, double taps and short
 * flicks with the actions picked in settings; one of them can be Breeze's menu ([BreezeMenu]). Long-press it,
 * then drag it anywhere: let go near the left or right edge and it becomes the bar there, anywhere else the button.
 */
class SideBar(private val ctx: Context, private val perform: (String) -> Unit) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private var view: Bar? = null
    private var lp: WindowManager.LayoutParams? = null
    private var onLeft = false
    /** The floating button, not the edge bar. */
    private var free = false
    /** Long-pressed: the stream moves Breeze instead of making gestures. */
    private var moving = false
    private val menu = BreezeMenu(ctx)

    /** Screen area Breeze takes, so touches starting on it are Breeze's. */
    val bounds = Rect()

    private val barW get() = (14 * density).toInt()
    private val barH get() = (130 * density).toInt()
    private val button get() = (56 * density).toInt()
    /** Let go this close to the left or right edge and Breeze is the bar there. */
    private val edgeSnap get() = 40 * density

    fun show() {
        hide()
        val side = Prefs.sideBarSide(ctx)
        free = side == Prefs.SIDE_FREE
        onLeft = side == "left"
        val screenH = ctx.resources.displayMetrics.heightPixels
        val screenW = ctx.resources.displayMetrics.widthPixels
        val w = if (free) button else barW
        val h = if (free) button else barH
        val y = (screenH * Prefs.sideBarPosition(ctx) / 100f - h / 2).toInt().coerceIn(0, screenH - h)
        val x = if (free) (screenW * Prefs.sideBarX(ctx) / 100f - w / 2).toInt().coerceIn(0, screenW - w) else 0
        val v = Bar(ctx)
        val lp = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or (if (free || onLeft) Gravity.LEFT else Gravity.RIGHT)
            this.x = x
            this.y = y
            title = "EtesianBreeze"
        }
        runCatching { wm.addView(v, lp); view = v; this.lp = lp }
        when {
            free -> bounds.set(x, y, x + w, y + h)
            onLeft -> bounds.set(0, y, w, y + h)
            else -> bounds.set(screenW - w, y, screenW, y + h)
        }
    }

    fun hide() {
        menu.dismiss()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        bounds.setEmpty()
    }

    private fun run(action: String) {
        if (action == Prefs.ACTION_MENU) {
            if (menu.showing) menu.dismiss() else menu.show(Rect(bounds), free, onLeft)
            return
        }
        menu.dismiss()
        perform(action)
    }

    private val flick = 36 * density
    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onLongPress(e: MotionEvent) {
            moving = true
            menu.dismiss()
            view?.let { N9Sounds.longPress(it) }
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            run(Prefs.sideBarAction(ctx, Prefs.SIDEBAR_TAP))
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            run(Prefs.sideBarAction(ctx, Prefs.SIDEBAR_DOUBLE_TAP))
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val start = e1 ?: return false
            val dx = e2.rawX - start.rawX
            val dy = e2.rawY - start.rawY
            // Inwards: away from the bar's edge; for the button, towards the middle of the screen.
            val inward = if (free) (if (start.rawX < ctx.resources.displayMetrics.widthPixels / 2f) dx else -dx)
                else dx * (if (onLeft) 1 else -1)
            val gesture = when {
                inward > flick && abs(dx) > abs(dy) -> Prefs.SIDEBAR_FLICK_IN
                dy < -flick -> Prefs.SIDEBAR_FLICK_UP
                dy > flick -> Prefs.SIDEBAR_FLICK_DOWN
                else -> return false
            }
            run(Prefs.sideBarAction(ctx, gesture))
            return true
        }
    })

    /**
     * Touches that start on Breeze, fed in from the input monitor (its own window doesn't take touches, so it
     * never gets in the way of the application).
     */
    fun onTouch(e: MotionEvent) {
        view?.active = e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE
        view?.invalidate()
        if (moving) { move(e); return }
        detector.onTouchEvent(e)
    }

    /** A touch outside Breeze and its menu closes the menu. */
    fun onOutsideDown(x: Float, y: Float) { if (menu.showing && !menu.contains(x, y)) menu.dismiss() }

    /**
     * Follows the finger as the button; on release the place is saved (and Breeze shown there again):
     * near the left or right edge the bar on that side, anywhere else the floating button.
     */
    private fun move(e: MotionEvent) {
        val v = view ?: return
        val p = lp ?: return
        val screenW = ctx.resources.displayMetrics.widthPixels
        val screenH = ctx.resources.displayMetrics.heightPixels
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                // While moving it is always the button, under the finger.
                free = true
                p.width = button; p.height = button
                p.gravity = Gravity.TOP or Gravity.LEFT
                p.x = (e.rawX - button / 2f).toInt().coerceIn(0, screenW - button)
                p.y = (e.rawY - button / 2f).toInt().coerceIn(0, screenH - button)
                runCatching { wm.updateViewLayout(v, p) }
                v.invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moving = false
                // The detector saw the press start: end it, so the release is not a flick.
                MotionEvent.obtain(e).also { it.action = MotionEvent.ACTION_CANCEL; detector.onTouchEvent(it); it.recycle() }
                val side = when {
                    e.rawX < edgeSnap -> "left"
                    e.rawX > screenW - edgeSnap -> "right"
                    else -> Prefs.SIDE_FREE
                }
                Prefs.setSideBarPlace(ctx, side, (e.rawY * 100 / screenH).toInt(), (e.rawX * 100 / screenW).toInt())
                // Saving the same place as before changes no setting: show it here either way.
                show()
            }
        }
    }

    @SuppressLint("ViewConstructor")
    private inner class Bar(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val logo = ctx.getDrawable(R.drawable.ic_launcher_foreground)
        var active = false

        override fun onDraw(canvas: Canvas) {
            if (free) {
                // A see-through squircle with the logo: visible over any app, never hiding much of it.
                val s = minOf(width, height).toFloat()
                paint.style = Paint.Style.FILL
                paint.color = if (active) (N9.ACCENT and 0x00FFFFFF) or 0xCC000000.toInt() else 0x66000000
                canvas.drawPath(IconFactory.shape(s), paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = density
                paint.color = 0x55FFFFFF
                canvas.drawPath(IconFactory.shape(s), paint)
                logo?.apply { alpha = if (active) 255 else 200; setBounds(0, 0, s.toInt(), s.toInt()); draw(canvas) }
                return
            }
            paint.style = Paint.Style.FILL
            val w = 4 * density
            val x = if (onLeft) 3 * density else width - 3 * density - w
            paint.color = if (active) N9.ACCENT else 0x66FFFFFF
            canvas.drawRoundRect(x, 0f, x + w, height.toFloat(), w / 2, w / 2, paint)
        }
    }
}
