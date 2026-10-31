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
import android.widget.TextView

/**
 * The N9 toolbar-menu sheet (n9-toolbar-menu.png): a light panel at the bottom, inset, with plain
 * bold rows (an optional grey line under the title) separated by hairlines. Tap outside closes it.
 */
object N9Sheet {
    class Item(val title: String, val subtitle: String? = null, val onClick: () -> Unit)

    private const val PANEL = 0xFFF4F3F6.toInt()
    private const val TEXT = 0xFF1A1A1A.toInt()
    private const val SUB = 0xFF6E6E6E.toInt()
    private const val LINE = 0xFFD6D5D9.toInt()

    fun show(ctx: Context, items: List<Item>, title: String? = null, label: String = "Menu sheet"): Dialog {
        val d = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
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
        }
        items.forEachIndexed { i, item ->
            if (i > 0 || title != null) panel.addView(View(ctx).apply { setBackgroundColor(LINE) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1f)))
            panel.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                // Mock 02 / 11 rows (mock px x1.36): 53 high, 16 in, 17 px bold title, 14 px subtitle.
                minimumHeight = px(72f)
                setPadding(px(22f), px(8f), px(22f), px(8f))
                addView(TextView(ctx).apply { text = item.title; setTextColor(TEXT); textSize = 23f; typeface = N9.FONT_BOLD })
                item.subtitle?.let { s ->
                    addView(TextView(ctx).apply { text = s; setTextColor(0xFF777777.toInt()); textSize = 19f; typeface = N9.FONT })
                }
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x22000000), null, ColorDrawable(Color.WHITE))
                setOnClickListener { d.dismiss(); item.onClick() }
            })
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
        return d
    }
}
