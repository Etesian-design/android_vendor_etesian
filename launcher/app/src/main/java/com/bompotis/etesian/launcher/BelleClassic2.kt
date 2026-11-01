package com.bompotis.etesian.launcher

import android.app.Activity
import android.app.usage.NetworkStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * More exact Symbian Belle widgets (references belle-ref7..11 in notes/bugs-2026-10-25.md (local notes)): the 1×1
 * switches, the data tracker, Notes, the big calendar, the large clocks and website shortcuts.
 */
object BelleClassic2 {
    private var ticking = false

    fun updateAll(ctx: Context) {
        tick(ctx)
        BelleSwitch1Widget.updateAll(ctx)
        BelleDataUsageWidget.updateAll(ctx)
        BelleNotesWidget.updateAll(ctx)
        BelleAgendaWidget.updateAll(ctx)
        BelleMailWidget.updateAll(ctx)
        BelleWordClockWidget.updateAll(ctx)
        BelleBigClockWidget.updateAll(ctx)
        BelleGearClockWidget.updateAll(ctx)
        BelleWebWidget.updateAll(ctx)
        BelleWeatherClassicWidget.updateAll(ctx)
    }

    /** The word clock and the data tracker follow the minute (the launcher is always running). */
    private fun tick(ctx: Context) {
        if (ticking) return
        ticking = true
        val app = ctx.applicationContext
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                BelleWordClockWidget.updateAll(app)
                if (i.action == Intent.ACTION_TIME_TICK && Calendar.getInstance().get(Calendar.MINUTE) % 5 == 0) {
                    BelleDataUsageWidget.updateAll(app)
                }
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
    }

    fun prefs(ctx: Context) = ctx.getSharedPreferences("belle_classic", Context.MODE_PRIVATE)

    /**
     * Where the day name and date sit inside an analog face, as fractions of its side: the day's
     * centre (x, y; x < 0 for none), the date's centre x and size.
     */
    class Face(val dayX: Float, val dayY: Float, val dateX: Float, val dateSize: Float, val text: Float)

    /** The squircle clocks (belle-ref10.jpg, belle-ref4.jpg). */
    /** The glyph colour on a Belle toggle cap that is off (BelleCap.vue). */
    const val CAP_OFF = 0xFF2E343D.toInt()
    /** A lit glyph on a cap that is on: Belle blue (belle-ref4). */
    const val CAP_ON = 0xFF3C8FE6.toInt()

    /**
     * Moves a cap's glyph with the cap (bc_toggle_*, bc_sw_*, a 72 × 82 face): the cap sits up when on, so the glyph rises
     * 6% of the face's [height] (dp); off it sits down in the well and the glyph drops as much.
     */
    fun capGlyph(v: RemoteViews, ids: IntArray, on: Boolean, height: Float) {
        val dip = android.util.TypedValue.COMPLEX_UNIT_DIP
        val shift = height * 0.12f
        for (id in ids) {
            v.setViewLayoutMargin(id, RemoteViews.MARGIN_BOTTOM, if (on) shift else 0f, dip)
            v.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, if (on) 0f else shift, dip)
        }
    }

    /** The squircle dial: the date window at 3 o'clock is 26 of 126, centred at 92 (the 701's clock art). */
    val SQUIRCLE = Face(0.245f, 0.5f, 0.73f, 0.206f, 0.09f)
    /** The gear clock (belle-ref11.jpg): the day low on the left, a round date at 3 o'clock. */
    val GEARS = Face(0.30f, 0.71f, 0.86f, 0.11f, 0.058f)

    /**
     * Sizes an analog face to its widget and places the day and date inside it. AnalogClock only
     * ever scales its 400dp dial down, so the face fills the widget's shorter side; everything round
     * it stays transparent.
     */
    fun fitFace(awm: AppWidgetManager, id: Int, v: RemoteViews, face: Face, day: Boolean) {
        val o = awm.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 220
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 220
        val side = (min(w, h) - 8).coerceAtLeast(56).toFloat()
        val dip = android.util.TypedValue.COMPLEX_UNIT_DIP
        v.setViewLayoutWidth(R.id.bc_face, side, dip)
        v.setViewLayoutHeight(R.id.bc_face, side, dip)
        val date = side * face.dateSize
        v.setViewLayoutWidth(R.id.bc_date, date, dip)
        v.setViewLayoutHeight(R.id.bc_date, date, dip)
        v.setViewLayoutMargin(R.id.bc_date, RemoteViews.MARGIN_END, side * (1 - face.dateX) - date / 2, dip)
        v.setTextViewTextSize(R.id.bc_date, dip, side * face.text)
        if (!day) return
        val textSize = side * face.text
        // The gear clock's day is top-anchored and sits in a round bubble as large as the date's (belle-ref11), centred on dayX, dayY;
        // a centred day (dayY 0.5) is plain text.
        val bubble = face.dayY != 0.5f
        val dayW = if (bubble) date else side * 0.26f
        v.setViewLayoutWidth(R.id.bc_day, dayW, dip)
        v.setViewLayoutMargin(R.id.bc_day, RemoteViews.MARGIN_START, side * face.dayX - dayW / 2, dip)
        if (bubble) {
            v.setViewLayoutHeight(R.id.bc_day, date, dip)
            v.setViewLayoutMargin(R.id.bc_day, RemoteViews.MARGIN_TOP, side * face.dayY - date / 2, dip)
        }
        v.setTextViewTextSize(R.id.bc_day, dip, if (bubble) textSize * 0.78f else textSize)
    }
}

// ---- 1×1 switches ----

