package com.bompotis.etesian.launcher

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings

/**
 * The launcher's own screens on the system N9 toolbar: the same messages the etesian-toolbar
 * library (com.bompotis.etesian.toolbar.EtesianToolbar) sends, kept here so the launcher still builds
 * outside the platform tree.
 */
class SystemToolbar(private val activity: Activity) {
    class Item(val id: String, val icon: Int, val label: String, val tab: Boolean = false, val selected: Boolean = false,
               val tint: Boolean = true, val bitmap: android.graphics.Bitmap? = null,
               /** a lone button's side: left when true, else right */ val alignStart: Boolean = false)

    private val action = "com.bompotis.etesian.toolbar.CLICK.${activity.packageName}.${System.identityHashCode(activity)}"
    private var listener: ((String) -> Unit)? = null
    private var registered = false
    private val clicks = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra("id")?.let { listener?.invoke(it) }
        }
    }

    /** [style] "dark" / "light" (null: SystemUI decides); [hidden]: no bar at all on this page. */
    fun set(back: Boolean, items: List<Item>, style: String? = null, hidden: Boolean = false, onItem: (String) -> Unit) {
        listener = onItem
        if (!registered) {
            activity.registerReceiver(clicks, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        val bundles = ArrayList(items.map {
            Bundle().apply {
                putString("id", it.id); putString("label", it.label)
                putParcelable("icon", if (it.bitmap != null) Icon.createWithBitmap(it.bitmap)
                    else Icon.createWithResource(activity.packageName, it.icon))
                putBoolean("tab", it.tab); putBoolean("selected", it.selected); putBoolean("enabled", true); putBoolean("tint", it.tint)
                putString("align", if (it.alignStart) "start" else "end")
            }
        })
        val callback = PendingIntent.getBroadcast(activity, System.identityHashCode(activity),
            Intent(action).setPackage(activity.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        activity.sendBroadcast(Intent("com.bompotis.etesian.toolbar.SET").setPackage("com.android.systemui")
            .putExtra("task", activity.taskId)
            .putExtra("component", activity.componentName.flattenToString())
            .putExtra("back", back)
            .putExtra("style", style)
            .putExtra("hidden", hidden)
            .putParcelableArrayListExtra("items", bundles)
            .putExtra("callback", callback))
    }

    fun clear() {
        if (registered) { runCatching { activity.unregisterReceiver(clicks) }; registered = false }
        activity.sendBroadcast(Intent("com.bompotis.etesian.toolbar.CLEAR").setPackage("com.android.systemui")
            .putExtra("task", activity.taskId)
            .putExtra("component", activity.componentName.flattenToString()))
    }

    companion object {
        /** Whether the system toolbar is showing (N9 mode, toolbar on). */
        fun available(ctx: Context) =
            Settings.Secure.getInt(ctx.contentResolver, "etesian_gestures", 0) == 1 &&
                Settings.Secure.getInt(ctx.contentResolver, "etesian_bottom_bar", 1) == 1
    }
}
