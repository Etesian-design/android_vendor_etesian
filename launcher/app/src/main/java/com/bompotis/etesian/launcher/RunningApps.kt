package com.bompotis.etesian.launcher

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

/**
 * The "Open applications" model. Built into the ROM, entries are the system's real tasks
 * with their snapshots ([SystemTasks]). As a plain app, Android doesn't expose other apps'
 * tasks, so entries come from apps launched here plus foreground changes seen by
 * [GestureService], which also supplies the thumbnails.
 */
object RunningApps {
    /** [second]: the bottom (or right) half of a split-screen pair, shown as one card with this one on top. */
    class Entry(val pkg: String, val label: String, var thumb: Bitmap?, val taskId: Int = -1) {
        var second: Entry? = null
        val title get() = second?.let { "$label + ${it.label}" } ?: label
    }

    private const val PREFS = "running"
    private const val MAX = 18
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var loaded = false

    val entries = mutableListOf<Entry>()
    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyChanged() = listeners.toList().forEach { it() }

    private fun thumbFile(ctx: Context, pkg: String) = File(File(ctx.cacheDir, "thumbs").apply { mkdirs() }, "$pkg.png")

    private const val THUMB_WIDTH = 720  // a two-column card is about 520 px wide; never scaled up

    /** Rebuilds the list from the system's recent tasks; a no-op outside the ROM. */
    fun sync(ctx: Context) {
        if (!SystemTasks.available(ctx)) return
        loaded = true
        val pm = ctx.packageManager
        val old = entries.associateBy { it.taskId }
        val fresh = SystemTasks.recent(ctx, MAX).map { t ->
            old[t.id] ?: Entry(t.pkg,
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(t.pkg, 0)).toString() }.getOrDefault(t.pkg),
                null, t.id)
        }
        fresh.forEach { it.second = null }
        // A split pair is one card: the top task's, holding the bottom one.
        val cards = fresh.toMutableList()
        Platform.splitPair?.invoke()?.let { (top, bottom) ->
            val a = cards.firstOrNull { it.taskId == top }; val b = cards.firstOrNull { it.taskId == bottom }
            if (a != null && b != null) { a.second = b; cards.remove(b) }
        }
        entries.clear()
        entries += arrange(ctx, cards, fresh.firstOrNull())
        notifyChanged()
        // Snapshots change whenever an app is left, so refresh them all off the main thread.
        io.execute {
            fresh.forEach { e ->
                val bmp = SystemTasks.snapshot(e.taskId, THUMB_WIDTH) ?: return@forEach
                main.post { e.thumb = bmp; notifyChanged() }
            }
        }
    }

    // ----- order: your own (dragged in edit mode), with auto-arrange moving the last opened to the top -----

    private fun orderPrefs(ctx: Context) = ctx.getSharedPreferences("switcher", Context.MODE_PRIVATE)
    fun autoArrange(ctx: Context) = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(AUTO_ARRANGE, true)
    const val AUTO_ARRANGE = "switcher_auto_arrange"
    private var lastFront = -1

    private fun arrange(ctx: Context, cards: List<Entry>, newest: Entry?): List<Entry> {
        val stored = orderPrefs(ctx).getString("order", "")!!.split(',').mapNotNull { it.toIntOrNull() }
        val pos = stored.withIndex().associate { it.value to it.index }
        fun key(e: Entry) = e.taskId
        val known = cards.filter { key(it) in pos }.sortedBy { pos[key(it)] }
        // New cards come in at the top, most recent first.
        val out = (cards.filter { key(it) !in pos } + known).toMutableList()
        if (autoArrange(ctx) && newest != null) {
            val card = out.firstOrNull { it === newest || it.second === newest }
            // Only when a different app was opened since: the order you dragged otherwise stays.
            if (card != null && newest.taskId != lastFront) { out.remove(card); out.add(0, card) }
        }
        lastFront = newest?.taskId ?: -1
        saveOrder(ctx, out)
        return out
    }

    private fun saveOrder(ctx: Context, list: List<Entry>) =
        orderPrefs(ctx).edit().putString("order", list.joinToString(",") { it.taskId.toString() }).apply()

    /** Edit mode: a card dragged from [from] to [to]. */
    fun move(ctx: Context, from: Int, to: Int) {
        if (from !in entries.indices || to !in entries.indices) return
        entries.add(to, entries.removeAt(from))
        if (SystemTasks.available(ctx)) saveOrder(ctx, entries) else save(ctx)
    }

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        if (SystemTasks.available(ctx)) { sync(ctx); return }
        loaded = true
        val pm = ctx.packageManager
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "")!!
            .split('\n').filter { it.isNotEmpty() }.forEach { pkg ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
                    ?: return@forEach
                val f = thumbFile(ctx, pkg)
                entries += Entry(pkg, label, if (f.exists()) BitmapFactory.decodeFile(f.path) else null)
            }
    }

    private fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("list", entries.joinToString("\n") { it.pkg }).apply()
    }

    /** Moves [pkg] to the front (most recent first, like the N9 switcher). */
    fun touch(ctx: Context, pkg: String) {
        if (SystemTasks.available(ctx)) return
        ensureLoaded(ctx)
        val existing = entries.indexOfFirst { it.pkg == pkg }
        if (existing == 0) return
        val entry = if (existing > 0) entries.removeAt(existing) else {
            val pm = ctx.packageManager
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            Entry(pkg, label, null)
        }
        entries.add(0, entry)
        while (entries.size > MAX) entries.removeAt(entries.size - 1).also { thumbFile(ctx, it.pkg).delete() }
        save(ctx)
        notifyChanged()
    }

    fun setThumb(ctx: Context, pkg: String, bmp: Bitmap) {
        if (SystemTasks.available(ctx)) return
        ensureLoaded(ctx)
        val e = entries.firstOrNull { it.pkg == pkg } ?: return
        e.thumb = bmp
        notifyChanged()
        val f = thumbFile(ctx, pkg)
        io.execute { runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
    }

    /** Closes an application: drops it from the switcher and kills its process once backgrounded. */
    fun close(ctx: Context, pkg: String) {
        ensureLoaded(ctx)
        if (SystemTasks.available(ctx)) {
            entries.filter { it.pkg == pkg }.forEach { SystemTasks.remove(it.taskId) }
            entries.removeAll { it.pkg == pkg }
            notifyChanged()
            return
        }
        entries.removeAll { it.pkg == pkg }
        thumbFile(ctx, pkg).delete()
        save(ctx)
        notifyChanged()
        val am = ctx.getSystemService(ActivityManager::class.java)
        main.postDelayed({ am.killBackgroundProcesses(pkg) }, 600)
    }

    /** Closes a card: both tasks of a split pair. */
    fun closeEntry(ctx: Context, e: Entry) {
        val b = e.second
        if (b == null || !SystemTasks.available(ctx)) { close(ctx, e.pkg); return }
        SystemTasks.remove(e.taskId); SystemTasks.remove(b.taskId)
        entries.remove(e)
        notifyChanged()
    }

    fun prune(ctx: Context) {
        if (SystemTasks.available(ctx)) { sync(ctx); return }
        ensureLoaded(ctx)
        val pm = ctx.packageManager
        if (entries.removeAll { pm.getLaunchIntentForPackage(it.pkg) == null }) { save(ctx); notifyChanged() }
    }
}
