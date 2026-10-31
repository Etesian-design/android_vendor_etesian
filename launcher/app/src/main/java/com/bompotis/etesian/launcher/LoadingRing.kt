package com.bompotis.etesian.launcher

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The N9 "application is starting" indicator: a white arc running around the icon's squircle
 * outline until the application has drawn itself. Shown in the icon view's overlay.
 */
class LoadingRing private constructor(private val view: View) : Drawable() {
    private val density = view.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val outline = Path()
    private val arc = Path()
    private val measure = PathMeasure()
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidateSelf() }
    }

    override fun onBoundsChange(b: android.graphics.Rect) {
        // Along the icon's squircle edge, as on the N9.
        val inset = paint.strokeWidth / 2
        val size = b.width().coerceAtMost(b.height()) - 2 * inset
        outline.reset()
        outline.addPath(IconFactory.shape(size).apply {
            offset(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f)
        })
        measure.setPath(outline, true)
    }

    override fun draw(canvas: Canvas) {
        val length = measure.length
        if (length <= 0f) return
        val start = phase * length
        val end = start + length * ARC
        arc.reset()
        measure.getSegment(start, end.coerceAtMost(length), arc, true)
        if (end > length) measure.getSegment(0f, end - length, arc, true)
        canvas.drawPath(arc, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT

    fun stop() {
        animator.cancel()
        view.overlay.remove(this)
        view.removeCallbacks(timeout)
    }

    /** In case the application never shows (crashed, or started something else). */
    private val timeout = Runnable { stop() }

    companion object {
        private const val ARC = 0.3f
        private const val TIMEOUT = 10_000L

        fun start(on: View): LoadingRing = LoadingRing(on).also { ring ->
            // On the icon's own edge: the overlay is clipped to the view.
            ring.setBounds(0, 0, on.width, on.height)
            on.overlay.add(ring)
            ring.animator.start()
            on.postDelayed(ring.timeout, TIMEOUT)
        }
    }
}
