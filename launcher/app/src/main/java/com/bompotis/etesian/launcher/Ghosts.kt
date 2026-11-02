package com.bompotis.etesian.launcher

import android.app.PendingIntent
import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android extras → Keep dismissed notifications ("ghost rows", story Phone v2 → Try it: ghost rows).
 * Notifications the user dismissed are kept here (newest first) and shown faded at the end of
 * Notifications; they expire after the Keep-for setting. Settings live in Settings.Secure so the
 * device tests can set them: etesian_ghost_size (small|big), etesian_ghost_rows (0.5..3),
 * etesian_ghost_keep_hours (0 = forever).
 */
object Ghosts {
    const val KEY_SIZE = "etesian_ghost_size"
    const val KEY_ROWS = "etesian_ghost_rows"
    const val KEY_KEEP = "etesian_ghost_keep_hours"
    private const val PREFS = "ghosts"
    private const val MAX = 100

    class Ghost(val key: String, val pkg: String, val title: String, val text: String, val time: Long, val dismissedAt: Long) {
        /** Only while the launcher keeps running: the notification's own tap action. */
        val intent: PendingIntent? get() = intents[id]
        val id get() = "$key@$dismissedAt"
    }

    private val intents = mutableMapOf<String, PendingIntent>()
    private var cache: MutableList<Ghost>? = null
    /** Clear (and our own dismissals that should not linger) suppress keeping for a moment. */
    private var suppressUntil = 0L

    fun on(ctx: Context) = Extras.on(ctx, Extras.GHOST_ROWS)
    fun big(ctx: Context) = Settings.Secure.getString(ctx.contentResolver, KEY_SIZE) == "big"
    fun rows(ctx: Context): Float {
        val r = Settings.Secure.getString(ctx.contentResolver, KEY_ROWS)?.toFloatOrNull() ?: 2.5f
        // 0.5 only exists for big rows.
        return if (r < 1f && !big(ctx)) 1f else r.coerceIn(0.5f, 3f)
    }
    /** Hours; 0 = forever. Fractions are allowed (device tests). */
    fun keepHours(ctx: Context) = Settings.Secure.getString(ctx.contentResolver, KEY_KEEP)?.toFloatOrNull() ?: 24f

    private fun load(ctx: Context): MutableList<Ghost> = cache ?: run {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]")
        val a = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        MutableList(a.length()) { i -> a.getJSONObject(i).let {
            Ghost(it.getString("k"), it.getString("p"), it.optString("t"), it.optString("x"), it.getLong("w"), it.getLong("d"))
        } }.also { cache = it }
    }

    private fun save(ctx: Context, l: List<Ghost>) {
        cache = l.toMutableList()
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("k", it.key).put("p", it.pkg).put("t", it.title).put("x", it.text).put("w", it.time).put("d", it.dismissedAt)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", a.toString()).apply()
    }

    /** The kept ones, newest dismissed first, without the expired ones. */
    fun list(ctx: Context): List<Ghost> {
        val all = load(ctx)
        val h = keepHours(ctx)
        if (h <= 0f) return all
        val cut = System.currentTimeMillis() - (h * 3_600_000f).toLong()
        val live = all.filter { it.dismissedAt >= cut }
        if (live.size != all.size) save(ctx, live)
        return live
    }

    fun add(ctx: Context, e: EventItem) {
        if (!on(ctx) || System.currentTimeMillis() < suppressUntil) return
        // The same key re-posted and dismissed again is a new entry; only an exact repeat is dropped.
        val l = load(ctx).filter { !(it.key == e.key && it.time == e.time) }.toMutableList()
        val g = Ghost(e.key, e.pkg, e.title, e.text, e.time, System.currentTimeMillis())
        e.intent?.let { intents[g.id] = it }
        l.add(0, g)
        save(ctx, l.take(MAX))
    }

    /** Swipe on the ghost rows, or Clear: all expire at once (as if their time ran out). */
    fun expireAll(ctx: Context) { save(ctx, emptyList()); intents.clear() }

    /** Clear cancels the notifications too: those must not come back as ghosts. */
    fun suppress(ms: Long = 3000) { suppressUntil = System.currentTimeMillis() + ms }

    /** Opens it like the original: its own tap action while we have it, else the app. */
    fun open(ctx: Context, g: Ghost) {
        val pi = g.intent
        if (pi != null) {
            val opts = android.app.ActivityOptions.makeBasic()
            if (android.os.Build.VERSION.SDK_INT >= 36) opts.setPendingIntentBackgroundActivityStartMode(
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            if (runCatching { pi.send(ctx, 0, null, null, null, null, opts.toBundle()) }.isSuccess) return
        }
        ctx.packageManager.getLaunchIntentForPackage(g.pkg)?.let {
            runCatching { ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
