package com.bompotis.etesian.launcher.standby

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.bompotis.etesian.launcher.N9
import com.bompotis.etesian.launcher.N9Sheet
import com.bompotis.etesian.launcher.R
import com.bompotis.etesian.launcher.SystemToolbar
import com.bompotis.etesian.launcher.dp

/**
 * Etesian settings → Standby screen. The live preview first (rule 20), then the lines: each a row of items (blue
 * squircles) and the user's own words; a tap opens the line's sheet (add an item or words, what to show when empty,
 * move, remove). + on the toolbar adds a line, the menu holds the presets. Then the appearance: colour, size,
 * alignment, and whether the clock, the battery glyph and the notification icons show. Every change saves at once.
 */
class StandbySettingsActivity : Activity() {
    private var config = StandbyConfig()
    private lateinit var content: LinearLayout
    private lateinit var preview: StandbyView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = StandbyConfig.load(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(N9.BACKGROUND))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), 0, dp(16f), dp(24f)) }
        val scroll = ScrollView(this).apply { addView(content) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(scroll)
        preview = StandbyView(this).apply { scale = 0.55f; fx = 0.5f; fy = 0.3f }
        build()
        SystemToolbar(this).set(back = true, items = listOf(
            SystemToolbar.Item("add", R.drawable.tb_add, "Add a line"),
            SystemToolbar.Item("menu", R.drawable.tb_menu, "Menu"))) { id ->
            when (id) {
                "back" -> finish()
                "add" -> pickItem { p -> change(config.copy(lines = config.lines + StandbyConfig.Line(listOf(p)))) }
                "menu" -> N9Sheet.show(this, StandbyConfig.PRESETS.map { (name, c) ->
                    N9Sheet.Item("Preset: $name") { change(c) }
                } + N9Sheet.Item("Clear all lines") { change(config.copy(lines = emptyList())) })
            }
        }
    }

    override fun onResume() { super.onResume(); refreshPreview() }

    private fun change(c: StandbyConfig) { config = c; StandbyConfig.save(this, c); build() }

    private fun refreshPreview() {
        preview.config = config
        preview.values = StandbyItems.values(this)
    }

    private fun build() {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = "Standby screen"; setTextColor(N9.TEXT); textSize = 30f; typeface = N9.FONT_LIGHT
            setPadding(0, dp(16f), 0, dp(10f))
        })
        content.addView(row("On while locked", "The clock and what you choose stay on the black screen, moving every minute") {
            Switch(this).apply {
                isChecked = StandbyConfig.isOn(this@StandbySettingsActivity)
                contentDescription = "On while locked"
                setOnCheckedChangeListener { _, on -> StandbyConfig.setOn(this@StandbySettingsActivity, on) }
            }
        })
        header("Preview")
        content.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(0xFF000000.toInt()); cornerRadius = dp(14f).toFloat(); setStroke(dp(1f), 0xFF2E2E2E.toInt()) }
            // build() runs again on every change: the preview leaves its old frame first.
            (preview.parent as? android.view.ViewGroup)?.removeView(preview)
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            contentDescription = "Preview"
        }, LinearLayout.LayoutParams(-1, dp(190f)))
        refreshPreview()

        header("Lines")
        if (config.lines.isEmpty()) content.addView(note("None: the N9's own, the date under the clock. + adds a line."))
        config.lines.forEachIndexed { i, l -> content.addView(lineRow(i, l)) }

        header("Appearance")
        content.addView(LinearLayout(this).apply {
            setPadding(0, dp(8f), 0, dp(8f))
            StandbyConfig.COLOURS.forEach { col ->
                addView(View(this@StandbySettingsActivity).apply {
                    background = Sq(col, if (col == config.color) N9.ACCENT else 0xFF444444.toInt(), dp(if (col == config.color) 3f else 1f))
                    contentDescription = "Colour ${"%06X".format(col and 0xFFFFFF)}"
                    isSelected = col == config.color
                    setOnClickListener { change(config.copy(color = col)) }
                }, LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { marginEnd = dp(12f) })
            }
        })
        content.addView(row("Size", "${config.size} sp") { null })
        content.addView(SeekBar(this).apply {
            max = 18; progress = config.size - 10; contentDescription = "Size"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, v: Int, user: Boolean) {
                    if (!user) return
                    config = config.copy(size = v + 10); StandbyConfig.save(this@StandbySettingsActivity, config); refreshPreview()
                }
                override fun onStartTrackingTouch(b: SeekBar) {}
                override fun onStopTrackingTouch(b: SeekBar) { build() }
            })
        })
        content.addView(LinearLayout(this).apply {
            setPadding(0, dp(12f), 0, dp(8f))
            StandbyConfig.Align.entries.forEach { a ->
                val on = a == config.align
                addView(TextView(this@StandbySettingsActivity).apply {
                    text = a.name.lowercase().replaceFirstChar { it.uppercase() }; gravity = Gravity.CENTER
                    setTextColor(N9.TEXT); typeface = N9.FONT; textSize = 15f; setPadding(0, dp(10f), 0, dp(10f))
                    setBackgroundColor(if (on) 0xFF268CE1.toInt() else 0xFF2A2A2A.toInt())
                    isSelected = on; contentDescription = "Align $text"
                    setOnClickListener { change(config.copy(align = a)) }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(1f) })
            }
        })
        content.addView(toggle("Clock", config.clock) { change(config.copy(clock = it)) })
        content.addView(toggle("Battery above the clock", config.battery) { change(config.copy(battery = it)) })
        content.addView(toggle("Notification icons", config.icons) { change(config.copy(icons = it)) })
    }

    private fun lineRow(i: Int, l: StandbyConfig.Line): View {
        val chips = Flow(this)
        l.parts.forEach { p ->
            chips.addView(TextView(this).apply {
                typeface = N9.FONT; textSize = 15f; setTextColor(N9.TEXT)
                when (p) {
                    is StandbyConfig.Part.Text -> text = p.text.replace(" ", " ")
                    is StandbyConfig.Part.Item -> {
                        text = StandbyItems.label(p.id); background = Sq(0xFF1F5A9E.toInt(), 0, 0)
                        setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
                    }
                }
            })
        }
        if (l.empty.isNotBlank()) chips.addView(TextView(this).apply {
            text = "empty: “${l.empty}”"; textSize = 13f; setTextColor(N9.TEXT_SECONDARY); setPadding(dp(6f), dp(5f), 0, dp(5f))
        })
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56f); setPadding(0, dp(6f), 0, dp(6f))
            contentDescription = "Line ${i + 1}"
            addView(chips, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@StandbySettingsActivity).apply { text = "›"; textSize = 22f; setTextColor(N9.TEXT_SECONDARY) })
            setOnClickListener { lineSheet(i) }
            setOnLongClickListener { lineSheet(i); true }
        }
    }

    private fun lineSheet(i: Int) {
        val l = config.lines[i]
        fun set(n: StandbyConfig.Line?) = change(config.copy(lines = config.lines.toMutableList().apply { if (n == null) removeAt(i) else this[i] = n }))
        val items = mutableListOf(
            N9Sheet.Item("Add an item") { pickItem { set(l.copy(parts = l.parts + it)) } },
            N9Sheet.Item("Add words") { ask("Words", "") { set(l.copy(parts = l.parts + StandbyConfig.Part.Text(it))) } },
            N9Sheet.Item("When empty", l.empty.ifBlank { "Hide the line" }) { ask("When its items are empty, show", l.empty) { set(l.copy(empty = it)) } },
        )
        if (l.parts.size > 1) items += N9Sheet.Item("Remove the last piece") { set(l.copy(parts = l.parts.dropLast(1))) }
        if (i > 0) items += N9Sheet.Item("Move up") { change(config.copy(lines = config.lines.toMutableList().apply { add(i - 1, removeAt(i)) })) }
        if (i < config.lines.size - 1) items += N9Sheet.Item("Move down") { change(config.copy(lines = config.lines.toMutableList().apply { add(i + 1, removeAt(i)) })) }
        items += N9Sheet.Item("Remove the line") { set(null) }
        N9Sheet.show(this, items, "Line ${i + 1}")
    }

    private fun pickItem(done: (StandbyConfig.Part) -> Unit) =
        N9Sheet.choose(this, "Add an item", StandbyItems.ALL.map { it.label }, -1) { done(StandbyConfig.Part.Item(StandbyItems.ALL[it].id)) }

    private fun ask(title: String, value: String, done: (String) -> Unit) {
        val f = EditText(this).apply { setText(value); setSelection(value.length); isSingleLine = true }
        AlertDialog.Builder(this).setTitle(title).setView(FrameLayout(this).apply { setPadding(dp(20f), dp(8f), dp(20f), 0); addView(f) })
            .setPositiveButton("Done") { _, _ -> done(f.text.toString()) }.setNegativeButton("Cancel", null).show()
    }

    private fun header(t: String) = content.addView(TextView(this).apply {
        text = t; textSize = 13f; typeface = N9.FONT; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(N9.TEXT_SECONDARY); setPadding(0, dp(20f), 0, dp(8f))
    })

    private fun note(t: String) = TextView(this).apply { text = t; textSize = 14f; setTextColor(N9.TEXT_SECONDARY); setPadding(0, dp(6f), 0, dp(6f)) }

    private fun row(title: String, summary: String?, end: () -> View?) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56f)
        addView(LinearLayout(this@StandbySettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@StandbySettingsActivity).apply { text = title; textSize = 17f; typeface = N9.FONT; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(N9.TEXT) })
            if (summary != null) addView(TextView(this@StandbySettingsActivity).apply { text = summary; textSize = 13f; setTextColor(N9.TEXT_SECONDARY) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        end()?.let { addView(it) }
    }

    private fun toggle(title: String, on: Boolean, set: (Boolean) -> Unit) = row(title, null) {
        Switch(this).apply { isChecked = on; contentDescription = title; setOnCheckedChangeListener { _, v -> set(v) } }
    }

    /** A squircle (n = 2.8) fill with an optional rim. */
    private class Sq(private val color: Int, private val rim: Int, private val rimW: Int) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(c: Canvas) {
            val b = bounds; val path = Path(); val n = 2.8
            for (k in 0..120) {
                val t = Math.toRadians(k * 3.0); val cs = Math.cos(t); val sn = Math.sin(t)
                val x = (b.exactCenterX() + b.width() / 2 * Math.signum(cs) * Math.pow(Math.abs(cs), 2 / n)).toFloat()
                val y = (b.exactCenterY() + b.height() / 2 * Math.signum(sn) * Math.pow(Math.abs(sn), 2 / n)).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            if (rimW > 0) { p.style = Paint.Style.FILL; p.color = rim; c.drawPath(path, p)
                c.save(); val s = 1f - 2f * rimW / b.width(); c.scale(s, 1f - 2f * rimW / b.height(), b.exactCenterX(), b.exactCenterY()) }
            p.color = color; c.drawPath(path, p)
            if (rimW > 0) c.restore()
        }
        override fun setAlpha(a: Int) { p.alpha = a }
        override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Lays its children left to right, wrapping. */
    private class Flow(ctx: android.content.Context) : android.view.ViewGroup(ctx) {
        private val gap = ctx.dp(6f)
        override fun onMeasure(ws: Int, hs: Int) {
            val w = MeasureSpec.getSize(ws); var x = 0; var y = 0; var rowH = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i); c.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
                if (x > 0 && x + c.measuredWidth > w) { x = 0; y += rowH + gap; rowH = 0 }
                x += c.measuredWidth + gap; rowH = maxOf(rowH, c.measuredHeight)
            }
            setMeasuredDimension(w, y + rowH)
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = r - l; var x = 0; var y = 0; var rowH = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (x > 0 && x + c.measuredWidth > w) { x = 0; y += rowH + gap; rowH = 0 }
                c.layout(x, y + 0, x + c.measuredWidth, y + c.measuredHeight)
                x += c.measuredWidth + gap; rowH = maxOf(rowH, c.measuredHeight)
            }
        }
    }
}
