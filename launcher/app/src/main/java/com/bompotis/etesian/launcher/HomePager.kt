package com.bompotis.etesian.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Etesian home views laid side by side. Swiping is circular, e.g.
 * Events ⟷ Applications ⟷ Open applications ⟷ Events.
 */
class HomePager(context: Context) : FrameLayout(context) {

    /** Fractional page position; children are positioned relative to it modulo the page count. */
    private var offset = 1f
    var topInset = 0
        set(v) { field = v; invalidate() }
    var onPageSettled: ((Int) -> Unit)? = null

    private val config = ViewConfiguration.get(context)
    private val slop = config.scaledTouchSlop
    private val flingThreshold = config.scaledMinimumFlingVelocity * 6
    private var downX = 0f
    private var downY = 0f
    private var startOffset = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null
    private var anim: ValueAnimator? = null
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = N9.TEXT }

    /** Home wallpaper: one picture panning across all the views (wider than the screen)... */
    var panorama: android.graphics.Bitmap? = null
        set(v) { field = v; invalidate() }
    /** ...or one per view, in view order (null: that view stays black). */
    var perPage: List<android.graphics.Bitmap?> = emptyList()
        set(v) { field = v; invalidate() }
    private val wallPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = android.graphics.Rect()
    private val dst = android.graphics.RectF()

    private val count get() = maxOf(childCount, 1)

    private companion object {
        /** The scrolling wallpaper's width, in screens. */
        const val PAN_WIDTH = 1.35f
    }
    val currentPage get() = Math.floorMod(offset.roundToInt(), count)

    init {
        setBackgroundColor(N9.BACKGROUND)
        setWillNotDraw(false)
    }

    private fun wrap(d: Float): Float {
        val n = count.toFloat()
        val half = n / 2f
        return ((d + half) % n + n) % n - half
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        applyOffsets()
    }

    private fun applyOffsets() {
        val w = width.toFloat()
        if (w == 0f) return
        for (i in 0 until childCount) {
            val d = wrap(i - offset)
            val c = getChildAt(i)
            c.translationX = d * w
            c.visibility = if (abs(d) < 1f) VISIBLE else INVISIBLE
        }
        invalidate()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                dragging = false
                if (anim?.isRunning == true && (getChildAt(currentPage) as? HomePage)?.holdsPaging() != true) {
                    anim?.cancel()
                    startOffset = offset
                    dragging = true
                }
            }
            MotionEvent.ACTION_MOVE -> maybeStartDrag(ev)
            MotionEvent.ACTION_POINTER_DOWN -> if (!dragging) return false
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) settle(ev.actionMasked == MotionEvent.ACTION_UP)
        }
        return dragging
    }

    private fun maybeStartDrag(ev: MotionEvent) {
        if (dragging || ev.pointerCount > 1 || childCount < 2) return
        if ((getChildAt(currentPage) as? HomePage)?.holdsPaging() == true) return
        val dx = ev.x - downX
        val dy = ev.y - downY
        if (abs(dx) > slop && abs(dx) > abs(dy) * 1.3f) {
            dragging = true
            startOffset = offset
            downX = ev.x
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_DOWN) track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; track(ev) }
            MotionEvent.ACTION_MOVE -> {
                maybeStartDrag(ev)
                if (dragging) {
                    offset = startOffset - (ev.x - downX) / width
                    applyOffsets()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                if (dragging) settle(ev.actionMasked == MotionEvent.ACTION_UP)
        }
        return true
    }

    private fun track(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { velocity?.recycle(); velocity = VelocityTracker.obtain() }
        velocity?.addMovement(ev)
    }

    private fun settle(fling: Boolean) {
        dragging = false
        val v = velocity?.run { computeCurrentVelocity(1000); xVelocity } ?: 0f
        val base = startOffset.roundToInt()
        var target = when {
            fling && v < -flingThreshold -> kotlin.math.floor(offset) + 1
            fling && v > flingThreshold -> kotlin.math.ceil(offset) - 1
            else -> offset.roundToInt().toFloat()
        }
        target = min(base + 1f, max(base - 1f, target))
        animateTo(target)
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        val from = offset
        anim = ValueAnimator.ofFloat(from, target).apply {
            duration = (220 + 180 * abs(target - from)).toLong().coerceAtMost(400)
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { offset = it.animatedValue as Float; applyOffsets() }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    offset = Math.floorMod(target.roundToInt(), count).toFloat()
                    applyOffsets()
                    onPageSettled?.invoke(currentPage)
                }
            })
            start()
        }
    }

    fun showPage(page: Int, animate: Boolean = true) {
        if (page !in 0 until childCount) return
        val target = offset + wrap(page - offset)
        if (animate && isLaidOut) animateTo(target) else {
            anim?.cancel()
            offset = page.toFloat()
            applyOffsets()
            onPageSettled?.invoke(page)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return
        panorama?.let { b ->
            // Cover the screen with room to pan: a third wider than it.
            val scale = max(h / b.height, w * PAN_WIDTH / b.width)
            val dw = b.width * scale; val dh = b.height * scale
            val n = count.toFloat()
            val p = ((offset % n) + n) % n
            // Left to right across the views; from the last view round to the first it pans back.
            val pan = if (n <= 1f) 0.5f else if (p <= n - 1f) p / (n - 1f) else n - p
            dst.set(-(dw - w) * pan, (h - dh) / 2f, -(dw - w) * pan + dw, (h - dh) / 2f + dh)
            canvas.drawBitmap(b, null, dst, wallPaint)
            return
        }
        for (i in 0 until min(childCount, perPage.size)) {
            val b = perPage[i] ?: continue
            val d = wrap(i - offset)
            if (abs(d) >= 1f) continue
            // Each view's picture moves with its view, covering it (centre crop).
            val scale = max(w / b.width, h / b.height)
            val sw = w / scale; val sh = h / scale
            src.set(((b.width - sw) / 2).toInt(), ((b.height - sh) / 2).toInt(), ((b.width + sw) / 2).toInt(), ((b.height + sh) / 2).toInt())
            dst.set(d * w, 0f, d * w + w, h)
            canvas.drawBitmap(b, src, dst, wallPaint)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // One dot per view, under the status bar or above the bottom edge (hidden by default:
        // the N9 had none).
        if (childCount < 2) return
        val where = Prefs.pageDots(context)
        if (where == Prefs.DOTS_HIDDEN) return
        val r = dp(2.5f).toFloat()
        val gap = dp(12f)
        val bottomInset = rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars())?.bottom ?: 0
        val cy = if (where == Prefs.DOTS_BOTTOM) height - bottomInset - dp(12f).toFloat()
            else topInset + dp(12f).toFloat()
        val cx = width / 2f
        for (i in 0 until childCount) {
            val d = abs(wrap(i - offset))
            dot.alpha = (70 + 185 * max(0f, 1f - d)).toInt()
            canvas.drawCircle(cx + (i - (childCount - 1) / 2f) * gap, cy, r, dot)
        }
    }
}
