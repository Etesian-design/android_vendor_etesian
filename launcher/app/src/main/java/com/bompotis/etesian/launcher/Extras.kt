package com.bompotis.etesian.launcher

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Additions the N9 did not have, one switch each, shown on the settings page of what they change; stored in
 * Settings.Secure as etesian_extra_<name> (SystemUI and other apps read the same keys).
 * All off (the default) = the pure N9.
 */
object Extras {
    class Extra(val name: String, val title: String, val summary: String) {
        val key get() = if (name == LOCK_WEATHER) Weather.SETTING_LOCK else "etesian_extra_$name"
    }

    const val STATUS_MENU = "status_menu"
    const val ACTIONS = "actions"
    const val INLINE_REPLY = "inline_reply"
    const val MUSIC = "music"
    const val MODES = "modes"
    const val SHARE_PEOPLE = "share_people"
    const val PRIVACY = "privacy"
    const val PINNING = "pinning"
    const val LIVE_UPDATES = "live_updates"
    const val GLANCE = "glance"
    /** Folded in: the existing etesian_lock_weather switch. */
    const val LOCK_WEATHER = "lock_weather"
    const val SHARE = "share"
    const val GHOST_ROWS = "ghost_rows"
    const val EMERGENCY = "emergency"
    const val COPIED = "copied"
    const val WIFI_QR = "wifi_qr"
    const val TODAY = "today"
    const val PRIORITY = "priority"
    const val PRIVATE = "private_space"
    const val SPLIT_MERGE = "split_merge"
    const val CARD_ACTIONS = "card_actions"

    val ALL = listOf(
        Extra(STATUS_MENU, "Status menu: toggle strip, sliders and hidden rows", "Your own toggles and sliders, fewer rows"),
        Extra(ACTIONS, "Notification actions and snooze", "Long-press an Events item: Mark as read, Snooze, Dismiss"),
        Extra(INLINE_REPLY, "Inline reply", "Reply to a message from Events"),
        Extra(MUSIC, "Music in the status menu", "The small music player at the top of the status menu"),
        Extra(MODES, "Do Not Disturb and Modes as profiles", "A Mode strip under Profile"),
        Extra(SHARE_PEOPLE, "Suggested people in share", "A row of people in the share sheet"),
        Extra(PRIVACY, "Privacy indicators", "Camera and microphone in use, in the status bar"),
        Extra(PINNING, "App pinning", "Keep one app on screen"),
        Extra(LIVE_UPDATES, "Live updates in Events", "Ongoing progress pinned under Notifications"),
        Extra(GLANCE, "Glance line on the lock screen", "Next alarm and event under the date"),
        Extra(LOCK_WEATHER, "Weather on the lock screen", "Current weather under the date (from the Events weather)"),
        Extra(SHARE, "Share filter and pinned targets", "The N9 share sheet with a filter and pinned apps"),
        Extra(CARD_ACTIONS, "Open applications carousel actions", "Split view, Pop-up, Screenshot and App info under the card in the carousel"),
        Extra(SPLIT_MERGE, "Split by two-finger merge", "Open applications edit mode: pull two cards together with two fingers for split screen; pull a pair apart to separate"),
        Extra(PRIVATE, "Private space", "Android’s private space as a locked folder at the end of the Applications grid"),
        Extra(PRIORITY, "Priority people", "A person’s messages, mail and calls first in Events, grouped under them"),
        Extra(TODAY, "Today in Events", "One-line items: screen time, all-day events, birthdays, upcoming events"),
        Extra(WIFI_QR, "Share network QR", "Share network in Etesian settings: a QR code and the password"),
        Extra(COPIED, "“Copied” bubble", "A short bubble when something is copied, instead of the clipboard overlay"),
        Extra(EMERGENCY, "Emergency information on the lock screen", "Owner and essentials beside the clock; tap for the full card"),
        Extra(GHOST_ROWS, "Keep dismissed notifications", "Faded rows at the end of Notifications; tap for Earlier"),
    )

    fun key(name: String) = ALL.first { it.name == name }.key

    fun on(ctx: Context, name: String) = Settings.Secure.getInt(ctx.contentResolver, key(name), 0) == 1

    fun set(ctx: Context, name: String, value: Boolean) {
        Settings.Secure.putInt(ctx.contentResolver, key(name), if (value) 1 else 0)
        if (name == SHARE) ShareActivity.sync(ctx)
    }

    /** Calls [onChange] on the main thread whenever extra [name] is switched; unregister the result. */
    fun observe(ctx: Context, name: String, onChange: () -> Unit): ContentObserver {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        ctx.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key(name)), false, o)
        return o
    }
}