/** One switch per widget, as on Belle: Wi-Fi, Bluetooth, mobile data, Silent, Flight mode or VPN. */
class BelleSwitch1Widget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) { BelleClassic.watch(ctx); updateAll(ctx) }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == BelleClassic.ACTION_TOGGLE) {
            when (val what = intent.getStringExtra(BelleClassic.EXTRA_WHAT).orEmpty()) {
                "flight" -> flipFlight(ctx)
                "3g" -> flip3g(ctx)
                "vpn" -> ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> BelleClassic.flip(ctx, what)
            }
            updateAll(ctx)
        } else super.onReceive(ctx, intent)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = BelleClassic2.prefs(ctx).edit(); ids.forEach { e.remove("sw$it") }; e.apply()
    }

    class Kind(val what: String, val label: String, val glyph: Int, val word: String?, val on: (Context) -> Boolean)

    companion object {
        val KINDS = listOf(
            Kind("wifi", "Wi-Fi", R.drawable.bc_glyph_wifi, null, BelleClassic::wifiOn),
            Kind("bluetooth", "Bluetooth", R.drawable.bc_glyph_bluetooth, null, BelleClassic::btOn),
            Kind("data", "Mobile data", R.drawable.bc_glyph_data, null, BelleClassic::dataOn),
            Kind("silent", "Silent", R.drawable.bc_glyph_silent, null, BelleClassic::silentOn),
            Kind("flight", "Flight mode", R.drawable.bc_glyph_flight, null, ::flightOn),
            Kind("vpn", "VPN", 0, "VPN", ::vpnOn),
            Kind("3g", "3G", 0, "3G", ::threeGOn),
        )

        private const val TWO_G = TelephonyManager.NETWORK_TYPE_BITMASK_GSM or TelephonyManager.NETWORK_TYPE_BITMASK_GPRS or
            TelephonyManager.NETWORK_TYPE_BITMASK_EDGE

        /** Belle's 3G switch: on = 3G and faster networks allowed, off = 2G only (the user's choice). */
        fun threeGOn(ctx: Context): Boolean = runCatching {
            val t = ctx.getSystemService(TelephonyManager::class.java)
            t.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER) and TWO_G.inv() != 0L
        }.getOrDefault(true)

        private fun flip3g(ctx: Context) {
            runCatching {
                val t = ctx.getSystemService(TelephonyManager::class.java)
                t.setAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    if (threeGOn(ctx)) TWO_G.toLong() else t.supportedRadioAccessFamily)
            }
        }

        fun flightOn(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

        fun vpnOn(ctx: Context): Boolean = runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        }.getOrDefault(false)

        private fun flipFlight(ctx: Context) {
            runCatching { ctx.getSystemService(ConnectivityManager::class.java).setAirplaneMode(!flightOn(ctx)) }
        }

        fun kind(ctx: Context, id: Int) = BelleClassic2.prefs(ctx).getString("sw$id", null)?.let { k -> KINDS.firstOrNull { it.what == k } }

        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            BelleWidgets.ids(ctx, BelleSwitch1Widget::class.java).forEach { update(ctx, awm, it) }
        }

        fun update(ctx: Context, awm: AppWidgetManager, id: Int) {
            val v = RemoteViews(ctx.packageName, R.layout.bc_switch1)
            val k = kind(ctx, id) ?: KINDS[0]
            val on = k.on(ctx)
            v.setInt(R.id.bc_face, "setBackgroundResource", if (on) R.drawable.bc_sw_on else R.drawable.bc_sw_off)
            if (k.word != null) {
                v.setViewVisibility(R.id.bc_word, View.VISIBLE); v.setViewVisibility(R.id.bc_glyph, View.GONE)
                v.setTextViewText(R.id.bc_word, k.word)
                v.setTextColor(R.id.bc_word, if (on) BelleClassic2.CAP_ON else BelleClassic2.CAP_OFF)
            } else {
                v.setViewVisibility(R.id.bc_word, View.GONE); v.setViewVisibility(R.id.bc_glyph, View.VISIBLE)
                v.setImageViewResource(R.id.bc_glyph, k.glyph)
                // Belle: lit blue when on, embossed grey when off.
                v.setInt(R.id.bc_glyph, "setColorFilter", if (on) BelleClassic2.CAP_ON else BelleClassic2.CAP_OFF)
            }
            // the glyph rides on the cap: up when on, pressed down when off
            BelleClassic2.capGlyph(v, intArrayOf(R.id.bc_glyph, R.id.bc_word), on, 82f)
            v.setContentDescription(R.id.bc_root, "${k.label} ${if (on) "on" else "off"}")
            v.setOnClickPendingIntent(R.id.bc_root, BelleClassic.broadcast(ctx, BelleSwitch1Widget::class.java, k.what, 7000 + id))
            awm.updateAppWidget(id, v)
        }
    }
}

/** Picks what a [BelleSwitch1Widget] switches. */
class BelleSwitchConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            setPadding(dp(16f), dp(40f), dp(16f), dp(16f))
            addView(TextView(this@BelleSwitchConfigActivity).apply {
                text = "Switch"; setTextColor(N9.TEXT); textSize = 26f; typeface = N9.FONT_LIGHT
                setPadding(0, 0, 0, dp(12f))
            })
        }
        BelleSwitch1Widget.KINDS.forEach { k ->
            list.addView(TextView(this).apply {
                text = k.label; setTextColor(N9.TEXT); textSize = 19f; typeface = N9.FONT
                gravity = Gravity.CENTER_VERTICAL; minHeight = dp(56f)
                setOnClickListener {
                    BelleClassic2.prefs(this@BelleSwitchConfigActivity).edit().putString("sw$id", k.what).apply()
                    BelleSwitch1Widget.update(this@BelleSwitchConfigActivity, AppWidgetManager.getInstance(this@BelleSwitchConfigActivity), id)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }
            })
        }
        setContentView(list)
    }
}

// ---- data tracker ----

