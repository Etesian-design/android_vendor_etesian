package com.bompotis.etesian.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * The user's own buttons on the N9 toolbar: a default set for every app, and sets for
 * particular apps (used when the app in front doesn't provide its own through the toolbar API).
 *
 * Stored in Settings.Secure as JSON, for SystemUI to draw:
 * `{"default":[button…], "apps":{"<package>":[button…]}}`, where a button is
 * `{"label", "icon": base64 PNG, "tint": monochrome?, "action": {…}}`. SystemUI hands taps back
 * to [ToolbarActions] here, which knows how to run each action.
 */
object ToolbarConfig {
    const val SETTING = "etesian_toolbar_config"
    const val DEFAULT = ""
    private const val ICON_PX = 96

    class Button(val label: String, val icon: String, val tint: Boolean, val action: JSONObject) {
        fun toJson(): JSONObject = JSONObject().put("label", label).put("icon", icon)
            .put("tint", tint).put("action", action)

        companion object {
            fun from(o: JSONObject) = Button(o.optString("label"), o.optString("icon"),
                o.optBoolean("tint", true), o.optJSONObject("action") ?: JSONObject())
        }
    }

    private fun read(ctx: Context): JSONObject = runCatching {
        JSONObject(Settings.Secure.getString(ctx.contentResolver, SETTING) ?: "{}")
    }.getOrElse { JSONObject() }

    /** The packages with a set of their own. */
    fun apps(ctx: Context): List<String> =
        read(ctx).optJSONObject("apps")?.keys()?.asSequence()?.toList().orEmpty().sorted()

    /** The buttons for [pkg], or the default set for [DEFAULT]. */
    fun buttons(ctx: Context, pkg: String): List<Button> {
        val root = read(ctx)
        val arr = if (pkg == DEFAULT) root.optJSONArray("default")
            else root.optJSONObject("apps")?.optJSONArray(pkg)
        return arr?.let { a -> (0 until a.length()).map { Button.from(a.getJSONObject(it)) } }.orEmpty()
    }

    fun hasSet(ctx: Context, pkg: String) =
        pkg == DEFAULT || read(ctx).optJSONObject("apps")?.has(pkg) == true

    fun save(ctx: Context, pkg: String, buttons: List<Button>) {
        val root = read(ctx)
        val arr = JSONArray().apply { buttons.forEach { put(it.toJson()) } }
        if (pkg == DEFAULT) root.put("default", arr)
        else root.put("apps", (root.optJSONObject("apps") ?: JSONObject()).put(pkg, arr))
        Settings.Secure.putString(ctx.contentResolver, SETTING, root.toString())
    }

    fun deleteSet(ctx: Context, pkg: String) {
        val root = read(ctx)
        root.optJSONObject("apps")?.remove(pkg)
        Settings.Secure.putString(ctx.contentResolver, SETTING, root.toString())
    }

    /**
     * The old setting (a comma list of "pkg/cls" and "settings:ACTION") becomes the default set,
     * once, the first time the new setup is opened.
     */
    fun migrate(ctx: Context) {
        if (Settings.Secure.getString(ctx.contentResolver, SETTING) != null) return
        val old = Prefs.bottomBarButtons(ctx)
        val buttons = old.mapNotNull { entry ->
            if (entry.startsWith("settings:")) {
                val action = entry.removePrefix("settings:")
                Button(SETTINGS_PAGES.firstOrNull { it.second == action }?.first ?: "Settings",
                    glyph(ctx, "tune"), true, JSONObject().put("t", "settings").put("a", action))
            } else {
                val cn = android.content.ComponentName.unflattenFromString(entry) ?: return@mapNotNull null
                val label = runCatching {
                    ctx.packageManager.getActivityInfo(cn, 0).loadLabel(ctx.packageManager).toString()
                }.getOrNull() ?: return@mapNotNull null
                val (icon, tint) = appIcon(ctx, cn.packageName, cn)
                Button(label, icon, tint, JSONObject().put("t", "app").put("c", entry))
            }
        }
        save(ctx, DEFAULT, buttons)
    }

    // --- Icons ---------------------------------------------------------------------------

    /** The N9-style symbols offered for buttons, by name. */
    val GLYPHS = listOf(
        "torch" to R.drawable.tb_torch, "wifi" to R.drawable.tb_wifi,
        "bluetooth" to R.drawable.tb_bluetooth, "rotate" to R.drawable.tb_rotate,
        "playpause" to R.drawable.tb_playpause, "next" to R.drawable.tb_next,
        "previous" to R.drawable.tb_previous, "volume" to R.drawable.tb_volume,
        "home" to R.drawable.tb_home, "camera" to R.drawable.tb_camera,
        "phone" to R.drawable.tb_phone, "message" to R.drawable.tb_message,
        "mail" to R.drawable.tb_mail, "search" to R.drawable.tb_search,
        "star" to R.drawable.tb_star, "macro" to R.drawable.tb_macro,
        "tune" to R.drawable.tb_tune, "music" to R.drawable.tb_music,
        "navigate" to R.drawable.tb_navigate, "note" to R.drawable.tb_note,
        "add" to R.drawable.tb_add, "refresh" to R.drawable.tb_refresh,
        "browser" to R.drawable.tb_browser, "share" to R.drawable.tb_share,
    )

    /** Settings pages offered as buttons: label to intent action. */
    val SETTINGS_PAGES = listOf(
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Mobile network" to Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
        "Display" to Settings.ACTION_DISPLAY_SETTINGS,
        "Sound" to Settings.ACTION_SOUND_SETTINGS,
        "Battery" to Intent_ACTION_BATTERY,
        "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "Security" to Settings.ACTION_SECURITY_SETTINGS,
        "Date & time" to Settings.ACTION_DATE_SETTINGS,
        "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "Notifications" to "android.settings.NOTIFICATION_SETTINGS",
        "Developer options" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "All settings" to Settings.ACTION_SETTINGS,
    )
    private const val Intent_ACTION_BATTERY = "android.intent.action.POWER_USAGE_SUMMARY"

    private fun encode(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decode(icon: String): Bitmap? = runCatching {
        val bytes = Base64.decode(icon, Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun render(d: Drawable, scale: Float = 1f): Bitmap {
        val b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val inset = ((1f - scale) * ICON_PX / 2f).toInt()
        d.setBounds(inset, inset, ICON_PX - inset, ICON_PX - inset)
        d.draw(c)
        return b
    }

    /** A symbol, as a monochrome mask. */
    fun glyph(ctx: Context, name: String): String {
        val res = GLYPHS.firstOrNull { it.first == name }?.second ?: R.drawable.tb_star
        return encode(render(ctx.getDrawable(res)!!, 0.84f))
    }

    /**
     * An app's icon: its monochrome (themed) layer when it has one, drawn as a mask the bar
     * tints, else the full-colour icon. Returns (icon, tint).
     */
    fun appIcon(ctx: Context, pkg: String, component: android.content.ComponentName? = null): Pair<String, Boolean> {
        val pm = ctx.packageManager
        val d = runCatching {
            if (component != null) pm.getActivityIcon(component) else pm.getApplicationIcon(pkg)
        }.getOrNull() ?: return glyph(ctx, "star") to true
        val mono = (d as? AdaptiveIconDrawable)?.monochrome
        // Adaptive layers are drawn 1.5x the icon (the outer third is bleed).
        return if (mono != null) encode(render(mono, 1.5f)) to true else encode(render(d, 0.92f)) to false
    }

    /** A drawable (e.g. an app shortcut's icon), in colour. */
    fun colourIcon(d: Drawable): String = encode(render(d, 0.92f))

    /** The user's own image, centre-cropped and scaled. */
    fun imageIcon(ctx: Context, uri: Uri): String? = runCatching {
        val src = ctx.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
        val side = minOf(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        encode(Bitmap.createScaledBitmap(crop, ICON_PX, ICON_PX, true))
    }.getOrNull()
}
