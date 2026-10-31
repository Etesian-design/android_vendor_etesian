package com.bompotis.etesian.feeds

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The N9 Feeds main page: the orange "Feeds" header, "All posts" first, then each feed with its
 * unread count and last update (red "Can't update" when it failed), an orange bar on feeds with
 * posts since they were last opened. Toolbar: add · refresh · search.
 */
class MainActivity : Activity() {

    private lateinit var list: LinearLayout
    private val listener: () -> Unit = { rebuild() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.PAGE) }
        val header = Ui.header(this, getString(R.string.app_name), brand = true)
        root.addView(header)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list); clipToPadding = false }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = Ui.toolbar(this, root, back = false, actions = listOf(
            Ui.Action("add", R.drawable.tb_add, "Add feed"),
            Ui.Action("refresh", R.drawable.tb_refresh, "Refresh"),
            Ui.Action("search", R.drawable.tb_search, "Search"))) { id ->
            when (id) {
                "add" -> addDialog()
                "refresh" -> refresh()
                "search" -> startActivity(Intent(this, PostsActivity::class.java).putExtra(PostsActivity.EXTRA_SEARCH, true))
            }
        }
        setContentView(root)
        Ui.edgeToEdge(this, root, header, bar ?: scroll)
    }

    override fun onStart() {
        super.onStart()
        Feeds.addListener(listener)
        Feeds.refresh(this, force = false)
        FeedsJob.schedule(this)
        rebuild()
    }

    override fun onStop() { Feeds.removeListener(listener); super.onStop() }

    private fun refresh() {
        if (Feeds.feeds(this).isEmpty()) return
        Toast.makeText(this, "Refreshing…", Toast.LENGTH_SHORT).show()
        Feeds.refresh(this, force = true)
    }

    private fun rebuild() {
        list.removeAllViews()
        val feeds = Feeds.feeds(this)
        if (feeds.isEmpty()) {
            list.addView(Ui.text(this, 18f, Ui.SECONDARY).apply {
                maxLines = 6
                text = "No feeds yet. Tap + to add a feed or website address, or to import an OPML file."
                setPadding(dp(16f), dp(24f), dp(16f), dp(24f))
            })
            return
        }
        val unread = Feeds.unread(this, null)
        list.addView(row(null, "All posts", "$unread unread post" + if (unread == 1) "" else "s",
            Ui.SECONDARY, null, null, false, chevron = true))
        for (f in feeds) {
            val failed = Feeds.failed(this, f.url)
            val n = Feeds.unread(this, f.url)
            val newest = Feeds.items(this).firstOrNull { it.feedUrl == f.url }?.time ?: 0L
            val updated = Feeds.updated(this, f.url)
            list.addView(row(f.url, f.title, if (failed) "Can't update" else null, Ui.RED,
                if (failed) "!" else n.takeIf { it > 0 }?.toString(),
                updated.takeIf { it > 0 }?.let { Ui.time(this, it) },
                fresh = newest > Feeds.seen(this, f.url) && n > 0))
        }
    }

    private fun row(url: String?, title: String, sub: String?, subColor: Int, badge: String?, time: String?,
                    fresh: Boolean, chevron: Boolean = false): View {
        val ctx: Context = this
        val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(80f)
            background = getDrawable(android.R.drawable.list_selector_background)
            // The N9's orange bar on a feed with new posts.
            addView(View(ctx).apply { setBackgroundColor(if (fresh) Ui.ORANGE else 0) },
                LinearLayout.LayoutParams(dp(5f), dp(70f)).apply { marginStart = dp(6f) })
            addView(Ui.feedIcon(ctx, url), LinearLayout.LayoutParams(dp(60f), dp(60f)).apply {
                marginStart = dp(9f); marginEnd = dp(16f)
            })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 20f, Ui.INK, Ui.FONT_BOLD).apply { text = title })
                if (sub != null) addView(Ui.text(ctx, 17f, subColor).apply { text = sub })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (chevron) addView(TextView(ctx).apply {
                text = "›"; textSize = 34f; setTextColor(Ui.INK); typeface = Ui.FONT_BOLD
                setPadding(dp(8f), 0, dp(16f), dp(4f))
            })
            else addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                setPadding(dp(8f), 0, dp(16f), 0)
                addView(Ui.rowBadge(ctx).apply {
                    text = badge.orEmpty()
                    visibility = if (badge == null) View.INVISIBLE else View.VISIBLE
                    if (badge == "!") setTextColor(Ui.RED)
                }, LinearLayout.LayoutParams(-2, dp(22f)).apply { gravity = Gravity.END })
                addView(Ui.text(ctx, 17f, Ui.DATE).apply { text = time.orEmpty(); gravity = Gravity.END },
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8f); gravity = Gravity.END })
            })
            setOnClickListener {
                startActivity(Intent(ctx, PostsActivity::class.java).putExtra(PostsActivity.EXTRA_FEED, url))
            }
            if (url != null) setOnLongClickListener { feedMenu(url, title); true }
        }
        wrap.addView(row, LinearLayout.LayoutParams(-1, -2))
        wrap.addView(Ui.rule(ctx), LinearLayout.LayoutParams(-1, 1).apply {
            marginStart = dp(16f); marginEnd = dp(16f)
        })
        return wrap
    }

    private fun addDialog() {
        val input = Ui.field(this, "Feed or website address")
        Ui.dialog(this).setTitle("Add feed").setView(Ui.wrap(this, input))
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                Toast.makeText(this, "Adding…", Toast.LENGTH_SHORT).show()
                Feeds.add(this, text) { error ->
                    if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    FeedsJob.schedule(this)
                }
            }
            .setNeutralButton("Import OPML") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"), REQUEST_OPML)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun feedMenu(url: String, title: String) {
        Ui.dialog(this).setTitle(title).setItems(arrayOf("Rename", "Remove")) { _, i ->
            if (i == 0) renameDialog(this, url, title) else { Feeds.remove(this, url); FeedsJob.schedule(this) }
        }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != REQUEST_OPML || resultCode != RESULT_OK) return
        val added = runCatching { contentResolver.openInputStream(uri)!!.use { Feeds.importOpml(this, it) } }
        Toast.makeText(this, added.fold({ "Imported $it feed" + if (it == 1) "" else "s" },
            { "That isn't an OPML file" }), Toast.LENGTH_LONG).show()
        if (added.isSuccess) {
            FeedsJob.schedule(this)
            Feeds.refresh(this, force = true)
        }
    }

    companion object {
        private const val REQUEST_OPML = 1

        fun renameDialog(a: Activity, url: String, title: String) {
            val input = Ui.field(a, "Name", title, uri = false)
            Ui.dialog(a).setTitle("Rename").setView(Ui.wrap(a, input))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    input.text.toString().trim().takeIf { it.isNotEmpty() }?.let { Feeds.rename(a, url, it) }
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
    }
}