/** Mobile data used this month, inside the Belle blue ring (full ring at the warning level, else 1 GB). */
class BelleDataUsageWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    companion object {
        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleDataUsageWidget::class.java)
            if (ids.isEmpty()) return
            Thread {
                val used = monthBytes(ctx)
                val mb = used / 1_000_000.0
                val v = RemoteViews(ctx.packageName, R.layout.bc_datausage)
                v.setImageViewBitmap(R.id.bc_ring, ring(ctx, min(1.0, mb / 1000.0).toFloat()))
                v.setTextViewText(R.id.bc_amount, if (mb < 1000) String.format("%.1f", mb) else String.format("%.1f", mb / 1000))
                v.setTextViewText(R.id.bc_unit, if (mb < 1000) "MB" else "GB")
                v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 60,
                    Intent(Settings.ACTION_DATA_USAGE_SETTINGS)))
                AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
            }.start()
        }

        private fun monthBytes(ctx: Context): Long = runCatching {
            val start = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            val b = ctx.getSystemService(NetworkStatsManager::class.java)
                .querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, System.currentTimeMillis())
            b.rxBytes + b.txBytes
        }.getOrDefault(0L)

        /** The 701's data meter: a squircle ringed by twelve #27AAE1 segments from 12 o'clock (the used share brighter), round a
         *  dark squircle that carries the amount. */
        private fun ring(ctx: Context, f: Float): Bitmap {
            // the 701's mobiletrackewidgeticon on its 80 unit face: ring band 8.5 deep, the inner squircle 62 across
            val px = ctx.dp(78f); val s = px.toFloat(); val u = s / 80f
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val outer = squircle(0f, 0f, s, 2.7)
            p.shader = LinearGradient(0f, 0f, 0f, s, 0xFF58595B.toInt(), 0xFF000000.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(outer, p); p.shader = null
            c.save(); c.clipPath(outer)
            val sweep = RectF(-s, -s, 2 * s, 2 * s)
            p.color = 0x4C27AAE1; c.drawArc(sweep, -90f, 360f, true, p)
            p.color = 0xB227AAE1.toInt(); c.drawArc(sweep, -90f, 360f * f.coerceIn(0f, 1f), true, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = u * 0.5f
            for (k in 0 until 12) {
                val a = Math.toRadians(k * 30.0); val dx = kotlin.math.sin(a).toFloat(); val dy = -kotlin.math.cos(a).toFloat()
                p.color = 0x26000000; c.drawLine(s / 2, s / 2, s / 2 + s * dx, s / 2 + s * dy, p)
                p.color = 0x26FFFFFF; c.drawLine(s / 2 + u * 0.5f, s / 2 + u * 0.5f, s / 2 + u * 0.5f + s * dx, s / 2 + u * 0.5f + s * dy, p)
            }
            c.restore()
            p.style = Paint.Style.FILL; p.color = 0xFF575756.toInt()
            c.drawPath(squircle(8.5f * u, 8.5f * u, 63f * u, 2.7), p)
            p.shader = LinearGradient(0f, 9f * u, 0f, 71f * u, 0xFF58595B.toInt(), 0xFF000000.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(squircle(9f * u, 9f * u, 62f * u, 2.7), p)
            return bmp
        }

        /** A squircle (|x|^n + |y|^n = 1) filling a size × size box at (x, y). */
        private fun squircle(x: Float, y: Float, size: Float, n: Double): android.graphics.Path {
            val path = android.graphics.Path()
            for (i in 0 until 96) {
                val t = i / 96.0 * 2 * Math.PI
                val cx = Math.cos(t); val sy = Math.sin(t)
                val px = x + size / 2 + size / 2 * (Math.signum(cx) * Math.pow(Math.abs(cx), 2 / n)).toFloat()
                val py = y + size / 2 + size / 2 * (Math.signum(sy) * Math.pow(Math.abs(sy), 2 / n)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); return path
        }
    }
}

// ---- Email ----

/**
 * Belle email widget: account and unread count in the blue header, then up to 20 mails (sender, time, subject) that scroll,
 * as the Belle Mail widget's list does; tapping a mail or the header opens the mail app.
 * Read from the mail app's notifications (CATEGORY_EMAIL, as EtesianMail posts them), so it needs the
 * launcher's notification access; tapping opens the mail app.
 */
class BelleMailWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    /** [tap] is the notification's own action: that mail in its app. */
    class Mail(val pkg: String, val from: String, val subject: String, val account: String?, val time: Long,
        val tap: android.app.PendingIntent?)

    /** The new mails (email notifications, newest first), the unread count and the app that opens them. */
    data class Inbox(val mails: List<Mail>, val unread: Int, val open: Intent?)

    companion object {
        const val MAIL_PKG = "com.bompotis.etesian.mail"

        fun inbox(ctx: Context): Inbox {
            val sbns = runCatching { EventsStore.service?.activeNotifications }.getOrNull().orEmpty()
                .filter { it.notification.category == android.app.Notification.CATEGORY_EMAIL }
            val (sums, singles) = sbns.partition { it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0 }
            val mails = singles.map {
                val e = it.notification.extras
                Mail(it.packageName, e.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    e.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.lineSequence()?.firstOrNull().orEmpty(),
                    e.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString(), it.postTime, it.notification.contentIntent)
            }.sortedByDescending { it.time }
            mails.firstOrNull()?.account?.takeIf { it.isNotBlank() }?.let { BelleClassic2.prefs(ctx).edit().putString("mail_account", it).apply() }
            val pkg = mails.firstOrNull()?.pkg ?: MAIL_PKG
            val open = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: ctx.packageManager.getLaunchIntentForPackage(MAIL_PKG)
            return Inbox(mails, maxOf(mails.size, sums.sumOf { it.notification.number }), open)
        }

        fun account(ctx: Context) = BelleClassic2.prefs(ctx).getString("mail_account", null) ?: "Mail"

        fun time(ctx: Context, t: Long): CharSequence =
            if (DateUtils.isToday(t)) DateFormat.getTimeFormat(ctx).format(java.util.Date(t)) else DateFormat.format("d MMM", t)

        fun updateAll(ctx: Context) {
            BelleStackMailWidget.updateAll(ctx)
            val ids = BelleWidgets.ids(ctx, BelleMailWidget::class.java)
            if (ids.isEmpty()) return
            val (mails, unread, open) = inbox(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.bc_mail)
            v.setTextViewText(R.id.bc_account, account(ctx))
            v.setViewVisibility(R.id.bc_count, if (unread > 0) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.bc_glyph, if (unread > 0) View.GONE else View.VISIBLE)
            v.setTextViewText(R.id.bc_count, if (unread > 99) "99+" else unread.toString())
            v.setTextViewText(R.id.bc_empty, if (EventsStore.isEnabled(ctx)) "No new mail" else "Allow Etesian events notification access")
            val rows = mails.take(BelleWidgets.LIST_MAX).map { m ->
                RemoteViews(ctx.packageName, R.layout.bc_mail_item).apply {
                    setTextViewText(R.id.bc_title, m.from)
                    setTextViewText(R.id.bc_sub, m.subject)
                    setTextViewText(R.id.bc_time, time(ctx, m.time))
                    setOnClickFillInIntent(R.id.bc_item, Intent())
                }
            }
            if (open != null) BelleWidgets.list(ctx, v, R.id.bc_items, R.id.bc_empty, rows, 67, open)
            if (open != null) v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 66, open))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}

/** Belle's Latest mail (stackemail): one new mail at a time, newest first; ▲ ▼ step through them, a tap opens that mail. */
class BelleStackMailWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            NEWER -> shown(ctx, shown(ctx) - 1)
            OLDER -> shown(ctx, shown(ctx) + 1)
            else -> { super.onReceive(ctx, intent); return }
        }
        updateAll(ctx)
    }

    companion object {
        private const val NEWER = "com.bompotis.etesian.launcher.MAIL_NEWER"
        private const val OLDER = "com.bompotis.etesian.launcher.MAIL_OLDER"
        private fun shown(ctx: Context) = BelleClassic2.prefs(ctx).getInt("stackmail_shown", 0)
        private fun shown(ctx: Context, i: Int) = BelleClassic2.prefs(ctx).edit().putInt("stackmail_shown", i.coerceAtLeast(0)).apply()

        private fun pi(ctx: Context, action: String, code: Int) = android.app.PendingIntent.getBroadcast(ctx, code,
            Intent(ctx, BelleStackMailWidget::class.java).setAction(action),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleStackMailWidget::class.java)
            if (ids.isEmpty()) return
            val box = BelleMailWidget.inbox(ctx)
            val n = box.mails.size
            // a newer mail than the one last seen on top brings the widget back to it
            val p = BelleClassic2.prefs(ctx)
            val newest = box.mails.firstOrNull()?.time ?: 0L
            if (newest != p.getLong("stackmail_newest", 0L)) { p.edit().putLong("stackmail_newest", newest).apply(); shown(ctx, 0) }
            val i = shown(ctx).coerceAtMost((n - 1).coerceAtLeast(0))
            val m = box.mails.getOrNull(i)
            val v = RemoteViews(ctx.packageName, R.layout.bc_stackmail)
            v.setTextViewText(R.id.bc_account, BelleMailWidget.account(ctx))
            v.setViewVisibility(R.id.bc_count, if (box.unread > 0) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.bc_glyph, if (box.unread > 0) View.GONE else View.VISIBLE)
            v.setTextViewText(R.id.bc_count, if (box.unread > 99) "99+" else box.unread.toString())
            v.setViewVisibility(R.id.bc_item, if (m != null) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.bc_empty, if (m == null) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.bc_empty, if (EventsStore.isEnabled(ctx)) "No new mail" else "Allow Etesian events notification access")
            if (m != null) {
                v.setTextViewText(R.id.bc_title, m.from)
                v.setTextViewText(R.id.bc_sub, m.subject)
                v.setTextViewText(R.id.bc_time, BelleMailWidget.time(ctx, m.time))
            }
            v.setTextViewText(R.id.bc_position, if (n > 0) "${i + 1} of $n" else "")
            v.setInt(R.id.bc_up, "setColorFilter", if (i > 0) 0xFFFFFFFF.toInt() else 0xFF6A6B70.toInt())
            v.setInt(R.id.bc_down, "setColorFilter", if (i < n - 1) 0xFFFFFFFF.toInt() else 0xFF6A6B70.toInt())
            v.setOnClickPendingIntent(R.id.bc_up, pi(ctx, NEWER, 71))
            v.setOnClickPendingIntent(R.id.bc_down, pi(ctx, OLDER, 72))
            // the mail itself (its notification's action), else the mail app
            (m?.tap ?: box.open?.let { BelleWidgets.activity(ctx, 73, it) })?.let { v.setOnClickPendingIntent(R.id.bc_body, it) }
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}

