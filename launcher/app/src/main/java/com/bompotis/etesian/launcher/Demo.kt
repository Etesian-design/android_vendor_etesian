package com.bompotis.etesian.launcher

import android.content.Context
import java.util.Calendar

/**
 * Demo mode, for screenshots and recordings only (README, website): made-up data in place of the owner's.
 * Switched over a root adb shell through TestNotifyReceiver (not exported): kind "demo", on true / false; nothing in the
 * settings shows it. While on: Events lists only this app's sample notifications (TestNotifyReceiver posts them), no kept
 * dismissed ones; the calendar, feeds and weather are the entries below; the application grid shows the stock apps in
 * alphabetical order, without folders or the private space, and never saves its layout (the owner's stays as it was).
 */
object Demo {
    const val KEY = "etesian_demo"
    @Volatile private var cached: Boolean? = null

    fun on(ctx: Context): Boolean = cached ?: Prefs.of(ctx).getBoolean(KEY, false).also { cached = it }

    fun set(ctx: Context, on: Boolean) { Prefs.of(ctx).edit().putBoolean(KEY, on).commit(); cached = on }

    /** The stock and Etesian apps; everything the owner installed, the SIM toolkit and the second FM radio stay out. */
    val APPS = setOf(
        "com.android.calculator2", "com.android.contacts", "com.android.deskclock", "com.android.dialer",
        "com.android.documentsui", "com.android.messaging", "com.android.settings",
        "com.bompotis.etesian.feeds", "com.bompotis.etesian.launcher", "com.bompotis.etesian.mail", "com.bompotis.etesian.store",
        "org.lineageos.aperture", "org.lineageos.etar", "org.lineageos.glimpse", "org.lineageos.jelly",
        "org.lineageos.recorder", "org.lineageos.twelve",
    )

    private fun today(h: Int, m: Int) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** The rest of today: whatever the time, the next entries are still ahead (a late recording keeps a calendar). */
    fun agenda(): List<Agenda.Entry> {
        val now = System.currentTimeMillis()
        val base = listOf(today(10, 0) to "Stand-up", today(14, 0) to "Design review", today(18, 30) to "Sauna with Mikko")
        val ahead = base.filter { it.first > now }
        val list = ahead.ifEmpty { listOf(now + 45 * 60_000L to "Design review", now + 3 * 3_600_000L to "Sauna with Mikko") }
        return list.mapIndexed { i, (t, title) -> Agenda.Entry(-1L - i, title, t, false) }
    }

    fun feeds(): List<Feeds.Item> {
        val now = System.currentTimeMillis()
        return listOf(
            Triple("Etesian journal", "A calm phone for the swipe generation", "Edge swipes, one list for everything new, and apps with nothing on screen but your content."),
            Triple("Etesian journal", "Squircles everywhere", "Why the icons, avatars and the loading spinner share one shape."),
            Triple("Northern news", "Northern lights expected again tonight", "Clear skies across the north after midnight."),
            Triple("Northern news", "Library extends its weekend hours", "Open until eight on Saturdays from next month."),
        ).mapIndexed { i, (feed, title, text) ->
            Feeds.Item("demo:$feed", feed, title, text, "https://etesian-design.com", now - (i + 1) * 47 * 60_000L, "demo-$i")
        }
    }

    fun weather(fahrenheit: Boolean): Weather.Now {
        val c = { v: Int -> if (fahrenheit) v * 9 / 5 + 32 else v }
        val day = 24 * 3_600_000L
        val start = today(12, 0)
        val days = listOf(Triple(6, 1, 2), Triple(5, 0, 3), Triple(3, -2, 61), Triple(4, -1, 1), Triple(7, 2, 0))
            .mapIndexed { i, (hi, lo, code) -> Weather.Day(start + i * day, c(hi), c(lo), code) }
        return Weather.Now(c(4), if (fahrenheit) "°F" else "°C", "Partly cloudy", "Helsinki", code = 2, day = true, days = days)
    }
}
