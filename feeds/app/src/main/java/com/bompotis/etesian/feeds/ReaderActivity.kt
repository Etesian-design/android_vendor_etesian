package com.bompotis.etesian.feeds

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bompotis.etesian.toolbar.EtesianMenu

/**
 * Reads one feed item, N9 content-app style: a light page with the title, the source and date,
 * an engraved rule and the text. Toolbar: back · readable version · open in browser · ≡ (star, share, unread).
 * Opening an item marks it read.
 */
class ReaderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent.getStringExtra(EXTRA_LINK).orEmpty()
        val item = Feeds.find(this, intent.getStringExtra(EXTRA_ID).orEmpty())
        if (item == null) {
            // Gone from the cache since the launcher listed it: the page itself, then.
            if (link.isNotEmpty()) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            finish(); return
        }
        Feeds.setRead(this, item.id, true)
        window.setBackgroundDrawable(ColorDrawable(PAGE))

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PAGE) }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(24f))
        }
        val openInBrowser = {
            if (item.link.isNotEmpty()) runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
            }
        }
        // As on the N9: the title (light), the feed's name and the date, an engraved rule.
        val title = TextView(this).apply {
            text = item.title
            setTextColor(INK); textSize = 28f; typeface = Ui.FONT_LIGHT
            setLineSpacing(0f, 1.05f)
            setOnClickListener { openInBrowser() }
        }
        val source = TextView(this).apply {
            text = item.feedTitle + "\n" + DateUtils.formatDateTime(this@ReaderActivity, item.time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE or DateUtils.FORMAT_SHOW_YEAR) +
                "  " + android.text.format.DateFormat.getTimeFormat(this@ReaderActivity).format(java.util.Date(item.time))
            setTextColor(SECONDARY); textSize = 16f; typeface = Ui.FONT
            setLineSpacing(0f, 1.1f)
            setPadding(0, dp(10f), 0, dp(12f))
        }
        val text = TextView(this).apply {
            // Images aren't loaded inline; the browser has the full page.
            text = Html.fromHtml(item.html.ifEmpty { item.summary }, Html.FROM_HTML_MODE_COMPACT,
                { ColorDrawable(0) }, null)
            setTextColor(INK); textSize = 18f; typeface = Ui.FONT
            setLineSpacing(0f, 1.2f)
            setLinkTextColor(LINK)
            movementMethod = LinkMovementMethod.getInstance()
        }
        body.addView(title)
        body.addView(source)
        body.addView(View(this).apply { setBackgroundColor(RULE) }, LinearLayout.LayoutParams(-1, dp(1f)))
        body.addView(View(this).apply { setBackgroundColor(0xFFFFFFFF.toInt()) }, LinearLayout.LayoutParams(-1, dp(1f)))
        body.addView(text, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })
        // The readable version (2026-11-24, user): the article from the page itself, in this same page; again, the
        // feed's own text.
        val feedText = text.text
        var readable = false
        val showReadable = {
            readable = !readable
            if (!readable) { text.text = feedText; text.contentDescription = null }
            else {
                text.text = "Loading the readable version…"
                Readable.load(this, item.link) { a ->
                    if (!readable || isDestroyed) return@load
                    if (a == null) { text.text = feedText; readable = false
                        android.widget.Toast.makeText(this, "No readable version of this page", android.widget.Toast.LENGTH_SHORT).show()
                        return@load }
                    // Paragraphs keep their gap here: an article is long, and compact mode runs it into one block.
                    text.text = Html.fromHtml(a.html, Html.FROM_HTML_MODE_LEGACY, { ColorDrawable(0) }, null).trimEnd()
                    text.contentDescription = "readable"
                    scroll.scrollTo(0, 0)
                }
            }
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val share = {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, item.title)
                .putExtra(Intent.EXTRA_TEXT, item.link.ifEmpty { item.title }), null))
        }
        val bar = Ui.toolbar(this, root, back = true, actions = listOf(
            Ui.Action("readable", R.drawable.tb_readable, "Readable version"),
            Ui.Action("browser", R.drawable.tb_browser, "Open in browser"),
            Ui.Action("menu", R.drawable.tb_menu, "Menu"))) { id ->
            if (id == "readable") { if (item.link.isNotEmpty()) showReadable() }
            else if (id == "browser") openInBrowser()
            else EtesianMenu.show(this, R.menu.reader, { m ->
                m.findItem(R.id.star).title = if (Feeds.isStarred(this, item.id)) "Unstar" else "Star"
            }) { mi ->
                when (mi.itemId) {
                    R.id.star -> Feeds.setStarred(this, item.id, !Feeds.isStarred(this, item.id))
                    R.id.share -> share()
                    R.id.mark_unread -> { Feeds.setRead(this, item.id, false); finish() }
                }
                true
            }
        }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, if (bar == null) 0 else bars.bottom)
            scroll.setPadding(0, 0, 0, if (bar == null) bars.bottom else 0)
            insets
        }
        window.setDecorFitsSystemWindows(false)
    }

    companion object {
        const val EXTRA_ID = "feed_item"
        const val EXTRA_LINK = "link"
        private const val PAGE = Ui.PAGE
        private const val INK = 0xFF292829.toInt()
        private const val SECONDARY = 0xFF525152.toInt()
        private const val RULE = 0xFFB9B4BA.toInt()
        private const val LINK = 0xFFFF8600.toInt()
    }
}