// ---- Notes ----

/** Belle Notes on the home screen: browse with ▲ ▼, + writes a new one, 🗑 deletes the one shown. */
class BelleNotesWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        val notes = notes(ctx)
        when (intent.action) {
            UP -> shown(ctx, (shown(ctx) - 1).coerceAtLeast(0))
            DOWN -> shown(ctx, (shown(ctx) + 1).coerceAtMost((notes.length() - 1).coerceAtLeast(0)))
            DELETE -> if (notes.length() > 0) {
                notes.remove(shown(ctx).coerceAtMost(notes.length() - 1)); save(ctx, notes)
                shown(ctx, shown(ctx).coerceAtMost((notes.length() - 1).coerceAtLeast(0)))
            }
            else -> { super.onReceive(ctx, intent); return }
        }
        updateAll(ctx)
    }

    companion object {
        private const val UP = "com.bompotis.etesian.launcher.NOTE_UP"
        private const val DOWN = "com.bompotis.etesian.launcher.NOTE_DOWN"
        private const val DELETE = "com.bompotis.etesian.launcher.NOTE_DELETE"

        fun notes(ctx: Context) = runCatching { JSONArray(BelleClassic2.prefs(ctx).getString("notes", "[]")) }.getOrDefault(JSONArray())
        fun save(ctx: Context, a: JSONArray) = BelleClassic2.prefs(ctx).edit().putString("notes", a.toString()).apply()
        private fun shown(ctx: Context) = BelleClassic2.prefs(ctx).getInt("note_shown", 0)
        private fun shown(ctx: Context, i: Int) = BelleClassic2.prefs(ctx).edit().putInt("note_shown", i).apply()

        fun add(ctx: Context, text: String) {
            val a = notes(ctx)
            val list = (0 until a.length()).map { a.getJSONObject(it) }.toMutableList()
            list.add(0, JSONObject().put("text", text).put("time", System.currentTimeMillis()))
            save(ctx, JSONArray(list)); shown(ctx, 0); updateAll(ctx)
        }

        private fun pi(ctx: Context, action: String, code: Int) = android.app.PendingIntent.getBroadcast(ctx, code,
            Intent(ctx, BelleNotesWidget::class.java).setAction(action),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleNotesWidget::class.java)
            if (ids.isEmpty()) return
            val a = notes(ctx)
            val i = shown(ctx).coerceAtMost((a.length() - 1).coerceAtLeast(0))
            val n = if (a.length() > 0) a.getJSONObject(i) else null
            val v = RemoteViews(ctx.packageName, R.layout.bc_notes)
            v.setTextViewText(R.id.bc_text, n?.optString("text") ?: "Tap + to write a note.")
            v.setTextViewText(R.id.bc_when, n?.optLong("time")?.let { t ->
                (if (DateUtils.isToday(t)) "Today " else DateFormat.getDateFormat(ctx).format(java.util.Date(t)) + " ") +
                    DateFormat.getTimeFormat(ctx).format(java.util.Date(t))
            } ?: "")
            v.setInt(R.id.bc_up, "setColorFilter", if (i > 0) 0xFFFFFFFF.toInt() else 0xFF6A6B70.toInt())
            v.setInt(R.id.bc_down, "setColorFilter", if (i < a.length() - 1) 0xFFFFFFFF.toInt() else 0xFF6A6B70.toInt())
            v.setOnClickPendingIntent(R.id.bc_up, pi(ctx, UP, 61))
            v.setOnClickPendingIntent(R.id.bc_down, pi(ctx, DOWN, 62))
            v.setOnClickPendingIntent(R.id.bc_delete, pi(ctx, DELETE, 63))
            v.setOnClickPendingIntent(R.id.bc_add, BelleWidgets.activity(ctx, 64, Intent(ctx, BelleNoteEditActivity::class.java)))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}

