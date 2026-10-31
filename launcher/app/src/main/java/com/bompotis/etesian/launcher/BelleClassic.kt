package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews

/**
 * Exact Symbian Belle widgets, next to the Belle-style ones in [BelleWidgets]: the flip clock,
 * the calendar card, single app and contact shortcuts, the square analog clock, the Wi-Fi switch
 * and the row of switches (references: docs/bugs-2026-10-25.md, belle-ref*.jpg).
 */
object BelleClassic {
    const val ACTION_TOGGLE = "com.bompotis.etesian.launcher.BELLE_TOGGLE"
    const val EXTRA_WHAT = "what"
    private val main = Handler(Looper.getMainLooper())
    private var watching = false

    fun broadcast(ctx: Context, cls: Class<*>, what: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, cls).setAction(ACTION_TOGGLE).putExtra(EXTRA_WHAT, what),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    // ---- switch state ----

    fun wifiOn(ctx: Context) = ctx.getSystemService(WifiManager::class.java).isWifiEnabled
    @SuppressLint("MissingPermission")
    fun btOn(ctx: Context) = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    fun dataOn(ctx: Context) = runCatching { ctx.getSystemService(TelephonyManager::class.java).isDataEnabled }.getOrDefault(false)
    fun silentOn(ctx: Context) = ctx.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL

    @SuppressLint("MissingPermission")
    fun flip(ctx: Context, what: String) {
        when (what) {
            "wifi" -> ctx.getSystemService(WifiManager::class.java).let { @Suppress("DEPRECATION") it.setWifiEnabled(!it.isWifiEnabled) }
            "bluetooth" -> ctx.getSystemService(BluetoothManager::class.java)?.adapter?.let {
                @Suppress("DEPRECATION") if (it.isEnabled) it.disable() else it.enable()
            }
            "data" -> runCatching {
                val tm = ctx.getSystemService(TelephonyManager::class.java)
                tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER, !tm.isDataEnabled)
            }
            "silent" -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                am.ringerMode = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_SILENT
                    else AudioManager.RINGER_MODE_NORMAL
            }
        }
        // State changes land a moment later; redraw then too.
        updateSwitches(ctx)
        main.postDelayed({ updateSwitches(ctx) }, 800)
        main.postDelayed({ updateSwitches(ctx) }, 2500)
    }

    /** Redraws the switches when Wi-Fi, Bluetooth or the ringer change by any means. */
    fun watch(ctx: Context) {
        if (watching) return
        watching = true
        val app = ctx.applicationContext
        val f = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = updateSwitches(app)
        }, f, Context.RECEIVER_NOT_EXPORTED)
    }

    fun updateSwitches(ctx: Context) {
        BelleTogglesWidget.updateAll(ctx)
        BelleWifiSwitchWidget.updateAll(ctx)
    }

    fun updateAll(ctx: Context) {
        watch(ctx)
        updateSwitches(ctx)
        BelleTodayWidget.updateAll(ctx)
        BelleAppWidget.updateAll(ctx)
        BelleContactWidget.updateAll(ctx)
    }
}

/** Flip clock: TextClocks, so it keeps time with no updates. Tap: the alarms. */
class BelleFlipClockWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        val v = RemoteViews(ctx.packageName, R.layout.bc_flipclock)
        v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 40, Intent(AlarmClock.ACTION_SHOW_ALARMS)))
        awm.updateAppWidget(ids, v)
    }
}

/** Square analog clock. Tap: the alarms. */
class BelleSquareClockWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.bc_analog)
            BelleClassic2.fitFace(awm, id, v, BelleClassic2.SQUIRCLE, day = false)
            v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 41, Intent(AlarmClock.ACTION_SHOW_ALARMS)))
            awm.updateAppWidget(id, v)
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, awm: AppWidgetManager, id: Int, o: android.os.Bundle) =
        onUpdate(ctx, awm, intArrayOf(id))
}

/** Calendar card: the next event's day as its header, then its time and title. */
class BelleTodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PROVIDER_CHANGED) updateAll(ctx) else super.onReceive(ctx, intent)
    }

    companion object {
        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleTodayWidget::class.java)
            if (ids.isEmpty()) return
            val e = BelleCalendarWidget.next(ctx).firstOrNull { it.begin < System.currentTimeMillis() + 7 * DateUtils.DAY_IN_MILLIS }
            val v = RemoteViews(ctx.packageName, R.layout.bc_today)
            v.setViewVisibility(R.id.bc_empty, if (e == null) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.bc_event, if (e == null) View.GONE else View.VISIBLE)
            if (e == null) {
                v.setTextViewText(R.id.bc_day, "Today")
            } else {
                v.setTextViewText(R.id.bc_day, when {
                    DateUtils.isToday(e.begin) -> "Today"
                    DateUtils.isToday(e.begin - DateUtils.DAY_IN_MILLIS) -> "Tomorrow"
                    else -> DateFormat.format("EEEE", e.begin).toString()
                })
                v.setTextViewText(R.id.bc_time, if (e.allDay) "All day" else DateFormat.getTimeFormat(ctx).format(java.util.Date(e.begin)))
                v.setTextViewText(R.id.bc_title, e.title)
            }
            val calendar = Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time").appendPath(System.currentTimeMillis().toString()).build())
            v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 42, calendar))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}

