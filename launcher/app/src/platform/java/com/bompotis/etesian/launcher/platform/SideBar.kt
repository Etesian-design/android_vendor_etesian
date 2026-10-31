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
import com.bompotis.etesian.launcher.N9
import com.bompotis.etesian.launcher.Prefs
import kotlin.math.abs

/**
 * Back for N9 mode, in the style of Xperia's Side Sense: a slim bar on the screen edge that
 * answers double taps and short flicks with configurable actions.
 */
class SideBar(private val ctx: Context, private val perform: (String) -> Unit) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private var view: Bar? = null
    private var onLeft = false

    /** Screen area the bar takes, so edge swipes starting on it are left to the bar. */
    val bounds = Rect()

    fun show() {
        hide()
        onLeft = Prefs.sideBarSide(ctx) == "left"
        val w = (14 * density).toInt()
        val h = (130 * density).toInt()
        val screenH = ctx.resources.displayMetrics.heightPixels
        val screenW = ctx.resources.displayMetrics.widthPixels
        val y = (screenH * Prefs.sideBarPosition(ctx) / 100f - h / 2).toInt()
        val v = Bar(ctx)
        val lp = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or (if (onLeft) Gravity.LEFT else Gravity.RIGHT)
            this.y = y
            title = "EtesianSideBar"
        }
        runCatching { wm.addView(v, lp); view = v }
        bounds.set(if (onLeft) 0 else screenW - w, y, if (onLeft) w else screenW, y + h)
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        bounds.setEmpty()
    }

    private val flick = 36 * density
    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            perform(Prefs.sideBarAction(ctx, Prefs.SIDEBAR_DOUBLE_TAP))
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val start = e1 ?: return false
            val dx = (e2.rawX - start.rawX) * (if (onLeft) 1 else -1)
            val dy = e2.rawY - start.rawY
            val gesture = when {
                dx > flick && dx > abs(dy) -> Prefs.SIDEBAR_FLICK_IN
                dy < -flick -> Prefs.SIDEBAR_FLICK_UP
                dy > flick -> Prefs.SIDEBAR_FLICK_DOWN
                else -> return false
            }
            perform(Prefs.sideBarAction(ctx, gesture))
            return true
        }
    })

    /**
     * Touches that start on the bar, fed in from the input monitor (the bar's own window
     * doesn't take touches, so it never gets in the way of the application).
     */
    fun onTouch(e: MotionEvent) {
        view?.active = e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE
        view?.invalidate()
        detector.onTouchEvent(e)
    }

    @SuppressLint("ViewConstructor")
    private inner class Bar(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var active = false

        override fun onDraw(canvas: Canvas) {
            val w = 4 * density
            val x = if (onLeft) 3 * density else width - 3 * density - w
            paint.color = if (active) N9.ACCENT else 0x66FFFFFF
            canvas.drawRoundRect(x, 0f, x + w, height.toFloat(), w / 2, w / 2, paint)
        }
    }
}
