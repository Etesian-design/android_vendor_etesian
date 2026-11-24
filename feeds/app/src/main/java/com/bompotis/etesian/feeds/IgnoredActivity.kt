package com.bompotis.etesian.feeds

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout

/**
 * Ignored feeds (2026-11-24, owner): feeds removed from this phone but left on the sync account's server, which every
 * sync skips. Opened from Etesian settings, so a dark page in the settings' look (DarkPage). Each row has Sync again;
 * the feed comes back with the next refresh.
 */
class IgnoredActivity : Activity() {

    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.n9_page_in, R.anim.n9_page_out)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.n9_page_back_in, R.anim.n9_page_back_out)
        }
        body = DarkPage.body(this)
        DarkPage.page(this, "Ignored feeds", body)
        rebuild()
    }

    private fun rebuild() {
        body.removeAllViews()
        val ignored = Sync.ignored(this)
        if (ignored.isEmpty()) {
            body.addView(DarkPage.note(this, "No ignored feeds. A feed you remove from this phone but keep on the server is listed here, so you can sync it again."))
            return
        }
        body.addView(DarkPage.note(this, "Removed from this phone, kept on the server. Sync again brings a feed back with the next refresh."))
        for ((url, title) in ignored.entries.sortedBy { it.value.lowercase() }) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(80f); setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
                addView(LinearLayout(this@IgnoredActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@IgnoredActivity, 20f, DarkPage.TEXT, Ui.FONT_BOLD).apply { text = title })
                    addView(Ui.text(this@IgnoredActivity, 15f, DarkPage.SECONDARY).apply { text = url })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(DarkPage.button(this@IgnoredActivity, "Sync again", blue = true) {
                    Sync.resume(this@IgnoredActivity, url)
                    Feeds.refresh(this@IgnoredActivity, force = true)
                    rebuild()
                }.apply { textSize = 15f }, LinearLayout.LayoutParams(dp(128f), dp(40f)).apply { marginStart = dp(12f) })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2))
            body.addView(DarkPage.rule(this))
        }
    }
}