/** Writes a new note for [BelleNotesWidget]: a yellow page with the keyboard up. */
class BelleNoteEditActivity : Activity() {
    private lateinit var edit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edit = EditText(this).apply {
            hint = "Note"; textSize = 19f; typeface = N9.FONT
            setTextColor(0xFF3A3A3A.toInt()); setHintTextColor(0xFF9A8F5A.toInt())
            gravity = Gravity.TOP; background = getDrawable(R.drawable.bc_note_paper)
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        }
        val done = TextView(this).apply {
            text = "Save"; setTextColor(N9.TEXT); textSize = 18f; typeface = N9.FONT_BOLD; gravity = Gravity.CENTER
            background = N9.button(this@BelleNoteEditActivity)
            setOnClickListener { finish() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            setPadding(dp(12f), dp(40f), dp(12f), dp(12f))
            // Save above the page, so the keyboard never covers it.
            addView(done, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply { bottomMargin = dp(10f) })
            addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        edit.requestFocus()
    }

    /** Leaving the page in any way (Save, Back, Home) keeps a non-empty note, as Belle's Notes saves as
     *  you go, and closes the page so the next + starts a blank one. */
    override fun onPause() {
        super.onPause()
        val t = edit.text.toString().trim()
        if (t.isNotEmpty()) BelleNotesWidget.add(this, t)
        edit.text.clear()
        if (!isFinishing) finish()
    }
}

// ---- big calendar ----

/** The big calendar card: one day's events, ◀ ▶ to change day, "+ Create new event". */
class BelleAgendaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            PREV, NEXT -> {
                val p = BelleClassic2.prefs(ctx)
                p.edit().putInt("agenda_day", p.getInt("agenda_day", 0) + if (intent.action == NEXT) 1 else -1).apply()
                updateAll(ctx)
            }
            Intent.ACTION_PROVIDER_CHANGED -> updateAll(ctx)
            else -> super.onReceive(ctx, intent)
        }
    }

    private class Event(val id: Long, val title: String, val begin: Long, val organiser: String?)

    companion object {
        private const val PREV = "com.bompotis.etesian.launcher.AGENDA_PREV"
        private const val NEXT = "com.bompotis.etesian.launcher.AGENDA_NEXT"
        private fun pi(ctx: Context, action: String, code: Int) = android.app.PendingIntent.getBroadcast(ctx, code,
            Intent(ctx, BelleAgendaWidget::class.java).setAction(action),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleAgendaWidget::class.java)
            if (ids.isEmpty()) return
            val day = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, BelleClassic2.prefs(ctx).getInt("agenda_day", 0))
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val events = events(ctx, day, day + DateUtils.DAY_IN_MILLIS)
            val v = RemoteViews(ctx.packageName, R.layout.bc_agenda)
            v.setTextViewText(R.id.bc_day, DateFormat.format("EEEE | d MMM", day))
            // every event of the day in a scrolling list; a tap opens that event
            val rows = events.map { e ->
                RemoteViews(ctx.packageName, R.layout.bc_agenda_item).apply {
                    setTextViewText(R.id.bc_title, e.title)
                    setTextViewText(R.id.bc_sub, e.organiser?.let { "@$it" } ?: DateFormat.getTimeFormat(ctx).format(java.util.Date(e.begin)))
                    setOnClickFillInIntent(R.id.bc_item, Intent().setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin))
                }
            }
            // a mutable tap template must name its app (Android 14+): the one that views calendar events
            val calendarApp = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, 1)), 0)?.activityInfo?.packageName
            if (calendarApp != null) BelleWidgets.list(ctx, v, R.id.bc_items, R.id.bc_empty, rows, 70, Intent(Intent.ACTION_VIEW).setPackage(calendarApp))
            v.setOnClickPendingIntent(R.id.bc_prev, pi(ctx, PREV, 73))
            v.setOnClickPendingIntent(R.id.bc_next, pi(ctx, NEXT, 74))
            v.setOnClickPendingIntent(R.id.bc_create, BelleWidgets.activity(ctx, 75,
                Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, day + 9 * DateUtils.HOUR_IN_MILLIS)))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }

        private fun events(ctx: Context, from: Long, to: Long): List<Event> = runCatching {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, from); ContentUris.appendId(it, to)
            }.build()
            val cols = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.ORGANIZER)
            ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext() && size < BelleWidgets.LIST_MAX) add(Event(c.getLong(0), c.getString(1).orEmpty().ifEmpty { "(No title)" },
                        c.getLong(2), c.getString(3)?.substringBefore('@')?.takeIf { it.isNotBlank() }))
                }
            }
        }.getOrNull().orEmpty()
    }
}

// ---- large clocks ----

