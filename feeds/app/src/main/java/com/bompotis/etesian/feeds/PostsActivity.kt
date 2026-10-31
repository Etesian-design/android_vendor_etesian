package com.bompotis.etesian.feeds

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.bompotis.etesian.toolbar.EtesianMenu

/**
 * A feed's posts, or All posts (N9 sub page): a plain header with the unread count in an orange
 * pill; rows = bold two-line title, the feed's name, the date; unread ones have the orange bar
 * and an orange date. Toolbar: back · refresh · ≡. With [EXTRA_SEARCH], a field filters titles.
 */
class PostsActivity : Activity() {

    private var feedUrl: String? = null
    private lateinit var list: LinearLayout
    private lateinit var count: android.widget.TextView
    private lateinit var title: android.widget.TextView
    private var search: EditText? = null
    private val listener: () -> Unit = { rebuild() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        feedUrl = intent.getStringExtra(EXTRA_FEED)
        feedUrl?.let { Feeds.markSeen(this, it) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.PAGE) }
        val header = Ui.header(this, "", brand = false)
        title = header.getChildAt(0) as android.widget.TextView
        count = Ui.countBadge(this)
        header.addView(count)
        root.addView(header)
        root.addView(Ui.rule(this), LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(16f); marginEnd = dp(16f) })
        if (intent.getBooleanExtra(EXTRA_SEARCH, false)) {
            search = Ui.field(this, "Search posts", uri = false).apply {
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { rebuild() }
                })
            }
            root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12f), dp(8f), dp(12f), dp(4f)) })
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list); clipToPadding = false }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = Ui.toolbar(this, root, back = true, actions = listOf(
            Ui.Action("refresh", R.drawable.tb_refresh, "Refresh"),
            Ui.Action("menu", R.drawable.tb_menu, "Menu"))) { id ->
            when (id) {
                "refresh" -> {
                    Toast.makeText(this, "Refreshing…", Toast.LENGTH_SHORT).show()
                    Feeds.refresh(this, force = true)
                }
                "menu" -> menu()
            }
        }
        setContentView(root)
        Ui.edgeToEdge(this, root, header, bar ?: scroll)
        search?.requestFocus()
    }

    override fun onStart() { super.onStart(); Feeds.addListener(listener); rebuild() }
    override fun onStop() { Feeds.removeListener(listener); super.onStop() }

    private fun menu() {
        EtesianMenu.show(this, R.menu.posts, { m ->
            m.findItem(R.id.rename).isVisible = feedUrl != null
            m.findItem(R.id.remove).isVisible = feedUrl != null
        }) { item ->
            val url = feedUrl
            when (item.itemId) {
                R.id.mark_all_read -> Feeds.markAllRead(this, url)
                R.id.rename -> if (url != null) MainActivity.renameDialog(this, url, title.text.toString())
                R.id.remove -> if (url != null) { Feeds.remove(this, url); FeedsJob.schedule(this); finish() }
            }
            true
        }
    }

    private fun rebuild() {
        val url = feedUrl
        title.text = url?.let { u -> Feeds.feeds(this).firstOrNull { it.url == u }?.title } ?: "All posts"
        val unread = Feeds.unread(this, url)
        count.text = unread.toString()
        count.visibility = if (unread > 0) View.VISIBLE else View.GONE
        val q = search?.text?.toString()?.trim().orEmpty()
        val items = Feeds.items(this).filter { url == null || it.feedUrl == url }
            .filter { q.isEmpty() || it.title.contains(q, true) || it.summary.contains(q, true) }
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(Ui.text(this, 18f, Ui.SECONDARY).apply {
                text = if (Feeds.refreshing) "Refreshing…" else if (q.isNotEmpty()) "No posts found" else "No posts yet"
                setPadding(dp(16f), dp(24f), dp(16f), dp(24f))
            })
            return
        }
        for (item in items) list.addView(row(item))
    }

    private fun row(item: Feeds.Item): View {
        val unread = !Feeds.isRead(this, item.id)
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(LinearLayout(this).apply {
            background = getDrawable(android.R.drawable.list_selector_background)
            setPadding(0, dp(10f), dp(16f), dp(10f))
            addView(View(context).apply { setBackgroundColor(if (unread) Ui.ORANGE else 0) },
                LinearLayout.LayoutParams(dp(5f), -1).apply { marginStart = dp(6f); marginEnd = dp(17f) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(context, 20f, Ui.INK, Ui.FONT_BOLD).apply { maxLines = 2; text = item.title })
                addView(Ui.text(context, 17f, Ui.SECONDARY).apply { text = item.feedTitle },
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4f) })
                addView(Ui.text(context, 14f, if (unread) Ui.ORANGE else Ui.DATE).apply { text = Ui.date(item.time) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener {
                startActivity(Intent(context, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_ID, item.id))
            }
        }, LinearLayout.LayoutParams(-1, -2))
        wrap.addView(Ui.rule(this), LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(16f); marginEnd = dp(16f) })
        return wrap
    }

    companion object {
        const val EXTRA_FEED = "feed"
        const val EXTRA_SEARCH = "search"
    }
}
