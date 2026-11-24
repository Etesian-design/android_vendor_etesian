package com.bompotis.etesian.feeds

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * Ignored feeds (2026-11-24, user): feeds removed from this phone but left on the sync account's server, which every
 * sync skips. A tap asks to sync it again; it comes back with the next refresh.
 */
class IgnoredActivity : Activity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.PAGE) }
        val header = Ui.header(this, "Ignored feeds", brand = false)
        root.addView(header)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list); clipToPadding = false }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = Ui.toolbar(this, root, back = true, actions = emptyList()) {}
        setContentView(root)
        Ui.edgeToEdge(this, root, header, bar ?: scroll)
        rebuild()
    }

    private fun rebuild() {
        list.removeAllViews()
        val ignored = Sync.ignored(this)
        if (ignored.isEmpty()) {
            list.addView(Ui.text(this, 18f, Ui.SECONDARY).apply {
                maxLines = 6
                text = "No ignored feeds. A feed you remove from this phone but keep on the server is listed here, so you can sync it again."
                setPadding(dp(16f), dp(24f), dp(16f), dp(24f))
            })
            return
        }
        for ((url, title) in ignored.entries.sortedBy { it.value.lowercase() }) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(80f)
                setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
                background = getDrawable(android.R.drawable.list_selector_background)
                addView(Ui.text(this@IgnoredActivity, 20f, Ui.INK, Ui.FONT_BOLD).apply { text = title })
                addView(Ui.text(this@IgnoredActivity, 15f, Ui.SECONDARY).apply { text = url; maxLines = 1 })
                setOnClickListener {
                    Ui.dialog(this@IgnoredActivity).setTitle("Sync $title again?")
                        .setMessage("It comes back to this phone with the next refresh.")
                        .setPositiveButton("Sync again") { _, _ ->
                            Sync.resume(this@IgnoredActivity, url)
                            Toast.makeText(this@IgnoredActivity, "Refreshing…", Toast.LENGTH_SHORT).show()
                            Feeds.refresh(this@IgnoredActivity, force = true)
                            rebuild()
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
            list.addView(Ui.rule(this), LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(16f); marginEnd = dp(16f) })
        }
    }
}
