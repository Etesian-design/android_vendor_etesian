package com.bompotis.etesian.launcher.platform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.bompotis.etesian.launcher.N9
import com.bompotis.etesian.launcher.platform.EdgeGestures.Edge

/** A round chevron that slides in from the edge while a back swipe is armed. */
class BackHint(private val ctx: Context, private val edge: Edge) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val size = (52 * density).toInt()
    private val backMin = 40 * density
    private var view: Chevron? = null

    fun show(x: Float, y: Float) {
        val v = Chevron(ctx)
        val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or (if (edge == Edge.RIGHT) Gravity.RIGHT else Gravity.LEFT)
            this.y = when (edge) {
                Edge.BOTTOM -> ctx.resources.displayMetrics.heightPixels - size * 2
                else -> (y - size / 2).toInt()
            }
            this.x = if (edge == Edge.BOTTOM) ((x - size / 2).toInt()) else 0
            title = "EtesianBack"
        }
        runCatching { wm.addView(v, lp); view = v }
        update(0f)
    }

    fun update(distance: Float) {
        val v = view ?: return
        val p = (distance / backMin).coerceIn(0f, 1f)
        v.alpha = p
        v.armed = p >= 1f
        v.translationX = when (edge) {
            Edge.LEFT -> (p - 1) * size / 2
            Edge.RIGHT -> (1 - p) * size / 2
            else -> 0f
        }
        v.invalidate()
    }

    fun hide() {
        val v = view ?: return
        view = null
        v.animate().alpha(0f).setDuration(150).withEndAction { runCatching { wm.removeView(v) } }.start()
    }

    /** Gone at once, for when the swipe turned out not to be Back after all. */
    fun hideNow() {
        val v = view ?: return
        view = null
        runCatching { wm.removeView(v) }
    }

    private inner class Chevron(ctx: Context) : View(ctx) {
        var armed = false
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0xFFFFFFFF.toInt()
        }

        override fun onDraw(canvas: Canvas) {
            val c = width / 2f
            fill.color = if (armed) N9.ACCENT else 0xCC1C1C1C.toInt()
            canvas.drawCircle(c, c, c * 0.9f, fill)
            val a = c * 0.32f
            canvas.drawPath(Path().apply {
                moveTo(c + a * 0.5f, c - a); lineTo(c - a * 0.5f, c); lineTo(c + a * 0.5f, c + a)
            }, stroke)
        }
    }
}
