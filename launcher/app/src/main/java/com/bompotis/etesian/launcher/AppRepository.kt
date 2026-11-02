package com.bompotis.etesian.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import java.util.concurrent.Executors

class AppInfo(
    val component: ComponentName,
    val label: String,
    val icon: Bitmap,
    val removable: Boolean,
    val user: UserHandle,
)

object AppRepository {
    private const val PREFS = "apps"
    private const val KEY_ORDER = "order"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var apps: List<AppInfo> = emptyList()
        private set
    /** Android extras → Private space: its applications while it is open (else empty). */
    @Volatile var privateApps: List<AppInfo> = emptyList()
        private set

    fun loadAsync(ctx: Context, done: (List<AppInfo>) -> Unit) {
        val app = ctx.applicationContext
        executor.execute {
            val result = load(app)
            main.post { apps = result; done(result) }
        }
    }

    private fun load(ctx: Context): List<AppInfo> {
        IconFactory.style = Prefs.iconShape(ctx)
        IconFactory.packName = Prefs.iconPack(ctx)
        IconFactory.pack = IconPacks.load(ctx, IconFactory.packName)
        val la = ctx.getSystemService(LauncherApps::class.java)
        val user = Process.myUserHandle()
        // Rendered at the largest icon size so every size setting stays sharp.
        val size = ctx.dp(Prefs.ICON_SIZE_MAX.toFloat())
        val density = ctx.resources.displayMetrics.densityDpi
        val all = la.getActivityList(null, user)
            // Hide the launcher itself, but keep its "Etesian settings" entry.
            .filter { it.componentName.className != HomeActivity::class.java.name }
            .map {
                AppInfo(
                    it.componentName,
                    it.label.toString(),
                    IconFactory.render(it.getIcon(density), size, it.componentName),
                    (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0,
                    user,
                )
            }
        privateApps = PrivateSpace.profile(ctx)?.takeIf { PrivateSpace.on(ctx) && !PrivateSpace.locked(ctx, it) }?.let { p ->
            runCatching { la.getActivityList(null, p) }.getOrDefault(emptyList()).map {
                AppInfo(it.componentName, it.label.toString(), IconFactory.render(it.getIcon(density), size, it.componentName), false, p)
            }.sortedBy { it.label.lowercase() }
        }.orEmpty()
        val order = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, "")!!.split('\n').filter { it.isNotEmpty() }
        val index = order.withIndex().associate { it.value to it.index }
        // Known apps keep their place; new installs are appended at the end, as on the N9.
        val known = all.filter { it.component.flattenToString() in index }
            .sortedBy { index[it.component.flattenToString()] }
        val fresh = all.filter { it.component.flattenToString() !in index }
            .sortedBy { it.label.lowercase() }
        return known + fresh
    }

    /** The grid's stored order: application components and "folder:<id>" entries. */
    fun order(ctx: Context): List<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, "")!!.split('\n').filter { it.isNotEmpty() }

    fun saveLayout(ctx: Context, keys: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, keys.joinToString("\n"))
            .apply()
    }

    fun iconFor(ctx: Context, pkg: String): Bitmap? =
        apps.firstOrNull { it.component.packageName == pkg }?.icon
            ?: runCatching { IconFactory.render(ctx.packageManager.getApplicationIcon(pkg), ctx.dp(58f)) }.getOrNull()
}
