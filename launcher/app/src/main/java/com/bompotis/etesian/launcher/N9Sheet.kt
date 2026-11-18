package com.bompotis.etesian.launcher

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The N9 toolbar-menu sheet (n9-toolbar-menu.png): a light panel at the bottom, inset, with plain
 * bold rows (an optional grey line under the title) separated by hairlines. Tap outside closes it.
 * Also the N9 selection sheet ([choose], [chooseMany]): the current choice in the accent colour with a tick.
 */
object N9Sheet {
    class Item(val title: String, val subtitle: String? = null, val onClick: () -> Unit)

    private const val PANEL = 0xFFF4F3F6.toInt()
    private const val TEXT = 0xFF1A1A1A.toInt()
    private const val SUB = 0xFF6E6E6E.toInt()
    private const val LINE = 0xFFD6D5D9.toInt()
    /** The selected row of a selection sheet: N9 accent text on a faint accent wash. */
    private const val PICKED = 0xFF1C6FC0.toInt()
    private const val PICKED_BG = 0xFFE3EEF9.toInt()

    fun show(ctx: Context, items: List<Item>, title: String? = null, label: String = "Menu sheet"): Dialog =
        sheet(ctx, title, label, items.size) { d, i, row ->
            val item = items[i]
            row.text(item.title, item.subtitle, false)
            row.view.setOnClickListener { d.dismiss(); item.onClick() }
        }

    /** One of [labels]: [selected] marked; tapping a row closes the sheet and gives its index. */
    fun choose(ctx: Context, title: String?, labels: List<CharSequence>, selected: Int, done: (Int) -> Unit): Dialog =
        sheet(ctx, title, "Selection sheet", labels.size) { d, i, row ->
            row.text(labels[i].toString(), null, i == selected)
            row.view.isSelected = i == selected
            row.view.setOnClickListener { d.dismiss(); if (i != selected) done(i) }
        }

    /** Any of [labels]: rows tick on and off; the last row, Done, gives the ticked indexes. */
    fun chooseMany(ctx: Context, title: String?, labels: List<CharSequence>, ticked: Set<Int>, done: (Set<Int>) -> Unit): Dialog {
        val now = ticked.toMutableSet()
        return sheet(ctx, title, "Selection sheet", labels.size + 1) { d, i, row ->
            if (i == labels.size) {
                row.text("Done", null, false)
                row.view.setOnClickListener { d.dismiss(); done(now.toSet()) }
                return@sheet
            }
            row.text(labels[i].toString(), null, i in now)
            row.view.isSelected = i in now
            row.view.setOnClickListener {
                if (!now.remove(i)) now += i
                row.text(labels[i].toString(), null, i in now)
                row.view.isSelected = i in now
            }
        }
    }

    class Row(val view: LinearLayout, private val title: TextView, private val sub: TextView, private val tick: TextView) {
        fun text(t: String, s: String?, picked: Boolean) {
            title.text = t
            title.setTextColor(if (picked) PICKED else TEXT)
            sub.text = s; sub.visibility = if (s == null) View.GONE else View.VISIBLE
            tick.visibility = if (picked) View.VISIBLE else View.INVISIBLE
            view.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000), null, ColorDrawable(if (picked) PICKED_BG else Color.WHITE))
        }
    }

    private fun sheet(ctx: Context, title: String?, label: String, n: Int, bind: (Dialog, Int, Row) -> Unit): Dialog {
        val d = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val rows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(PANEL); cornerRadius = 8 * dp; setStroke(px(1f), LINE) }
            contentDescription = label
            isClickable = true
            clipToOutline = true
        }
        title?.let {
            panel.addView(TextView(ctx).apply {
                text = it; setTextColor(SUB); textSize = 15f; typeface = N9.FONT
                setPadding(px(16f), px(14f), px(16f), px(6f))
            })
            panel.addView(View(ctx).apply { setBackgroundColor(LINE) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1f)))
        }
        // Long lists scroll inside the sheet; the title stays put.
        val maxH = (ctx.resources.displayMetrics.heightPixels * 0.72f).toInt()
        val scroll = object : ScrollView(ctx) {
            override fun onMeasure(w: Int, h: Int) =
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }.apply { isVerticalScrollBarEnabled = true; addView(rows) }
        panel.addView(scroll)
        for (i in 0 until n) {
            if (i > 0) rows.addView(View(ctx).apply { setBackgroundColor(LINE) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1f)))
            val t = TextView(ctx).apply { setTextColor(TEXT); textSize = 23f; typeface = N9.FONT_BOLD }
            val s = TextView(ctx).apply { setTextColor(0xFF777777.toInt()); textSize = 19f; typeface = N9.FONT }
            val tick = TextView(ctx).apply { text = "✓"; textSize = 24f; setTextColor(PICKED); typeface = N9.FONT_BOLD }
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(t); addView(s) }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // Mock 02 / 11 rows (mock px x1.36): 53 high, 16 in, 17 px bold title, 14 px subtitle.
                minimumHeight = px(72f)
                setPadding(px(22f), px(8f), px(22f), px(8f))
                addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(tick)
            }
            bind(d, i, Row(row, t, s, tick))
            rows.addView(row)
        }
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(0x73000000)
            setOnClickListener { d.dismiss() }
            addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(px(15f), 0, px(15f), px(15f)) })
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom); ins
            }
        }
        d.setContentView(root)
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
        d.show()
        // A long list opens at its current choice.
        rows.post { (0 until rows.childCount).map { rows.getChildAt(it) }.firstOrNull { it.isSelected }?.let { scroll.scrollTo(0, it.top - px(72f)) } }
        return d
    }
}
