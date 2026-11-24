package com.bompotis.etesian.feeds

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The readable version of an article (2026-11-24, user): the page itself, fetched, with only its article kept, as
 * Firefox's Reader View does it (Mozilla's Readability.js, assets/readability). The page is parsed off screen with
 * DOMParser, so none of its own scripts run and nothing else is loaded. Kept per link, so it opens again offline.
 */
object Readable {
    class Article(val title: String, val html: String)

    private val main = Handler(Looper.getMainLooper())

    /** For the device tests (mocked, never the live site): a page put here (files/test-pages/<key>.html) is used instead. */
    private const val TEST_PAGES = "test-pages"

    fun load(ctx: Context, link: String, done: (Article?) -> Unit) {
        cached(ctx, link)?.let { done(it); return }
        Thread {
            val html = runCatching { fetch(ctx, link) }.getOrNull()
            main.post {
                if (html == null) { done(null); return@post }
                parse(ctx, link, html) { a ->
                    if (a != null) save(ctx, link, a)
                    done(a)
                }
            }
        }.start()
    }

    private fun fetch(ctx: Context, link: String): String {
        File(File(ctx.filesDir, TEST_PAGES), key(link) + ".html").takeIf { it.canRead() }?.let { return it.readText() }
        val c = Feeds.open(link)
        return try { c.inputStream.use { it.readBytes().toString(charset(c)) } } finally { c.disconnect() }
    }

    private fun charset(c: java.net.HttpURLConnection) = runCatching {
        Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(c.contentType.orEmpty())?.groupValues?.get(1)?.trim()
            ?.let { charset(it) }
    }.getOrNull() ?: Charsets.UTF_8

    @SuppressLint("SetJavaScriptEnabled")
    private fun parse(ctx: Context, link: String, page: String, done: (Article?) -> Unit) {
        val script = ctx.assets.open("readability/Readability.js").use { it.readBytes().toString(Charsets.UTF_8) }
        val web = WebView(ctx.applicationContext)
        web.settings.javaScriptEnabled = true
        // Only Readability runs here: the page is data, and nothing is fetched (no images, no scripts, no trackers).
        web.settings.blockNetworkLoads = true
        web.settings.blockNetworkImage = true
        var finished = false
        val finish = { a: Article? -> if (!finished) { finished = true; web.destroy(); done(a) } }
        main.postDelayed({ finish(null) }, 15000)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (finished) return
                val js = "(function(){var d=new DOMParser().parseFromString(" + JSONObject.quote(page) + ",'text/html');" +
                    "var b=d.createElement('base');b.href=" + JSONObject.quote(link) + ";d.head&&d.head.prepend(b);" +
                    "var r=new Readability(d).parse();return r&&r.content?JSON.stringify({t:r.title||'',c:r.content}):'';})()"
                view.evaluateJavascript(js) { result ->
                    val json = runCatching { org.json.JSONTokener(result).nextValue() as String }.getOrNull().orEmpty()
                    finish(runCatching { JSONObject(json).let { Article(it.getString("t"), it.getString("c")) } }.getOrNull())
                }
            }
        }
        web.loadDataWithBaseURL("about:blank", "<html><head><script>$script</script></head><body></body></html>",
            "text/html", "utf-8", null)
    }

    private fun key(link: String) = MessageDigest.getInstance("SHA-1").digest(link.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "readable").apply { mkdirs() }

    private fun cached(ctx: Context, link: String): Article? = runCatching {
        JSONObject(File(dir(ctx), key(link)).readText()).let { Article(it.getString("t"), it.getString("c")) }
    }.getOrNull()

    private fun save(ctx: Context, link: String, a: Article) {
        runCatching { File(dir(ctx), key(link)).writeText(JSONObject().put("t", a.title).put("c", a.html).toString()) }
    }
}
