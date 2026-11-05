package com.bompotis.etesian.launcher

import android.content.Context
import android.provider.Settings

/**
 * Notifications over apps (Settings → Events and notifications → Over apps), read by SystemUI's
 * EtesianOverStyles under the same Settings.Secure keys. Discreet (the N9's one line over the status
 * bar) is the default; the lock screen keeps its cards whatever is chosen.
 */
object NotifOver {
    const val STYLE = "etesian_notif_over"
    const val SHADE = "etesian_notif_belle_shade"
    const val COLOUR = "etesian_notif_colour"

    val STYLES = arrayOf("n9", "belle", "card", "colour", "android")
    val STYLE_NAMES = arrayOf("Discreet (N9)", "Strip (Belle)", "Card", "Colour", "Android")
    val SHADES = arrayOf("light", "medium", "dark", "solid")
    val SHADE_NAMES = arrayOf("Light", "Medium", "Dark", "Solid")
    val COLOURS = arrayOf("app", "system", "#2A86D8", "#3FA34D", "#E8742A", "#D63A7A", "#7B4FD0")
    val COLOUR_NAMES = arrayOf("App’s colour", "System accent", "Blue", "Green", "Orange", "Pink", "Purple")

    fun style(ctx: Context) = Settings.Secure.getString(ctx.contentResolver, STYLE)?.takeIf { it in STYLES } ?: "n9"
    fun shade(ctx: Context) = Settings.Secure.getString(ctx.contentResolver, SHADE)?.takeIf { it in SHADES } ?: "medium"
    fun colour(ctx: Context) = Settings.Secure.getString(ctx.contentResolver, COLOUR)?.takeIf { it in COLOURS } ?: "app"

    /** The strip's black, 0–255, as SystemUI draws it. */
    fun shadeAlpha(ctx: Context) = when (shade(ctx)) { "light" -> 140; "dark" -> 219; "solid" -> 255; else -> 184 }
}