/** One app's shortcut, picked in [BelleShortcutsConfigActivity] (one slot). */
class BelleAppWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, awm, it) }

    companion object {
        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            BelleWidgets.ids(ctx, BelleAppWidget::class.java).forEach { update(ctx, awm, it) }
        }

        fun update(ctx: Context, awm: AppWidgetManager, id: Int) {
            val v = RemoteViews(ctx.packageName, R.layout.bc_app)
            val cn = BelleShortcutsWidget.slots(ctx, id).firstOrNull()
            val info = cn?.let { runCatching { ctx.packageManager.getActivityInfo(it, 0) }.getOrNull() }
            if (cn == null || info == null) {
                v.setImageViewResource(R.id.bc_icon, R.drawable.belle_add)
                v.setTextViewText(R.id.bc_label, "Add")
                v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 5000 + id,
                    Intent(ctx, BelleShortcutsConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .setData(Uri.parse("belle://app/$id"))))
            } else {
                v.setImageViewBitmap(R.id.bc_icon, BelleShortcutsWidget.sized(
                    BelleShortcutsWidget.icon(ctx, cn, info.loadIcon(ctx.packageManager)), ctx.dp(64f)))
                v.setTextViewText(R.id.bc_label, info.loadLabel(ctx.packageManager))
                v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 5000 + id,
                    Intent.makeMainActivity(cn).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)))
            }
            awm.updateAppWidget(id, v)
        }
    }
}

/** Wi-Fi switch: the power squircle flips Wi-Fi; the wide one shows networks in range and opens Wi-Fi settings. */
class BelleWifiSwitchWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) { BelleClassic.watch(ctx); updateAll(ctx) }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == BelleClassic.ACTION_TOGGLE) BelleClassic.flip(ctx, "wifi") else super.onReceive(ctx, intent)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, awm: AppWidgetManager, id: Int, o: Bundle) = updateAll(ctx)

    companion object {
        @SuppressLint("MissingPermission")
        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            val ids = BelleWidgets.ids(ctx, BelleWifiSwitchWidget::class.java)
            if (ids.isEmpty()) return
            val on = BelleClassic.wifiOn(ctx)
            val count = if (on) runCatching {
                ctx.getSystemService(WifiManager::class.java).scanResults.map { it.SSID }.filter { it.isNotEmpty() }.toSet().size
            }.getOrDefault(0) else 0
            ids.forEach { id ->
                // One cell: the power squircle alone; wider: the Wi-Fi side too.
                val wide = awm.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) >= 120
                val v = RemoteViews(ctx.packageName, R.layout.bc_switch)
                v.setInt(R.id.bc_power, "setBackgroundResource", if (on) R.drawable.bc_power_on else R.drawable.bc_power_off)
                v.setViewVisibility(R.id.bc_side, if (wide) View.VISIBLE else View.GONE)
                v.setInt(R.id.bc_side_glyph, "setColorFilter", if (on) 0xFFFFFFFF.toInt() else 0xFF6E6F73.toInt())
                v.setViewVisibility(R.id.bc_count, if (wide && count > 0) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.bc_count, count.toString())
                v.setContentDescription(R.id.bc_power, if (on) "Wi-Fi on" else "Wi-Fi off")
                v.setOnClickPendingIntent(R.id.bc_power, BelleClassic.broadcast(ctx, BelleWifiSwitchWidget::class.java, "wifi", 43))
                v.setOnClickPendingIntent(R.id.bc_side, BelleWidgets.activity(ctx, 44, Intent(Settings.ACTION_WIFI_SETTINGS)))
                awm.updateAppWidget(id, v)
            }
        }
    }
}

