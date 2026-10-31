package com.bompotis.etesian.launcher

import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The "Earlier" page (Android extras → Keep dismissed notifications): a plain N9 page header, then
 * every kept dismissed notification, newest first. Tap one to open it like the original.
 */
class EarlierActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(N9.BACKGROUND)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom); ins
            }
        }
        root.addView(TextView(this).apply {
            text = "Earlier"; setTextColor(N9.TEXT); textSize = 32f; typeface = N9.FONT_LIGHT
            setPadding(dp(16f), dp(20f), dp(16f), dp(12f))
        })
        root.addView(View(this).apply { setBackgroundColor(0xFF333333.toInt()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (SystemToolbar.available(this)) SystemToolbar(this).set(back = true, items = emptyList(), style = "dark", hidden = false) {}
        fill()
    }

    private fun fill() {
        list.removeAllViews()
        val ghosts = Ghosts.list(this)
        if (ghosts.isEmpty()) list.addView(TextView(this).apply {
            text = "Nothing dismissed"; setTextColor(0xFFA0A0A0.toInt()); textSize = 16f; typeface = N9.FONT
            setPadding(dp(16f), dp(24f), dp(16f), dp(24f))
        })
        ghosts.forEach { g -> list.addView(row(g)) }
    }

    private fun row(g: Ghosts.Ghost) = LinearLayout(this).apply {
        setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        contentDescription = g.title.ifEmpty { g.text }
        addView(ImageView(context).apply {
            setImageBitmap(AppRepository.iconFor(context, g.pkg))
        }, LinearLayout.LayoutParams(dp(56f), dp(56f)).apply { marginEnd = dp(14f) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = g.title.ifEmpty { g.text }; setTextColor(N9.TEXT); textSize = 16f; typeface = N9.FONT_BOLD
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = if (DateUtils.isToday(g.time)) DateFormat.getTimeFormat(context).format(g.time)
                        else DateFormat.getDateFormat(context).format(g.time)
                    setTextColor(N9.TEXT_SECONDARY); textSize = 12f; typeface = N9.FONT
                })
            })
            if (g.title.isNotEmpty() && g.text.isNotEmpty()) addView(TextView(context).apply {
                text = g.text; setTextColor(N9.TEXT_SECONDARY); textSize = 14f; typeface = N9.FONT
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setOnClickListener { Ghosts.open(context, g) }
    }
}
