package com.bompotis.etesian.launcher.standby

import android.app.ActivityManager
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.text.format.Formatter
import java.net.Inet4Address
import java.util.Date
import java.util.Locale

/**
 * What a standby line can show (the N9's Billboard placeholders, read from Android). Each item has an id (stored in
 * [StandbyConfig]), a name for the settings page, and its value now, "" when there is nothing to show (no song
 * playing, no alarm set). The battery bar is drawn, not text: its value is the level, 0-100.
 */
object StandbyItems {
    class Item(val id: String, val label: String)

    val ALL = listOf(
        Item("time", "Time"), Item("date_compact", "Compact date"), Item("date_full", "Full date"),
        Item("song_artist", "Song artist"), Item("song_title", "Song title"), Item("song", "Artist – Title"),
        Item("network", "Network name"), Item("connection", "Internet connection"), Item("ip", "IP address"),
        Item("battery", "Battery %"), Item("battery_bar", "Battery bar"), Item("battery_state", "Battery state"),
        Item("profile", "Profile"), Item("alarm", "Next alarm"), Item("uptime", "Uptime"),
        Item("ram", "Memory used"), Item("load", "System load (1, 5, 15 min)"), Item("storage", "Storage used"),
    )
    const val BAR = "battery_bar"

    fun label(id: String) = ALL.firstOrNull { it.id == id }?.label ?: id

    /** Every item's value now; [now] for the clock and dates (the preview and the minute tick share it). */
    fun values(ctx: Context, now: Long = System.currentTimeMillis()): Map<String, String> {
        val v = HashMap<String, String>()
        val d = Date(now)
        v["time"] = DateFormat.getTimeFormat(ctx).format(d)
        v["date_compact"] = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"), d).toString()
        v["date_full"] = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMMyyyy"), d).toString()
        song(ctx, v)
        net(ctx, v)
        battery(ctx, v)
        v["profile"] = when (ctx.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Beep" // the status menu's N9 profile: Silent | Beep | Ringing
            AudioManager.RINGER_MODE_NORMAL -> "Ringing"
            else -> ""
        }
        v["alarm"] = ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.let {
            DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(),
                if (DateFormat.is24HourFormat(ctx)) "EEE HH:mm" else "EEE h:mm a"), it.triggerTime).toString()
        } ?: ""
        v["uptime"] = uptime(SystemClock.elapsedRealtime())
        runCatching {
            val mi = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            v["ram"] = "${Formatter.formatShortFileSize(ctx, mi.totalMem - mi.availMem)} / ${Formatter.formatShortFileSize(ctx, mi.totalMem)}"
        }
        runCatching {
            val s = StatFs(Environment.getDataDirectory().path)
            v["storage"] = "${Formatter.formatShortFileSize(ctx, s.totalBytes - s.availableBytes)} / ${Formatter.formatShortFileSize(ctx, s.totalBytes)}"
        }
        v["load"] = load()
        return v
    }

    /** "3 d 4 h", "4 h 12 min", "12 min". */
    fun uptime(ms: Long): String {
        val m = ms / 60000; val h = m / 60; val days = h / 24
        return when { days > 0 -> "$days d ${h % 24} h"; h > 0 -> "$h h ${m % 60} min"; else -> "$m min" }
    }

    private fun song(ctx: Context, v: MutableMap<String, String>) {
        val c = runCatching {
            ctx.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(ctx.packageName, "com.bompotis.etesian.launcher.EventsService"))
        }.getOrNull().orEmpty().firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val md = c?.metadata
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        v["song_artist"] = artist; v["song_title"] = title
        v["song"] = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" – ")
    }

    private fun net(ctx: Context, v: MutableMap<String, String>) {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        v["network"] = runCatching { tm?.networkOperatorName }.getOrNull().orEmpty()
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val n = cm?.activeNetwork
        val caps = n?.let { cm.getNetworkCapabilities(it) }
        v["connection"] = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> mobile(runCatching { tm?.dataNetworkType }.getOrNull() ?: 0)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Online"
        }
        v["ip"] = n?.let { cm.getLinkProperties(it) }?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress.orEmpty()
    }

    private fun mobile(t: Int) = when (t) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GSM,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
        0 -> "Mobile"
        else -> "3G"
    }

    private fun battery(ctx: Context, v: MutableMap<String, String>) {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (lvl < 0) -1 else lvl * 100 / scale.coerceAtLeast(1)
        v["battery"] = if (pct < 0) "" else "$pct"
        v[BAR] = if (pct < 0) "" else "$pct"
        v["battery_state"] = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "On battery"
            else -> ""
        }
        v["charging"] = if (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) "1" else ""
    }

    /**
     * The 1, 5 and 15 minute load averages. Apps may not read /proc/loadavg (SELinux), but the system's "cpuinfo"
     * service prints them first ("Load: 1.2 / 1.0 / 0.9"); the launcher is a platform app with DUMP.
     */
    fun load(): String = runCatching {
        val p = ParcelFileDescriptor.createPipe()
        val t = Thread {
            runCatching {
                val sm = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                    .invoke(null, "cpuinfo") as android.os.IBinder
                sm.dump(p[1].fileDescriptor, arrayOf())
            }
            p[1].close()
        }.apply { start() }
        val first = ParcelFileDescriptor.AutoCloseInputStream(p[0]).bufferedReader().use { r ->
            generateSequence { r.readLine() }.take(4).firstOrNull { it.startsWith("Load:") }
        }
        t.join(1000)
        first?.removePrefix("Load:")?.trim()?.replace(" / ", " ").orEmpty()
    }.getOrDefault("")

    /** A line's text: its parts with the items' values; null when every item in it is empty (the line's "when empty"). */
    fun text(line: StandbyConfig.Line, v: Map<String, String>): String? {
        val items = line.parts.filterIsInstance<StandbyConfig.Part.Item>()
        if (items.isNotEmpty() && items.all { v[it.id].isNullOrBlank() }) return null
        return line.parts.joinToString("") { p ->
            when (p) { is StandbyConfig.Part.Text -> p.text; is StandbyConfig.Part.Item -> v[p.id].orEmpty() }
        }
    }
}
