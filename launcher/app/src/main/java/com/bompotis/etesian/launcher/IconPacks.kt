package com.bompotis.etesian.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Third-party icon packs in the common ADW / Nova / Apex format (appfilter.xml). */
object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME",
    )
    private const val APEX_CATEGORY = "com.anddoes.launcher.THEME"

    class Pack(
        val pkg: String,
        val res: Resources,
        /** Flattened component ("pkg/cls") to drawable name. */
        val items: Map<String, String>,
        val backs: List<String>,
        val mask: String?,
        val upon: String?,
        val scale: Float,
        val updated: Long,
    ) {
        fun drawable(name: String): Drawable? {
            val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 }
                ?: res.getIdentifier(name, "mipmap", pkg).takeIf { it != 0 } ?: return null
            return runCatching { res.getDrawable(id, null) }.getOrNull()
        }

        fun iconFor(cn: ComponentName): Drawable? = items[cn.flattenToString()]?.let { drawable(it) }

        /** Composes a pack-styled icon for an app the pack doesn't cover, or null when it can't. */
        fun compose(original: Drawable, px: Int, seed: Int): Bitmap? {
            if (backs.isEmpty() && mask == null && upon == null) return null
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val back = backs.getOrNull(Math.floorMod(seed, backs.size.coerceAtLeast(1)))
                ?.let { drawable(it) }
            back?.apply { setBounds(0, 0, px, px); draw(c) }
            // The scaled app icon, cut by the mask, sits on the back plate.
            val layer = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            val inset = ((px - px * scale) / 2).toInt()
            original.setBounds(inset, inset, px - inset, px - inset)
            original.draw(lc)
            mask?.let { drawable(it) }?.let { m ->
                val mb = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                m.setBounds(0, 0, px, px); m.draw(Canvas(mb))
                lc.drawBitmap(mb, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
                mb.recycle()
            }
            c.drawBitmap(layer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            layer.recycle()
            upon?.let { drawable(it) }?.apply { setBounds(0, 0, px, px); draw(c) }
            return bmp
        }
    }

    private var cached: Pack? = null

    /** Installed icon packs as package name to label, sorted by label. */
    fun installed(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val found = ACTIONS.flatMap { pm.queryIntentActivities(Intent(it), 0) } +
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(APEX_CATEGORY), 0)
        return found.map { it.activityInfo.packageName }.distinct()
            .mapNotNull { p -> runCatching { p to pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrNull() }
            .sortedBy { it.second.lowercase() }
    }

    /** The parsed pack, re-read when another pack is chosen or the pack was updated; null if none. */
    @Synchronized
    fun load(ctx: Context, pkg: String): Pack? {
        if (pkg.isEmpty()) { cached = null; return null }
        val pm = ctx.packageManager
        val updated = try { pm.getPackageInfo(pkg, 0).lastUpdateTime } catch (_: PackageManager.NameNotFoundException) {
            cached = null; return null
        }
        cached?.let { if (it.pkg == pkg && it.updated == updated) return it }
        cached = runCatching { parse(pm.getResourcesForApplication(pkg), pkg, updated) }.getOrNull()
        return cached
    }

    /** Forgets the cached pack when its package changes or goes away. */
    @Synchronized
    fun invalidate(pkg: String) {
        if (cached?.pkg == pkg) cached = null
    }

    private fun parse(res: Resources, pkg: String, updated: Long): Pack {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (id != 0) res.getXml(id) else
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), "UTF-8")
            }
        val items = HashMap<String, String>()
        val backs = ArrayList<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f
        fun imgs() = (0 until parser.attributeCount)
            .filter { parser.getAttributeName(it).startsWith("img") }
            .map { parser.getAttributeValue(it) }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "item" -> {
                    val comp = parser.getAttributeValue(null, "component") ?: continue
                    val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                    val cn = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                    ComponentName.unflattenFromString(cn)?.let { items[it.flattenToString()] = drawable }
                }
                "iconback" -> backs += imgs()
                "iconmask" -> mask = imgs().firstOrNull()
                "iconupon" -> upon = imgs().firstOrNull()
                "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
            }
        }
        return Pack(pkg, res, items, backs, mask, upon, scale, updated)
    }
}
