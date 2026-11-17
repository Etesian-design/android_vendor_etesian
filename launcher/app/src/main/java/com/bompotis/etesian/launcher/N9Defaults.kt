package com.bompotis.etesian.launcher

import android.content.ContentUris
import android.content.Context
import android.media.RingtoneManager
import android.provider.MediaStore
import android.provider.Settings
import org.json.JSONObject
import java.io.File

/**
 * Switches the phone to Etesian's defaults once: with the user's own N9 files built in (vendor/etesian/n9-files),
 * Nokia tune, Message 1 and Clock 1, the N9's charger tones and the Nokia Pure font; without them, Etesian's own
 * tones and sounds (vendor/etesian/sounds), never Android's.
 */
object N9Defaults {
    private const val PREFS = "n9_defaults"
    private const val FONT_OVERLAY = "com.bompotis.etesian.overlay.font.nokiapure"
    private const val FONT_CATEGORY = "android.theme.customization.font"

    fun applyOnce(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("tones", false) && applyTones(ctx)) prefs.edit().putBoolean("tones", true).apply()
        if (!prefs.getBoolean("font", false) && applyFont(ctx)) prefs.edit().putBoolean("font", true).apply()
        if (!prefs.getBoolean("sounds", false) && applySounds(ctx)) prefs.edit().putBoolean("sounds", true).apply()
        if (!prefs.getBoolean("look", false) && applyLook(ctx)) prefs.edit().putBoolean("look", true).apply()
    }

    private fun applyTones(ctx: Context): Boolean {
        val n9 = File("/product/media/audio/ringtones/Nokia_tune.mp3").exists()
        val picks = if (n9) mapOf(
            RingtoneManager.TYPE_RINGTONE to "Nokia_tune.mp3",
            RingtoneManager.TYPE_NOTIFICATION to "Message_1.mp3",
            RingtoneManager.TYPE_ALARM to "Clock_1.mp3",
        ) else mapOf(
            RingtoneManager.TYPE_RINGTONE to "Etesian.ogg",
            RingtoneManager.TYPE_NOTIFICATION to "Etesian_message.ogg",
            RingtoneManager.TYPE_ALARM to "Etesian_dawn.ogg",
        )
        var all = true
        picks.forEach { (type, file) ->
            val uri = find(ctx, file)
            if (uri == null) all = false   // not scanned yet; try again next time
            else runCatching { RingtoneManager.setActualDefaultRingtoneUri(ctx, type, uri) }.onFailure { all = false }
        }
        return all
    }

    /** The N9's charger and low-battery tones (touch, keyboard and camera sounds need no setting). */
    private fun applySounds(ctx: Context): Boolean {
        val dir = listOf("/product/media/audio/ui/n9/", "/product/media/audio/ui/etesian/")
            .firstOrNull { File(it + "accessory_connected.wav").exists() } ?: return false
        val cr = ctx.contentResolver
        return runCatching {
            Settings.Global.putString(cr, "charging_started_sound", dir + "accessory_connected.wav")
            Settings.Global.putString(cr, "wireless_charging_started_sound", dir + "accessory_connected.wav")
            Settings.Global.putString(cr, "low_battery_sound", dir + "battery_low.wav")
        }.isSuccess
    }

    private fun find(ctx: Context, file: String) = runCatching {
        ctx.contentResolver.query(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?", arrayOf(file), null)?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, c.getLong(0)) else null
        }
    }.getOrNull()

    /** The N9's blue as the system colour (the N9-styled apps are light on their own). */
    private fun applyLook(ctx: Context): Boolean {
        val cr = ctx.contentResolver
        val json = runCatching { JSONObject(Settings.Secure.getString(cr, "theme_customization_overlay_packages") ?: "{}") }
            .getOrDefault(JSONObject())
        json.put("android.theme.customization.system_palette", "FF00A5E5")
        json.put("android.theme.customization.accent_color", "FF00A5E5")
        json.put("android.theme.customization.color_source", "preset")
        json.put("android.theme.customization.theme_style", "CONTENT")
        return runCatching { Settings.Secure.putString(cr, "theme_customization_overlay_packages", json.toString()) }.isSuccess
    }

    private fun applyFont(ctx: Context): Boolean {
        val installed = runCatching { ctx.packageManager.getPackageInfo(FONT_OVERLAY, 0) }.isSuccess
        if (!installed) return false
        val cr = ctx.contentResolver
        val json = runCatching { JSONObject(Settings.Secure.getString(cr, "theme_customization_overlay_packages") ?: "{}") }
            .getOrDefault(JSONObject())
        if (json.has(FONT_CATEGORY)) return true   // the user already picked a font
        json.put(FONT_CATEGORY, FONT_OVERLAY)
        return runCatching { Settings.Secure.putString(cr, "theme_customization_overlay_packages", json.toString()) }.isSuccess
    }
}
