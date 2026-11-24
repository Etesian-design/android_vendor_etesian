package com.bompotis.etesian.feeds

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Linkwarden (2026-11-24, user): the user's own bookmark archive. An article can be saved to it, and once Linkwarden
 * has preserved it, its readable copy and its PDF are read from there instead of the live page. Signed in with an
 * access token made in Linkwarden's settings.
 */
object Linkwarden {
    class Link(val id: Long, val url: String, val readable: Boolean, val pdf: Boolean)

    private const val PREFS = "linkwarden"
    /** ArchivedFormat in Linkwarden: 2 is the PDF, 3 the readable copy (JSON). */
    private const val PDF = 2
    private const val READABLE = 3

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun server(ctx: Context) = prefs(ctx).getString("server", "").orEmpty()
    private fun token(ctx: Context) = prefs(ctx).getString("token", "").orEmpty()
    fun active(ctx: Context) = server(ctx).isNotEmpty() && token(ctx).isNotEmpty()

    /** Blocking: checks the server and token, and keeps them when they work. An error message, or null. */
    fun signIn(ctx: Context, server: String, token: String): String? = runCatching {
        val base = server.trim().trimEnd('/')
        get(base, token, "/api/v1/links?cursor=0")
        prefs(ctx).edit().putString("server", base).putString("token", token.trim()).apply()
        null
    }.getOrElse { it.message ?: "Couldn't connect" }

    fun signOut(ctx: Context) = prefs(ctx).edit().clear().apply()

    /** Blocking: the saved link for [url], if it is in Linkwarden. */
    fun find(ctx: Context, url: String): Link? {
        val r = JSONObject(get(server(ctx), token(ctx), "/api/v1/links?searchQueryString=" + URLEncoder.encode(url, "UTF-8")))
        val arr = r.optJSONArray("response") ?: return null
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .firstOrNull { same(it.optString("url"), url) }?.let(::link)
    }

    /** Blocking: saves [url] to Linkwarden (its default collection); Linkwarden preserves it in the background. */
    fun save(ctx: Context, url: String, title: String): Link {
        val body = JSONObject().put("url", url).put("name", title).toString()
        return link(JSONObject(http("POST", server(ctx), token(ctx), "/api/v1/links", body)).getJSONObject("response"))
    }

    /** Blocking: Linkwarden's readable copy (title, HTML). */
    fun readable(ctx: Context, link: Link): Readable.Article {
        val o = JSONObject(get(server(ctx), token(ctx), "/api/v1/archives/${link.id}?format=$READABLE"))
        return Readable.Article(o.optString("title"), o.optString("content").ifEmpty { error("No readable copy") })
    }

    /** Blocking: Linkwarden's PDF of the page, downloaded to the cache. */
    fun pdf(ctx: Context, link: Link): File {
        val out = File(File(ctx.cacheDir, "linkwarden").apply { mkdirs() }, "${link.id}.pdf")
        val c = open("GET", server(ctx) + "/api/v1/archives/${link.id}?format=$PDF", token(ctx))
        try { c.inputStream.use { i -> out.outputStream().use { i.copyTo(it) } } } finally { c.disconnect() }
        return out
    }

    /** Linkwarden keeps a path, or "unavailable"/"pending", for each preserved format. */
    private fun available(v: String) = v.isNotEmpty() && v != "null" && v != "unavailable" && v != "pending"

    private fun link(o: JSONObject) = Link(o.getLong("id"), o.optString("url"),
        available(o.optString("readable")), available(o.optString("pdf")))

    private fun same(a: String, b: String) = norm(a) == norm(b)
    private fun norm(u: String) = u.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    private fun get(base: String, token: String, path: String) = http("GET", base, token, path, null)

    private fun open(method: String, url: String, token: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10000; c.readTimeout = 30000
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("User-Agent", "EtesianFeeds/1.0 (Android)")
        return c
    }

    private fun http(method: String, base: String, token: String, path: String, body: String?): String {
        val c = open(method, base + path, token)
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            if (code == 401 || code == 403) error("Linkwarden refused the access token")
            if (code >= 400) error("Linkwarden answered $code")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }
}
