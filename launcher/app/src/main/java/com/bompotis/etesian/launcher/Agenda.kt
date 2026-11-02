package com.bompotis.etesian.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar

/** Today's remaining calendar entries, for the Calendar section of the Events view. */
object Agenda {
    class Entry(val id: Long, val title: String, val begin: Long, val allDay: Boolean) {
        fun intent(): Intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Android extras → glance line: publishes today's next timed event as "title HH:mm|begin" in
     * Settings.Secure etesian_glance_event for the lock screen (SystemUI cannot read calendars).
     */
    fun publishGlance(ctx: Context, entries: List<Entry> = today(ctx)) {
        val cr = ctx.contentResolver
        val next = entries.filter { !it.allDay && it.begin > System.currentTimeMillis() }.minByOrNull { it.begin }
        val v = next?.let { "${it.title} ${android.text.format.DateFormat.getTimeFormat(ctx).format(it.begin)}|${it.begin}" }
        runCatching {
            if (v == null) android.provider.Settings.Secure.putString(cr, "etesian_glance_event", null)
            else if (android.provider.Settings.Secure.getString(cr, "etesian_glance_event") != v)
                android.provider.Settings.Secure.putString(cr, "etesian_glance_event", v)
        }
    }

    fun today(ctx: Context): List<Entry> {
        if (Demo.on(ctx)) return Demo.agenda()
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val now = System.currentTimeMillis()
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, end)
        }.build()
        val cols = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY)
        return runCatching {
            ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext() && size < 8) {
                        add(Entry(c.getLong(0), c.getString(1).orEmpty().ifEmpty { "(No title)" }, c.getLong(2), c.getInt(3) == 1))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }
}
