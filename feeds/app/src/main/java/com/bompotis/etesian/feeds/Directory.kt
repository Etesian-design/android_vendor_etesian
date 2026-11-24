package com.bompotis.etesian.feeds

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Find feeds (2026-11-24, user): search well-known feed lists instead of typing addresses. The lists are large, so
 * nothing is fetched until the first search; then they are downloaded once, reduced to a small full-text index on the
 * phone (title, address, a line about it), and searched offline. They are fetched again when a month old.
 *
 * - rumca-js/awesome-database-feeds: about 57,000 feeds with titles and descriptions (data: CC BY 4.0).
 * - infobubble World RSS Database: feeds by country and category, addresses only (ODbL).
 */
object Directory {
    class Result(val title: String, val url: String, val about: String, val source: String)

    class Source(val id: String, val name: String, val url: String, val licence: String)

    val SOURCES = listOf(
        Source("rumca", "Awesome database of feeds (rumca-js)",
            "https://github.com/rumca-js/awesome-database-feeds/raw/main/feeds.db.zip", "CC BY 4.0"),
        Source("infobubble", "InfoBubble World RSS Database",
            "https://raw.githubusercontent.com/infobubble/infobubble-support/main/Sources/all.sources.rss.yaml", "ODbL 1.0"),
    )

    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    /** For the device tests (mocked, never the live lists): a file holding a base address the lists are read from. */
    private const val TEST_BASE = "test-directory-base"

    private fun dbFile(ctx: Context) = File(ctx.filesDir, "directory.db")

    fun ready(ctx: Context) = dbFile(ctx).exists()
    fun stale(ctx: Context) = !ready(ctx) || System.currentTimeMillis() - dbFile(ctx).lastModified() > MAX_AGE_MS

    /**
     * Blocking: downloads the lists and builds the index in a new file, then swaps it in (a failed download leaves the
     * old index). [progress] gets the source being fetched.
     */
    fun download(ctx: Context, progress: (String) -> Unit) {
        val tmp = File(ctx.filesDir, "directory.db.new").apply { delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(tmp, null)
        try {
            db.execSQL("CREATE VIRTUAL TABLE feeds USING fts4(title, url, about, source, notindexed=source)")
            db.beginTransaction()
            for (s in SOURCES) {
                progress(s.name)
                when (s.id) {
                    "rumca" -> rumca(ctx, fetchFile(ctx, s), db)
                    "infobubble" -> infobubble(fetchFile(ctx, s), db)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            runCatching { db.endTransaction() }
            db.close()
        }
        File(ctx.cacheDir, "directory").deleteRecursively()
        if (!tmp.renameTo(dbFile(ctx))) error("Couldn't save the lists")
    }

    private fun fetchFile(ctx: Context, s: Source): File {
        val base = runCatching { File(ctx.filesDir, TEST_BASE).readText().trim() }.getOrNull()
        val url = if (base.isNullOrEmpty()) s.url else "$base/" + s.url.substringAfterLast('/')
        val out = File(File(ctx.cacheDir, "directory").apply { mkdirs() }, s.id)
        val c = Feeds.open(url)
        try { c.inputStream.use { i -> out.outputStream().use { i.copyTo(it) } } } finally { c.disconnect() }
        return out
    }

    private fun add(db: SQLiteDatabase, title: String, url: String, about: String, source: String) {
        db.insert("feeds", null, ContentValues().apply {
            put("title", title); put("url", url); put("about", about); put("source", source)
        })
    }

    /** The zip holds an SQLite database; only working feeds' title, address and description are kept. */
    private fun rumca(ctx: Context, zip: File, into: SQLiteDatabase) {
        val db = File(zip.parentFile, "rumca.db")
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: error("No database in the rumca-js archive")
                if (e.name.endsWith(".db")) { db.outputStream().use { z.copyTo(it) }; break }
            }
        }
        val src = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            src.rawQuery("SELECT title, link, description FROM linkdatamodel WHERE status_code < 400 AND date_dead_since IS NULL", null).use { c ->
                while (c.moveToNext()) {
                    val url = c.getString(1) ?: continue
                    val about = c.getString(2).orEmpty().replace(Regex("\\s+"), " ").trim().take(160)
                    add(into, c.getString(0).orEmpty().ifBlank { host(url) }, url, about, "rumca")
                }
            }
        } finally { src.close() }
    }

    /** Country, then category, then '- url' lines; the title is the site's address. */
    private fun infobubble(yaml: File, into: SQLiteDatabase) {
        var country = ""; var category = ""
        yaml.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank() || line.trimStart().startsWith("#")) continue
                val indent = line.length - line.trimStart().length
                val t = line.trim()
                when {
                    t.startsWith("- ") -> {
                        val url = t.removePrefix("- ").trim().trim('\'', '"')
                        if (url.startsWith("http")) add(into, host(url), url, "$country · $category", "infobubble")
                    }
                    t.endsWith(":") && indent == 0 -> country = t.dropLast(1).trim('\'', '"')
                    t.endsWith(":") -> category = t.dropLast(1).trim('\'', '"')
                }
            }
        }
    }

    private fun host(url: String) = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull() ?: url

    /** Every word must match (word starts); titles that match come first. */
    fun search(ctx: Context, query: String, limit: Int = 100): List<Result> {
        if (!ready(ctx)) return emptyList()
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val match = words.joinToString(" ") { "$it*" }
        val db = SQLiteDatabase.openDatabase(dbFile(ctx).path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val out = ArrayList<Result>()
            db.rawQuery("SELECT title, url, about, source FROM feeds WHERE feeds MATCH ? LIMIT 2000", arrayOf(match)).use { c ->
                while (c.moveToNext()) out += Result(c.getString(0), c.getString(1), c.getString(2), c.getString(3))
            }
            return out.distinctBy { it.url.trimEnd('/').removePrefix("https://").removePrefix("http://") }
                .sortedByDescending { r -> words.count { r.title.lowercase().contains(it) } }
                .take(limit)
        } finally { db.close() }
    }
}
