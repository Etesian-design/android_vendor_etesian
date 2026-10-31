package com.bompotis.etesian.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.PathParser

/** Simple white glyphs on a 24-unit grid, for lit squircle tiles and icon buttons. */
object Glyphs {
    val PATHS = mapOf(
        "flight" to "M21,16v-2l-8,-5V3.5C13,2.67 12.33,2 11.5,2S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5L21,16z",
        "torch" to "M6,2h12v3H6zM6,7v1l2,3v11h8V11l2,-3V7H6zM12,15.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
        "rotate" to "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z",
        "location" to "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z",
        "data" to "M16,17.01V10h-2v7.01h-3L15,21l4,-3.99h-3zM9,3L5,6.99h3V14h2V6.99h3L9,3z",
        "wifi" to "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
        "bt" to "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z",
        "moon" to "M12.34,2.02C6.59,1.82 2,6.42 2,12c0,5.52 4.48,10 10,10 3.71,0 6.93,-2.02 8.66,-5.02C13.15,16.73 8.57,8.55 12.34,2.02z",
        "pencil" to "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
        "drag" to "M20,9H4v2h16V9zM4,15h16v-2H4V15z",
        "prev" to "M6,6h2v12H6zM9.5,12l8.5,6V6z",
        "play" to "M8,5v14l11,-7z",
        "pause" to "M6,19h4V5H6v14zM14,5v14h4V5h-4z",
        "next" to "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z",
        "camera" to "M12,15.2A3.2,3.2 0,1 0,12 8.8a3.2,3.2 0,0 0,0 6.4zM9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z",
        "mic" to "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
        "search" to "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
        "add" to "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
        "close" to "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
        "brightness" to "M20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69L12,23.31 15.31,20H20v-4.69L23.31,12 20,8.69zM12,18c-3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6 6,2.69 6,6 -2.69,6 -6,6z",
        "record" to "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z",
        "chevron" to "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z",
        "lock" to "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z",
        "phone" to "M20.01,15.38c-1.23,0 -2.42,-0.2 -3.53,-0.56 -0.35,-0.12 -0.74,-0.03 -1.01,0.24l-1.57,1.97c-2.83,-1.35 -5.48,-3.9 -6.89,-6.83l1.95,-1.66c0.27,-0.28 0.35,-0.67 0.24,-1.02 -0.37,-1.11 -0.56,-2.3 -0.56,-3.53 0,-0.54 -0.45,-0.99 -0.99,-0.99H4.19C3.65,3 3,3.24 3,3.99 3,13.28 10.73,21 20.01,21c0.71,0 0.99,-0.63 0.99,-1.18v-3.45c0,-0.54 -0.45,-0.99 -0.99,-0.99z",
        "clock" to "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
        "star" to "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
        "calendar" to "M19,4h-1V2h-2v2H8V2H6v2H5c-1.11,0 -1.99,0.9 -1.99,2L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM19,20H5V10h14v10zM19,8H5V6h14v2z",
        "cast" to "M21,3H3c-1.1,0 -2,0.9 -2,2v3h2V5h18v14h-7v2h7c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM1,18v3h3c0,-1.66 -1.34,-3 -3,-3zM1,14v2c2.76,0 5,2.24 5,5h2c0,-3.87 -3.13,-7 -7,-7zM1,10v2c4.97,0 9,4.03 9,9h2c0,-6.08 -4.93,-11 -11,-11z",
        "music" to "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z",
    )

    fun path(name: String, size: Float): Path = Path(PathParser.createPathFromPathData(PATHS.getValue(name))).apply {
        transform(Matrix().apply { setScale(size / 24f, size / 24f) })
    }

    /** A glyph drawable in [color], filling its bounds. */
    fun drawable(name: String, color: Int = N9.TEXT, outline: Boolean = false): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (outline) { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        }
        override fun draw(c: Canvas) {
            val b = bounds; val s = minOf(b.width(), b.height()).toFloat()
            // Outline glyphs: a 1.6/24 stroke, inset so it stays inside the bounds.
            if (outline) paint.strokeWidth = s * 1.6f / 24f
            c.save(); c.translate(b.left + (b.width() - s) / 2, b.top + (b.height() - s) / 2)
            c.drawPath(path(name, s), paint); c.restore()
        }
        override fun setAlpha(a: Int) { paint.alpha = a }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

/**
 * The N9 lit squircle (n = 2.8): a tile lit from the top (lighter top, darker bottom, 1px rim)
 * with an optional glyph; used for toggles, icon buttons and the edit ✕ / ✎ marks.
 */
class LitSquircle(ctx: Context, var color: Int, var glyph: String? = null, var glyphColor: Int = N9.TEXT,
                  var glyphScale: Float = 0.55f) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun set(color: Int, glyph: String? = this.glyph) { this.color = color; this.glyph = glyph; invalidate() }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        c.save(); c.translate((width - s) / 2, (height - s) / 2)
        val shape = IconFactory.superellipse(s, 2.8)
        paint.shader = LinearGradient(0f, 0f, 0f, s, lighter(color, 0.28f), darker(color, 0.25f), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        c.drawPath(shape, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density
        paint.color = 0x40FFFFFF
        c.drawPath(shape, paint)
        paint.style = Paint.Style.FILL
        glyph?.let { g ->
            val gs = s * glyphScale
            paint.color = glyphColor
            c.translate((s - gs) / 2, (s - gs) / 2)
            c.drawPath(Glyphs.path(g, gs), paint)
        }
        c.restore()
    }

    companion object {
        fun lighter(c: Int, f: Float): Int = android.graphics.Color.argb(android.graphics.Color.alpha(c),
            (android.graphics.Color.red(c) + (255 - android.graphics.Color.red(c)) * f).toInt(),
            (android.graphics.Color.green(c) + (255 - android.graphics.Color.green(c)) * f).toInt(),
            (android.graphics.Color.blue(c) + (255 - android.graphics.Color.blue(c)) * f).toInt())
        fun darker(c: Int, f: Float): Int = android.graphics.Color.argb(android.graphics.Color.alpha(c),
            (android.graphics.Color.red(c) * (1 - f)).toInt(), (android.graphics.Color.green(c) * (1 - f)).toInt(),
            (android.graphics.Color.blue(c) * (1 - f)).toInt())

        /** The red edit ✕ (#F78673→#EF2C10, white cross). */
        fun remove(ctx: Context) = LitSquircle(ctx, 0xFFF35A42.toInt(), "close", glyphScale = 0.7f)
        /** The white edit ✎ (white lit, dark pencil). */
        fun pencil(ctx: Context) = LitSquircle(ctx, 0xFFE4E4E8.toInt(), "pencil", 0xFF2A2A2C.toInt(), 0.6f)
    }
}
