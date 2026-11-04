package com.bompotis.etesian.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.util.concurrent.Executors

/**
 * The Feeds app's subscriptions and items, for the Events view's Feed section and the Belle
 * widget. The app (com.bompotis.etesian.feeds) owns them; this reads its provider off the main
 * thread, caches the result, and re-reads when the app announces a change.
 */
object Feeds {
    class Feed(val url: String, val title: String)

    class Item(
        val feedUrl: String,
        val feedTitle: String,
        val title: String,
        /** Plain-text excerpt for the Events row. */
        val summary: String,
        val link: String,
        val time: Long,
        val id: String,
        /** Opened (or marked read) in the app: gone from Events, no orange dot on the widgets. */
        val read: Boolean = false,
    )

    const val PACKAGE = "com.bompotis.etesian.feeds"
    private val ROOT: Uri = Uri.parse("content://$PACKAGE")
    private val ITEMS: Uri = Uri.withAppendedPath(ROOT, "items")
    private val FEEDS: Uri = Uri.withAppendedPath(ROOT, "feeds")

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()
    @Volatile var refreshing = false
        private set
    private var feeds: List<Feed> = emptyList()
    private var items: List<Item> = emptyList()
    private var app: Context? = null

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    /** Starts watching the app (once); the first read lands through the listeners. */
    private fun init(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        app = a
        runCatching {
            a.contentResolver.registerContentObserver(ITEMS, true, object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) = load()
            })
        }
        load()
    }

    private fun load() {
        val a = app ?: return
        io.execute {
            val f = runCatching {
                a.contentResolver.query(FEEDS, null, null, null, null)?.use { c ->
                    buildList { while (c.moveToNext()) add(Feed(c.getString(0), c.getString(1))) }
                }
            }.getOrNull().orEmpty()
            val i = runCatching {
                a.contentResolver.query(ITEMS, null, null, null, null)?.use { c ->
                    val col = { n: String -> c.getColumnIndexOrThrow(n) }
                    val fu = col("feedUrl"); val ft = col("feedTitle"); val t = col("title"); val s = col("summary")
                    val l = col("link"); val d = col("time"); val id = col("id")
                    val r = c.getColumnIndex("read")  // -1 from an older Feeds app
                    buildList {
                        while (c.moveToNext()) add(Item(c.getString(fu), c.getString(ft), c.getString(t),
                            c.getString(s), c.getString(l), c.getLong(d), c.getString(id), r >= 0 && c.getInt(r) == 1))
                    }
                }
            }.getOrNull().orEmpty()
            val busy = call(a, "state", null)?.getBoolean("refreshing") ?: false
            main.post {
                feeds = f; items = i; refreshing = busy
                listeners.toList().forEach { it() }
            }
        }
    }

    private fun call(ctx: Context, method: String, arg: String?, extras: Bundle? = null): Bundle? =
        runCatching { ctx.contentResolver.call(ROOT, method, arg, extras) }.getOrNull()

    fun feeds(ctx: Context): List<Feed> { init(ctx); return feeds }

    /** Newest first, across all feeds. */
    fun items(ctx: Context): List<Item> { init(ctx); return items }

    /** What the Events view lists: only the unread ones (read ones leave it; the widgets keep them). */
    fun forEvents(items: List<Item>): List<Item> = items.filter { !it.read }

    /** Asks the app to refresh; with [force] false only if its last refresh is old. */
    fun refresh(ctx: Context, force: Boolean, done: (() -> Unit)? = null) {
        init(ctx)
        val a = ctx.applicationContext
        io.execute {
            call(a, "refresh", if (force) "force" else null)
            done?.let { main.post(it) }
        }
    }

    /** The N9's feed icon: from the user's own N9 files, or drawn. */
    fun feedIcon(ctx: Context): android.graphics.drawable.Drawable =
        android.graphics.BitmapFactory.decodeFile("/product/media/etesian/icons/icon-l-rss.png")
            ?.let { android.graphics.drawable.BitmapDrawable(ctx.resources, it) }
            ?: ctx.getDrawable(R.drawable.ic_feed_rss)!!

    /** The Feeds app's main page. */
    fun openApp(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(PACKAGE, "$PACKAGE.MainActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Opens one item in the app's reader. Goes through the app's OpenActivity, which rebuilds its
     * task so back is always the same: to [feedUrl]'s posts (widgets) or All posts (Events, null).
     * Starting the reader directly only brought an already open Feeds to the front.
     */
    fun openItem(id: String, link: String, feedUrl: String? = null): Intent =
        Intent().setComponent(ComponentName(PACKAGE, "$PACKAGE.OpenActivity"))
            .putExtras(itemExtras(id, link, feedUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** A widget row's fill-in for [openItem]'s template; extras only, so every row has its own. */
    fun itemFillIn(item: Item): Intent = Intent().putExtras(itemExtras(item.id, item.link, item.feedUrl))

    private fun itemExtras(id: String, link: String, feedUrl: String?) = Bundle().apply {
        putString(EXTRA_ID, id); putString(EXTRA_LINK, link); putString(EXTRA_FEED, feedUrl.orEmpty())
    }

    const val EXTRA_ID = "feed_item"
    const val EXTRA_LINK = "link"
    const val EXTRA_FEED = "feed"

    /**
     * Once: hands the launcher's own old subscriptions (prefs "feeds", key "list") to the app.
     * The old prefs stay as they were, apart from the "migrated" flag.
     */
    fun migrate(ctx: Context) {
        val a = ctx.applicationContext
        val prefs = a.getSharedPreferences("feeds", Context.MODE_PRIVATE)
        if (prefs.getBoolean("migrated", false)) return
        val arr = runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrElse { JSONArray() }
        if (arr.length() == 0) return
        io.execute {
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }
            val done = call(a, "import", null, Bundle().apply {
                putStringArray("urls", list.map { it.getString("url") }.toTypedArray())
                putStringArray("titles", list.map { it.optString("title", it.getString("url")) }.toTypedArray())
            })
            if (done != null) prefs.edit().putBoolean("migrated", true).apply()
        }
    }
}
