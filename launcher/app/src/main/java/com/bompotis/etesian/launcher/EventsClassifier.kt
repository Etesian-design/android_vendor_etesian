package com.bompotis.etesian.launcher

import android.app.Notification
import android.app.NotificationManager
import android.content.Context

/**
 * Sorts notifications into the N9's Events sections. In the N9 days only messages, mail and
 * calls were notifications; everything else was the feed. Here the user decides, per app or
 * per Android notification channel, with sensible defaults from the category the app sets.
 */
object EventsClassifier {
    enum class Section(val key: String, val title: String) {
        NOTIFICATIONS("notifications", "Notifications"),
        CALENDAR("calendar", "Calendar"),
        FEED("feed", "Feed"),
        LOW("low", "Low priority"),
    }

    private const val PREFS = "events_sections"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun channelKey(e: EventItem) = "${e.pkg}|${e.channelId}"

    fun classify(ctx: Context, e: EventItem): Section {
        val p = prefs(ctx)
        (p.getString(channelKey(e), null) ?: p.getString(e.pkg, null))?.let { key ->
            Section.entries.firstOrNull { it.key == key }?.let { return it }
        }
        return defaultSection(e)
    }

    private fun defaultSection(e: EventItem): Section = when {
        e.category == Notification.CATEGORY_EVENT || e.category == Notification.CATEGORY_REMINDER -> Section.CALENDAR
        e.category in setOf(Notification.CATEGORY_MESSAGE, Notification.CATEGORY_EMAIL, Notification.CATEGORY_CALL,
            Notification.CATEGORY_MISSED_CALL, Notification.CATEGORY_ALARM) -> Section.NOTIFICATIONS
        e.importance in 1..NotificationManager.IMPORTANCE_LOW -> Section.LOW
        e.category in setOf(Notification.CATEGORY_PROMO, Notification.CATEGORY_RECOMMENDATION,
            Notification.CATEGORY_STATUS, Notification.CATEGORY_SYSTEM, Notification.CATEGORY_SERVICE) -> Section.LOW
        e.category == Notification.CATEGORY_SOCIAL -> Section.FEED
        e.importance >= NotificationManager.IMPORTANCE_HIGH -> Section.NOTIFICATIONS
        else -> Section.FEED
    }

    /** Files every notification from [e]'s app, or only its channel, under [section]. */
    fun assign(ctx: Context, e: EventItem, section: Section, wholeApp: Boolean) {
        prefs(ctx).edit().apply {
            if (wholeApp) {
                // A whole-app choice replaces earlier per-channel choices for that app.
                prefs(ctx).all.keys.filter { it.startsWith("${e.pkg}|") }.forEach { remove(it) }
                putString(e.pkg, section.key)
            } else {
                putString(channelKey(e), section.key)
            }
        }.apply()
    }

    /** How a section lays out its notifications (see Prefs.eventsStyle). */
    enum class Style(val key: String, val title: String) {
        GROUPED("grouped", "Grouped per app"),
        INDIVIDUAL("individual", "Each one separately"),
        CARD("card", "One card per app (Android)"),
        STACK("stack", "Stacked cards (iPhone)"),
    }
}
