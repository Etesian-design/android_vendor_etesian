package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** RSS items shown under Feed (the newest, across all feeds). */
private const val FEED_ROWS = 20

/**
 * The Events view, laid out like the N9's: date and weather on top, then Notifications
 * (with Calendar alongside), then the Feed, and a collapsed Low priority list at the bottom.
 * Long-press anything to file that app, or just that kind of its notifications, elsewhere.
 */
@SuppressLint("ViewConstructor")
class EventsPage(context: Context) : FrameLayout(context), HomePage {

    /** Apps (per section) whose grouped notifications are expanded. */
    private val expanded = mutableSetOf<String>()
    private var lowOpen = false
    /** Long-press: swipe any entry away; tap the date or weather to finish. */
    private var dismissMode = false
    // Declared before the adapter, which reads them while being created.
    private var agenda: List<Agenda.Entry> = emptyList()
    private val list = RecyclerView(context)
    private val adapter = Adapter()
    private val listener: () -> Unit = { refresh() }

    init {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        // After construction: [swipe] is declared further down.
        post { androidx.recyclerview.widget.ItemTouchHelper(swipe).attachToRecyclerView(list) }
        list.clipToPadding = false
        list.overScrollMode = OVER_SCROLL_NEVER
        list.itemAnimator = FoldAnimator()
        // Ghost rows: a cut half row slips ~10dp under the next header (no empty band before it).
        list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: android.graphics.Rect, v: View, rv: RecyclerView, st: RecyclerView.State) {
                val g = v as? GhostView ?: return
                out.bottom = if (g.half && rv.getChildAdapterPosition(v) < adapter.itemCount - 1) -dp(10f) else 0
            }
        })
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EventsStore.addListener(listener)
        Feeds.addListener(listener)
        liveObserver = Extras.observe(context, Extras.LIVE_UPDATES) { refresh() }
        ghostObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }.also { o ->
            listOf(Extras.key(Extras.GHOST_ROWS), Ghosts.KEY_SIZE, Ghosts.KEY_ROWS, Ghosts.KEY_KEEP, Extras.key(Extras.TODAY), Today.KEY_ITEMS).forEach {
                context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(it), false, o)
            }
        }
    }
    private var ghostObserver: android.database.ContentObserver? = null
    private var liveObserver: android.database.ContentObserver? = null
    override fun onDetachedFromWindow() {
        liveObserver?.let { context.contentResolver.unregisterContentObserver(it) }
        ghostObserver?.let { context.contentResolver.unregisterContentObserver(it) }
        EventsStore.removeListener(listener)
        Feeds.removeListener(listener)
        super.onDetachedFromWindow()
    }
    override fun onShown() {
        agenda = Agenda.today(context)
        if (Extras.on(context, Extras.GLANCE)) Agenda.publishGlance(context, agenda)
        Weather.refresh(context) { refresh() }
        Feeds.refresh(context, force = false)
        refresh()
    }
    override fun exitModes(): Boolean {
        if (!dismissMode) return false
        setDismissMode(false)
        return true
    }

    override fun holdsPaging() = dismissMode


    private fun setDismissMode(on: Boolean) {
        if (dismissMode == on) return
        dismissMode = on
        if (on) performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        refresh()
    }

    /** Long-press anywhere on an entry locks the view into dismiss mode. */
    private fun onLongPress() = setDismissMode(true)

    /**
     * Long-press on one notification: with Android extras "Notification actions and snooze" or
     * "Inline reply" on, the N9 menu sheet (Reply / Mark as read / Snooze / Dismiss); otherwise
     * the N9 dismiss mode as before.
     */
    private fun onLongPress(e: EventItem) {
        val actions = Extras.on(context, Extras.ACTIONS)
        val reply = Extras.on(context, Extras.INLINE_REPLY) && e.replyAction != null
        // Without the approved actions sheet, long-press is always the N9 dismiss mode (never a new menu).
        if (dismissMode || (!actions && !reply)) { onLongPress(); return }
        // Android extras → Priority people: a row in the existing sheet only.
        val contact = if (Priority.on(context)) Priority.contactOf(context, e) else -1L
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        val items = mutableListOf<N9Sheet.Item>()
        if (reply) items += N9Sheet.Item("Reply") { EventsReply.show(context, e, iconFor(e)) }
        if (actions) {
            e.markReadAction?.let { a -> items += N9Sheet.Item("Mark as read") {
                runCatching { a.actionIntent.send() }
            } }
            items += N9Sheet.Item("Snooze", "15 minutes, 1 hour or tomorrow") { snooze(e) }
            items += N9Sheet.Item("Dismiss") { EventsStore.service?.cancelNotification(e.key) }
        }
        if (contact >= 0) {
            val name = Priority.person(context, contact)?.name.orEmpty()
            items += if (Priority.isPriority(context, contact))
                N9Sheet.Item("Not a priority person", name) { Priority.set(context, contact, false); refresh() }
            else N9Sheet.Item("Priority person", name) { Priority.set(context, contact, true); refresh() }
        }
        // Mock 2: only the notification's own actions, Snooze and Dismiss (Edit Events stays on the feed long-press).
        if (items.isEmpty()) { onLongPress(); return }
        N9Sheet.show(context, items, label = "Events actions")
    }

    /** Snooze choices as an N9 selection sheet; tomorrow = 08:00 next morning. */
    private fun snooze(e: EventItem) {
        val tomorrow = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 8); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
        }.timeInMillis - System.currentTimeMillis()
        fun go(ms: Long) { runCatching { EventsStore.service?.snoozeNotification(e.key, ms) } }
        N9Sheet.show(context, listOf(
            N9Sheet.Item("15 minutes") { go(15 * 60_000L) },
            N9Sheet.Item("1 hour") { go(60 * 60_000L) },
            N9Sheet.Item("Tomorrow morning") { go(tomorrow) },
        ), title = "Snooze", label = "Snooze")
    }

    /** In dismiss mode a tap files the entry elsewhere instead of opening it. */
    private fun onTap(e: EventItem, open: () -> Unit) = if (dismissMode) reclassify(e) else open()

    /** The notifications a row stands for, for dismissing it. */
    private fun itemsOf(row: Row): List<EventItem> = when (row) {
        is Row.Event -> listOf(row.item)
        is Row.Group -> row.items
        is Row.Card -> row.items
        is Row.Stack -> row.items
        is Row.PriorityGroup -> row.items
        else -> emptyList()
    }

    private val swipe = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0,
        androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
        override fun getSwipeDirs(rv: RecyclerView, h: RecyclerView.ViewHolder): Int {
            val row = adapter.rowAt(h.bindingAdapterPosition) ?: return 0
            // The ghost rows are one item: swiping it in dismiss mode expires them all, like any notification.
            if (row is Row.Ghosts) return if (dismissMode) super.getSwipeDirs(rv, h) else 0
            return if (dismissMode && itemsOf(row).isNotEmpty()) super.getSwipeDirs(rv, h) else 0
        }
        override fun onMove(rv: RecyclerView, a: RecyclerView.ViewHolder, b: RecyclerView.ViewHolder) = false
        override fun onSwiped(h: RecyclerView.ViewHolder, direction: Int) {
            adapter.rowAt(h.bindingAdapterPosition)?.let { row ->
                if (row is Row.Ghosts) Ghosts.expireAll(context)
                itemsOf(row).forEach { EventsStore.service?.cancelNotification(it.key) }
            }
            refresh()
        }
    }

    override fun applyInsets(left: Int, top: Int, right: Int, bottom: Int) {
        list.setPadding(left, top, right, bottom + dp(16f))
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refresh() = adapter.notifyDataSetChanged()

    private fun open(item: EventItem) {
        val pi = item.intent ?: return
        val opts = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            opts.setPendingIntentBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                else @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }
        runCatching { pi.send(context, 0, null, null, null, null, opts.toBundle()) }
        if (item.autoCancel) EventsStore.service?.cancelNotification(item.key)
    }

    /** Long-press: file this app, or only this kind of its notifications, under another section. */
    private fun reclassify(e: EventItem) {
        val sections = EventsClassifier.Section.entries
        val current = EventsClassifier.classify(context, e)
        fun pick(wholeApp: Boolean) {
            android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(if (wholeApp) "Show ${appLabel(e.pkg)} under" else "Show \u201c${e.channelName}\u201d under")
                .setSingleChoiceItems(sections.map { it.title }.toTypedArray(), sections.indexOf(current)) { d, i ->
                    EventsClassifier.assign(context, e, sections[i], wholeApp)
                    d.dismiss()
                    refresh()
                }
                .show()
        }
        if (e.channelName.isEmpty()) { pick(true); return }
        android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(appLabel(e.pkg))
            .setItems(arrayOf("All notifications from this app", "Only \u201c${e.channelName}\u201d")) { _, i -> pick(i == 0) }
            .show()
    }

    private sealed class Row {
        object Top : Row()
        object DismissHint : Row()
        object Permission : Row()
        class Message(val text: String) : Row()
        class Section(val section: EventsClassifier.Section, val count: Int) : Row()
        class Event(val item: EventItem, val nested: Boolean = false, val card: Boolean = false, val live: Boolean = false) : Row()
        class Group(val key: String, val items: List<EventItem>, val open: Boolean) : Row()
        class Card(val items: List<EventItem>) : Row()
        class Stack(val key: String, val items: List<EventItem>) : Row()
        class AgendaRow(val entry: Agenda.Entry) : Row()
        class LowToggle(val count: Int, val open: Boolean) : Row()
        class FeedEntry(val item: Feeds.Item) : Row()
        class PriorityGroup(val person: Priority.Person, val items: List<EventItem>) : Row()
        class TodayHeader : Row()
        class TodayRow(val item: Today.Item) : Row()
        class Ghosts(val ghosts: List<com.bompotis.etesian.launcher.Ghosts.Ghost>) : Row()
    }

    /** A stable id per row, so dismissing animates (fold out, the rest closes up). */
    private val ids = mutableMapOf<String, Long>()
    private fun idOf(row: Row, seen: MutableSet<String>): Long {
        var k = when (row) {
            Row.Top -> "top"; Row.DismissHint -> "hint"; Row.Permission -> "perm"
            is Row.Message -> "m:" + row.text
            is Row.Section -> "s:" + row.section.name
            is Row.Event -> "e:${row.item.key}:${row.nested}:${row.card}:${row.live}"
            is Row.Group -> "g:" + row.key
            is Row.Card -> "c:" + row.items.first().key
            is Row.Stack -> "st:" + row.key + row.items.size
            is Row.AgendaRow -> "a:${row.entry.title}:${row.entry.begin}"
            is Row.LowToggle -> "low"
            is Row.FeedEntry -> "f:" + row.item.id
            is Row.Ghosts -> "ghosts"
            is Row.TodayHeader -> "today"
            is Row.PriorityGroup -> "p:" + row.person.id
            is Row.TodayRow -> "t:" + row.item.text
        }
        while (!seen.add(k)) k += "+"
        return ids.getOrPut(k) { ids.size.toLong() + 1 }
    }

    private fun rows(): List<Row> {
        val out = mutableListOf<Row>(Row.Top)
        if (dismissMode) out += Row.DismissHint
        if (!EventsStore.isEnabled(context)) { out += Row.Permission; return out }
        val bySection = EventsStore.items.groupBy { EventsClassifier.classify(context, it) }
        fun section(s: EventsClassifier.Section, extra: List<Row> = emptyList()) {
            val items = bySection[s].orEmpty()
            if (items.isEmpty() && extra.isEmpty()) return
            out += Row.Section(s, items.size)
            out += extra
            layout(s, items, out)
        }
        // Android extras → Today: one-line items above Notifications.
        val today = if (Today.on(context)) Today.items(context, agenda) else emptyList()
        if (today.isNotEmpty()) { out += Row.TodayHeader(); today.forEach { out += Row.TodayRow(it) } }
        var notifications = bySection[EventsClassifier.Section.NOTIFICATIONS].orEmpty()
        // Android extras → Priority people: their notifications (any app) first, grouped under them.
        val priority = mutableListOf<Row.PriorityGroup>()
        Priority.clearCache()  // contacts change: match afresh on each refresh
        if (Priority.on(context) && Priority.ids(context).isNotEmpty()) {
            val ids = Priority.ids(context)
            val byPerson = notifications.groupBy { Priority.contactOf(context, it) }.filterKeys { it in ids }
            byPerson.entries.sortedByDescending { en -> en.value.maxOf { it.time } }.forEach { (id, list) ->
                Priority.person(context, id)?.let { priority += Row.PriorityGroup(it, list.sortedByDescending { e -> e.time }) }
            }
            val taken = priority.flatMap { it.items }.toSet()
            notifications = notifications.filter { it !in taken }
        }
        // Android extras → Live updates: ongoing progress pinned first under Notifications.
        val live = if (Extras.on(context, Extras.LIVE_UPDATES)) EventsStore.live else emptyList()
        // As on the N9, no notifications means no Notifications section at all (no header, no message).
        if (notifications.isNotEmpty() || live.isNotEmpty() || priority.isNotEmpty()) {
            out += Row.Section(EventsClassifier.Section.NOTIFICATIONS, notifications.size + priority.sumOf { it.items.size })
            live.forEach { out += Row.Event(it, live = true) }
            out += priority
            if (notifications.isNotEmpty()) layout(EventsClassifier.Section.NOTIFICATIONS, notifications, out)
        }
        // Android extras → Keep dismissed notifications: faded rows at the end of Notifications (alone,
        // with no header above, when there are no current ones).
        if (Ghosts.on(context)) Ghosts.list(context).takeIf { it.isNotEmpty() }?.let { out += Row.Ghosts(it) }
        // Today's upcoming events already list today's agenda: no second Calendar section then.
        val agendaRows = if (Today.on(context) && "upcoming" in Today.shown(context)) emptyList() else agenda.map { Row.AgendaRow(it) }
        section(EventsClassifier.Section.CALENDAR, agendaRows)
        // Feed: the user's RSS feeds alongside apps filed under Feed.
        val feedRows = if (Feeds.feeds(context).isEmpty()) emptyList()
            else Feeds.items(context).take(FEED_ROWS).map { Row.FeedEntry(it) }
                .ifEmpty { listOf(Row.Message(if (Feeds.refreshing) "Refreshing\u2026" else "No feed items yet")) }
        section(EventsClassifier.Section.FEED, feedRows)
        val low = bySection[EventsClassifier.Section.LOW].orEmpty()
        if (low.isNotEmpty()) {
            out += Row.LowToggle(low.size, lowOpen)
            if (lowOpen) layout(EventsClassifier.Section.LOW, low, out)
        }
        return out
    }

    /** Lays [items] out in the style the user picked for [s]. */
    private fun layout(s: EventsClassifier.Section, items: List<EventItem>, out: MutableList<Row>) {
        val style = Prefs.eventsStyle(context, s)
        val groups = items.groupBy { it.pkg }.values.sortedByDescending { it.first().time }
        when (style) {
            EventsClassifier.Style.INDIVIDUAL -> items.forEach { out += Row.Event(it) }
            EventsClassifier.Style.CARD -> groups.forEach { out += Row.Card(it) }
            EventsClassifier.Style.STACK -> groups.forEach { g ->
                val key = "${s.key}/${g[0].pkg}"
                if (g.size == 1) out += Row.Event(g[0], card = true)
                else if (key in expanded) {
                    out += Row.Stack(key, g.take(0))
                    g.forEach { out += Row.Event(it, card = true) }
                } else out += Row.Stack(key, g)
            }
            EventsClassifier.Style.GROUPED -> groups.forEach { g ->
                if (g.size == 1) out += Row.Event(g[0])
                else {
                    val key = "${s.key}/${g[0].pkg}"
                    val open = key in expanded
                    out += Row.Group(key, g, open)
                    if (open) g.forEach { out += Row.Event(it, nested = true) }
                }
            }
        }
    }

    private fun toggle(key: String) {
        if (!expanded.remove(key)) expanded += key
        refresh()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows = rows()
        private var rowIds = LongArray(0)
        init { setHasStableIds(true) }
        fun rowAt(position: Int) = rows.getOrNull(position)

        override fun getItemCount(): Int {
            rows = rows()
            val seen = mutableSetOf<String>()
            rowIds = LongArray(rows.size) { idOf(rows[it], seen) }
            return rows.size
        }
        override fun getItemId(position: Int) = rowIds.getOrElse(position) { RecyclerView.NO_ID }
        override fun getItemViewType(position: Int) = when (rows[position]) {
            Row.Top -> 0; Row.Permission -> 1; is Row.Message -> 2; is Row.Event -> 3; is Row.Group -> 4
            is Row.Section -> 5; is Row.Card -> 6; is Row.Stack -> 7; is Row.AgendaRow -> 8; is Row.LowToggle -> 9
            Row.DismissHint -> 10; is Row.FeedEntry -> 11; is Row.Ghosts -> 12; is Row.TodayHeader -> 13; is Row.TodayRow -> 14; is Row.PriorityGroup -> 15
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            val v: View = when (viewType) {
                0 -> TopRow(ctx)
                1 -> message(ctx, "Allow notification access to see your events here").apply {
                    setBackgroundColor(N9.CARD)
                    setTextColor(N9.TEXT)
                    setOnClickListener {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                2 -> message(ctx, "")
                4 -> GroupRow(ctx)
                5 -> SectionRow(ctx)
                6 -> CardRow(ctx)
                7 -> StackRow(ctx)
                8 -> AgendaView(ctx)
                9 -> LowRow(ctx)
                11 -> FeedRow(ctx)
                12 -> GhostView(ctx)
                13 -> SectionRow(ctx).apply { bindToday() }
                14 -> TodayView(ctx)
                15 -> PriorityView(ctx)
                10 -> TextView(ctx).apply {
                    text = "Swipe entries away to dismiss them \u00b7 tap the date to finish"
                    setTextColor(N9.TEXT); textSize = 14f; typeface = N9.FONT
                    gravity = Gravity.CENTER
                    setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
                    background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(N9.ACCENT) }
                    setOnClickListener { setDismissMode(false) }
                }
                else -> EventRow(ctx)
            }
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (viewType == 1 || viewType == 10) setMargins(dp(16f), dp(8f), dp(16f), dp(4f))
            }
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                Row.Top -> (h.itemView as TopRow).bind()
                is Row.Message -> (h.itemView as TextView).text = row.text
                is Row.Event -> (h.itemView as EventRow).bind(row.item, row.nested, row.card, row.live)
                is Row.Group -> (h.itemView as GroupRow).bind(row)
                is Row.Section -> (h.itemView as SectionRow).bind(row)
                is Row.Card -> (h.itemView as CardRow).bind(row.items)
                is Row.Stack -> (h.itemView as StackRow).bind(row)
                is Row.AgendaRow -> (h.itemView as AgendaView).bind(row.entry)
                is Row.LowToggle -> (h.itemView as LowRow).bind(row)
                is Row.FeedEntry -> (h.itemView as FeedRow).bind(row.item)
                is Row.Ghosts -> (h.itemView as GhostView).bind(row.ghosts)
                is Row.TodayHeader -> (h.itemView as SectionRow).bindToday()
                is Row.TodayRow -> (h.itemView as TodayView).bind(row.item)
                is Row.PriorityGroup -> (h.itemView as PriorityView).bind(row)
                else -> Unit
            }
        }
    }

    /** Date, and the weather when enabled. */
    private inner class TopRow(ctx: Context) : LinearLayout(ctx) {
        private val day = TextView(ctx).apply { setTextColor(N9.TEXT); textSize = 30f; typeface = N9.FONT_LIGHT }
        private val date = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 15f; typeface = N9.FONT }
        private val temp = TextView(ctx).apply { setTextColor(N9.TEXT); textSize = 30f; typeface = N9.FONT_LIGHT; gravity = Gravity.END; contentDescription = "weather temperature"; includeFontPadding = false }
        private val icon = android.widget.ImageView(ctx).apply { contentDescription = "weather symbol" }
        private val skyRule = View(ctx).apply { setBackgroundColor(N9.DIVIDER) }
        private val sky = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 15f; typeface = N9.FONT; gravity = Gravity.CENTER_HORIZONTAL; contentDescription = "weather place" }

        init {
            orientation = HORIZONTAL
            setPadding(dp(16f), dp(6f), dp(16f), dp(14f))
            addView(LinearLayout(ctx).apply { orientation = VERTICAL; addView(day); addView(date) },
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            // N9: the place in small bold type over a thin line, then the temperature with the
            // weather symbol beside it, all centred on the line.
            addView(LinearLayout(ctx).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(sky)
                // A fixed-width line: a full-width one would claim the whole row from the date.
                addView(skyRule, LayoutParams(dp(150f), dp(1f)).apply { topMargin = dp(4f); bottomMargin = dp(2f) })
                // Wrapped, so the temperature and symbol sit centred under the place (a full-width
                // row left-aligned them).
                addView(LinearLayout(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(temp, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_VERTICAL })
                    addView(icon, LayoutParams(dp(48f), dp(48f)).apply { marginStart = dp(10f); gravity = Gravity.CENTER_VERTICAL })
                }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            })
            setOnClickListener { setDismissMode(false) }
        }

        fun bind() {
            val now = System.currentTimeMillis()
            day.text = DateFormat.format("EEEE", now)
            date.text = DateUtils.formatDateTime(context, now, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)
            val w = Weather.current
            temp.text = w?.let { "${it.temperature}${it.unit}" }.orEmpty()
            icon.setImageDrawable(w?.takeIf { it.code >= 0 }?.let { WeatherIcon(it.code, it.day) })
            icon.visibility = if (icon.drawable == null) GONE else VISIBLE
            // The place; the description only when there's no symbol to show it.
            sky.text = w?.let { it.place ?: it.description }.orEmpty()
            sky.typeface = N9.FONT_BOLD
            sky.setTextColor(N9.TEXT)
            skyRule.visibility = if (w == null) GONE else VISIBLE
        }
    }

    /** Section title; Notifications also carries Clear. */
    private inner class SectionRow(ctx: Context) : LinearLayout(ctx) {
        private val title = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 15f; typeface = N9.FONT_BOLD }
        private val rule = View(ctx).apply { setBackgroundColor(N9.DIVIDER) }
        private val clear = TextView(ctx).apply {
            text = "Clear"
            setTextColor(N9.TEXT)
            textSize = 14f
            typeface = N9.FONT
            setPadding(dp(18f), dp(6f), dp(18f), dp(6f))
            background = N9.button(context)
        }
        private var section = EventsClassifier.Section.NOTIFICATIONS
        private val refreshButton = TextView(ctx).apply {
            text = "Refresh"
            setTextColor(N9.TEXT)
            textSize = 14f
            typeface = N9.FONT
            setPadding(dp(18f), dp(6f), dp(18f), dp(6f))
            background = N9.button(context)
            setOnClickListener { Feeds.refresh(context, force = true) }
        }

        init {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(8f))
            // N9 group header: label, then a thin line running to the right edge.
            addView(title)
            addView(rule, LayoutParams(0, dp(1f), 1f).apply { marginStart = dp(12f); marginEnd = dp(12f) })
            addView(clear)
            addView(refreshButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f) })
            clear.setOnClickListener {
                // Clears that section only.
                // Clear also clears the kept dismissed ones, and the cleared ones are not kept.
                if (section == EventsClassifier.Section.NOTIFICATIONS) { Ghosts.suppress(); Ghosts.expireAll(context); refresh() }
                EventsStore.items.filter { EventsClassifier.classify(context, it) == section }
                    .forEach { EventsStore.service?.cancelNotification(it.key) }
            }
        }

        /** The Today header: title and rule only. */
        fun bindToday() {
            title.text = "Today"; clear.visibility = GONE; refreshButton.visibility = GONE
        }

        fun bind(r: Row.Section) {
            section = r.section
            title.text = r.section.title
            clear.visibility = if (r.count > 0 || (r.section == EventsClassifier.Section.NOTIFICATIONS &&
                Ghosts.on(context) && Ghosts.list(context).isNotEmpty())) VISIBLE else GONE
            // N9: the Feed carries Refresh.
            val feed = r.section == EventsClassifier.Section.FEED && Feeds.feeds(context).isNotEmpty()
            refreshButton.visibility = if (feed) VISIBLE else GONE
            refreshButton.text = if (Feeds.refreshing) "Refreshing\u2026" else "Refresh"
        }
    }

    private fun message(ctx: Context, s: String) = TextView(ctx).apply {
        text = s
        setTextColor(N9.TEXT_SECONDARY)
        textSize = 16f
        typeface = N9.FONT_LIGHT
        gravity = Gravity.CENTER
        setPadding(dp(16f), dp(24f), dp(16f), dp(24f))
    }

    private inner class EventRow(ctx: Context) : LinearLayout(ctx) {
        private val icon = ImageView(ctx)
        private val title = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 16f; typeface = N9.FONT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val time = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 12f; typeface = N9.FONT }
        private val body = TextView(ctx).apply {
            setTextColor(N9.TEXT_SECONDARY); textSize = 14f; typeface = N9.FONT
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        private var item: EventItem? = null
        private val status = TextView(ctx).apply {
            setTextColor(N9.TEXT_SECONDARY); textSize = 13f; typeface = N9.FONT; text = "Ongoing"; visibility = GONE
        }
        /** Live update progress: a flat bar, app colour on dark grey. */
        private val bar = object : View(ctx) {
            var fraction = 0f; var color = N9.ACCENT
            private val p = android.graphics.Paint()
            override fun onDraw(c: android.graphics.Canvas) {
                p.color = 0xFF3A3A3A.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
                p.color = color; c.drawRect(0f, 0f, width * fraction, height.toFloat(), p)
            }
        }.apply { visibility = GONE; contentDescription = "Live update progress" }

        init {
            orientation = HORIZONTAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            addView(icon, LayoutParams(dp(56f), dp(56f)).apply { marginEnd = dp(14f) })
            val titleRow = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(time)
            }
            addView(LinearLayout(ctx).apply {
                orientation = VERTICAL
                addView(titleRow)
                addView(body)
                addView(status)
                addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(5f)).apply { topMargin = dp(6f) })
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { item?.let { e -> onTap(e) { open(e) } } }
            setOnLongClickListener { item?.let { onLongPress(it) } ?: onLongPress(); true }
        }

        fun bind(e: EventItem, nested: Boolean, card: Boolean = false, live: Boolean = false) {
            item = e
            status.visibility = if (live) VISIBLE else GONE
            bar.visibility = if (live) VISIBLE else GONE
            if (live) {
                bar.fraction = if (e.indeterminate || e.progressMax <= 0) 1f else e.progress.toFloat() / e.progressMax
                bar.color = if (e.color != 0 && android.graphics.Color.alpha(e.color) == 255) e.color else N9.ACCENT
                bar.invalidate()
            }
            setPadding(dp(if (nested) 86f else 16f), dp(10f), dp(16f), dp(10f))
            background = if (card) cardBackground() else null
            (layoutParams as? MarginLayoutParams)?.setMargins(if (card) dp(10f) else 0, if (card) dp(3f) else 0,
                if (card) dp(10f) else 0, if (card) dp(3f) else 0)
            icon.visibility = if (nested) GONE else VISIBLE
            icon.setImageBitmap(iconFor(e))
            title.text = e.title.ifEmpty { e.text }
            body.text = if (e.title.isEmpty()) "" else e.text
            body.visibility = if (body.text.isEmpty()) GONE else VISIBLE
            time.text = if (DateUtils.isToday(e.time)) DateFormat.getTimeFormat(context).format(e.time)
            else DateFormat.getDateFormat(context).format(e.time)
        }
    }

    private val fallbackIcons = mutableMapOf<String, android.graphics.Bitmap>()

    /** App icon, or the notification's own small icon on a dark tile when the app is not visible to us. */
    private fun iconFor(e: EventItem): android.graphics.Bitmap? =
        AppRepository.iconFor(context, e.pkg) ?: fallbackIcons.getOrPut(e.pkg) {
            val px = dp(58f)
            val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.drawPath(IconFactory.shape(px.toFloat()), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = N9.TILE })
            e.smallIcon?.loadDrawable(context)?.mutate()?.apply {
                setTint(N9.TEXT)
                val inset = px / 4
                setBounds(inset, inset, px - inset, px - inset)
                draw(c)
            }
            bmp
        }

    private fun appLabel(pkg: String): String {
        val pm = context.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    /** Collapsed: app name, newest event and a count. Tap toggles the list of that app's events. */
    /** An RSS item, N9 Feed style: site icon, bold title, the text, then source and time. */
    private inner class FeedRow(ctx: Context) : LinearLayout(ctx) {
        private val icon = ImageView(ctx)
        private val title = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 16f; typeface = N9.FONT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val text = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 14f; typeface = N9.FONT
            maxLines = 4; ellipsize = TextUtils.TruncateAt.END
        }
        private val meta = TextView(ctx).apply {
            setTextColor(N9.TEXT_SECONDARY); textSize = 12f; typeface = N9.FONT
        }
        private var id = ""
        private var link = ""

        init {
            orientation = HORIZONTAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(12f))
            addView(icon, LayoutParams(dp(56f), dp(56f)).apply { marginEnd = dp(14f) })
            addView(LinearLayout(ctx).apply {
                orientation = VERTICAL
                addView(title)
                addView(text)
                addView(meta, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener {
                runCatching { context.startActivity(Feeds.openItem(id, link)) }
            }
        }

        fun bind(item: Feeds.Item) {
            id = item.id
            link = item.link
            // As on the N9: the feed's name, then "title: summary", then the time.
            title.text = item.feedTitle
            text.text = if (item.summary.isEmpty()) item.title else "${item.title}: ${item.summary}"
            meta.text = if (DateUtils.isToday(item.time)) DateFormat.getTimeFormat(context).format(java.util.Date(item.time))
                else DateUtils.formatDateTime(context, item.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
            icon.setImageDrawable(Feeds.feedIcon(context))
        }
    }

    private inner class GroupRow(ctx: Context) : LinearLayout(ctx) {
        private val icon = ImageView(ctx)
        private val title = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 16f; typeface = N9.FONT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val count = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 12f; typeface = N9.FONT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(24f)
            setPadding(dp(7f), dp(1f), dp(7f), dp(1f))
            background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(N9.ACCENT) }
        }
        private val body = TextView(ctx).apply {
            setTextColor(N9.TEXT_SECONDARY); textSize = 14f; typeface = N9.FONT
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val latest = TextView(ctx).apply {
            setTextColor(N9.TEXT_SECONDARY); textSize = 12f; typeface = N9.FONT
        }
        private var pkg = ""

        init {
            orientation = HORIZONTAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            addView(icon, LayoutParams(dp(56f), dp(56f)).apply { marginEnd = dp(14f) })
            val titleRow = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(count)
            }
            addView(LinearLayout(ctx).apply {
                orientation = VERTICAL
                addView(titleRow)
                addView(body)
                addView(latest)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { val f = first; if (dismissMode && f != null) reclassify(f) else toggle(key) }
            setOnLongClickListener { onLongPress(); true }
        }

        private var key = ""
        private var first: EventItem? = null

        fun bind(g: Row.Group) {
            key = g.key
            first = g.items.first()
            pkg = g.items.first().pkg
            icon.setImageBitmap(iconFor(g.items.first()))
            // N9: "3 new messages", then who they're from, then when the latest arrived.
            val n = g.items.size
            title.text = groupTitle(g.items)
            count.visibility = GONE
            body.text = if (g.open) "Tap to collapse"
                else g.items.map { it.title }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
            latest.text = "Latest " + DateFormat.getTimeFormat(context).format(java.util.Date(g.items.first().time))
        }
    }

    /**
     * What a group is, never "from <app>" (an app name there reads like a sender): "2 missed calls",
     * "3 new messages", "2 new emails", else "2 <App> notifications". Who it's from goes underneath.
     */
    internal fun groupTitle(items: List<EventItem>): String {
        val n = items.size
        fun all(p: (EventItem) -> Boolean) = items.all(p)
        return when {
            all { it.category == android.app.Notification.CATEGORY_MISSED_CALL || it.channelId.contains("missed", true) } ->
                if (n == 1) "1 missed call" else "$n missed calls"
            all { it.category == android.app.Notification.CATEGORY_EMAIL } -> "$n new emails"
            all { it.category == android.app.Notification.CATEGORY_MESSAGE || it.category == android.app.Notification.CATEGORY_EMAIL } -> "$n new messages"
            else -> "$n ${appLabel(items.first().pkg)} notifications"
        }
    }

    /** Flat N9 surface (no rounded cards on home views); used only by the Android/iPhone-style layouts. */
    private fun cardBackground() = GradientDrawable().apply { setColor(N9.CARD) }

    /** Android style: one card per app, its notifications listed inside. */
    private inner class CardRow(ctx: Context) : LinearLayout(ctx) {
        init {
            orientation = VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = cardBackground()
        }

        fun bind(items: List<EventItem>) {
            (layoutParams as? MarginLayoutParams)?.setMargins(dp(10f), dp(4f), dp(10f), dp(4f))
            removeAllViews()
            val first = items.first()
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(context).apply { setImageBitmap(iconFor(first)) }, LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(8f) })
                addView(TextView(context).apply {
                    text = appLabel(first.pkg); setTextColor(N9.TEXT_SECONDARY); textSize = 13f; typeface = N9.FONT
                })
            })
            items.take(5).forEach { e ->
                addView(TextView(context).apply {
                    text = listOf(e.title, e.text).filter { it.isNotEmpty() }.joinToString(": ")
                    setTextColor(N9.TEXT); textSize = 14f; typeface = N9.FONT
                    maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(6f), 0, 0)
                    setOnClickListener { onTap(e) { open(e) } }
                    setOnLongClickListener { onLongPress(e); true }
                })
            }
            if (items.size > 5) addView(TextView(context).apply {
                text = "+${items.size - 5} more"; setTextColor(N9.TEXT_SECONDARY); textSize = 13f; setPadding(0, dp(6f), 0, 0)
            })
        }
    }

    /** iPhone style: the newest notification on top of a stack; tap to fan it out. */
    private inner class StackRow(ctx: Context) : FrameLayout(ctx) {
        private val layers = List(2) { View(ctx) }
        private val top = EventRow(ctx)
        private val collapse = TextView(ctx).apply {
            text = "Show less"; setTextColor(N9.ACCENT); textSize = 13f; typeface = N9.FONT_BOLD
            setPadding(dp(16f), dp(8f), dp(16f), dp(4f))
        }
        private var key = ""

        init {
            layers.forEachIndexed { i, v ->
                v.background = GradientDrawable().apply { setColor(if (i == 0) 0xFF141414.toInt() else 0xFF181818.toInt()) }
                addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    val inset = dp(10f + 8f * (2 - i))
                    setMargins(inset, dp(3f) + dp(6f) * (2 - i), inset, 0)
                })
            }
            addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(12f)) })
            addView(collapse, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END))
            top.setOnClickListener { toggle(key) }
            collapse.setOnClickListener { toggle(key) }
        }

        fun bind(r: Row.Stack) {
            key = r.key
            val open = r.items.isEmpty()
            layers.forEach { it.visibility = if (open) GONE else VISIBLE }
            top.visibility = if (open) GONE else VISIBLE
            collapse.visibility = if (open) VISIBLE else GONE
            if (!open) {
                top.bind(r.items.first(), nested = false, card = true)
                top.setOnClickListener { toggle(key) }
            }
        }
    }

    /** A calendar entry for today. */
    private inner class AgendaView(ctx: Context) : LinearLayout(ctx) {
        private val time = TextView(ctx).apply { setTextColor(N9.ACCENT); textSize = 14f; typeface = N9.FONT_BOLD; minWidth = dp(64f) }
        private val title = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 15f; typeface = N9.FONT; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private var entry: Agenda.Entry? = null

        init {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            addView(time)
            addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { entry?.let { e -> runCatching { context.startActivity(e.intent()) } } }
        }

        fun bind(e: Agenda.Entry) {
            entry = e
            time.text = if (e.allDay) "All day" else DateFormat.getTimeFormat(context).format(e.begin)
            title.text = e.title
        }
    }

    /** "Low priority (12)"; tap to show or hide them. */
    private inner class LowRow(ctx: Context) : LinearLayout(ctx) {
        private val label = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 15f; typeface = N9.FONT_BOLD }
        private val state = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 14f; typeface = N9.FONT }

        init {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(8f))
            addView(label)
            addView(View(ctx).apply { setBackgroundColor(N9.DIVIDER) },
                LayoutParams(0, dp(1f), 1f).apply { marginStart = dp(12f); marginEnd = dp(12f) })
            addView(state)
            setOnClickListener { lowOpen = !lowOpen; refresh() }
        }

        fun bind(r: Row.LowToggle) {
            label.text = "${EventsClassifier.Section.LOW.title} (${r.count})"
            state.text = if (r.open) "Hide" else "Show"
        }
    }

    /**
     * Ghost rows (story Phone v2 → Try it: ghost rows): the kept dismissed notifications as ONE item.
     * Whole rows fade gently (0.5, then -0.05 a row); a half row is cut and masked so it fades only
     * where it slips under the next header. Tap: the Earlier page. Swipe in dismiss mode: they all expire.
     */
    private inner class GhostView(ctx: Context) : LinearLayout(ctx) {
        var half = false; private set
        private var shownTop: String? = null
        private var shown = emptyList<String>()

        init {
            orientation = VERTICAL
            setOnClickListener { context.startActivity(Intent(context, EarlierActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            // N9: long-press is dismiss mode, as on every Events item; then a swipe expires them all.
            setOnLongClickListener { onLongPress(); true }
        }

        fun bind(all: List<Ghosts.Ghost>) {
            val big = Ghosts.big(context)
            val amount = Ghosts.rows(context)
            val whole = amount.toInt()
            half = amount % 1f != 0f
            val vis = all.take(whole + if (half) 1 else 0)
            half = half && vis.size == whole + 1
            contentDescription = "Earlier notifications (${all.size})"
            removeAllViews()
            vis.forEachIndexed { i, g ->
                val row = if (big) EventRow(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    bind(EventItem(g.key, g.pkg, g.title, g.text, g.time, null, false, null), nested = false)
                    setOnClickListener(null); setOnLongClickListener(null); isClickable = false; isLongClickable = false
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                } else smallRow(g)
                val cut = half && i == whole
                val v = if (cut) FadeClip(context, dp(if (big) 40f else 16f)).apply { addView(row) } else row
                v.alpha = 0.5f - i * 0.05f
                v.tag = "ghost:" + g.id
                addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
            // Dismissing: the new one drops in as the top ghost row and settles to ghost opacity.
            val top = vis.firstOrNull()?.id
            if (top != null && shownTop != null && top != shownTop && top !in shown) getChildAt(0)?.let { v ->
                val target = v.alpha
                v.alpha = 0.9f; v.translationY = -dp(28f).toFloat(); v.scaleX = 1.03f; v.scaleY = 1.03f
                v.animate().alpha(target).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(200).setDuration(400)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.3f)).start()
            }
            shownTop = top; shown = vis.map { it.id }
        }

        private fun smallRow(g: Ghosts.Ghost) = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(30f)
            setPadding(dp(16f), 0, dp(16f), 0)
            addView(ImageView(context).apply {
                setImageBitmap(iconFor(EventItem(g.key, g.pkg, g.title, g.text, g.time, null, false, null)))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(10f) })
            addView(TextView(context).apply {
                text = listOf(g.title, g.text).filter { it.isNotEmpty() }.joinToString(": ")
                setTextColor(N9.TEXT); textSize = 15f; typeface = N9.FONT
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = if (DateUtils.isToday(g.time)) DateFormat.getTimeFormat(context).format(g.time)
                    else DateFormat.getDateFormat(context).format(g.time)
                setTextColor(N9.TEXT); textSize = 13f; typeface = N9.FONT
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10f) })
        }
    }

    /** A row cut to [h] px, faded out towards its bottom (mask: opaque to 20%, then to transparent). */
    private class FadeClip(ctx: Context, private val h: Int) : FrameLayout(ctx) {
        private val paint = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        init { setWillNotDraw(false) }
        override fun onMeasure(w: Int, hs: Int) {
            val c = getChildAt(0)
            c?.measure(w, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            setMeasuredDimension(MeasureSpec.getSize(w), h)
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            getChildAt(0)?.let { it.layout(0, 0, it.measuredWidth, it.measuredHeight) }
        }
        override fun dispatchDraw(c: android.graphics.Canvas) {
            val save = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            super.dispatchDraw(c)
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000), floatArrayOf(0f, 0.2f, 1f),
                android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            c.restoreToCount(save)
        }
    }

    /** Dismissing folds a notification out of the list: fade, a slight drop, then the rest closes up. */
    private inner class FoldAnimator : androidx.recyclerview.widget.DefaultItemAnimator() {
        private val folding = mutableSetOf<RecyclerView.ViewHolder>()
        init { removeDuration = 250; moveDuration = 300 }
        override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
            val v = holder.itemView
            if (v is GhostView || v is TopRow) return super.animateRemove(holder)
            folding += holder
            dispatchRemoveStarting(holder)
            v.animate().alpha(0f).translationY(dp(24f).toFloat()).scaleX(0.96f).scaleY(0.96f).setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction { done(holder) }.start()
            return false
        }
        private fun done(holder: RecyclerView.ViewHolder) {
            if (!folding.remove(holder)) return
            val v = holder.itemView
            v.animate().setListener(null); v.alpha = 1f; v.translationY = 0f; v.scaleX = 1f; v.scaleY = 1f
            dispatchRemoveFinished(holder); if (!isRunning) dispatchAnimationsFinished()
        }
        override fun isRunning() = folding.isNotEmpty() || super.isRunning()
        override fun endAnimation(item: RecyclerView.ViewHolder) {
            if (item in folding) { item.itemView.animate().cancel(); done(item) }
            super.endAnimation(item)
        }
        override fun endAnimations() {
            folding.toList().forEach { it.itemView.animate().cancel(); done(it) }
            super.endAnimations()
        }
    }

    /** A Today item: a lit squircle sized to the line, then one line of text (story state screen-time). */
    private inner class TodayView(ctx: Context) : LinearLayout(ctx) {
        private val tile = LitSquircle(ctx, N9.TILE)
        private val text = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 15f; typeface = N9.FONT_LIGHT
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private var item: Today.Item? = null
        init {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(30f)
            setPadding(dp(16f), 0, dp(16f), 0)
            addView(tile, LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(10f) })
            addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { item?.intent?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }
        }
        fun bind(t: Today.Item) {
            item = t; tile.set(t.color, t.glyph); text.text = t.text; contentDescription = t.text
        }
    }

    /**
     * A priority person (story state priority): their photo (or initials) as a squircle, the name
     * with a gold star, then one line per notification from any app: app icon, text, age.
     */
    private inner class PriorityView(ctx: Context) : LinearLayout(ctx) {
        private val avatar = ImageView(ctx)
        private val name = TextView(ctx).apply { setTextColor(N9.TEXT); textSize = 18f; typeface = N9.FONT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val star = ImageView(ctx).apply { setImageDrawable(Glyphs.drawable("star", 0xFFF2B111.toInt())); contentDescription = "Priority person" }
        private val lines = LinearLayout(ctx).apply { orientation = VERTICAL }
        init {
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            addView(avatar, LayoutParams(dp(56f), dp(56f)).apply { marginEnd = dp(14f) })
            addView(LinearLayout(ctx).apply {
                orientation = VERTICAL
                addView(LinearLayout(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(name, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f))
                    addView(star, LayoutParams(dp(16f), dp(16f)).apply { marginStart = dp(6f) })
                })
                addView(lines)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        fun bind(g: Row.PriorityGroup) {
            name.text = g.person.name
            contentDescription = "Priority: ${g.person.name}"
            avatar.setImageBitmap(avatarFor(g.person))
            lines.removeAllViews()
            g.items.take(5).forEach { e ->
                lines.addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(26f)
                    addView(ImageView(context).apply { setImageBitmap(iconFor(e)) }, LayoutParams(dp(20f), dp(20f)).apply { marginEnd = dp(8f) })
                    addView(TextView(context).apply {
                        text = if (e.title.equals(g.person.name, true) || e.title.isBlank()) e.text.ifBlank { e.title } else e.title
                        setTextColor(N9.TEXT); textSize = 15f; typeface = N9.FONT; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply { text = age(e.time); setTextColor(N9.TEXT_SECONDARY); textSize = 14f; typeface = N9.FONT },
                        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f) })
                    contentDescription = "${g.person.name}: ${e.title} ${e.text}"
                    setOnClickListener { onTap(e) { open(e) } }
                    setOnLongClickListener { onLongPress(e); true }
                })
            }
        }
    }

    /** "2 min", "1 h", or the date. */
    private fun age(t: Long): String {
        val m = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
        return when { m < 1 -> "now"; m < 60 -> "$m min"; m < 24 * 60 -> "${m / 60} h"; else -> DateFormat.getDateFormat(context).format(t) }
    }

    private val avatars = mutableMapOf<Long, android.graphics.Bitmap>()
    /** The contact photo as a squircle, or their initials on a lit blue squircle. */
    private fun avatarFor(p: Priority.Person): android.graphics.Bitmap = avatars.getOrPut(p.id) {
        val px = dp(56f)
        p.photo?.let { u -> runCatching { context.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() }
            ?.let { return@getOrPut IconFactory.squircled(it, px) }
        val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawPath(IconFactory.shape(px.toFloat()), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, px.toFloat(), 0xFF7FB0D8.toInt(), 0xFF2A5B8A.toInt(), android.graphics.Shader.TileMode.CLAMP)
        })
        val initials = p.name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = N9.TEXT; textSize = px * 0.36f; typeface = N9.FONT_BOLD; textAlign = android.graphics.Paint.Align.CENTER
        }
        c.drawText(initials, px / 2f, px / 2f - (tp.descent() + tp.ascent()) / 2, tp)
        bmp
    }
}
