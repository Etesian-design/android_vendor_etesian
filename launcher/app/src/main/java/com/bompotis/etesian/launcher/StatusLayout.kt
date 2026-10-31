package com.bompotis.etesian.launcher

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Android extras → status menu customisation: which blocks the status menu shows and in what
 * order (Profile, Volume, sliders, the toggle strip, rows), the strip's toggles, how many rows
 * stay visible and how the hidden ones are revealed. Only used while the extra is on.
 */
object StatusLayout {
    /** Rows and strip tiles share these ids. */
    val TOGGLES = listOf("wifi", "data", "bt", "flight", "torch", "rotate", "location", "record")
    val SLIDERS = listOf("brightness", "alarm")
    const val STRIP = "strip"
    const val PROFILE = "profile"
    const val VOLUME = "volume"
    const val MODES = "modes"
    /** The small music widget (Music extra); first by default, as in mock 07. */
    const val MUSIC = "music"

    private const val BLOCKS = "status_blocks"
    private const val STRIP_TOGGLES = "status_toggles"
    const val MAX_ROWS = "status_max_rows"
    const val REVEAL = "status_reveal"

    val DEFAULT_BLOCKS = listOf(PROFILE, VOLUME, STRIP, "row:wifi", "row:bt", "row:data")
    // Mock 1: flight, torch, rotate, location, data, screen recorder.
    val DEFAULT_TOGGLES = listOf("flight", "torch", "rotate", "location", "data", "record")

    private fun sp(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    private fun list(ctx: Context, key: String, def: List<String>): List<String> =
        sp(ctx).getString(key, null)?.let { s -> s.split(',').filter { it.isNotEmpty() } } ?: def

    fun blocks(ctx: Context): List<String> = list(ctx, BLOCKS, DEFAULT_BLOCKS).let { b ->
        // The strip is never removed (its ✎ brings toggles back), only moved.
        val s = if (STRIP in b) b else b + STRIP
        // The music widget follows its extra: always in the list, so it can be moved like any block.
        if (MUSIC in s) s else listOf(MUSIC) + s
    }
    fun setBlocks(ctx: Context, b: List<String>) = sp(ctx).edit().putString(BLOCKS, b.distinct().joinToString(",")).apply()

    fun toggles(ctx: Context): List<String> = list(ctx, STRIP_TOGGLES, DEFAULT_TOGGLES).filter { it in TOGGLES }
    fun setToggles(ctx: Context, t: List<String>) = sp(ctx).edit().putString(STRIP_TOGGLES, t.distinct().joinToString(",")).apply()

    /** 0 = all rows. */
    fun maxRows(ctx: Context) = sp(ctx).getString(MAX_ROWS, "0")!!.toIntOrNull() ?: 0
    /** "button", "swipe" or "both". */
    fun reveal(ctx: Context) = sp(ctx).getString(REVEAL, "both")!!

    fun title(id: String): String = when (id) {
        PROFILE -> "Profile"; VOLUME -> "Volume"; STRIP -> "Toggles"; MUSIC -> "Music"; MODES -> "Mode strip"
        "brightness" -> "Brightness"; "alarm" -> "Alarm volume"
        "wifi" -> "Internet connection"; "data" -> "Mobile data"; "bt" -> "Bluetooth"; "flight" -> "Flight mode"
        "torch" -> "Flashlight"; "rotate" -> "Auto-rotate"; "location" -> "Location"; "record" -> "Screen recorder"
        else -> if (id.startsWith("row:")) title(id.removePrefix("row:")) else id
    }
}
