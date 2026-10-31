package com.bompotis.etesian.launcher

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Settings
import java.util.Calendar

/**
 * Android extras → Today in Events (story Phone v2, state screen-time): an optional "Today"
 * section of one-line items with a small lit squircle sized to the line: screen time, all-day
 * events, birthdays, upcoming calendar events. Which items show: Settings.Secure
 * etesian_today_items (comma list of screen,allday,birthdays,upcoming; default all).
 */
object Today {
    const val KEY_ITEMS = "etesian_today_items"
    val ALL = listOf("screen" to "Screen time", "allday" to "All-day events", "birthdays" to "Birthdays", "upcoming" to "Upcoming events")

    class Item(val glyph: String, val color: Int, val text: String, val intent: Intent?)

    fun on(ctx: Context) = Extras.on(ctx, Extras.TODAY)
    fun shown(ctx: Context): Set<String> =
        (Settings.Secure.getString(ctx.contentResolver, KEY_ITEMS) ?: ALL.joinToString(",") { it.first }).split(',').map { it.trim() }.toSet()

    fun items(ctx: Context, agenda: List<Agenda.Entry>): List<Item> {
        val show = shown(ctx)
        val out = mutableListOf<Item>()
        if ("screen" in show) screenTime(ctx)?.let { out += it }
        if ("allday" in show) agenda.filter { it.allDay }.forEach { out += Item("calendar", 0xFFEF5500.toInt(), it.title, it.intent()) }
        if ("birthdays" in show) birthdays(ctx).forEach { out += it }
        if ("upcoming" in show) agenda.filter { !it.allDay && it.begin > System.currentTimeMillis() }.forEach {
            out += Item("calendar", 0xFFEF5500.toInt(), "${android.text.format.DateFormat.getTimeFormat(ctx).format(it.begin)} ${it.title}", it.intent())
        }
        return out
    }

    private fun startOfDay() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun duration(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    /** "2 h 14 min screen time · Messages 1 h 4 min" (apps in front today; the launcher itself not counted). */
    private fun screenTime(ctx: Context): Item? {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val stats = runCatching { usm.queryAndAggregateUsageStats(startOfDay(), System.currentTimeMillis()) }.getOrNull() ?: return null
        val apps = stats.values.filter { it.packageName != ctx.packageName && it.totalTimeInForeground > 0 &&
            ctx.packageManager.getLaunchIntentForPackage(it.packageName) != null }
        val total = apps.sumOf { it.totalTimeInForeground }
        if (total < 60_000) return null
        val top = apps.maxByOrNull { it.totalTimeInForeground }!!
        val label = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(top.packageName, 0)).toString() }
            .getOrDefault(top.packageName)
        return Item("clock", 0xFF3A3A3A.toInt(), "${duration(total)} screen time · $label ${duration(top.totalTimeInForeground)}",
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).let { runCatching { Intent("android.settings.DIGITAL_WELLBEING") }.getOrDefault(it) })
    }

    /** Contacts whose birthday is today. */
    private fun birthdays(ctx: Context): List<Item> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val c = Calendar.getInstance()
        val md = String.format("-%02d-%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        val E = ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
        return runCatching {
            ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
                arrayOf(E, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()), null)?.use { q ->
                buildList {
                    while (q.moveToNext()) {
                        val date = q.getString(2).orEmpty()
                        // "1990-10-26" or "--10-26" (no year)
                        if (!date.endsWith(md)) continue
                        val uri = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, q.getLong(0))
                        add(Item("star", 0xFFC2185B.toInt(), "${q.getString(1)}’s birthday",
                            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }
}
