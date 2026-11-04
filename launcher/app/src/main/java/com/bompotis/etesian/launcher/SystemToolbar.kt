package com.bompotis.etesian.launcher

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Icon
import com.bompotis.etesian.toolbar.EtesianToolbar

/**
 * The launcher's own screens on the N9 toolbar, through the etesian-toolbar library (its sources
 * are compiled into the Gradle build too), which draws the bar in each page's own window.
 */
class SystemToolbar(private val activity: Activity) {
    class Item(val id: String, val icon: Int, val label: String, val tab: Boolean = false, val selected: Boolean = false,
               val tint: Boolean = true, val bitmap: android.graphics.Bitmap? = null,
               /** a lone button's side: left when true, else right */ val alignStart: Boolean = false)

    private val bar get() = EtesianToolbar.of(activity)

    /**
     * [style] "dark" / "light" (null: the page's theme decides); [hidden]: no bar at all on this page.
     * The bar is drawn in this page's own window (the toolbar library), so it moves with the page.
     */
    fun set(back: Boolean, items: List<Item>, style: String? = null, hidden: Boolean = false, onItem: (String) -> Unit) {
        val list = items.map {
            val icon = if (it.bitmap != null) Icon.createWithBitmap(it.bitmap)
                else Icon.createWithResource(activity.packageName, it.icon)
            (if (it.tab) EtesianToolbar.Item.tab(it.id, icon, it.label, it.selected)
                else EtesianToolbar.Item.action(it.id, icon, it.label))
                .setTinted(it.tint).setAlignStart(it.alignStart)
        }
        bar.setStyle(when (style) { "dark" -> EtesianToolbar.STYLE_DARK; "light" -> EtesianToolbar.STYLE_LIGHT; else -> EtesianToolbar.STYLE_DEFAULT })
            .setHidden(hidden).setBack(back).setItems(list) { onItem(it) }
    }

    fun clear() = bar.clear()

    companion object {
        /** Whether the system toolbar is showing (N9 mode, toolbar on). */
        fun available(ctx: Context) = EtesianToolbar.isAvailable(ctx)
    }
}