/** The large dark squircle analog clock (belle-ref10.jpg). */
class BelleBigClockWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)
    override fun onAppWidgetOptionsChanged(ctx: Context, awm: AppWidgetManager, id: Int, o: Bundle) = updateAll(ctx)

    companion object {
        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            for (id in BelleWidgets.ids(ctx, BelleBigClockWidget::class.java)) {
                val v = RemoteViews(ctx.packageName, R.layout.bc_bigclock)
                BelleClassic2.fitFace(awm, id, v, BelleClassic2.SQUIRCLE, day = true)
                v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 80, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)))
                awm.updateAppWidget(id, v)
            }
        }
    }
}

/** The gear analog clock (belle-ref11.jpg): the same hands over a dial of dark gears. */
class BelleGearClockWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)
    override fun onAppWidgetOptionsChanged(ctx: Context, awm: AppWidgetManager, id: Int, o: Bundle) = updateAll(ctx)

    companion object {
        private var dial: Bitmap? = null

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleGearClockWidget::class.java)
            if (ids.isEmpty()) return
            val awm = AppWidgetManager.getInstance(ctx)
            // As large as the hands (400dp), so the face fills the widget.
            val d = dial ?: gears(ctx.dp(400f)).also { dial = it }
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.bc_gearclock)
                v.setIcon(R.id.bc_clock, "setDial", Icon.createWithBitmap(d))
                BelleClassic2.fitFace(awm, id, v, BelleClassic2.GEARS, day = true)
                v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 81, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)))
                awm.updateAppWidget(id, v)
            }
        }

        /** A round dark dial with three gears behind and the 12 marks round its edge. */
        private fun gears(px: Int): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); val s = px.toFloat(); val m = s / 2
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            // the lighter metal rim carrying the twelve ticks, then the dark face (belle-ref11)
            p.shader = LinearGradient(0f, 0f, 0f, s, intArrayOf(0xFF6A6B70.toInt(), 0xFF3A3B3F.toInt(), 0xFF202124.toInt()), null, Shader.TileMode.CLAMP)
            c.drawCircle(m, m, m, p)
            val face = m * 53f / 63f
            p.shader = RadialGradient(m, m * 0.8f, face, 0xFF3A3B3F.toInt(), 0xFF121214.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(m, m, face, p)
            p.shader = null
            c.save(); c.clipPath(Path().apply { addCircle(m, m, face, Path.Direction.CW) })
            fun gear(cx: Float, cy: Float, r: Float, teeth: Int, color: Int) {
                val path = Path()
                for (i in 0 until teeth * 2) {
                    val a = Math.PI * i / teeth
                    val rr = if (i % 2 == 0) r else r * 0.86f
                    val x = cx + rr * cos(a).toFloat(); val y = cy + rr * sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                p.color = color; c.drawPath(path, p)
                p.color = 0xFF1A1B1E.toInt(); c.drawCircle(cx, cy, r * 0.55f, p)
                p.color = color; c.drawCircle(cx, cy, r * 0.18f, p)
            }
            gear(m * 1.32f, m * 0.62f, m * 0.42f, 14, 0xFF2E2F33.toInt())
            gear(m * 0.62f, m * 1.32f, m * 0.38f, 12, 0xFF2A2B2F.toInt())
            gear(m, m, m * 0.36f, 12, 0xFF37383C.toInt())
            gear(m * 1.38f, m * 1.3f, m * 0.22f, 9, 0xFF323337.toInt())
            c.restore()
            p.color = 0xFFC8C9CD.toInt(); p.strokeWidth = s * 0.011f; p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 12) {
                val a = Math.PI * i / 6
                val r1 = m * 0.945f; val r2 = m * (if (i % 3 == 0) 0.86f else 0.88f)
                c.drawLine(m + r1 * cos(a).toFloat(), m + r1 * sin(a).toFloat(), m + r2 * cos(a).toFloat(), m + r2 * sin(a).toFloat(), p)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.008f; p.color = 0xFF050506.toInt()
            c.drawCircle(m, m, m - p.strokeWidth, p)
            return bmp
        }
    }
}

/** The LCD digital clock (belle-ref10.jpg); TextClocks, no updates needed. */
class BelleLcdClockWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        val v = RemoteViews(ctx.packageName, R.layout.bc_lcdclock)
        v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 82, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)))
        awm.updateAppWidget(ids, v)
    }
}

/** The word clock (belle-ref11.jpg): "Ten" / "Forty Seven" / the date. */
class BelleWordClockWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    companion object {
        private val ONES = listOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
        private val TENS = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty")

        fun words(n: Int): String = when {
            n < 20 -> ONES[n]
            else -> listOf(TENS[n / 10], ONES[n % 10]).filter { it.isNotEmpty() }.joinToString(" ")
        }

        /** As the reference: the 12-hour hour, then the minutes ("O'Clock" on the hour, "Oh Five"). */
        fun minuteWords(m: Int) = when {
            m == 0 -> "O'Clock"
            m < 10 -> "Oh ${ONES[m]}"
            else -> words(m)
        }

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleWordClockWidget::class.java)
            if (ids.isEmpty()) return
            val now = Calendar.getInstance()
            val h = now.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val v = RemoteViews(ctx.packageName, R.layout.bc_wordclock)
            v.setTextViewText(R.id.bc_hour, words(h))
            v.setTextViewText(R.id.bc_minute, minuteWords(now.get(Calendar.MINUTE)))
            v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 83, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}

// ---- website shortcuts ----

/** A website shortcut (belle-ref6.jpg): the page's thumbnail, its title on the dark strip. */
class BelleWebWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, awm, it) }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = BelleClassic2.prefs(ctx).edit()
        ids.forEach { e.remove("web$it"); e.remove("webt$it"); thumb(ctx, it).delete() }
        e.apply()
    }

    companion object {
        fun thumb(ctx: Context, id: Int) = File(File(ctx.filesDir, "web").apply { mkdirs() }, "$id.png")

        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            BelleWidgets.ids(ctx, BelleWebWidget::class.java).forEach { update(ctx, awm, it) }
        }

        fun update(ctx: Context, awm: AppWidgetManager, id: Int) {
            val p = BelleClassic2.prefs(ctx)
            val url = p.getString("web$id", null)
            val v = RemoteViews(ctx.packageName, R.layout.bc_contact)
            if (url == null) {
                v.setTextViewText(R.id.bc_name, "Add website")
                v.setImageViewResource(R.id.bc_photo, R.drawable.belle_add)
                v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 8000 + id,
                    Intent(ctx, BelleWebConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .setData(Uri.parse("belle://web/$id"))))
            } else {
                v.setTextViewText(R.id.bc_name, p.getString("webt$id", null) ?: Uri.parse(url).host ?: url)
                BitmapFactory.decodeFile(thumb(ctx, id).path)?.let { v.setImageViewBitmap(R.id.bc_photo, it) }
                    ?: v.setImageViewResource(R.id.bc_photo, R.drawable.belle_add)
                v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 8000 + id, Intent(Intent.ACTION_VIEW, Uri.parse(url))))
            }
            awm.updateAppWidget(id, v)
        }
    }
}

