package com.bompotis.etesian.feeds

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A sync account (2026-11-24, user): the subscriptions, the posts and what is read or starred live on the user's own
 * server, and Feeds shows and changes them there. With no account, Feeds fetches its feeds itself, as before.
 *
 * A refresh first sends what changed here since the last one (read, unread, star, unstar: queued, so it also works
 * offline), then takes the server's feeds, posts and states. Adding and removing a feed go to the server.
 */
object Sync {
    const val NONE = ""
    const val FRESHRSS = "freshrss"
    const val NEXTCLOUD = "nextcloud"

    /** The services, in the order the account dialog lists them. */
    val SERVICES = listOf(FRESHRSS to "FreshRSS", NEXTCLOUD to "Nextcloud News")

    class Account(val service: String, val server: String, val user: String, val password: String) {
        val name get() = SERVICES.firstOrNull { it.first == service }?.second ?: service
    }

    class Remote(val feeds: List<Feeds.Feed>, val items: List<Feeds.Item>, val read: Set<String>, val starred: Set<String>)

    private const val PREFS = "sync"
    private const val KEY_PENDING = "pending"
    /** Posts taken from the server at each refresh, newest first (read and unread). */
    private const val BATCH = 200

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun account(ctx: Context): Account? = prefs(ctx).let { p ->
        p.getString("service", NONE)!!.takeIf { it.isNotEmpty() }?.let {
            Account(it, p.getString("server", "")!!, p.getString("user", "")!!, p.getString("password", "")!!)
        }
    }

    fun active(ctx: Context) = account(ctx) != null

    /** Checks the account on the server (blocking); stores it when it works. Returns an error message or null. */
    fun signIn(ctx: Context, a: Account): String? = runCatching {
        val s = service(a)
        s.login()
        s.feeds()
        prefs(ctx).edit().putString("service", a.service).putString("server", a.server.trim().trimEnd('/'))
            .putString("user", a.user).putString("password", a.password).remove(KEY_PENDING).remove("feed_ids").apply()
        null
    }.getOrElse { it.message ?: "Couldn't sign in" }

    fun signOut(ctx: Context) { prefs(ctx).edit().clear().apply() }

    /** A read or star change made here, for the server at the next refresh. */
    fun queue(ctx: Context, op: String, id: String) {
        if (!active(ctx)) return
        val opposite = mapOf("read" to "unread", "unread" to "read", "star" to "unstar", "unstar" to "star")[op]
        val s = prefs(ctx).getStringSet(KEY_PENDING, emptySet())!!.toMutableSet()
        s.remove("$opposite:$id"); s.add("$op:$id")
        prefs(ctx).edit().putStringSet(KEY_PENDING, s).apply()
    }

    /** Blocking: sends the queued changes, then returns everything on the server. */
    fun pull(ctx: Context): Remote {
        val a = account(ctx) ?: error("No account")
        val s = service(a)
        s.login()
        val pending = prefs(ctx).getStringSet(KEY_PENDING, emptySet())!!
        if (pending.isNotEmpty()) {
            pending.groupBy({ it.substringBefore(':') }, { it.substringAfter(':') }).forEach { (op, ids) -> s.mark(op, ids) }
            prefs(ctx).edit().remove(KEY_PENDING).apply()
        }
        val feeds = s.feeds()
        prefs(ctx).edit().putString("feed_ids", JSONObject(feeds.associate { it.second.url to it.first }).toString()).apply()
        return s.items(feeds.map { it.second })
    }

    /** Blocking: subscribes on the server. */
    fun subscribe(ctx: Context, url: String) { service(account(ctx)!!).also { it.login() }.subscribe(url) }

    /** Blocking: unsubscribes on the server. */
    fun unsubscribe(ctx: Context, url: String) {
        val id = runCatching { JSONObject(prefs(ctx).getString("feed_ids", "{}")!!).getString(url) }.getOrNull() ?: return
        service(account(ctx)!!).also { it.login() }.unsubscribe(id)
    }

    private fun service(a: Account): Service = when (a.service) {
        FRESHRSS -> GReader(a)
        NEXTCLOUD -> NextcloudNews(a)
        else -> error("Unknown service")
    }

    private interface Service {
        fun login()
        /** (the server's id, the feed). */
        fun feeds(): List<Pair<String, Feeds.Feed>>
        fun items(feeds: List<Feeds.Feed>): Remote
        /** [op]: read, unread, star or unstar; [ids]: Feeds item ids. */
        fun mark(op: String, ids: List<String>)
        fun subscribe(url: String)
        fun unsubscribe(id: String)
    }

    // --- HTTP ---------------------------------------------------------------------------------------------------

    private fun http(method: String, url: String, headers: Map<String, String>, body: String? = null, type: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "EtesianFeeds/1.0 (Android)")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type ?: "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            if (code == 401 || code == 403) error("Wrong user name or password")
            if (code >= 400) error("The server answered $code")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun form(vararg p: Pair<String, String>) = p.joinToString("&") { enc(it.first) + "=" + enc(it.second) }

