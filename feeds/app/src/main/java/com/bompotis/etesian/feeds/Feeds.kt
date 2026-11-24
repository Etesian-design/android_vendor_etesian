package com.bompotis.etesian.feeds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * RSS and Atom feeds, as in the N9 Feeds app: the user subscribes to feed URLs, which refresh in
 * the background. The launcher's Events view reads them through [FeedsProvider].
 */
object Feeds {
    class Feed(val url: String, val title: String)

    class Item(
        val feedUrl: String,
        val feedTitle: String,
        val title: String,
        /** Plain-text excerpt for the Events row. */
        val summary: String,
        /** The item's HTML, for the reader. */
        val html: String,
        val link: String,
        val time: Long,
        val id: String,
    )

    private const val PREFS = "feeds"
    private const val KEY_FEEDS = "list"
    private const val KEY_REFRESHED = "refreshed"
    private const val MAX_ITEMS = 40
    private const val PER_FEED = 15
    const val AUTO_REFRESH_MS = 30 * 60 * 1000L

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()
    @Volatile var refreshing = false
        private set
    @Volatile private var items: List<Item>? = null

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    /** Set by [FeedsProvider.onCreate], which runs first in the process. */
    lateinit var app: Context

    private fun changed() {
        if (::app.isInitialized) app.contentResolver.notifyChange(FeedsProvider.ITEMS, null)
        main.post { listeners.toList().forEach { it() } }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun feeds(ctx: Context): List<Feed> {
        val arr = runCatching { JSONArray(prefs(ctx).getString(KEY_FEEDS, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { Feed(it.getString("url"), it.optString("title", it.getString("url"))) }
    }

    private fun save(ctx: Context, list: List<Feed>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title)) }
        prefs(ctx).edit().putString(KEY_FEEDS, arr.toString()).apply()
        changed()
    }

    fun rename(ctx: Context, url: String, title: String) =
        save(ctx, feeds(ctx).map { if (it.url == url) Feed(url, title) else it })

    /**
     * Removes [url] here. Signed in to an account: with [onServer] it is unsubscribed there too; otherwise (or if the
     * server can't) it stays on the server and every sync skips it ([Sync.ignored]). [done] gets a message for the user
     * when the server could not remove it, or null.
     */
    fun remove(ctx: Context, url: String, onServer: Boolean = false, done: (String?) -> Unit = {}) {
        val app = ctx.applicationContext
        if (Sync.active(app)) {
            val title = feeds(app).firstOrNull { it.url == url }?.title ?: url
            if (!onServer) Sync.ignore(app, url, title)
            else io.execute {
                val error = runCatching { Sync.unsubscribe(app, url) }.exceptionOrNull()
                if (error != null) Sync.ignore(app, url, title)
                main.post { done(error?.let { "Couldn't remove it on the server (${it.message}). It won't sync to this phone." }) }
            }
        }
        dropLocal(ctx, url)
    }

    private fun dropLocal(ctx: Context, url: String) {
        save(ctx, feeds(ctx).filterNot { it.url == url })
        items = items(ctx).filterNot { it.feedUrl == url }
        writeCache(ctx)
        changed()
    }

    /**
     * Subscribes to [input] (a feed URL, or a site whose page links to its feed), fetching it once
     * to learn its title. [done] gets an error message, or null on success.
     */
    fun add(ctx: Context, input: String, done: (String?) -> Unit) {
        val app = ctx.applicationContext
        io.execute {
            if (Sync.active(app)) {
                // The account's server fetches and finds the feed; it comes back with the refresh.
                val error = runCatching {
                    Sync.subscribe(app, input.trim().let { if ("://" in it) it else "https://$it" })
                    fetchAll(app); null
                }.getOrElse { it.message ?: "Couldn't add that feed" }
                main.post { done(error) }
                return@execute
            }
            val error = runCatching {
                var url = input.trim().let { if ("://" in it) it else "https://$it" }
                var parsed = runCatching { fetch(url) }.getOrNull()
                if (parsed == null) {
                    // Maybe a web page: look for its <link rel="alternate" type="application/rss+xml">.
                    url = discover(url) ?: error("No feed found at that address")
                    parsed = fetch(url)
                }
                if (feeds(app).none { it.url == url }) save(app, feeds(app) + Feed(url, parsed.first))
                merge(app, url, parsed.first, parsed.second)
                setFailed(app, url, false)
                null
            }.getOrElse { it.message ?: "Couldn't add that feed" }
            main.post { done(error) }
        }
    }

    /** Subscribes to every feed in an OPML file; returns how many were new. */
    fun importOpml(ctx: Context, input: InputStream): Int {
        val found = mutableListOf<Feed>()
        val p = Xml.newPullParser()
        p.setInput(input, null)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name.equals("outline", true)) {
                val url = p.getAttributeValue(null, "xmlUrl") ?: continue
                val title = p.getAttributeValue(null, "title") ?: p.getAttributeValue(null, "text") ?: url
                found += Feed(url, title)
            }
        }
        val existing = feeds(ctx)
        val fresh = found.filter { f -> existing.none { it.url == f.url } }.distinctBy { it.url }
        save(ctx, existing + fresh)
        return fresh.size
    }

    /** Newest first, across all feeds. */
    fun items(ctx: Context): List<Item> = items ?: readCache(ctx).also { items = it }

    fun lastRefresh(ctx: Context) = prefs(ctx).getLong(KEY_REFRESHED, 0L)

    /** Refreshes all feeds; with [force] false only if the last refresh is old. */
    fun refresh(ctx: Context, force: Boolean, done: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        if (refreshing || (feeds(app).isEmpty() && !Sync.active(app)) ||
            (!force && System.currentTimeMillis() - lastRefresh(app) < AUTO_REFRESH_MS)) {
            done?.let { main.post(it) }
            return
        }
        refreshing = true
        changed()
        io.execute {
            fetchAll(app)
            refreshing = false
            changed()
            done?.let { main.post(it) }
        }
    }

    /** Minutes between background refreshes; 0: only when Events or the app is opened. */
    fun interval(ctx: Context) = prefs(ctx).getInt("interval", 60)

    fun setInterval(ctx: Context, minutes: Int) = prefs(ctx).edit().putInt("interval", minutes).apply()

    /** Blocking refresh, for the background job. */
    fun refreshNow(ctx: Context) {
        fetchAll(ctx)
        changed()
    }

    private fun fetchAll(ctx: Context) {
        if (Sync.active(ctx)) { pullAccount(ctx); return }
        for (f in feeds(ctx)) {
            runCatching { fetch(f.url) }
                .onSuccess { (_, list) -> merge(ctx, f.url, f.title, list); setFailed(ctx, f.url, false) }
                .onFailure { setFailed(ctx, f.url, true) }
        }
        prefs(ctx).edit().putLong(KEY_REFRESHED, System.currentTimeMillis()).apply()
    }

    /**
     * The account's server has the feeds, posts and states: they replace what is here. Changes made here went to the
     * server first (Sync.pull). Offline, everything stays as it was and the feeds show "Can't update".
     */
    private fun pullAccount(ctx: Context) {
        val r = runCatching { Sync.pull(ctx) }.getOrElse {
            feeds(ctx).forEach { f -> setFailed(ctx, f.url, true) }
            return
        }
        save(ctx, r.feeds)
        r.feeds.forEach { setFailed(ctx, it.url, false) }
        synchronized(this) {
            items = r.items.sortedByDescending { it.time }
            writeCache(ctx)
        }
        prefs(ctx).edit().apply {
            r.read?.let { putStringSet(KEY_READ, it) }
            r.starred?.let { putStringSet(KEY_STARRED, it) }
        }.putLong(KEY_REFRESHED, System.currentTimeMillis()).apply()
        changed()
    }

    /**
     * Signing in ([toAccount]) or out: the phone's own feeds are put aside while an account has the list, and come back
     * when it is signed out; the account's posts and states are not kept after it.
     */
    fun switchPlace(ctx: Context, toAccount: Boolean) {
        val p = prefs(ctx)
        val e = p.edit()
        if (toAccount) e.putString(KEY_OWN, p.getString(KEY_FEEDS, "[]")).putStringSet(KEY_OWN_READ, set(ctx, KEY_READ))
            .putStringSet(KEY_OWN_STARRED, set(ctx, KEY_STARRED)).remove(KEY_FEEDS).remove(KEY_READ).remove(KEY_STARRED)
        else e.putString(KEY_FEEDS, p.getString(KEY_OWN, "[]")).putStringSet(KEY_READ, p.getStringSet(KEY_OWN_READ, emptySet()))
            .putStringSet(KEY_STARRED, p.getStringSet(KEY_OWN_STARRED, emptySet())).remove(KEY_OWN).remove(KEY_OWN_READ).remove(KEY_OWN_STARRED)
        e.remove(KEY_FAILED).putLong(KEY_REFRESHED, 0L).apply()
        items = emptyList(); writeCache(ctx); changed()
    }
    private const val KEY_OWN = "own_list"
    private const val KEY_OWN_READ = "own_read"
    private const val KEY_OWN_STARRED = "own_starred"

    // --- Per-feed state: failures, last update, last seen; read items -----------------------

    private const val KEY_FAILED = "failed"
    private const val KEY_READ = "read"

    private fun set(ctx: Context, key: String) = prefs(ctx).getStringSet(key, emptySet())!!
    private fun edit(ctx: Context, key: String, value: String, on: Boolean) {
        val s = set(ctx, key).toMutableSet()
        if (if (on) s.add(value) else s.remove(value)) prefs(ctx).edit().putStringSet(key, s).apply()
    }

    fun failed(ctx: Context, url: String) = url in set(ctx, KEY_FAILED)

    private fun setFailed(ctx: Context, url: String, failed: Boolean) {
        edit(ctx, KEY_FAILED, url, failed)
        if (!failed) prefs(ctx).edit().putLong("updated:$url", System.currentTimeMillis()).apply()
    }

    /** When [url] last fetched fine (0 if never). */
    fun updated(ctx: Context, url: String) = prefs(ctx).getLong("updated:$url", 0L)

    /** When the user last opened [url]'s posts ("" for All posts). */
    fun seen(ctx: Context, url: String) = prefs(ctx).getLong("seen:$url", 0L)
    fun markSeen(ctx: Context, url: String) = prefs(ctx).edit().putLong("seen:$url", System.currentTimeMillis()).apply()

    fun isRead(ctx: Context, id: String) = id in set(ctx, KEY_READ)

    fun setRead(ctx: Context, id: String, read: Boolean) {
        if (isRead(ctx, id) != read) Sync.queue(ctx, if (read) "read" else "unread", id)
        edit(ctx, KEY_READ, id, read); changed()
    }

    fun markAllRead(ctx: Context, feedUrl: String?) {
        val ids = items(ctx).filter { feedUrl == null || it.feedUrl == feedUrl }.map { it.id }
        ids.filterNot { isRead(ctx, it) }.forEach { Sync.queue(ctx, "read", it) }
        // Forget ids that are no longer cached, so the set doesn't grow forever.
        val cached = items(ctx).map { it.id }.toSet()
        prefs(ctx).edit().putStringSet(KEY_READ, (set(ctx, KEY_READ).filter { it in cached } + ids).toSet()).apply()
        changed()
    }

    fun unread(ctx: Context, feedUrl: String?): Int {
        val read = set(ctx, KEY_READ)
        return items(ctx).count { (feedUrl == null || it.feedUrl == feedUrl) && it.id !in read }
    }

    /**
     * Adds [list] (from the launcher's old Feeds list) to the subscriptions; returns how many were
     * new. Their items come with the next refresh.
     */
    fun import(ctx: Context, list: List<Feed>): Int {
        val existing = feeds(ctx)
        val fresh = list.filter { f -> existing.none { it.url == f.url } }.distinctBy { it.url }
        if (fresh.isNotEmpty()) save(ctx, existing + fresh)
        return fresh.size
    }

    @Synchronized
    private fun merge(ctx: Context, url: String, feedTitle: String, fetched: List<Item>) {
        val mine = fetched.map {
            Item(url, feedTitle, it.title, it.summary, it.html, it.link, it.time, it.id)
        }.sortedByDescending { it.time }.take(PER_FEED)
        val all = (items(ctx).filterNot { it.feedUrl == url } + mine).sortedByDescending { it.time }
        // Starred items stay even when newer ones push them out.
        val kept = all.take(MAX_ITEMS)
        items = kept + all.drop(MAX_ITEMS).filter { isStarred(ctx, it.id) && it.feedUrl != url } +
            items(ctx).filter { it.feedUrl == url && isStarred(ctx, it.id) && mine.none { m -> m.id == it.id } }
        items = items!!.sortedByDescending { it.time }
        writeCache(ctx)
        changed()
    }

    private const val KEY_STARRED = "starred"

    fun isStarred(ctx: Context, id: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_STARRED, emptySet())!!.contains(id)

    fun setStarred(ctx: Context, id: String, starred: Boolean) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_STARRED, emptySet())!!.toMutableSet()
        if (starred != (id in set)) Sync.queue(ctx, if (starred) "star" else "unstar", id)
        if (starred) set.add(id) else set.remove(id)
        prefs.edit().putStringSet(KEY_STARRED, set).apply()
    }

    fun find(ctx: Context, id: String) = items(ctx).firstOrNull { it.id == id }

    // --- Network and parsing -------------------------------------------------------------

    internal fun open(url: String): HttpURLConnection {
        var current = url
        repeat(5) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "EtesianFeeds/1.0 (Android)")
            c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            val code = c.responseCode
            if (code in 300..399) {
                current = URL(URL(current), c.getHeaderField("Location")).toString()
                c.disconnect()
            } else {
                if (code >= 400) error("HTTP $code")
                return c
            }
        }
        error("Too many redirects")
    }

    /** (feed title, items) for the RSS or Atom document at [url]. */
    private fun fetch(url: String): Pair<String, List<Item>> {
        val c = open(url)
        return try { c.inputStream.use { parse(it, url) } } finally { c.disconnect() }
    }

    private fun parse(input: InputStream, url: String): Pair<String, List<Item>> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        var feedTitle = ""
        val out = mutableListOf<Item>()
        var sawFeed = false
        var inItem = false
        var title = ""; var link = ""; var html = ""; var date = ""; var id = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name.lowercase(Locale.ROOT)) {
                    "rss", "feed", "rdf:rdf" -> sawFeed = true
                    "item", "entry" -> {
                        inItem = true; title = ""; link = ""; html = ""; date = ""; id = ""
                    }
                    "title" -> {
                        val t = text(p)
                        if (inItem) title = t else if (feedTitle.isEmpty()) feedTitle = t
                    }
                    "link" -> if (inItem) {
                        val href = p.getAttributeValue(null, "href")
                        val rel = p.getAttributeValue(null, "rel")
                        if (href != null) { if (rel == null || rel == "alternate") link = href }
                        else link = text(p)
                    }
                    "description", "summary" -> if (inItem && html.isEmpty()) html = text(p)
                    "content:encoded", "content" -> if (inItem) html = text(p).ifEmpty { html }
                    "pubdate", "published", "updated", "dc:date" -> if (inItem && date.isEmpty()) date = text(p)
                    "guid", "id" -> if (inItem) id = text(p)
                }
                XmlPullParser.END_TAG -> if (p.name.lowercase(Locale.ROOT) in setOf("item", "entry") && inItem) {
                    inItem = false
                    val absolute = runCatching { URL(URL(url), link).toString() }.getOrDefault(link)
                    out += Item(url, feedTitle, clean(title), excerpt(html), html, absolute,
                        parseDate(date), id.ifEmpty { absolute.ifEmpty { title } })
                }
            }
        }
        if (!sawFeed) error("Not a feed")
        return feedTitle.ifEmpty { URL(url).host } to out
    }

    /** Text of the current element, tolerating nested markup (e.g. Atom xhtml content). */
    private fun text(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(p.text)
                XmlPullParser.START_TAG -> { depth++; sb.append('<').append(p.name).append('>') }
                XmlPullParser.END_TAG -> { depth--; if (depth > 0) sb.append("</").append(p.name).append('>') }
                XmlPullParser.END_DOCUMENT -> return sb.toString().trim()
            }
        }
        return sb.toString().trim()
    }

    private fun clean(s: String) =
        Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim()

    private fun excerpt(html: String) = clean(html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), ""))
        .replace("￼", "").trim().take(280)

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm Z",
        "dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z",
    )

    private fun parseDate(s: String): Long {
        val t = s.trim()
        if (t.isEmpty()) return System.currentTimeMillis()
        runCatching { return java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        runCatching { return java.time.Instant.parse(t).toEpochMilli() }
        for (f in dateFormats) {
            runCatching { return SimpleDateFormat(f, Locale.ENGLISH).parse(t)!!.time }
        }
        return System.currentTimeMillis()
    }

    /** The feed a web page advertises in its head. */
    private fun discover(pageUrl: String): String? {
        val c = open(pageUrl)
        val page = try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
        val tag = Regex("<link[^>]+type=[\"']application/(rss|atom)\\+xml[\"'][^>]*>", RegexOption.IGNORE_CASE)
            .find(page)?.value ?: return null
        val href = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
            ?: return null
        return URL(URL(pageUrl), Html.fromHtml(href, 0).toString()).toString()
    }

    // --- Cache -----------------------------------------------------------------------------

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "feed_items.json")

    private fun writeCache(ctx: Context) {
        val arr = JSONArray()
        items.orEmpty().forEach {
            arr.put(JSONObject().put("f", it.feedUrl).put("ft", it.feedTitle).put("t", it.title)
                .put("s", it.summary).put("h", it.html).put("l", it.link).put("d", it.time).put("i", it.id))
        }
        runCatching { cacheFile(ctx).writeText(arr.toString()) }
    }

    private fun readCache(ctx: Context): List<Item> = runCatching {
        val arr = JSONArray(cacheFile(ctx).readText())
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Item(it.getString("f"), it.optString("ft"), it.optString("t"), it.optString("s"),
                it.optString("h"), it.optString("l"), it.optLong("d"), it.optString("i"))
        }
    }.getOrElse { emptyList() }

    // --- Site icons ------------------------------------------------------------------------

    private val icons = HashMap<String, Bitmap?>()

    /** The site's icon for [feedUrl], or null (and it's fetched in the background for next time). */
    fun icon(ctx: Context, feedUrl: String): Bitmap? {
        val host = runCatching { URL(feedUrl).host }.getOrNull() ?: return null
        if (icons.containsKey(host)) return icons[host]
        val file = File(File(ctx.filesDir, "feed_icons").apply { mkdirs() }, "$host.png")
        if (file.exists()) {
            val b = BitmapFactory.decodeFile(file.path)
            icons[host] = b
            return b
        }
        icons[host] = null
        val app = ctx.applicationContext
        io.execute {
            val b = runCatching {
                // The site's apple-touch-icon is large and square; fall back to the favicon.
                val base = "https://" + host.removePrefix("feeds.").removePrefix("rss.")
                listOf("$base/apple-touch-icon.png", "$base/favicon.ico", "https://$host/favicon.ico")
                    .firstNotNullOfOrNull { u ->
                        runCatching {
                            val c = open(u)
                            try { c.inputStream.use { BitmapFactory.decodeStream(it) } } finally { c.disconnect() }
                        }.getOrNull()
                    }
            }.getOrNull()
            if (b != null) {
                runCatching { file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                main.post { icons[host] = b; listeners.toList().forEach { it() } }
            }
        }
        return null
    }
}