/** Asks for the address, loads the page off screen and keeps a thumbnail of its top. */
class BelleWebConfigActivity : Activity() {
    private var id = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val address = EditText(this).apply {
            hint = "Web address"; setTextColor(N9.TEXT); setHintTextColor(N9.TEXT_SECONDARY); textSize = 18f
            isSingleLine = true; inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val status = TextView(this).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 14f }
        val web = WebView(this)
        val add = TextView(this).apply {
            text = "Add"; setTextColor(N9.TEXT); textSize = 18f; typeface = N9.FONT_BOLD; gravity = Gravity.CENTER
            background = N9.button(this@BelleWebConfigActivity)
        }
        add.setOnClickListener {
            var url = address.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            if (!url.contains("://")) url = "https://$url"
            status.text = "Loading…"
            web.settings.javaScriptEnabled = true
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, u: String) {
                    // Let it paint, then keep the top of the page at the card's proportions.
                    view.postDelayed({ capture(view, url, view.title) }, 1200)
                }
            }
            web.loadUrl(url)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            setPadding(dp(16f), dp(40f), dp(16f), dp(16f))
            addView(TextView(this@BelleWebConfigActivity).apply { text = "Website"; setTextColor(N9.TEXT); textSize = 26f; typeface = N9.FONT_LIGHT })
            addView(address)
            addView(add, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply { topMargin = dp(12f) })
            addView(status)
            // The page loads here at phone width; it is only read back for the thumbnail.
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12f) })
        })
    }

    private fun capture(view: WebView, url: String, title: String?) {
        val w = view.width; val h = min(view.height, w)
        if (w <= 0 || h <= 0) return
        val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(full))
        val out = Bitmap.createScaledBitmap(full, dp(200f), dp(200f) * h / w, true)
        BelleWebWidget.thumb(this, id).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        BelleClassic2.prefs(this).edit().putString("web$id", url).putString("webt$id", title?.takeIf { it.isNotBlank() }).apply()
        BelleWebWidget.update(this, AppWidgetManager.getInstance(this), id)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}

// ---- weather ----

/** Belle weather (belle-ref6.jpg): now, today's high/low, the place, the next two days. */
class BelleWeatherClassicWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        val app = ctx.applicationContext
        updateAll(app)
        Weather.refresh(app) { updateAll(app) }
    }

    companion object {
        private fun icon(ctx: Context, code: Int, day: Boolean, dp: Float): Bitmap {
            val px = ctx.dp(dp)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            WeatherIcon(code, day).apply { setBounds(0, 0, px, px) }.draw(Canvas(bmp))
            return bmp
        }

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleWeatherClassicWidget::class.java)
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.bc_weather)
            val now = Weather.current
            v.setTextViewText(R.id.bc_temp, now?.let { "${it.temperature}°" } ?: "--°")
            v.setTextViewText(R.id.bc_place, now?.place ?: if (Prefs.weatherEnabled(ctx)) "Updating…" else "Weather off")
            v.setImageViewBitmap(R.id.bc_icon, icon(ctx, now?.code ?: 3, now?.day ?: true, 84f))
            val days = now?.days.orEmpty()
            v.setTextViewText(R.id.bc_hilo, days.firstOrNull()?.let { "${it.high}°/${it.low}°" } ?: "")
            listOf(Triple(R.id.bc_d1_icon, R.id.bc_d1_hilo, R.id.bc_d1_day), Triple(R.id.bc_d2_icon, R.id.bc_d2_hilo, R.id.bc_d2_day))
                .forEachIndexed { i, (ic, hl, dy) ->
                    val d = days.getOrNull(i + 1)
                    if (d == null) { v.setTextViewText(hl, ""); v.setTextViewText(dy, ""); return@forEachIndexed }
                    v.setImageViewBitmap(ic, icon(ctx, d.code, true, 40f))
                    v.setTextViewText(hl, "${d.high}°/${d.low}°")
                    v.setTextViewText(dy, DateFormat.format("EEE", d.time))
                }
            v.setOnClickPendingIntent(R.id.bc_root, BelleWidgets.activity(ctx, 84, BelleWidgets.eventsPage(ctx)))
            v.setOnClickPendingIntent(R.id.bc_place, BelleWidgets.activity(ctx, 85, Intent(ctx, WeatherPlaceActivity::class.java)))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}

// ---- RSS per site ----

