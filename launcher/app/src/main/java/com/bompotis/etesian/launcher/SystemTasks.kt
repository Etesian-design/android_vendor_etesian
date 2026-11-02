package com.bompotis.etesian.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle

/**
 * Real task access, available when Etesian ships in the ROM signed with the platform key.
 * Platform-signed apps may call the hidden task APIs Launcher3 uses, so these go through
 * reflection to keep the app buildable against the public SDK.
 */
object SystemTasks {
    class Task(val id: Int, val pkg: String)

    fun available(ctx: Context) =
        ctx.checkSelfPermission("android.permission.MANAGE_ACTIVITY_TASKS") == PackageManager.PERMISSION_GRANTED

    /** Tasks of running apps, most recent first, without home screens. */
    fun recent(ctx: Context, max: Int): List<Task> {
        val am = ctx.getSystemService(ActivityManager::class.java)
        // Android keeps tasks of killed apps as history; like MeeGo, only list apps with a live process.
        val alive = am.runningAppProcesses.orEmpty().flatMap { it.pkgList.orEmpty().toList() }.toSet()
        @Suppress("DEPRECATION")
        return am.getRecentTasks(max * 2, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            .mapNotNull { t ->
                val pkg = t.baseActivity?.packageName ?: t.baseIntent.component?.packageName ?: return@mapNotNull null
                val home = t.baseIntent.hasCategory(Intent.CATEGORY_HOME) || pkg == ctx.packageName
                // Like the N9, only applications that are still open; emptied tasks linger in recents.
                if (home || !t.isRunning || pkg !in alive) null else Task(t.taskId, pkg)
            }
            .take(max)
    }

    /** The system's own snapshot of the task, scaled to [width] pixels wide. */
    fun snapshot(taskId: Int, width: Int): Bitmap? = runCatching {
        val cls = Class.forName("android.window.TaskSnapshotManager")
        val mgr = cls.getMethod("getInstance").invoke(null)
        // TaskSnapshotManager.RESOLUTION_HIGH, else ANY: ANY hands back the half-size copy when there is one, and its
        // text breaks up once the card scales it.
        val get = cls.getMethod("getTaskSnapshot", Int::class.java, Int::class.java)
        val snap = (runCatching { get.invoke(mgr, taskId, 1) }.getOrNull() ?: get.invoke(mgr, taskId, 3))
            ?: return null.also { android.util.Log.w("Etesian", "no snapshot for $taskId") }
        // Android 17 no longer hands out the buffer itself; wrapToBitmap() replaces it.
        val hw = snap.javaClass.getMethod("wrapToBitmap").invoke(snap) as? Bitmap ?: return null
        val copy = hw.copy(Bitmap.Config.ARGB_8888, false)
        hw.recycle()
        // An app the phone was turned for (landscape, or the keyboard slid out) is snapshotted at that rotation. The
        // launcher stays at the natural, portrait one, so turn the picture back by the difference, as Launcher3
        // does: it fills the portrait card and stands upright the way the phone is held.
        val rotation = runCatching { snap.javaClass.getMethod("getRotation").invoke(snap) as Int }.getOrDefault(0)
        val soft = if (rotation == 0) copy
            else Bitmap.createBitmap(copy, 0, 0, copy.width, copy.height, android.graphics.Matrix().apply { setRotate(90f * rotation) }, true)
                .also { copy.recycle() }
        runCatching { snap.javaClass.getMethod("closeBuffer").invoke(snap) }
        android.util.Log.d("Etesian", "snapshot $taskId ${soft.width}x${soft.height} rotation $rotation")  // the device tests read this
        if (soft.width <= width) soft
        else Bitmap.createScaledBitmap(soft, width, soft.height * width / soft.width, true).also { soft.recycle() }
    }.onFailure { android.util.Log.w("Etesian", "snapshot $taskId", it) }.getOrNull()

    /** Brings an existing task back, the way the system Recents does. */
    fun start(taskId: Int, options: Bundle?): Boolean = runCatching {
        val service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
        service.javaClass.getMethod("startActivityFromRecents", Int::class.java, Bundle::class.java)
            .invoke(service, taskId, options)
        true
    }.getOrDefault(false)

    /** Android extras → App pinning (mock 10): pins the task (screen pinning; Back long-press unpins). */
    fun pin(taskId: Int): Boolean = runCatching {
        val service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
        service.javaClass.getMethod("startSystemLockTaskMode", Int::class.java).invoke(service, taskId)
        true
    }.onFailure { android.util.Log.w("Etesian", "pin $taskId", it) }.getOrDefault(false)

    /** Closes a task for real: removes it and stops the app's activities. */
    fun remove(taskId: Int): Boolean = runCatching {
        val cls = Class.forName("android.app.ActivityTaskManager")
        val atm = cls.getMethod("getInstance").invoke(null)
        cls.getMethod("removeTask", Int::class.java).invoke(atm, taskId) as Boolean
    }.getOrDefault(false)
}