/** The switches row: Mobile data, Wi-Fi, Bluetooth, Silent. Long-press is not available on widgets. */
class BelleTogglesWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) { BelleClassic.watch(ctx); updateAll(ctx) }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == BelleClassic.ACTION_TOGGLE) BelleClassic.flip(ctx, intent.getStringExtra(BelleClassic.EXTRA_WHAT).orEmpty())
        else super.onReceive(ctx, intent)
    }

    private class Switch(val what: String, val label: String, val icon: Int, val on: (Context) -> Boolean)

    companion object {
        private val SWITCHES = listOf(
            Switch("data", "Mobile data", R.drawable.bc_glyph_data, BelleClassic::dataOn),
            Switch("wifi", "Wi-Fi", R.drawable.bc_glyph_wifi, BelleClassic::wifiOn),
            Switch("bluetooth", "Bluetooth", R.drawable.bc_glyph_bluetooth, BelleClassic::btOn),
            Switch("silent", "Silent", R.drawable.bc_glyph_silent, BelleClassic::silentOn),
        )
        private val CELLS = intArrayOf(R.id.bc_t0, R.id.bc_t1, R.id.bc_t2, R.id.bc_t3)
        private val BGS = intArrayOf(R.id.bc_t0_bg, R.id.bc_t1_bg, R.id.bc_t2_bg, R.id.bc_t3_bg)
        private val ICONS = intArrayOf(R.id.bc_t0_icon, R.id.bc_t1_icon, R.id.bc_t2_icon, R.id.bc_t3_icon)
        private val LABELS = intArrayOf(R.id.bc_t0_label, R.id.bc_t1_label, R.id.bc_t2_label, R.id.bc_t3_label)

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleTogglesWidget::class.java)
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.bc_toggles)
            SWITCHES.forEachIndexed { i, s ->
                val on = s.on(ctx)
                v.setInt(BGS[i], "setBackgroundResource", if (on) R.drawable.bc_toggle_on else R.drawable.bc_toggle_off)
                v.setImageViewResource(ICONS[i], s.icon)
                // Belle: the glyph sits on the metal cap, white when on, dark grey when off.
                v.setInt(ICONS[i], "setColorFilter", if (on) 0xFFFFFFFF.toInt() else BelleClassic2.CAP_OFF)
                BelleClassic2.capGlyph(v, intArrayOf(ICONS[i]), on, 62f)
                v.setTextViewText(LABELS[i], s.label)
                v.setContentDescription(CELLS[i], "${s.label} ${if (on) "on" else "off"}")
                v.setOnClickPendingIntent(CELLS[i], BelleClassic.broadcast(ctx, BelleTogglesWidget::class.java, s.what, 50 + i))
            }
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}

/** A contact's photo and name; tap opens the contact. Picked in [BelleContactConfigActivity]. */
class BelleContactWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, awm, it) }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = prefs(ctx).edit(); ids.forEach { e.remove("c$it") }; e.apply()
    }

    companion object {
        fun prefs(ctx: Context) = ctx.getSharedPreferences("belle_contacts", Context.MODE_PRIVATE)

        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            BelleWidgets.ids(ctx, BelleContactWidget::class.java).forEach { update(ctx, awm, it) }
        }

        fun update(ctx: Context, awm: AppWidgetManager, id: Int) {
            val v = RemoteViews(ctx.packageName, R.layout.bc_contact)
            val uri = prefs(ctx).getString("c$id", null)?.let(Uri::parse)
            val pick = BelleWidgets.activity(ctx, 6000 + id, Intent(ctx, BelleContactConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setData(Uri.parse("belle://contact/$id")))
            if (uri == null) {
                v.setTextViewText(R.id.bc_name, "Add contact")
                v.setImageViewResource(R.id.bc_photo, R.drawable.belle_add)
                v.setOnClickPendingIntent(R.id.bc_root, pick)
            } else {
                v.setTextViewText(R.id.bc_name, name(ctx, uri) ?: "Contact")
                photo(ctx, uri)?.let { v.setImageViewBitmap(R.id.bc_photo, it) }
                    ?: v.setImageViewResource(R.id.bc_photo, R.drawable.belle_add)
                v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 6000 + id, Intent(Intent.ACTION_VIEW, uri)))
            }
            awm.updateAppWidget(id, v)
        }

        private fun name(ctx: Context, uri: Uri): String? = runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()

        private fun photo(ctx: Context, uri: Uri): Bitmap? = runCatching {
            ContactsContract.Contacts.openContactPhotoInputStream(ctx.contentResolver, uri, true)?.use { s ->
                BitmapFactory.decodeStream(s)?.let { BelleWidgets.scaled(it, ctx.dp(200f)) }
            }
        }.getOrNull()
    }
}

/** Picks the contact for a [BelleContactWidget] with the system contact picker. */
class BelleContactConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (savedInstanceState == null) {
            runCatching { startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI), 1) }
                .onFailure { finish() }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val picked = data?.data
        if (resultCode == RESULT_OK && picked != null) {
            // The lookup URI survives contact merges and id changes.
            val lookup = runCatching { ContactsContract.Contacts.getLookupUri(contentResolver, picked) }.getOrNull() ?: picked
            BelleContactWidget.prefs(this).edit().putString("c$widgetId", lookup.toString()).apply()
            BelleContactWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        finish()
    }
}