/** One site's feed (belle-ref8.jpg): its newest posts (up to 20, scrolling, as rsswidget3) under the orange header. */
class BelleRssWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        BelleWidgets.init(ctx)
        updateAll(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == REFRESH) Feeds.refresh(ctx.applicationContext, force = true) { updateAll(ctx.applicationContext) }
        else super.onReceive(ctx, intent)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = BelleClassic2.prefs(ctx).edit(); ids.forEach { e.remove("rss$it") }; e.apply()
    }

    companion object {
        private const val REFRESH = "com.bompotis.etesian.launcher.RSS_REFRESH"

        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            BelleWidgets.ids(ctx, BelleRssWidget::class.java).forEach { update(ctx, awm, it) }
        }

        fun update(ctx: Context, awm: AppWidgetManager, id: Int) {
            val url = BelleClassic2.prefs(ctx).getString("rss$id", null)
            val feed = Feeds.feeds(ctx).firstOrNull { it.url == url }
            val items = Feeds.items(ctx).filter { it.feedUrl == url }.sortedByDescending { it.time }.take(BelleWidgets.LIST_MAX)
            val v = RemoteViews(ctx.packageName, R.layout.bc_rss)
            v.setTextViewText(R.id.bc_site, feed?.title ?: items.firstOrNull()?.feedTitle ?: "Feed")
            v.setTextViewText(R.id.bc_time, items.firstOrNull()?.time?.takeIf { it > 0 }?.let {
                if (DateUtils.isToday(it)) DateFormat.getTimeFormat(ctx).format(java.util.Date(it))
                else DateFormat.format("d MMM", it).toString()
            } ?: "")
            val rows = items.map { item ->
                RemoteViews(ctx.packageName, R.layout.bc_rss_item).apply {
                    setTextViewText(R.id.bc_title, item.title)
                    setOnClickFillInIntent(R.id.bc_item, Intent().putExtra("feed_item", item.id).putExtra("link", item.link))
                }
            }
            BelleWidgets.list(ctx, v, R.id.bc_items, R.id.bc_empty, rows, 9000 + id * 8, Feeds.openItem("", ""))
            v.setOnClickPendingIntent(R.id.bc_header, BelleWidgets.activity(ctx, 9000 + id * 8 + 5, Feeds.openApp()))
            v.setOnClickPendingIntent(R.id.bc_refresh, android.app.PendingIntent.getBroadcast(ctx, 9000 + id * 8 + 6,
                Intent(ctx, BelleRssWidget::class.java).setAction(REFRESH),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            awm.updateAppWidget(id, v)
        }
    }
}

/** Picks the site a [BelleRssWidget] follows, from the Feeds app's subscriptions. */
class BelleRssConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var list: LinearLayout
    private val listener: () -> Unit = { fill() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            setPadding(dp(16f), dp(40f), dp(16f), dp(16f))
        }
        setContentView(android.widget.ScrollView(this).apply { setBackgroundColor(N9.BACKGROUND); addView(list) })
        Feeds.addListener(listener)
        fill()
    }

    override fun onDestroy() { Feeds.removeListener(listener); super.onDestroy() }

    private fun fill() {
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = "Feed"; setTextColor(N9.TEXT); textSize = 26f; typeface = N9.FONT_LIGHT; setPadding(0, 0, 0, dp(12f))
        })
        val feeds = Feeds.feeds(this)
        if (feeds.isEmpty()) list.addView(TextView(this).apply {
            text = "No feeds yet. Add one in Feeds."; setTextColor(N9.TEXT_SECONDARY); textSize = 17f; typeface = N9.FONT
            minHeight = dp(56f); gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { startActivity(Feeds.openApp()) }
        })
        feeds.forEach { f ->
            list.addView(TextView(this).apply {
                text = f.title; setTextColor(N9.TEXT); textSize = 19f; typeface = N9.FONT
                gravity = Gravity.CENTER_VERTICAL; minHeight = dp(56f)
                setOnClickListener {
                    BelleClassic2.prefs(this@BelleRssConfigActivity).edit().putString("rss$widgetId", f.url).apply()
                    BelleRssWidget.update(this@BelleRssConfigActivity, AppWidgetManager.getInstance(this@BelleRssConfigActivity), widgetId)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    finish()
                }
            })
        }
    }
}

// ---- FM radio ----

/** The FM Radio switch: follows the FM app's media session; power plays / pauses or opens the app. */
class BelleFmSwitchWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) { watch(ctx.applicationContext); updateAll(ctx) }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != BelleClassic.ACTION_TOGGLE) { super.onReceive(ctx, intent); return }
        val s = session(ctx)
        when {
            s == null -> ctx.packageManager.getLaunchIntentForPackage(FM_PACKAGES.firstOrNull { pkg ->
                ctx.packageManager.getLaunchIntentForPackage(pkg) != null } ?: return)
                ?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            playing(s) -> s.transportControls.pause()
            else -> s.transportControls.play()
        }
    }

    companion object {
        private val FM_PACKAGES = listOf("com.caf.fmradio", "com.android.fmradio")
        private var watching = false

        private fun session(ctx: Context): android.media.session.MediaController? = runCatching {
            ctx.getSystemService(android.media.session.MediaSessionManager::class.java)
                .getActiveSessions(android.content.ComponentName(ctx, EventsService::class.java))
                .firstOrNull { it.packageName in FM_PACKAGES }
        }.getOrNull()

        private fun playing(s: android.media.session.MediaController?) =
            s?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

        /** Session list changes and the FM session's own changes both redraw the switch. */
        fun watch(app: Context) {
            if (watching) return
            val msm = app.getSystemService(android.media.session.MediaSessionManager::class.java)
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            var current: android.media.session.MediaController? = null
            val cb = object : android.media.session.MediaController.Callback() {
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = updateAll(app)
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = updateAll(app)
            }
            fun follow() {
                current?.unregisterCallback(cb)
                current = session(app)?.also { it.registerCallback(cb, main) }
                updateAll(app)
            }
            runCatching {
                msm.addOnActiveSessionsChangedListener({ follow() }, android.content.ComponentName(app, EventsService::class.java), main)
                watching = true
                follow()
            }
        }

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleFmSwitchWidget::class.java)
            if (ids.isEmpty()) return
            val s = session(ctx)
            val on = playing(s)
            val meta = s?.metadata
            // The FM apps put the station or frequency in the title; "FM Radio" until one has played.
            val freq = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                ?: meta?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "FM Radio"
            val v = RemoteViews(ctx.packageName, R.layout.bc_fmswitch)
            v.setInt(R.id.bc_power, "setBackgroundResource", if (on) R.drawable.bc_power_on else R.drawable.bc_power_off)
            v.setTextViewText(R.id.bc_freq, freq)
            v.setTextColor(R.id.bc_freq, if (on) 0xFFFFFFFF.toInt() else 0xFF8E8F94.toInt())
            v.setContentDescription(R.id.bc_power, if (on) "FM radio on" else "FM radio off")
            v.setOnClickPendingIntent(R.id.bc_power, BelleClassic.broadcast(ctx, BelleFmSwitchWidget::class.java, "fm", 85))
            v.setOnClickPendingIntent(R.id.bc_freq, s?.sessionActivity ?: BelleWidgets.activity(ctx, 86,
                ctx.packageManager.getLaunchIntentForPackage(FM_PACKAGES[0]) ?: Intent(Settings.ACTION_SOUND_SETTINGS)))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v)
        }
    }
}
