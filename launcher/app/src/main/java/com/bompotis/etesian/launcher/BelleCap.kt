package com.bompotis.etesian.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Belle's toggle cap (BelleCap.vue, measured from the 701's switch art, a 72 × 82 face): a blue-grey metal squircle
 * (n = 2.95) in a well. On, the cap sits up and the well glows blue below it; off, it sits down in a dark well. A flip
 * slides the cap and fades the glow ([set] with animate), so the toggle answers the finger at once.
 */
class BelleCap(ctx: Context, var glyph: String? = null) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    /** 0: off (cap down, dark well), 1: on (cap up, blue glow). */
    private var t = 0f
    private var anim: ValueAnimator? = null
    var on = false
        private set

    fun set(on: Boolean, animate: Boolean) {
        if (on == this.on && anim == null) { if (!animate) { t = if (on) 1f else 0f; invalidate() }; return }
        this.on = on
        anim?.cancel(); anim = null
        val to = if (on) 1f else 0f
        if (!animate || !isAttachedToWindow) { t = to; invalidate(); return }
        anim = ValueAnimator.ofFloat(t, to).apply {
            duration = (180 * abs(to - t)).toLong().coerceAtLeast(60)
            interpolator = DecelerateInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { if (anim === a) anim = null }
            })
            start()
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        // The face keeps the art's 72 × 82 proportions.
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 82f / 72f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val k = width / 72f
        if (k <= 0f) return
        c.save(); c.scale(k, k)
        val metal = LinearGradient(0f, 0f, 0f, 82f, 0xFFA6B6C1.toInt(), 0xFF555E70.toInt(), Shader.TileMode.CLAMP)
        // body
        paint.style = Paint.Style.FILL; paint.shader = metal
        c.drawPath(rect(0f, 0f, 72f, 82f), paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0x4D010101
        c.drawPath(rect(0f, 0f, 72f, 82f), paint)
        // well: dark when off, the blue glow fading in as the cap rises
        val well = rect(3f, 3f, 66f, 76f)
        paint.style = Paint.Style.FILL; paint.color = 0xFF4E5662.toInt(); c.drawPath(well, paint)
        if (t > 0f) {
            paint.shader = LinearGradient(0f, 79f, 0f, 3f, intArrayOf(0xFF2FA7DD.toInt(), 0xFF2F86DD.toInt(), 0xFF355CA6.toInt()),
                floatArrayOf(0f, 0.16f, 1f), Shader.TileMode.CLAMP)
            paint.alpha = (255 * t).toInt(); c.drawPath(well, paint); paint.shader = null; paint.alpha = 255
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.8f
        paint.color = blend(0xFF808C9A.toInt(), 0xFF355CA6.toInt(), t); c.drawPath(well, paint)
        // cap: y 14 (down) → 4 (up)
        val capY = 14f - 10f * t
        val cap = rect(4f, capY, 64f, 64f)
        paint.style = Paint.Style.FILL; paint.shader = metal; c.drawPath(cap, paint)
        paint.shader = LinearGradient(0f, capY, 0f, capY + 64f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawPath(cap, paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0x4D010101; c.drawPath(cap, paint)
        // the glyph rides on the cap: grey off, lit Belle blue on
        glyph?.let { g ->
            val gs = 34f
            paint.style = Paint.Style.FILL
            paint.color = blend(BelleClassic2.CAP_OFF, BelleClassic2.CAP_ON, t)
            c.translate(4f + (64f - gs) / 2, capY + (64f - gs) / 2)
            c.drawPath(Glyphs.path(g, gs), paint)
        }
        c.restore()
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * f)).toInt() shl s
        return ch(24) or ch(16) or ch(8) or ch(0)
    }

    /** A squircle (n = 2.95) filling the box at [x], [y]: never a rounded rectangle. */
    private fun rect(x: Float, y: Float, w: Float, h: Float, n: Double = 2.95): Path {
        val p = Path(); val steps = 96
        for (i in 0..steps) {
            val a = 2 * Math.PI * i / steps
            val cx = cos(a); val sy = sin(a)
            val px = x + w / 2 + (w / 2) * (abs(cx).pow(2 / n) * sign(cx)).toFloat()
            val py = y + h / 2 + (h / 2) * (abs(sy).pow(2 / n) * sign(sy)).toFloat()
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close(); return p
    }
}
