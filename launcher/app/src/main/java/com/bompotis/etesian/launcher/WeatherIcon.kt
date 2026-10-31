package com.bompotis.etesian.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Weather symbol in the spirit of the N9's glossy weather icons, drawn in code: a glowing sun
 * (or pale moon at night), soft grey-white clouds, rain streaks, snow flakes, fog bars or a
 * lightning bolt, chosen from the WMO weather code.
 */
class WeatherIcon(private val code: Int, private val day: Boolean) : Drawable() {

    private enum class Sky { CLEAR, FEW_CLOUDS, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

    private val sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.FEW_CLOUDS
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.THUNDER
        else -> Sky.CLOUDY
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        canvas.save()
        canvas.translate(b.left + (b.width() - s) / 2f, b.top + (b.height() - s) / 2f)
        when (sky) {
            Sky.CLEAR -> orb(canvas, s * 0.5f, s * 0.5f, s * 0.26f, s)
            Sky.FEW_CLOUDS -> {
                orb(canvas, s * 0.64f, s * 0.36f, s * 0.2f, s)
                cloud(canvas, s, s * 0.08f, s * 0.18f)
            }
            Sky.CLOUDY -> {
                cloud(canvas, s * 0.8f, s * 0.22f, s * 0.04f, dark = true)
                cloud(canvas, s, s * 0.04f, s * 0.2f)
            }
            Sky.FOG -> {
                cloud(canvas, s, s * 0.04f, s * 0.06f)
                bars(canvas, s)
            }
            Sky.DRIZZLE, Sky.RAIN -> {
                if (sky == Sky.DRIZZLE) orb(canvas, s * 0.66f, s * 0.3f, s * 0.17f, s)
                cloud(canvas, s, s * 0.04f, s * 0.06f, dark = sky == Sky.RAIN)
                streaks(canvas, s, if (sky == Sky.RAIN) 4 else 3)
            }
            Sky.SNOW -> {
                cloud(canvas, s, s * 0.04f, s * 0.06f)
                flakes(canvas, s)
            }
            Sky.THUNDER -> {
                cloud(canvas, s, s * 0.04f, s * 0.06f, dark = true)
                bolt(canvas, s)
            }
        }
        canvas.restore()
    }

    /** Sun by day (warm, with a soft glow), moon by night. */
    private fun orb(c: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        paint.color = Color.WHITE
        if (day) {
            paint.shader = RadialGradient(cx, cy, r * 1.9f, intArrayOf(0x66FFC23A, 0x00FFC23A),
                null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r * 1.9f, paint)
            paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.3f,
                intArrayOf(0xFFFFF3A0.toInt(), 0xFFFFC21E.toInt(), 0xFFF08A10.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, paint)
        } else {
            paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
                intArrayOf(0xFFFFFFF0.toInt(), 0xFFD8DDE6.toInt()), null, Shader.TileMode.CLAMP)
            path.reset()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            val cut = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW) }
            path.op(cut, Path.Op.DIFFERENCE)
            c.drawPath(path, paint)
        }
        paint.shader = null
    }

    /** Three overlapping puffs on a flat base, lit from above. */
    private fun cloud(c: Canvas, s: Float, dx: Float, top: Float, dark: Boolean = false) {
        val w = s * 0.84f
        val left = dx
        val base = top + s * 0.5f
        path.reset()
        path.addCircle(left + w * 0.28f, base - s * 0.14f, s * 0.16f, Path.Direction.CW)
        path.addCircle(left + w * 0.55f, base - s * 0.22f, s * 0.22f, Path.Direction.CW)
        path.addCircle(left + w * 0.8f, base - s * 0.12f, s * 0.14f, Path.Direction.CW)
        path.addRoundRect(RectF(left + w * 0.1f, base - s * 0.16f, left + w * 0.95f, base),
            s * 0.08f, s * 0.08f, Path.Direction.CW)
        // Soft shadow under the cloud.
        paint.color = 0x33000000
        c.save(); c.translate(0f, s * 0.03f); c.drawPath(path, paint); c.restore()
        paint.color = Color.WHITE // gradients are drawn with the paint's alpha
        val (hi, lo) = if (dark) 0xFFD4D8DE.toInt() to 0xFF8E959F.toInt()
            else 0xFFFFFFFF.toInt() to 0xFFC9CED6.toInt()
        paint.shader = LinearGradient(0f, base - s * 0.44f, 0f, base, hi, lo, Shader.TileMode.CLAMP)
        c.drawPath(path, paint)
        paint.shader = null
    }

    private fun streaks(c: Canvas, s: Float, n: Int) {
        paint.color = 0xFF5FA8E8.toInt()
        paint.strokeWidth = s * 0.045f
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until n) {
            val x = s * (0.28f + i * 0.5f / n)
            c.drawLine(x, s * 0.7f, x - s * 0.07f, s * 0.9f, paint)
        }
    }

    private fun flakes(c: Canvas, s: Float) {
        paint.color = Color.WHITE
        for ((x, y) in listOf(0.3f to 0.76f, 0.5f to 0.88f, 0.7f to 0.76f, 0.4f to 0.95f, 0.6f to 0.95f)) {
            c.drawCircle(s * x, s * y, s * 0.045f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.012f
        paint.color = 0xFF9AA4B0.toInt()
        for ((x, y) in listOf(0.3f to 0.76f, 0.5f to 0.88f, 0.7f to 0.76f, 0.4f to 0.95f, 0.6f to 0.95f)) {
            c.drawCircle(s * x, s * y, s * 0.045f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun bars(c: Canvas, s: Float) {
        paint.color = 0xCCB8BEC6.toInt()
        for (i in 0 until 3) {
            val y = s * (0.66f + i * 0.11f)
            c.drawRoundRect(RectF(s * (0.14f + i * 0.04f), y, s * (0.86f - i * 0.04f), y + s * 0.05f),
                s * 0.025f, s * 0.025f, paint)
        }
    }

    private fun bolt(c: Canvas, s: Float) {
        path.reset()
        path.moveTo(s * 0.54f, s * 0.6f)
        path.lineTo(s * 0.38f, s * 0.82f)
        path.lineTo(s * 0.5f, s * 0.82f)
        path.lineTo(s * 0.42f, s * 0.99f)
        path.lineTo(s * 0.66f, s * 0.74f)
        path.lineTo(s * 0.53f, s * 0.74f)
        path.lineTo(s * 0.62f, s * 0.6f)
        path.close()
        paint.color = Color.WHITE
        paint.shader = LinearGradient(0f, s * 0.6f, 0f, s, 0xFFFFF07A.toInt(), 0xFFF0A010.toInt(),
            Shader.TileMode.CLAMP)
        c.drawPath(path, paint)
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
