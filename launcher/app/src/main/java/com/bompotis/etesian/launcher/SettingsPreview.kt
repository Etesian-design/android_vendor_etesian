package com.bompotis.etesian.launcher

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder

/**
 * The miniature at the top of a settings page: the part of the phone the page changes, drawn from
 * the current settings, redrawn as soon as one of them changes. [kind] is the page (lock, status,
 * events, dismissed, priority, open, apps, sharing, emergency).
 */
class SettingsPreview(ctx: Context, private val kind: String) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cr = ctx.contentResolver
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = invalidate()
    }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> invalidate() }

    init { contentDescription = "Preview: $kind" }

    override fun onMeasure(w: Int, h: Int) {
        val height = when (kind) { "status", "events", "dismissed", "apps" -> 236f; "open" -> 262f; else -> 172f }
        setMeasuredDimension(MeasureSpec.getSize(w), (height * d).toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cr.registerContentObserver(Settings.Secure.CONTENT_URI, true, observer)
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDetachedFromWindow() {
        cr.unregisterContentObserver(observer)
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDetachedFromWindow()
    }

    private fun on(name: String) = Extras.on(context, name)
    private fun secure(k: String) = Settings.Secure.getString(cr, k)

    // Layout: the miniature screen fills the width less the page gutter.
    private lateinit var screen: RectF
    private val x0 get() = screen.left
    private val y0 get() = screen.top
    private val w get() = screen.width()

    override fun onDraw(c: Canvas) {
        val g = 16 * d
        screen = RectF(g, 8 * d, width - g, height - 8 * d)
        p.style = Paint.Style.FILL; p.shader = null; p.color = 0xFF0A0A0A.toInt()
        c.drawRoundRect(screen, 10 * d, 10 * d, p)
        c.save(); c.clipRect(screen)
        when (kind) {
            "lock", "emergency" -> lock(c)
            "status" -> status(c)
            "events" -> events(c)
            "dismissed" -> dismissed(c)
            "priority" -> priority(c)
            "open" -> openApps(c)
            "apps" -> apps(c)
            "sharing" -> sharing(c)
        }
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = 0xFF393839.toInt(); p.shader = null
        c.drawRoundRect(screen, 10 * d, 10 * d, p)
        p.style = Paint.Style.FILL
    }

    // ---- drawing helpers ----

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = N9.TEXT,
                     bold: Boolean = false, light: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color; p.textSize = size * d; p.textAlign = align
        p.typeface = when { bold -> N9.FONT_BOLD; light -> N9.FONT_LIGHT; else -> N9.FONT }
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun squircle(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color
        c.drawPath(IconFactory.superellipse(size, 2.8).apply { offset(x, y) }, p)
    }

    /** A lit N9 tile: top light, as the icons are. */
    private fun tile(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        squircle(c, x, y, size, color)
        p.shader = LinearGradient(0f, y, 0f, y + size, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawPath(IconFactory.superellipse(size, 2.8).apply { offset(x, y) }, p)
        p.shader = null
    }

    /** A bubble with squircle-half ends (n = 2.8), [h] tall, from [left] to [right]. */
    private fun ends(c: Canvas, left: Float, top: Float, right: Float, h: Float, color: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color
        val path = IconFactory.superellipse(h, 2.8).apply { offset(left, top) }
        path.op(IconFactory.superellipse(h, 2.8).apply { offset(right - h, top) }, Path.Op.UNION)
        path.op(Path().apply { addRect(RectF(left + h / 2, top, right - h / 2, top + h), Path.Direction.CW) }, Path.Op.UNION)
        c.drawPath(path, p)
    }

    private fun bar(c: Canvas, x: Float, y: Float, bw: Float, bh: Float, color: Int, r: Float = 3f) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color
        c.drawRoundRect(RectF(x, y, x + bw, y + bh), r * d, r * d, p)
    }

    /** The status bar strip across the top of the miniature. */
    private fun statusBar(c: Canvas, privacy: Boolean) {
        text(c, "12:45", x0 + 10 * d, y0 + 14 * d, 10f, bold = true)
        if (privacy) squircle(c, x0 + w - 70 * d, y0 + 5 * d, 11 * d, 0xFF35B34A.toInt())
        bar(c, x0 + w - 50 * d, y0 + 7 * d, 16 * d, 7 * d, 0xFFBDBDBD.toInt(), 1.5f)
        bar(c, x0 + w - 28 * d, y0 + 7 * d, 18 * d, 7 * d, 0xFFBDBDBD.toInt(), 1.5f)
    }

    /** One Events notification card: squircle icon, bold title, a grey line. Returns its bottom. */
    private fun card(c: Canvas, y: Float, icon: Int, title: String, line: String, alpha: Int = 255, small: Boolean = false): Float {
        val h = (if (small) 22f else 40f) * d
        val a = alpha shl 24
        if (small) {
            squircle(c, x0 + 12 * d, y + 4 * d, 14 * d, (icon and 0xFFFFFF) or a)
            text(c, "$title  $line", x0 + 34 * d, y + 15 * d, 10f, (0xFFFFFF) or a)
        } else {
            squircle(c, x0 + 12 * d, y + 6 * d, 28 * d, (icon and 0xFFFFFF) or a)
            text(c, title, x0 + 48 * d, y + 18 * d, 11f, 0xFFFFFF or a, bold = true)
            text(c, line, x0 + 48 * d, y + 33 * d, 10f, 0x8C8C8C or a)
        }
        return y + h
    }

    private fun header(c: Canvas, s: String, y: Float) {
        text(c, s, x0 + 12 * d, y, 10f, 0xFF8C8C8C.toInt(), bold = true)
        bar(c, x0 + 12 * d + p.measureText(s) + 6 * d, y - 4 * d, w - p.measureText(s) - 30 * d, d, 0xFF3A3A3A.toInt(), 0f)
    }

    // ---- the pages ----

    private fun lock(c: Canvas) {
        statusBar(c, false)
        val emergency = on(Extras.EMERGENCY) || kind == "emergency"
        val cx = x0 + w / 2 + if (emergency) 40 * d else 0f
        text(c, "12:45", cx, y0 + 74 * d, 44f, light = true, align = Paint.Align.CENTER)
        val date = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(java.util.Date())
        var y = y0 + 94 * d
        text(c, date, cx, y, 11f, 0xFFBDBDBD.toInt(), align = Paint.Align.CENTER)
        if (on(Extras.LOCK_WEATHER)) { y += 16 * d; text(c, "☀ 18°  Partly cloudy", cx, y, 10f, 0xFFBDBDBD.toInt(), align = Paint.Align.CENTER) }
        if (on(Extras.GLANCE)) { y += 16 * d; text(c, "Alarm 07:00 · Design review 14:00", cx, y, 10f, 0xFFBDBDBD.toInt(), align = Paint.Align.CENTER) }
        if (emergency) {
            // Design A2: a red rule, "✚ owner", the chosen essentials, "Emergency info ›".
            val lx = x0 + 14 * d
            bar(c, lx, y0 + 40 * d, 2 * d, 70 * d, if (on(Extras.EMERGENCY)) 0xFFE0352B.toInt() else 0xFF5A2A28.toInt(), 0f)
            val show = (secure("etesian_emergency_show") ?: "name,blood,allergies").split(',')
            val labels = mapOf("blood" to "Blood", "allergies" to "Allergy", "medication" to "Meds", "notes" to "Notes", "donor" to "Donor")
            var ly = y0 + 52 * d
            val name = Emergency.get(context, "name").ifEmpty { "Owner" }
            text(c, "✚ " + if ("name" in show) name else "Owner", lx + 8 * d, ly, 10f, bold = true); ly += 14 * d
            show.filter { it != "name" }.take(3).forEach { k ->
                val v = Emergency.get(context, k).ifEmpty { "–" }
                text(c, "${labels[k] ?: k} $v", lx + 8 * d, ly, 9f, 0xFFBDBDBD.toInt()); ly += 12 * d
            }
            text(c, "Emergency info ›", lx + 8 * d, ly + 2 * d, 9f, 0xFFE0352B.toInt())
            if (!on(Extras.EMERGENCY)) text(c, "Off: not on the lock screen", x0 + w / 2, y0 + screen.height() - 10 * d, 9f, 0xFF8C8C8C.toInt(), align = Paint.Align.CENTER)
        }
        if (kind == "lock") card(c, y0 + screen.height() - 46 * d, 0xFF2A86D8.toInt(), "Anna", "See you at eight", 200)
    }

    private fun status(c: Canvas) {
        statusBar(c, on(Extras.PRIVACY))
        var y = y0 + 22 * d
        val custom = on(Extras.STATUS_MENU)
        if (on(Extras.MUSIC)) {
            tile(c, x0 + 12 * d, y, 30 * d, 0xFFE6007E.toInt())
            text(c, "Etesian Winds", x0 + 50 * d, y + 13 * d, 11f, bold = true)
            text(c, "⏮  ⏸  ⏭", x0 + 50 * d, y + 27 * d, 10f, 0xFFBDBDBD.toInt())
            y += 38 * d
        }
        text(c, "Profile", x0 + 12 * d, y + 12 * d, 10f, 0xFF8C8C8C.toInt())
        bar(c, x0 + 60 * d, y + 3 * d, w - 72 * d, 14 * d, 0xFF262626.toInt(), 7f)
        bar(c, x0 + 60 * d, y + 3 * d, (w - 72 * d) / 3, 14 * d, N9.ACCENT, 7f)
        y += 22 * d
        if (on(Extras.MODES)) {
            text(c, "Mode", x0 + 12 * d, y + 12 * d, 10f, 0xFF8C8C8C.toInt())
            bar(c, x0 + 60 * d, y + 3 * d, w - 72 * d, 14 * d, 0xFF262626.toInt(), 7f)
            text(c, "Off   Do not disturb   Sleep", x0 + 68 * d, y + 13 * d, 9f, 0xFFBDBDBD.toInt())
            y += 22 * d
        }
        if (custom) {
            val n = 6; val gap = 6 * d; val s = (w - 24 * d - gap * (n - 1)) / n
            listOf(0xFF2A86D8, 0xFF2A86D8, 0xFF3A3A3A, 0xFF2A86D8, 0xFF3A3A3A, 0xFF3A3A3A).forEachIndexed { i, col ->
                val t = minOf(s, 26 * d)
                tile(c, x0 + 12 * d + i * (s + gap) + (s - t) / 2, y, t, col.toInt())
            }
            y += 32 * d
        }
        val rows = listOf("Internet connection", "Bluetooth", "Mobile network", "Battery", "Screen rotation", "Location")
        val max = if (custom) StatusLayout.maxRows(context) else 0
        val shown = if (max == 0) rows else rows.take(max)
        shown.forEach { r ->
            if (y > y0 + screen.height() - 20 * d) return@forEach
            text(c, r, x0 + 12 * d, y + 13 * d, 11f, bold = true)
            bar(c, x0 + 12 * d, y + 19 * d, w - 24 * d, d, 0xFF262626.toInt(), 0f)
            y += 22 * d
        }
        if (shown.size < rows.size) {
            val how = when (StatusLayout.reveal(context)) { "button" -> "More ▾"; "swipe" -> "Swipe down for more"; else -> "More ▾ · or swipe down" }
            text(c, how, x0 + w / 2, y + 13 * d, 10f, 0xFF8C8C8C.toInt(), align = Paint.Align.CENTER)
        }
    }

    private fun events(c: Canvas) {
        text(c, "Events", x0 + 12 * d, y0 + 26 * d, 20f, light = true)
        var y = y0 + 44 * d
        if (on(Extras.TODAY)) {
            header(c, "Today", y); y += 6 * d
            val show = Today.shown(context)
            listOf("screen" to "Screen time 1 h 12 min", "allday" to "Name day: Kostas", "birthdays" to "Birthday: Maria",
                "upcoming" to "14:00 Design review").filter { it.first in show }.take(2).forEach {
                y = card(c, y, 0xFF8C8C8C.toInt(), "", it.second, small = true)
            }
            y += 6 * d
        }
        header(c, "Notifications", y); y += 4 * d
        if (on(Extras.PRIORITY)) {
            text(c, "★ Anna", x0 + 12 * d, y + 13 * d, 10f, 0xFFF0C419.toInt(), bold = true); y += 16 * d
        }
        y = card(c, y, 0xFF35B34A.toInt(), "Anna", "Are we still on for lunch?")
        if (on(Extras.INLINE_REPLY)) {
            bar(c, x0 + 48 * d, y, w - 100 * d, 16 * d, 0xFF262626.toInt(), 4f)
            text(c, "Reply", x0 + 54 * d, y + 11.5f * d, 9f, 0xFF8C8C8C.toInt()); y += 20 * d
        }
        if (on(Extras.ACTIONS)) {
            text(c, "Long-press: Mark as read · Snooze · Dismiss", x0 + 48 * d, y + 9 * d, 9f, 0xFF8C8C8C.toInt()); y += 14 * d
        }
        if (on(Extras.LIVE_UPDATES)) {
            y = card(c, y, 0xFF2A86D8.toInt(), "Downloading map", "62%")
            bar(c, x0 + 48 * d, y - 4 * d, w - 64 * d, 3 * d, 0xFF262626.toInt(), 1.5f)
            bar(c, x0 + 48 * d, y - 4 * d, (w - 64 * d) * .62f, 3 * d, N9.ACCENT, 1.5f)
            y += 4 * d
        }
        y = card(c, y, 0xFFE6007E.toInt(), "Mail", "Your order has shipped")
        if (on(Extras.GHOST_ROWS)) card(c, y, 0xFF8C8C8C.toInt(), "Calendar", "Dentist moved", 110, small = true)
        // Over an app (Over apps): the chosen style on a light app, and the "Copied" bubble.
        val bx = x0 + w * .52f; val bw = w * .45f
        bar(c, bx, y0 + 6 * d, bw, 80 * d, 0xFFF2F2F2.toInt(), 6f)
        bar(c, bx, y0 + 6 * d, bw, 9 * d, 0xFF000000.toInt(), 0f)
        text(c, "In an app", bx + 8 * d, y0 + 30 * d, 9f, 0xFF8C8C8C.toInt())
        val green = 0xFF35B34A.toInt()
        when (NotifOver.style(context)) {
            "n9" -> {   // one line over the status bar
                bar(c, bx, y0 + 6 * d, bw, 9 * d, 0xFF000000.toInt(), 0f)
                text(c, "Anna  Are we still on for lu", bx + 4 * d, y0 + 13.5f * d, 7f, bold = true)
            }
            "belle" -> {   // a strip across the top, over the status bar
                bar(c, bx, y0 + 6 * d, bw, 26 * d, NotifOver.shadeAlpha(context) shl 24, 0f)
                squircle(c, bx + 5 * d, y0 + 10 * d, 16 * d, green)
                text(c, "Anna", bx + 26 * d, y0 + 18 * d, 8f, bold = true)
                text(c, "Lunch?", bx + 26 * d, y0 + 28 * d, 8f)
            }
            "colour" -> {   // squircle-half ends in the chosen colour
                val col = when (val v = NotifOver.colour(context)) {
                    "app" -> green
                    "system" -> context.getColor(android.R.color.system_accent1_500)
                    else -> android.graphics.Color.parseColor(v)
                }
                ends(c, bx + 5 * d, y0 + 36 * d, bx + bw - 5 * d, 22 * d, col)
                text(c, "Anna: Lunch?", bx + 15 * d, y0 + 50.5f * d, 9f, bold = true)
            }
            "android" -> {   // Android's own pop-up
                bar(c, bx + 4 * d, y0 + 36 * d, bw - 8 * d, 30 * d, 0xFFFFFFFF.toInt(), 6f)
                text(c, "Anna: Lunch?", bx + 10 * d, y0 + 48 * d, 9f, 0xFF1D1B20.toInt(), bold = true)
                text(c, "Reply   Mark as read", bx + 10 * d, y0 + 61 * d, 8f, 0xFF6750A4.toInt(), bold = true)
            }
            else -> {   // the lock screen card under the status bar
                bar(c, bx + 6 * d, y0 + 36 * d, bw - 12 * d, 24 * d, 0xE0141414.toInt(), 5f)
                squircle(c, bx + 10 * d, y0 + 40 * d, 16 * d, green)
                text(c, "Anna: Lunch?", bx + 31 * d, y0 + 51.5f * d, 9f, bold = true)
            }
        }
        if (on(Extras.COPIED)) {
            ends(c, bx + bw / 2 - 30 * d, y0 + 66 * d, bx + bw / 2 + 30 * d, 16 * d, 0xE0141414.toInt())
            text(c, "Copied", bx + bw / 2, y0 + 77.5f * d, 9f, align = Paint.Align.CENTER)
        }
    }

    private fun dismissed(c: Canvas) {
        text(c, "Events", x0 + 12 * d, y0 + 26 * d, 20f, light = true)
        var y = y0 + 44 * d
        header(c, "Notifications", y); y += 4 * d
        y = card(c, y, 0xFF35B34A.toInt(), "Anna", "Are we still on for lunch?")
        if (!on(Extras.GHOST_ROWS)) {
            text(c, "Dismissed notifications are gone", x0 + w / 2, y + 40 * d, 10f, 0xFF8C8C8C.toInt(), align = Paint.Align.CENTER)
            return
        }
        // Whole rows fade gently (about 0.5 to 0.4), only the half row fades out, slipping under the next header.
        val big = Ghosts.big(context); val rows = Ghosts.rows(context)
        val full = rows.toInt(); val half = rows - full >= .5f
        val ghosts = listOf("Mail" to "Your order has shipped", "Calendar" to "Dentist moved", "Clock" to "Alarm dismissed")
        for (i in 0 until full) {
            val (t, l) = ghosts[i % ghosts.size]
            y = card(c, y, 0xFF8C8C8C.toInt(), t, l, (128 - i * 12).coerceAtLeast(100), small = !big)
        }
        if (half) {
            val (t, l) = ghosts[full % ghosts.size]
            val h = (if (big) 40f else 22f) * d
            c.save(); c.clipRect(x0, y, x0 + w, y + h / 2)
            card(c, y, 0xFF8C8C8C.toInt(), t, l, 40, small = !big)
            c.restore(); y += h / 2
        }
        header(c, "Feed", y + 14 * d)
        val keep = Ghosts.keepHours(context)
        text(c, if (keep == 0f) "Kept until you clear them" else "Kept for ${keep.toInt()} h · tap a faded row for Earlier",
            x0 + w / 2, y0 + screen.height() - 10 * d, 9f, 0xFF8C8C8C.toInt(), align = Paint.Align.CENTER)
    }

    private fun priority(c: Canvas) {
        text(c, "Events", x0 + 12 * d, y0 + 26 * d, 20f, light = true)
        var y = y0 + 44 * d
        header(c, "Notifications", y); y += 4 * d
        val names = Priority.names(context)
        if (on(Extras.PRIORITY) && names.isNotEmpty()) {
            val who = names.first()
            text(c, "★ $who", x0 + 12 * d, y + 13 * d, 10f, 0xFFF0C419.toInt(), bold = true); y += 16 * d
            y = card(c, y, 0xFF35B34A.toInt(), who, "Messages: running late")
            y = card(c, y, 0xFFE6007E.toInt(), who, "Mail: the photos")
        }
        y = card(c, y, 0xFF2A86D8.toInt(), "News", "Ferry timetable changes")
        if (!on(Extras.PRIORITY) || names.isEmpty())
            text(c, if (on(Extras.PRIORITY)) "Add a person to see them first" else "Off: newest first, no grouping",
                x0 + w / 2, y + 22 * d, 10f, 0xFF8C8C8C.toInt(), align = Paint.Align.CENTER)
    }

    private fun openApps(c: Canvas) {
        val gap = 10 * d; val cw = (w - 3 * gap) / 2; val ch = 62 * d
        val cols = listOf(0xFFF2F2F2, 0xFF262626, 0xFFF2F2F2, 0xFF1C2A3A)
        val labels = listOf("Messages", "Music", "Mail", "Maps")
        val split = on(Extras.SPLIT_MERGE)
        for (i in 0 until 4) {
            val x = x0 + gap + (i % 2) * (cw + gap); val y = y0 + 12 * d + (i / 2) * (ch + 24 * d)
            if (split && i == 0) {
                bar(c, x, y, cw, ch / 2 - d, cols[0].toInt(), 4f); bar(c, x, y + ch / 2 + d, cw, ch / 2 - d, cols[1].toInt(), 4f)
                bar(c, x + cw / 2 - 8 * d, y + ch / 2 - 1.5f * d, 16 * d, 3 * d, 0xFF8C8C8C.toInt(), 1.5f)
                text(c, "Messages | Music", x, y + ch + 13 * d, 10f, bold = true)
                continue
            }
            if (split && i == 1) continue
            bar(c, x, y, cw, ch, cols[i].toInt(), 4f)
            text(c, labels[i], x, y + ch + 13 * d, 10f, bold = true)
        }
        val acts = buildList {
            if (on(Extras.CARD_ACTIONS)) addAll(listOf("Split", "Pop-up", "Screenshot", "Info"))
            if (on(Extras.PINNING)) add("Pin")
        }
        val y = y0 + screen.height() - 14 * d
        text(c, if (acts.isEmpty()) "Carousel: no actions under the card (N9)" else "Carousel: " + acts.joinToString(" · "),
            x0 + w / 2, y, 10f, if (acts.isEmpty()) 0xFF8C8C8C.toInt() else N9.TEXT, align = Paint.Align.CENTER)
        if (split) text(c, "Two fingers: pull two cards together", x0 + w / 2, y - 14 * d, 9f, 0xFF8C8C8C.toInt(), align = Paint.Align.CENTER)
    }

    private fun apps(c: Canvas) {
        val n = 4; val gap = 14 * d; val s = minOf((w - gap * (n + 1)) / n, 48 * d)
        val left = x0 + (w - (n * s + (n - 1) * gap)) / 2
        val colors = listOf(0xFF2A86D8, 0xFF35B34A, 0xFFE6007E, 0xFFF07D00, 0xFF8E44AD, 0xFF16A085, 0xFFE0352B, 0xFF2A86D8)
        val names = listOf("Phone", "Messages", "Music", "Maps", "Store", "Clock", "Calendar", "Mail")
        colors.forEachIndexed { i, col ->
            val x = left + (i % n) * (s + gap); val y = y0 + 12 * d + (i / n) * (s + 22 * d)
            tile(c, x, y, s, col.toInt())
            text(c, names[i], x + s / 2, y + s + 12 * d, 9f, align = Paint.Align.CENTER)
        }
        if (on(Extras.PRIVATE)) {
            val y = y0 + 12 * d + 2 * (s + 22 * d)
            tile(c, left, y, s, 0xFF3A3A3A.toInt())
            text(c, "🔒", left + s / 2, y + s * .64f, 16f, align = Paint.Align.CENTER)
            text(c, "Private", left + s / 2, y + s + 12 * d, 9f, align = Paint.Align.CENTER)
        }
    }

    private fun sharing(c: Canvas) {
        text(c, "Share", x0 + 12 * d, y0 + 24 * d, 16f, light = true)
        var y = y0 + 34 * d
        val n9 = on(Extras.SHARE)
        if (n9) {
            bar(c, x0 + 12 * d, y, w - 24 * d, 18 * d, 0xFF262626.toInt(), 4f)
            text(c, "Filter", x0 + 20 * d, y + 12.5f * d, 9f, 0xFF8C8C8C.toInt()); y += 26 * d
        }
        if (on(Extras.SHARE_PEOPLE)) {
            listOf(0xFF35B34A, 0xFF2A86D8, 0xFFE6007E, 0xFFF07D00).forEachIndexed { i, col ->
                squircle(c, x0 + 12 * d + i * 34 * d, y, 26 * d, col.toInt())
            }
            text(c, "Hide", x0 + w - 12 * d, y + 16 * d, 9f, 0xFF8C8C8C.toInt(), align = Paint.Align.RIGHT); y += 32 * d
        }
        if (n9) { header(c, "Pinned", y + 8 * d); y += 12 * d }
        val apps = listOf("Messages" to 0xFF35B34A, "Mail" to 0xFFE6007E, "Bluetooth" to 0xFF2A86D8)
        apps.forEach { (t, col) ->
            if (y > y0 + screen.height() - 24 * d) return@forEach
            squircle(c, x0 + 12 * d, y + 3 * d, 18 * d, col.toInt())
            text(c, t, x0 + 38 * d, y + 16 * d, 11f, bold = true); y += 24 * d
        }
        if (!n9) text(c, "Off: Android's own share sheet", x0 + w - 12 * d, y0 + 24 * d, 9f, 0xFF8C8C8C.toInt(), align = Paint.Align.RIGHT)
        if (on(Extras.WIFI_QR)) {
            val q = 46 * d; val qx = x0 + w - q - 14 * d; val qy = y0 + screen.height() - q - 12 * d
            bar(c, qx, qy, q, q, 0xFFFFFFFF.toInt(), 3f)
            val cell = q / 9
            for (i in 0 until 9) for (j in 0 until 9) if ((i * 7 + j * 3 + i * j) % 3 == 0)
                bar(c, qx + i * cell, qy + j * cell, cell, cell, 0xFF000000.toInt(), 0f)
            text(c, "Share network", qx + q / 2, qy - 5 * d, 9f, 0xFFBDBDBD.toInt(), align = Paint.Align.CENTER)
        }
    }

    /** The miniature as a preference row at the top of a page. */
    class Row(ctx: Context, private val kind: String) : Preference(ctx) {
        init { isSelectable = false; isPersistent = false; isIconSpaceReserved = false; key = "preview_$kind"; layoutResource = R.layout.pref_preview }
        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val root = holder.itemView as android.view.ViewGroup
            root.removeAllViews()
            root.addView(SettingsPreview(context, kind), android.view.ViewGroup.LayoutParams(-1, -2))
        }
    }
}

/** For the preview and the Priority people page: a marked person's name. */
fun Priority.nameOf(ctx: Context, id: Long): String? = runCatching {
    ctx.contentResolver.query(android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id),
        arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull()

fun Priority.names(ctx: Context): List<String> = ids(ctx).mapNotNull { nameOf(ctx, it) }
