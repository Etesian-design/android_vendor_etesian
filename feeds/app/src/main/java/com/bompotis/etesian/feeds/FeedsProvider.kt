package com.bompotis.etesian.feeds

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle

/**
 * Read-only view of the feeds for the launcher (Events' Feed section, the Belle widget):
 * `feeds` (url, title) and `items` (newest first; `read` 1 once opened or marked read). Calls:
 * `refresh` (arg "force"), `state` (refreshing, last refresh), `import` (the launcher's old list, "urls" and "titles" arrays).
 * Changes are announced on [ITEMS].
 */
class FeedsProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        Feeds.app = context!!.applicationContext
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val ctx = context!!
        return when (uri.lastPathSegment) {
            "feeds" -> MatrixCursor(FEED_COLUMNS).apply {
                Feeds.feeds(ctx).forEach { addRow(arrayOf(it.url, it.title)) }
            }
            "items" -> MatrixCursor(ITEM_COLUMNS).apply {
                Feeds.items(ctx).forEach {
                    addRow(arrayOf<Any>(it.feedUrl, it.feedTitle, it.title, it.summary, it.link, it.time, it.id,
                        if (Feeds.isRead(ctx, it.id)) 1 else 0))
                }
            }
            else -> null
        }?.apply { setNotificationUri(ctx.contentResolver, ITEMS) }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context!!
        // The provider's readPermission doesn't cover call().
        ctx.enforceCallingOrSelfPermission(PERMISSION, "Feeds")
        val out = Bundle()
        when (method) {
            "refresh" -> {
                val force = arg == "force" || extras?.getBoolean("force") == true
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Feeds.refresh(ctx, force)
                    FeedsJob.schedule(ctx)
                }
            }
            "import" -> {
                val urls = extras?.getStringArray("urls").orEmpty()
                val titles = extras?.getStringArray("titles").orEmpty()
                val added = Feeds.import(ctx, urls.mapIndexed { i, u -> Feeds.Feed(u, titles.getOrNull(i) ?: u) })
                out.putInt("added", added)
                if (added > 0) android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Feeds.refresh(ctx, force = true)
                    FeedsJob.schedule(ctx)
                }
            }
            "state" -> {}
            else -> return null
        }
        out.putBoolean("refreshing", Feeds.refreshing)
        out.putLong("lastRefresh", Feeds.lastRefresh(ctx))
        return out
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        const val AUTHORITY = "com.bompotis.etesian.feeds"
        const val PERMISSION = "com.bompotis.etesian.feeds.permission.READ"
        val ITEMS: Uri = Uri.parse("content://$AUTHORITY/items")
        private val FEED_COLUMNS = arrayOf("url", "title")
        private val ITEM_COLUMNS = arrayOf("feedUrl", "feedTitle", "title", "summary", "link", "time", "id", "read")
    }
}
