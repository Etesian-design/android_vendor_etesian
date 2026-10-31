package com.bompotis.etesian.launcher

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews

/** Symbian Belle-style home screen widgets, for any launcher. */
object BelleWidgets {
    private var listening = false

    /** Hooks the widgets to the launcher's own change signals and redraws them; safe to call often. */
    fun init(ctx: Context) {
        val app = ctx.applicationContext
        if (!listening) {
            listening = true
            Feeds.addListener { BelleFeedsWidget.updateAll(app); BelleRssWidget.updateAll(app) }
            EventsStore.addListener { BelleMailWidget.updateAll(app) }
        }
        BelleMusicWidget.watch(app)
        BelleWeatherWidget.updateAll(app)
        BelleCalendarWidget.updateAll(app)
        BelleFeedsWidget.updateAll(app)
        BelleShortcutsWidget.updateAll(app)
        BelleClassic.updateAll(app)
        BelleClassic2.updateAll(app)
    }

    fun ids(ctx: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    fun activity(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(ctx, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Most rows a list widget holds (Belle's Mail and feed widgets: mail1..mail20, feed1..feed20); it scrolls through them. */
    const val LIST_MAX = 20

    /**
     * Fills [listId] with [rows] as a scrolling list (Android 12+ collection items, no service needed) and shows [emptyId]
     * when there are none. A tap on a row sends [template], filled in with that row's own fill-in intent.
     */
    fun list(ctx: Context, v: RemoteViews, listId: Int, emptyId: Int, rows: List<RemoteViews>, code: Int, template: Intent) {
        val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
        rows.forEachIndexed { i, row -> items.addItem(i.toLong(), row) }
        v.setRemoteAdapter(listId, items.build())
        v.setEmptyView(listId, emptyId)
        v.setPendingIntentTemplate(listId, PendingIntent.getActivity(ctx, code, template.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
    }

    fun eventsPage(ctx: Context): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .setClass(ctx, HomeActivity::class.java)
        .putExtra(HomeActivity.EXTRA_PAGE, HomeActivity.PAGE_EVENTS)

    fun scaled(src: Bitmap, px: Int): Bitmap {
        if (src.width <= px && src.height <= px) return src
        val f = px.toFloat() / maxOf(src.width, src.height)
        return Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
    }
}

class BelleClockWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(ctx.packageName, R.layout.belle_clock)
        views.setOnClickPendingIntent(R.id.belle_root, BelleWidgets.activity(ctx, 1, Intent(AlarmClock.ACTION_SHOW_ALARMS)))
        awm.updateAppWidget(ids, views)
    }
}

class BelleAnalogWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(ctx.packageName, R.layout.belle_analog)
        views.setOnClickPendingIntent(R.id.belle_root, BelleWidgets.activity(ctx, 2, Intent(AlarmClock.ACTION_SHOW_ALARMS)))
        awm.updateAppWidget(ids, views)
    }
}

class BelleWeatherWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        BelleWidgets.init(ctx)
        val app = ctx.applicationContext
        Weather.refresh(app) { updateAll(app) }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleWeatherWidget::class.java)
            if (ids.isEmpty()) return
            val views = RemoteViews(ctx.packageName, R.layout.belle_weather)
            val now = Weather.current
            if (now == null) {
                views.setTextViewText(R.id.belle_place, "Weather")
                views.setTextViewText(R.id.belle_condition, if (Prefs.weatherEnabled(ctx)) "Updating…" else "Turned off")
                views.setTextViewText(R.id.belle_temp, "--°")
                views.setImageViewBitmap(R.id.belle_icon, icon(ctx, 3, true))
            } else {
                views.setTextViewText(R.id.belle_place, now.place ?: "Here")
                views.setTextViewText(R.id.belle_condition, now.description)
                views.setTextViewText(R.id.belle_temp, "${now.temperature}°")
                views.setImageViewBitmap(R.id.belle_icon, icon(ctx, now.code, now.day))
            }
            views.setOnClickPendingIntent(R.id.belle_root, BelleWidgets.activity(ctx, 3, BelleWidgets.eventsPage(ctx)))
            // The place, as on Belle: picks the place shown.
            views.setOnClickPendingIntent(R.id.belle_place, BelleWidgets.activity(ctx, 4, Intent(ctx, WeatherPlaceActivity::class.java)))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, views)
        }

        private fun icon(ctx: Context, code: Int, day: Boolean): Bitmap {
            val px = ctx.dp(56f)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            WeatherIcon(code, day).apply { setBounds(0, 0, px, px) }.draw(Canvas(bmp))
            return bmp
        }
    }
}

class BelleCalendarWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PROVIDER_CHANGED) updateAll(ctx) else super.onReceive(ctx, intent)
    }

    class Event(val id: Long, val title: String, val begin: Long, val allDay: Boolean)

    companion object {
        private val ROWS = intArrayOf(R.id.belle_row0, R.id.belle_row1, R.id.belle_row2)
        private val TITLES = intArrayOf(R.id.belle_title0, R.id.belle_title1, R.id.belle_title2)
        private val SUBS = intArrayOf(R.id.belle_sub0, R.id.belle_sub1, R.id.belle_sub2)

        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleCalendarWidget::class.java)
            if (ids.isEmpty()) return
            val events = next(ctx)
            val views = RemoteViews(ctx.packageName, R.layout.belle_calendar)
            val calendar = Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time").appendPath(System.currentTimeMillis().toString()).build())
            views.setOnClickPendingIntent(R.id.belle_header, BelleWidgets.activity(ctx, 10, calendar))
            views.setOnClickPendingIntent(R.id.belle_empty, BelleWidgets.activity(ctx, 10, calendar))
            views.setViewVisibility(R.id.belle_empty, if (events.isEmpty()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.belle_rows, if (events.isEmpty()) View.GONE else View.VISIBLE)
            for (i in ROWS.indices) {
                val e = events.getOrNull(i)
                views.setViewVisibility(ROWS[i], if (e == null) View.INVISIBLE else View.VISIBLE)
                if (e == null) continue
                views.setTextViewText(TITLES[i], e.title)
                views.setTextViewText(SUBS[i], whenText(ctx, e))
                val open = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                views.setOnClickPendingIntent(ROWS[i], BelleWidgets.activity(ctx, 11 + i, open))
            }
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, views)
        }

        private fun whenText(ctx: Context, e: Event): String {
            val day = when {
                DateUtils.isToday(e.begin) -> "Today"
                DateUtils.isToday(e.begin - DateUtils.DAY_IN_MILLIS) -> "Tomorrow"
                else -> DateUtils.formatDateTime(ctx, e.begin, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
            }
            return if (e.allDay) "$day · All day" else "$day · ${DateFormat.getTimeFormat(ctx).format(java.util.Date(e.begin))}"
        }

        fun next(ctx: Context): List<Event> {
            if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, now)
                ContentUris.appendId(it, now + 14 * DateUtils.DAY_IN_MILLIS)
            }.build()
            val cols = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.END)
            return runCatching {
                ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    buildList {
                        while (c.moveToNext() && size < 3) {
                            if (c.getLong(4) <= now) continue
                            add(Event(c.getLong(0), c.getString(1).orEmpty().ifEmpty { "(No title)" }, c.getLong(2), c.getInt(3) == 1))
                        }
                    }
                }
            }.getOrNull().orEmpty()
        }
    }
}

class BelleFeedsWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        BelleWidgets.init(ctx)
        Feeds.refresh(ctx, force = false)
    }

    companion object {
        fun updateAll(ctx: Context) {
            val ids = BelleWidgets.ids(ctx, BelleFeedsWidget::class.java)
            if (ids.isEmpty()) return
            val items = Feeds.items(ctx).sortedByDescending { it.time }.take(BelleWidgets.LIST_MAX)
            val views = RemoteViews(ctx.packageName, R.layout.belle_feeds)
            val list = BelleWidgets.activity(ctx, 20, Feeds.openApp())
            views.setOnClickPendingIntent(R.id.belle_header, list)
            views.setOnClickPendingIntent(R.id.belle_empty, list)
            val rows = items.map { item ->
                RemoteViews(ctx.packageName, R.layout.belle_feeds_item).apply {
                    setTextViewText(R.id.belle_title, item.title)
                    val ago = if (item.time > 0) " · " + DateUtils.getRelativeTimeSpanString(item.time, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE) else ""
                    setTextViewText(R.id.belle_sub, item.feedTitle + ago)
                    setOnClickFillInIntent(R.id.belle_item, Intent().putExtra("feed_item", item.id).putExtra("link", item.link))
                }
            }
            BelleWidgets.list(ctx, views, R.id.belle_rows, R.id.belle_empty, rows, 21, Feeds.openItem("", ""))
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, views)
        }
    }
}

class BelleShortcutsWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, awm, it) }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = prefs(ctx).edit()
        for (id in ids) for (s in 0 until SLOTS) e.remove(key(id, s))
        e.apply()
    }

    companion object {
        const val SLOTS = 4
        private val SLOT_VIEWS = intArrayOf(R.id.belle_slot0, R.id.belle_slot1, R.id.belle_slot2, R.id.belle_slot3)
        private val ICONS = intArrayOf(R.id.belle_slot_icon0, R.id.belle_slot_icon1, R.id.belle_slot_icon2, R.id.belle_slot_icon3)
        private val LABELS = intArrayOf(R.id.belle_slot_label0, R.id.belle_slot_label1, R.id.belle_slot_label2, R.id.belle_slot_label3)

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("belle_shortcuts", Context.MODE_PRIVATE)
        private fun key(id: Int, slot: Int) = "w${id}_$slot"

        fun slots(ctx: Context, id: Int): List<ComponentName?> = (0 until SLOTS).map { s ->
            prefs(ctx).getString(key(id, s), null)?.let(ComponentName::unflattenFromString)
        }

        fun save(ctx: Context, id: Int, slots: List<ComponentName?>) {
            val e = prefs(ctx).edit()
            slots.forEachIndexed { s, c -> if (c == null) e.remove(key(id, s)) else e.putString(key(id, s), c.flattenToString()) }
            e.apply()
        }

        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            BelleWidgets.ids(ctx, BelleShortcutsWidget::class.java).forEach { update(ctx, awm, it) }
        }

        fun update(ctx: Context, awm: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.belle_shortcuts)
            val pm = ctx.packageManager
            slots(ctx, id).forEachIndexed { s, cn ->
                val info = cn?.let { runCatching { pm.getActivityInfo(it, 0) }.getOrNull() }
                val code = 1000 + id * SLOTS + s
                if (cn == null || info == null) {
                    views.setImageViewResource(ICONS[s], R.drawable.belle_add)
                    views.setTextViewText(LABELS[s], "")
                    views.setOnClickPendingIntent(SLOT_VIEWS[s], BelleWidgets.activity(ctx, code,
                        Intent(ctx, BelleShortcutsConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .setData(android.net.Uri.parse("belle://shortcuts/$id"))))
                } else {
                    views.setImageViewBitmap(ICONS[s], sized(icon(ctx, cn, info.loadIcon(pm)), ctx.dp(40f)))
                    views.setTextViewText(LABELS[s], info.loadLabel(pm))
                    views.setOnClickPendingIntent(SLOT_VIEWS[s], BelleWidgets.activity(ctx, code,
                        Intent.makeMainActivity(cn).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)))
                }
            }
            awm.updateAppWidget(id, views)
        }

        fun icon(ctx: Context, cn: ComponentName, fallback: android.graphics.drawable.Drawable): Bitmap =
            AppRepository.apps.firstOrNull { it.component == cn }?.icon ?: IconFactory.render(fallback, ctx.dp(48f))

        /** The app's icon at [px], in its full colours as on Belle's shortcut bar. */
        fun sized(src: Bitmap, px: Int): Bitmap = BelleWidgets.scaled(src, px)
    }
}

class BelleMusicWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        watch(ctx.applicationContext)
        updateAll(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action
        if (action != PREV && action != PLAY && action != NEXT) { super.onReceive(ctx, intent); return }
        watch(ctx.applicationContext)
        val c = controller?.transportControls ?: return
        when (action) {
            PREV -> c.skipToPrevious()
            NEXT -> c.skipToNext()
            else -> if (playing()) c.pause() else c.play()
        }
    }

    companion object {
        private const val PREV = "com.bompotis.etesian.launcher.belle.PREV"
        private const val PLAY = "com.bompotis.etesian.launcher.belle.PLAY"
        private const val NEXT = "com.bompotis.etesian.launcher.belle.NEXT"
        private val main = Handler(Looper.getMainLooper())
        private var watching = false
        private var controller: MediaController? = null
        private var appCtx: Context? = null

        private val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { appCtx?.let(::updateAll) }
            override fun onMetadataChanged(metadata: MediaMetadata?) { appCtx?.let(::updateAll) }
            override fun onSessionDestroyed() { appCtx?.let { pick(it, null) } }
        }

        private fun playing() = controller?.playbackState?.state == PlaybackState.STATE_PLAYING

        /** Follows the active media session; needs the launcher's notification access (EventsService). */
        fun watch(ctx: Context) {
            if (watching || !EventsStore.isEnabled(ctx)) return
            if (BelleWidgets.ids(ctx, BelleMusicWidget::class.java).isEmpty()) return
            val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return
            val listener = ComponentName(ctx, EventsService::class.java)
            runCatching {
                msm.addOnActiveSessionsChangedListener({ pick(ctx, it) }, listener, main)
                appCtx = ctx
                watching = true
                pick(ctx, msm.getActiveSessions(listener))
            }
        }

        private fun pick(ctx: Context, sessions: List<MediaController>?) {
            val list = sessions ?: runCatching {
                ctx.getSystemService(MediaSessionManager::class.java)
                    .getActiveSessions(ComponentName(ctx, EventsService::class.java))
            }.getOrNull().orEmpty()
            val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
            if (next?.sessionToken != controller?.sessionToken) {
                controller?.unregisterCallback(callback)
                controller = next
                next?.registerCallback(callback, main)
            }
            updateAll(ctx)
        }

        private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(ctx, code, Intent(action).setClass(ctx, BelleMusicWidget::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun updateAll(ctx: Context) {
            update(ctx, BelleMusicWidget::class.java, R.layout.belle_music, false)
            update(ctx, BelleMusicClassicWidget::class.java, R.layout.bc_music, true)
        }

        /** [classic]: the Belle player (belle-ref7.jpg), "Title - Artist" on one line. */
        private fun update(ctx: Context, cls: Class<*>, layout: Int, classic: Boolean) {
            val ids = BelleWidgets.ids(ctx, cls)
            if (ids.isEmpty()) return
            val views = RemoteViews(ctx.packageName, layout)
            val c = controller
            val meta = c?.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            views.setTextViewText(R.id.belle_title, if (classic && title != null && !artist.isNullOrEmpty()) "$title - $artist" else title ?: "Music")
            views.setTextViewText(R.id.belle_artist, artist ?: when {
                !EventsStore.isEnabled(ctx) -> "Allow Etesian events notification access"
                c == null -> "Nothing playing"
                else -> ""
            })
            if (art != null) {
                // album art is content: shown whole and square, never squircled
                views.setImageViewBitmap(R.id.belle_art, if (classic) art
                    else android.graphics.Bitmap.createScaledBitmap(art, ctx.dp(64f), ctx.dp(64f), true))
                views.setViewPadding(R.id.belle_art, 0, 0, 0, 0)
            } else {
                views.setImageViewResource(R.id.belle_art, R.drawable.belle_music)
                val p = ctx.dp(if (classic) 26f else 14f)
                views.setViewPadding(R.id.belle_art, p, p, p, p)
            }
            views.setImageViewResource(R.id.belle_play, when {
                classic -> if (playing()) R.drawable.bc_glyph_pause else R.drawable.bc_glyph_play
                else -> if (playing()) R.drawable.belle_pause else R.drawable.belle_play
            })
            views.setOnClickPendingIntent(R.id.belle_prev, broadcast(ctx, PREV, 30))
            views.setOnClickPendingIntent(R.id.belle_play, broadcast(ctx, PLAY, 31))
            views.setOnClickPendingIntent(R.id.belle_next, broadcast(ctx, NEXT, 32))
            val open = c?.sessionActivity ?: BelleWidgets.activity(ctx, 33,
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
            views.setOnClickPendingIntent(R.id.belle_root, open)
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, views)
        }
    }
}

/** The Belle music player (belle-ref7.jpg); [BelleMusicWidget] drives it. */
class BelleMusicClassicWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        BelleMusicWidget.watch(ctx.applicationContext)
        BelleMusicWidget.updateAll(ctx.applicationContext)
    }
}
