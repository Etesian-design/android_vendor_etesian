package com.bompotis.etesian.launcher.standby

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.bompotis.etesian.launcher.WeatherIcon
import android.view.View
import com.bompotis.etesian.launcher.N9

/**
 * The standby block: the battery glyph, the clock, the user's lines and the notification icons, in one colour on black,
 * drawn where [fx], [fy] (0..1 of the free space) put it. Sizes from the user's N9 with Billboard (n9px x 0.64 = dp):
 * a 104px light clock, 22px lines 33px apart, a 176 x 10 battery bar, a 32 x 22 battery glyph. Shared by
 * [StandbyDream] and the settings page's preview.
 */
class StandbyView(ctx: Context) : View(ctx) {
    var config = StandbyConfig()
        set(c) { field = c; requestLayout(); invalidate() }
    var values: Map<String, String> = emptyMap()
        set(v) { field = v; invalidate() }
    /** The notification icons: each app's icon and how many it has. */
    var icons: List<Pair<Drawable, Int>> = emptyList()
        set(v) { field = v; invalidate() }
    var fx = 0.5f
    var fy = 0.35f
    /** The preview draws smaller. */
    var scale = 1f
        set(s) { field = s; invalidate() }

    private val d get() = resources.displayMetrics.density * scale
    private val sp get() = resources.displayMetrics.scaledDensity * scale
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = N9.FONT_LIGHT }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = N9.FONT }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /** What is drawn, top to bottom. */
    private sealed class Row { abstract val h: Float; abstract val w: Float }
    private class TextRow(val text: String, val paint: Paint, override val h: Float) : Row() { override val w = paint.measureText(text) }
    private class BarRow(val pct: Int, override val w: Float, override val h: Float) : Row()
    private class GlyphRow(override val w: Float, override val h: Float) : Row()
    private class IconsRow(override val w: Float, override val h: Float) : Row()
    /** A line with the weather in it: the Events view's weather picture, then the line's text. */
    private class WeatherRow(val icon: Drawable, val text: String, val paint: Paint, val size: Float, override val h: Float) : Row() {
        override val w = size + size * 0.3f + paint.measureText(text)
    }
    private class Gap(override val h: Float) : Row() { override val w = 0f }

    private fun rows(): List<Row> {
        val c = config
        clockPaint.color = c.color; linePaint.color = c.color; stroke.color = c.color; fill.color = c.color
        clockPaint.textSize = 66 * d
        linePaint.textSize = c.size * sp
        val lineH = linePaint.textSize * 1.5f
        val out = mutableListOf<Row>()
        if (c.battery) { out += GlyphRow(20 * d, 14 * d); out += Gap(4 * d) }
        if (c.clock) {
            val t = values["time"].orEmpty()
            out += TextRow(t, clockPaint, clockPaint.textSize * 1.05f)
            out += Gap(if (c.lines.isEmpty()) 6 * d else 30 * d)
        }
        // With no lines: the N9's own, the compact date under the clock.
        val lines = c.lines.ifEmpty { listOf(StandbyConfig.Line(listOf(StandbyConfig.Part.Item("date_compact")))) }
        for (l in lines) {
            val bar = l.parts.size == 1 && (l.parts[0] as? StandbyConfig.Part.Item)?.id == StandbyItems.BAR
            if (bar) {
                val pct = values[StandbyItems.BAR]?.toIntOrNull()
                if (pct != null) { out += BarRow(pct, 113 * d, lineH) } else if (l.empty.isNotBlank()) out += TextRow(l.empty, linePaint, lineH)
                continue
            }
            val text = StandbyItems.text(l, values)
            val sky = values[StandbyItems.WEATHER]?.split('|')
            if (text != null && sky != null && sky.size >= 3 && l.parts.any { (it as? StandbyConfig.Part.Item)?.id == StandbyItems.WEATHER }) {
                val size = linePaint.textSize * 1.9f
                val icon = WeatherIcon(sky[0].toIntOrNull() ?: -1, sky[1] != "0").apply {
                    colorFilter = android.graphics.PorterDuffColorFilter(c.color, android.graphics.PorterDuff.Mode.SRC_IN) }
                out += WeatherRow(icon, text, linePaint, size, maxOf(lineH, size * 1.15f))
                continue
            }
            (text ?: l.empty).takeIf { it.isNotBlank() }?.let { out += TextRow(it, linePaint, lineH) }
        }
        if (c.icons && icons.isNotEmpty()) { out += Gap(12 * d); out += IconsRow(iconsWidth(), 22 * d) }
        return out
    }

    private fun iconsWidth(): Float = icons.sumOf { (_, n) -> (18 * d + 4 * d + linePaint.measureText("$n") + 14 * d).toDouble() }.toFloat() - 14 * d

    /** The block's size, for moving it. */
    fun blockSize(): Pair<Float, Float> { val r = rows(); return (r.maxOfOrNull { it.w } ?: 0f) to r.sumOf { it.h.toDouble() }.toFloat() }

    override fun onDraw(c: Canvas) {
        val rows = rows()
        val bw = rows.maxOfOrNull { it.w } ?: return
        val bh = rows.sumOf { it.h.toDouble() }.toFloat()
        val left = (width - bw).coerceAtLeast(0f) * fx
        var y = (height - bh).coerceAtLeast(0f) * fy
        for (r in rows) {
            val x = when (config.align) {
                StandbyConfig.Align.LEFT -> left
                StandbyConfig.Align.CENTRE -> left + (bw - r.w) / 2
                StandbyConfig.Align.RIGHT -> left + bw - r.w
            }
            when (r) {
                is TextRow -> c.drawText(r.text, x, y + r.h * 0.5f - (r.paint.ascent() + r.paint.descent()) / 2, r.paint)
                is BarRow -> {
                    val top = y + (r.h - 6 * d) / 2
                    stroke.strokeWidth = 1 * d
                    c.drawRect(x + 0.5f * d, top + 0.5f * d, x + r.w - 0.5f * d, top + 6 * d - 0.5f * d, stroke)
                    c.drawRect(x, top, x + r.w * r.pct / 100f, top + 6 * d, fill)
                }
                is GlyphRow -> drawBattery(c, x, y, r.w, r.h)
                is WeatherRow -> {
                    val top = (y + (r.h - r.size) / 2).toInt()
                    r.icon.setBounds(x.toInt(), top, (x + r.size).toInt(), top + r.size.toInt()); r.icon.draw(c)
                    c.drawText(r.text, x + r.size * 1.3f, y + r.h * 0.5f - (r.paint.ascent() + r.paint.descent()) / 2, r.paint)
                }
                is IconsRow -> {
                    var ix = x
                    for ((icon, n) in icons) {
                        val s = (18 * d).toInt()
                        icon.setBounds(ix.toInt(), (y + (r.h - s) / 2).toInt(), ix.toInt() + s, (y + (r.h + s) / 2).toInt())
                        icon.setTint(config.color); icon.draw(c)
                        ix += s + 4 * d
                        c.drawText("$n", ix, y + r.h / 2 - (linePaint.ascent() + linePaint.descent()) / 2, linePaint)
                        ix += linePaint.measureText("$n") + 14 * d
                    }
                }
                is Gap -> {}
            }
            y += r.h
        }
    }

    /** The N9's battery glyph: an outline with a nub, filled to the level, a tick when charging. */
    private fun drawBattery(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        stroke.strokeWidth = 1.3f * d
        val body = RectF(x + stroke.strokeWidth / 2, y + stroke.strokeWidth / 2, x + w - 3 * d, y + h - stroke.strokeWidth / 2)
        c.drawRoundRect(body, 1.5f * d, 1.5f * d, stroke)
        c.drawRect(x + w - 3 * d, y + h * 0.33f, x + w - 1 * d, y + h * 0.67f, fill)
        val pct = values["battery"]?.toIntOrNull() ?: return
        if (values["charging"] == "1" || pct >= 100) {
            stroke.strokeWidth = 1.6f * d
            val cx = body.centerX(); val cy = body.centerY()
            c.drawLine(cx - 4 * d, cy, cx - 1 * d, cy + 3 * d, stroke); c.drawLine(cx - 1 * d, cy + 3 * d, cx + 4 * d, cy - 3 * d, stroke)
        } else {
            val i = 2.5f * d
            c.drawRect(body.left + i, body.top + i, body.left + i + (body.width() - 2 * i) * pct / 100f, body.bottom - i, fill)
        }
    }
}