    private fun excerpt(html: String) = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT)
        .toString().replace(Regex("\\s+"), " ").trim().take(300)

    // --- FreshRSS: the Google Reader API (api/greader.php) ------------------------------------------------------

    private class GReader(val a: Account) : Service {
        val base = a.server.trim().trimEnd('/').let { if (it.endsWith("greader.php")) it else "$it/api/greader.php" }
        var auth = ""
        val h get() = mapOf("Authorization" to "GoogleLogin auth=$auth")
        val token by lazy { http("GET", "$base/reader/api/0/token", h).trim() }

        override fun login() {
            val r = http("POST", "$base/accounts/ClientLogin", emptyMap(), form("Email" to a.user, "Passwd" to a.password))
            auth = r.lines().firstOrNull { it.startsWith("Auth=") }?.substringAfter("=") ?: error("Wrong user name or password")
        }

        override fun feeds(): List<Pair<String, Feeds.Feed>> {
            val arr = JSONObject(http("GET", "$base/reader/api/0/subscription/list?output=json", h)).getJSONArray("subscriptions")
            return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                it.getString("id") to Feeds.Feed(it.optString("url").ifEmpty { it.getString("id") }, it.optString("title"))
            }
        }

        override fun items(feeds: List<Feeds.Feed>): Remote {
            val byStream = JSONObject(http("GET", "$base/reader/api/0/subscription/list?output=json", h))
                .getJSONArray("subscriptions").let { arr -> (0 until arr.length()).associate {
                    arr.getJSONObject(it).let { f -> f.getString("id") to f.optString("url") } } }
            val arr = JSONObject(http("GET", "$base/reader/api/0/stream/contents/user/-/state/com.google/reading-list?output=json&n=$BATCH", h))
                .getJSONArray("items")
            val read = HashSet<String>(); val starred = HashSet<String>()
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
                val id = "fr:" + o.getString("id")
                val cats = o.optJSONArray("categories") ?: JSONArray()
                (0 until cats.length()).map { cats.getString(it) }.forEach { c ->
                    if (c.endsWith("/state/com.google/read")) read += id
                    if (c.endsWith("/state/com.google/starred")) starred += id
                }
                val origin = o.optJSONObject("origin")
                val stream = origin?.optString("streamId").orEmpty()
                val html = (o.optJSONObject("content") ?: o.optJSONObject("summary"))?.optString("content").orEmpty()
                val link = (o.optJSONArray("canonical") ?: o.optJSONArray("alternate"))?.optJSONObject(0)?.optString("href").orEmpty()
                Feeds.Item(byStream[stream] ?: stream, origin?.optString("title").orEmpty(), o.optString("title"),
                    excerpt(html), html, link, o.optLong("published") * 1000, id)
            }
            return Remote(feeds, items, read, starred)
        }

        override fun mark(op: String, ids: List<String>) {
            val tag = if (op == "read" || op == "unread") "user/-/state/com.google/read" else "user/-/state/com.google/starred"
            val add = op == "read" || op == "star"
            ids.chunked(50).forEach { chunk ->
                val body = (chunk.map { "i" to it.removePrefix("fr:") } + ((if (add) "a" else "r") to tag) + ("T" to token))
                http("POST", "$base/reader/api/0/edit-tag", h, form(*body.toTypedArray()))
            }
        }

        override fun subscribe(url: String) {
            http("POST", "$base/reader/api/0/subscription/quickadd", h, form("quickadd" to url, "T" to token))
        }

        override fun unsubscribe(id: String) {
            http("POST", "$base/reader/api/0/subscription/edit", h, form("ac" to "unsubscribe", "s" to id, "T" to token))
        }
    }

    // --- Nextcloud News: its REST API v1-3 ------------------------------------------------------------------------

    private class NextcloudNews(val a: Account) : Service {
        val base = a.server.trim().trimEnd('/').let { if ("/apps/news/api/" in it) it else "$it/index.php/apps/news/api/v1-3" }
        val h = mapOf("Authorization" to "Basic " + Base64.encodeToString("${a.user}:${a.password}".toByteArray(), Base64.NO_WRAP))
        var urls = emptyMap<Long, Feeds.Feed>()

        override fun login() {}

        override fun feeds(): List<Pair<String, Feeds.Feed>> {
            val arr = JSONObject(http("GET", "$base/feeds", h)).getJSONArray("feeds")
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                it.getLong("id") to Feeds.Feed(it.getString("url"), it.optString("title").ifEmpty { it.getString("url") })
            }
            urls = list.toMap()
            return list.map { it.first.toString() to it.second }
        }

        override fun items(feeds: List<Feeds.Feed>): Remote {
            val read = HashSet<String>(); val starred = HashSet<String>()
            // type 3: all posts; type 2: starred ones (which may be older than the newest batch).
            val all = listOf("$base/items?type=3&id=0&getRead=true&batchSize=$BATCH", "$base/items?type=2&id=0&getRead=true&batchSize=-1")
                .flatMap { u -> JSONObject(http("GET", u, h)).getJSONArray("items").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } } }
                .distinctBy { it.getLong("id") }
            val items = all.map { o ->
                val id = "nc:" + o.getLong("id")
                if (!o.optBoolean("unread", true)) read += id
                if (o.optBoolean("starred")) starred += id
                val feed = urls[o.optLong("feedId")]
                val html = o.optString("body")
                Feeds.Item(feed?.url.orEmpty(), feed?.title.orEmpty(), o.optString("title"), excerpt(html), html,
                    o.optString("url"), o.optLong("pubDate") * 1000, id)
            }
            return Remote(feeds, items, read, starred)
        }

        override fun mark(op: String, ids: List<String>) {
            val numbers = JSONArray(ids.mapNotNull { it.removePrefix("nc:").toLongOrNull() })
            http("POST", "$base/items/$op/multiple", h, JSONObject().put("itemIds", numbers).toString(), "application/json")
        }

        override fun subscribe(url: String) {
            http("POST", "$base/feeds", h, JSONObject().put("url", url).put("folderId", JSONObject.NULL).toString(), "application/json")
        }

        override fun unsubscribe(id: String) { http("DELETE", "$base/feeds/$id", h) }
    }
}
