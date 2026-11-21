package com.bompotis.etesian.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Etesian Glance (com.bompotis.etesian.glance, its own app): the News screen's stories and the applications
 * search's results from the rest of the phone. Glance owns them, and the permissions to read contacts, messages,
 * notifications and files; this reads its provider. Everything a row opens is an intent for Glance's own activity.
 */
object Glance {
    const val PACKAGE = "com.bompotis.etesian.glance"
    private const val PERMISSION = "$PACKAGE.permission.READ"
    private val ROOT: Uri = Uri.parse("content://$PACKAGE")
    private val NEWS: Uri = Uri.withAppendedPath(ROOT, "news")

    class Story(
        val section: String,
        val title: String,
        val source: String,
        val time: Long,
        val topic: String,
        val outlets: Int,
        val link: String,
        val open: String,
        /** The story's picture (a URL), or "" (a tile in the outlet's colour stands in). */
        val image: String = "",
    )

    /** A search result from Glance: [section] is its heading ("Contacts", "Messages"...). */
    class Result(val kind: String, val section: String, val title: String, val subtitle: String, val pkg: String,
                 val time: Long, val open: String)

    fun installed(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun available(ctx: Context) =
        installed(ctx) && ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The ranked stories (Top stories, then For you). Off the main thread: it may rank them first. */
    fun stories(ctx: Context): List<Story> {
        if (!available(ctx)) return emptyList()
        return runCatching {
            ctx.contentResolver.query(NEWS, null, null, null, null)?.use { c ->
                fun col(n: String) = c.getColumnIndexOrThrow(n)
                val section = col("section"); val title = col("title"); val source = col("source"); val time = col("time")
                val topic = col("topic"); val outlets = col("outlets"); val link = col("link"); val open = col("open")
                val image = c.getColumnIndex("image")
                buildList {
                    while (c.moveToNext()) add(Story(c.getString(section), c.getString(title), c.getString(source),
                        c.getLong(time), c.getString(topic), c.getInt(outlets), c.getString(link), c.getString(open),
                        if (image >= 0) c.getString(image).orEmpty() else ""))
                }
            }
        }.getOrNull().orEmpty()
    }

    /**
     * Glance's results for [query] except applications (the launcher's grid shows those), in Glance's section
     * order. Off the main thread. [kinds]: only those of Glance's sources (its Kind names), so each can be asked
     * on its own and shown as it answers; [Answer.all] when Glance searched everything anyway (an older Glance).
     */
    fun search(ctx: Context, query: String, perSection: Int = 3, kinds: List<String>? = null, apps: Boolean = false): Answer {
        if (!available(ctx) || query.isBlank()) return Answer(emptyList(), true)
        val out = runCatching {
            ctx.contentResolver.call(ROOT, "search", query, Bundle().apply {
                putInt("perSection", perSection); kinds?.let { putStringArray("kinds", it.toTypedArray()) }
            })
        }.getOrNull() ?: return Answer(emptyList(), kinds == null)
        return Answer(results(out, apps), kinds == null || out.getStringArray("kinds") == null)
    }

    /** One search's answer; [all]: every source, not only the kinds asked for. */
    class Answer(val results: List<Result>, val all: Boolean)

    /** The sources the launcher asks for one by one, in the order their sections show (applications are its own). */
    val KINDS = listOf("CONTACT", "MESSAGE", "NOTIFICATION", "SHORTCUT", "SETTING", "FILE", "NEWS", "WEB")

    private fun results(out: Bundle, apps: Boolean): List<Result> {
        @Suppress("DEPRECATION")
        val sections = out.getParcelableArrayList<Bundle>("sections").orEmpty()
        return sections.filter { apps || it.getString("kind") != "APP" }.flatMap { s ->
            @Suppress("DEPRECATION")
            s.getParcelableArrayList<Bundle>("results").orEmpty().map { r ->
                Result(r.getString("kind").orEmpty(), s.getString("title").orEmpty(), r.getString("title").orEmpty(),
                    r.getString("subtitle").orEmpty(), r.getString("pkg").orEmpty(), r.getLong("time"),
                    r.getString("open").orEmpty())
            }
        }
    }

    fun refresh(ctx: Context, force: Boolean) {
        if (available(ctx)) runCatching { ctx.contentResolver.call(ROOT, "refresh", if (force) "force" else null, null) }
    }

    fun fewerLike(ctx: Context, link: String) {
        if (available(ctx)) runCatching { ctx.contentResolver.call(ROOT, "fewer", link, null) }
    }

    /** Opens a story or a result: Glance's own activity does it (shortcuts, notifications, files). */
    fun open(ctx: Context, uri: String) = runCatching {
        ctx.startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Glance itself, its keyboard up when [search]. */
    fun launch(ctx: Context, search: Boolean = false) = runCatching {
        ctx.startActivity(Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity")
            .putExtra("focus_search", search).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Calls [onChange] on the main thread whenever Glance's news changes; returns the observer to unregister. */
    fun observe(ctx: Context, onChange: () -> Unit): ContentObserver? {
        if (!available(ctx)) return null
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        return runCatching { ctx.contentResolver.registerContentObserver(NEWS, false, o); o }.getOrNull()
    }
}
