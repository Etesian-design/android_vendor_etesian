package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Optional Widgets view: widgets flow on a 4-column grid.
 * Long-press anywhere on the page enters edit mode (resize handles on three corners, Belle-style ✕
 * badges, drag to rearrange).
 */
@SuppressLint("ViewConstructor")
/**
 * A view of widgets. As one scrolling list ([fixed] false) it holds every widget; as one of the fixed
 * screens ([fixed] true, numbered by [screen]) it is cut to the display and never scrolls, so vertical
 * drags reach the widgets themselves (lists in mail or feed widgets) instead of moving the view.
 */
class WidgetsPage(private val activity: Activity, private val screen: Int = 0, private val fixed: Boolean = false) :
    FrameLayout(activity), HomePage {

    companion object {
        private const val HOST_ID = 0x4E39
        const val REQ_BIND = 0x4E01
        const val REQ_CONFIGURE = 0x4E02
        private const val COLUMNS = 4
        private const val MAX_ROWS = 8
        private const val KEY_ITEMS = "widget_items"
        private const val KEY_LEGACY_IDS = "widget_ids"
        /** Hold to enter edit mode: twice the system long-press, so a slow scroll never triggers it. */
        private const val EDIT_HOLD_MS = 800L
        /** In edit mode, hold this long before a widget follows the finger. */
        private const val MOVE_HOLD_MS = 300L

        /** For the on-device tests (TestNotifyReceiver): the list, or the first fixed screen. */
        var current: WidgetsPage? = null
            private set

        /** One host for every widget view: a second host with the same id would take the updates from the first. */
        private var sharedHost: Host? = null
        private fun host(ctx: Context) = sharedHost ?: Host(ctx.applicationContext).also { sharedHost = it }

        private fun key(screen: Int) = if (screen == 0) KEY_ITEMS else "${KEY_ITEMS}_$screen"

        /**
         * Before the views are built: as a list, every fixed screen's widgets join the list (in screen
         * order); as [screens] fixed screens, the widgets of screens beyond them join the last one.
         */
        fun arrange(ctx: Context, fixed: Boolean, screens: Int) {
            val prefs = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)
            val keep = if (fixed) screens.coerceAtLeast(1) else 1
            val extra = prefs.all.keys.mapNotNull { k -> k.removePrefix("${KEY_ITEMS}_").takeIf { it != k }?.toIntOrNull() }
                .filter { it >= keep }.sorted()
            if (extra.isEmpty()) return
            val into = key(keep - 1)
            val merged = (listOf(prefs.getString(into, "")) + extra.map { prefs.getString(key(it), "") })
                .filterNot { it.isNullOrEmpty() }.joinToString(",")
            prefs.edit().putString(into, merged).apply { extra.forEach { remove(key(it)) } }.apply()
        }
    }

    /** The next fixed screen (further left), which takes the widgets that do not fit on this one. */
    var next: WidgetsPage? = null

    /** One placed widget: its id and size in grid cells. */
    private class Item(val id: Int, var cols: Int, var rows: Int)

    private val awm = AppWidgetManager.getInstance(context)
    private val host = host(context)
    private val itemsKey = key(screen)
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val items = mutableListOf<Item>()
    private val frames = mutableMapOf<Int, WidgetFrame>()
    private var pendingId = -1
    private var editMode = false

    private val grid = WidgetGrid(context)
    private val scroll = Scroller(context).apply { clipToPadding = false; isVerticalScrollBarEnabled = false }

    /** Scrolls as a list; a fixed screen scrolls only if it still holds more than fits (its last screen). */
    private inner class Scroller(ctx: Context) : ScrollView(ctx) {
        private fun can() = !fixed || overflowing
        override fun onInterceptTouchEvent(ev: MotionEvent) = can() && super.onInterceptTouchEvent(ev)
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent) = can() && super.onTouchEvent(ev)
    }

    /** Rows that fit on a fixed screen (0 until measured). */
    private var rowsFit = 0
    private var overflowing = false
    private val maxRows get() = if (fixed && rowsFit > 0) rowsFit else MAX_ROWS

    /** On the page only while it has no widgets; otherwise Add widget lives in the edit bar. */
    private val addButton = N9.editButton(context, "Add widget") { pick() }
    /** The app grid's edit bar (n9-grid-edit.png): Done and Add widget, stuck to the bottom. */
    private val editBar = N9.editBar(context,
        N9.editButton(context, "Done") { exitModes() },
        N9.editButton(context, "Add widget") { pick() }).apply { visibility = GONE }
    private var insetBottom = 0

    /**
     * The edit-mode hold is timed here, on the page, so it covers empty space too and is cancelled
     * by any movement or by the pager taking the swipe (which sends this page a cancel). Timed on a
     * widget it kept running after the swipe moved to the next view and fired there.
     */
    private val pageSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var holdX = 0f
    private var holdY = 0f
    private var holdFrame: WidgetFrame? = null
    private val editHold = Runnable {
        enterEdit()
        // The same finger keeps the widget it is on: drag straight on without lifting.
        holdFrame?.pickUp()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(editHold)
                if (!editMode) {
                    holdX = ev.rawX; holdY = ev.rawY
                    holdFrame = frameAt(ev.rawX, ev.rawY)
                    postDelayed(editHold, EDIT_HOLD_MS)
                }
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.rawX - holdX) > pageSlop || abs(ev.rawY - holdY) > pageSlop) removeCallbacks(editHold)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(editHold)
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Takes the down so the rest of the gesture reaches the page (and its hold timer). */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = event.actionMasked == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)

    override fun onDetachedFromWindow() {
        removeCallbacks(editHold)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != VISIBLE) removeCallbacks(editHold)
        super.onWindowVisibilityChanged(visibility)
    }

    private fun frameAt(rawX: Float, rawY: Float): WidgetFrame? {
        val loc = IntArray(2)
        return frames.values.firstOrNull { f ->
            f.getLocationOnScreen(loc)
            rawX >= loc[0] && rawX < loc[0] + f.width && rawY >= loc[1] && rawY < loc[1] + f.height
        }
    }

    init {
        val buttons = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(addButton, LinearLayout.LayoutParams(dp(N9.EDIT_BUTTON_W), dp(N9.EDIT_BUTTON_H)))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(16f), 0, dp(16f))
            })
        }
        scroll.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(editBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        load()
        updateAddButton()
        if (screen == 0) current = this
    }

    /** Tests only: binds and places this app's widget [cls] without the picker or its setup screen. */
    fun addForTest(cls: String) {
        val provider = android.content.ComponentName(context, "${context.packageName}.$cls")
        val id = host.allocateAppWidgetId()
        if (awm.bindAppWidgetIdIfAllowed(id, provider)) commit(id) else host.deleteAppWidgetId(id)
    }

    // ---- persistence ----

    private fun load() {
        val saved = prefs.getString(itemsKey, null)
        val loaded = if (saved != null) {
            saved.split(',').mapNotNull { e ->
                val p = e.split(':').mapNotNull { it.toIntOrNull() }
                if (p.size == 3) Item(p[0], p[1], p[2]) else null
            }
        } else if (screen != 0) emptyList() else {
            // Widgets from the first version were full-width.
            prefs.getString(KEY_LEGACY_IDS, "")!!.split(',').mapNotNull { it.toIntOrNull() }
                .map { id -> Item(id, COLUMNS, awm.getAppWidgetInfo(id)?.let { defaultRows(it) } ?: 2) }
        }
        loaded.forEach { item ->
            if (awm.getAppWidgetInfo(item.id) == null) host.deleteAppWidgetId(item.id)
            else { items += item; createFrame(item) }
        }
        save()
    }

    private fun save() = prefs.edit()
        .putString(itemsKey, items.joinToString(",") { "${it.id}:${it.cols}:${it.rows}" })
        .apply { if (screen == 0) remove(KEY_LEGACY_IDS) }
        .apply()

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    override fun applyInsets(left: Int, top: Int, right: Int, bottom: Int) {
        insetBottom = bottom
        editBar.setPadding(dp(8f), dp(10f), dp(8f), dp(10f) + bottom)
        padScroll(left, top, right)
    }

    private fun padScroll(left: Int = scroll.paddingLeft - dp(8f), top: Int = scroll.paddingTop, right: Int = scroll.paddingRight - dp(8f)) =
        // Room for the edit bar while it shows, so the last widget can scroll above it. A fixed screen
        // always keeps that room, so its rows stay put when edit mode starts.
        scroll.setPadding(left + dp(8f), top, right + dp(8f), insetBottom + dp(16f) + if (editMode || fixed) dp(64f) else 0)

    private fun updateAddButton() { addButton.visibility = if (items.isEmpty() && !editMode) VISIBLE else GONE }

    // ---- fixed screens ----

    /** Height in rows of [list] packed on the grid; fills [out] with each widget's cells. */
    private fun pack(list: List<Item>, out: MutableMap<Int, Rect>? = null): Int {
        val taken = mutableListOf<BooleanArray>()
        fun free(r: Int, c: Int): Boolean { while (taken.size <= r) taken += BooleanArray(COLUMNS); return !taken[r][c] }
        var height = 0
        for (item in list) {
            val cols = item.cols.coerceIn(1, COLUMNS)
            var row = 0
            search@ while (true) {
                for (col in 0..COLUMNS - cols) {
                    val fits = (row until row + item.rows).all { r -> (col until col + cols).all { c -> free(r, c) } }
                    if (fits) {
                        for (r in row until row + item.rows) for (c in col until col + cols) taken[r][c] = true
                        out?.put(item.id, Rect(col, row, col + cols, row + item.rows))
                        height = max(height, row + item.rows)
                        break@search
                    }
                }
                row++
            }
        }
        return height
    }

    /** True if [list] fits on this screen (always, as a list or before the screen is measured). */
    private fun fits(list: List<Item>) = !fixed || rowsFit == 0 || pack(list) <= rowsFit

    /** Moves the widgets that do not fit on to the next screen; with none left, this screen scrolls. */
    private fun spill() {
        val target = next
        val moved = mutableListOf<Item>()
        while (target != null && items.size > 1 && !fits(items)) moved.add(0, items.removeAt(items.lastIndex))
        moved.forEach { frames.remove(it.id)?.let(grid::removeView) }
        if (moved.isNotEmpty()) { save(); updateAddButton(); target?.adopt(moved.map { Triple(it.id, it.cols, it.rows) }) }
        overflowing = !fits(items)
    }

    /** Takes widgets spilled from the previous screen, in front of this screen's own. */
    private fun adopt(moved: List<Triple<Int, Int, Int>>) {
        val list = moved.map { Item(it.first, it.second, it.third) }
        items.addAll(0, list)
        list.forEach { createFrame(it) }
        save(); updateAddButton()
        grid.requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (fixed) post { grid.requestLayout() }
    }

    // ---- edit mode ----

    private fun enterEdit() {
        if (editMode) return
        editMode = true
        frames.values.forEach { it.setEditing(true) }
        N9Sounds.play(context, N9Sounds.EDIT)
        editBar.visibility = VISIBLE
        updateAddButton(); padScroll()
    }

    override fun exitModes(): Boolean {
        if (!editMode) return false
        editMode = false
        frames.values.forEach { it.setEditing(false) }
        editBar.visibility = GONE
        updateAddButton(); padScroll()
        save()
        return true
    }

    private fun remove(item: Item) {
        val frame = frames.remove(item.id) ?: return
        frame.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(150).withEndAction {
            TransitionManager.beginDelayedTransition(grid, ChangeBounds().setDuration(200))
            grid.removeView(frame)
            items.remove(item)
            host.deleteAppWidgetId(item.id)
            save()
            updateAddButton()
            if (items.isEmpty()) exitModes()
        }.start()
    }

    /** Moves the dragged item to the slot under (x, y) in grid coordinates. */
    private fun dragOver(item: Item, x: Float, y: Float) {
        val target = items.firstOrNull { it !== item && frames[it.id]?.let { f -> x >= f.left && x < f.right && y >= f.top && y < f.bottom } == true }
            ?: return
        val from = items.indexOf(item)
        val to = items.indexOf(target)
        if (from == to) return
        val order = items.toMutableList().apply { add(to, removeAt(from)) }
        if (!fits(order)) return
        TransitionManager.beginDelayedTransition(grid, ChangeBounds().setDuration(180).excludeTarget(frames[item.id], true))
        items.add(to, items.removeAt(from))
        grid.requestLayout()
    }

    private fun resize(item: Item, cols: Int, rows: Int) {
        if (cols == item.cols && rows == item.rows) return
        val old = item.cols to item.rows
        item.cols = cols; item.rows = rows
        val ok = fits(items)
        item.cols = old.first; item.rows = old.second
        if (!ok) return
        TransitionManager.beginDelayedTransition(grid, ChangeBounds().setDuration(140))
        item.cols = cols
        item.rows = rows
        grid.requestLayout()
    }

    // ---- sizes ----

    private val cellEstimate get() = resources.displayMetrics.widthPixels / COLUMNS

    private fun cellsFor(px: Int, cell: Int) = max(1, ceil(px / cell.toFloat()).toInt())

    private fun defaultCols(info: AppWidgetProviderInfo) =
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) min(COLUMNS, info.targetCellWidth)
        else min(COLUMNS, cellsFor(info.minWidth, cellEstimate))

    private fun defaultRows(info: AppWidgetProviderInfo) =
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) min(maxRows, info.targetCellHeight)
        else min(maxRows, cellsFor(info.minHeight, cellEstimate))

    /** Tells the widget its new size (in dp) so it can adapt its layout. */
    private fun reportSize(frame: WidgetFrame) {
        val d = resources.displayMetrics.density
        val w = (frame.item.cols * grid.cell / d).toInt()
        val h = (frame.item.rows * grid.cellH / d).toInt()
        @Suppress("DEPRECATION")
        frame.hostView.updateAppWidgetSize(null, w, h, w, h)
    }

    // ---- adding ----

    private fun createFrame(item: Item): Boolean {
        val info = awm.getAppWidgetInfo(item.id) ?: return false
        val hostView = host.createView(context, item.id, info)
        val frame = WidgetFrame(context, item, info, hostView)
        frames[item.id] = frame
        grid.addView(frame)
        if (editMode) frame.setEditing(true)
        grid.post { reportSize(frame) }
        return true
    }

    private fun pick() = WidgetPicker(context) { bind(it) }.show()

    private fun bind(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) configureOrAdd(id)
        else {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider), REQ_BIND)
        }
    }

    private fun configureOrAdd(id: Int) {
        val info = awm.getAppWidgetInfo(id)
        if (info?.configure != null) {
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null) }
                .onFailure { commit(id) }
        } else commit(id)
    }

    private fun commit(id: Int) {
        pendingId = -1
        val info = awm.getAppWidgetInfo(id) ?: run { host.deleteAppWidgetId(id); return }
        val item = Item(id, defaultCols(info), defaultRows(info))
        if (!fits(items + item)) {
            host.deleteAppWidgetId(id)
            android.widget.Toast.makeText(context, "No room on this screen", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        items += item
        createFrame(item)
        if (editMode) frames[id]?.setEditing(true)
        updateAddButton()
        save()
        scroll.post { scroll.fullScroll(FOCUS_DOWN) }
    }

    /** Returns true if the result belonged to this page. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return false
        val id = pendingId
        if (id == -1) return true
        if (resultCode != Activity.RESULT_OK) {
            host.deleteAppWidgetId(id)
            pendingId = -1
        } else if (requestCode == REQ_BIND) configureOrAdd(id) else commit(id)
        return true
    }

    private class Host(ctx: Context) : AppWidgetHost(ctx, HOST_ID)

    /** Packs widgets top-to-bottom on a 4-column grid, each into the first gap it fits. */
    private inner class WidgetGrid(ctx: Context) : ViewGroup(ctx) {
        var cell = 0
            private set
        /** A fixed screen stretches its rows to fill the display; a list keeps square cells. */
        var cellH = 0
            private set
        private val gap = dp(6f)
        private val placed = mutableMapOf<Int, Rect>()
        private val tap = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean { exitModes(); return true }
        })

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            cell = width / COLUMNS
            cellH = cell
            if (fixed && cell > 0) {
                val avail = scroll.height - scroll.paddingTop - scroll.paddingBottom
                if (avail > 0) {
                    rowsFit = max(1, avail / cell)
                    cellH = avail / rowsFit
                }
            }
            placed.clear()
            val rows = pack(items, placed)
            for (item in items) {
                val f = frames[item.id] ?: continue
                val r = placed[item.id] ?: continue
                f.measure(
                    MeasureSpec.makeMeasureSpec(r.width() * cell - 2 * gap, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(r.height() * cellH - 2 * gap, MeasureSpec.EXACTLY),
                )
            }
            setMeasuredDimension(width, max(rows * cellH, if (editMode) cellH else 0))
            // Too many rows for the screen (or no longer): pass the rest on, or let the screen scroll.
            if (fixed && rowsFit > 0 && (rows > rowsFit) != overflowing) post { spill() }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            for (item in items) {
                val f = frames[item.id] ?: continue
                val p = placed[item.id] ?: continue
                f.layout(p.left * cell + gap, p.top * cellH + gap, p.right * cell - gap, p.bottom * cellH - gap)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Tapping empty space between widgets leaves edit mode.
            if (!editMode) return false
            tap.onTouchEvent(event)
            return true
        }
    }

    /** A widget plus its edit-mode chrome: outline, ✕ badge and resize handle. */
    @SuppressLint("ViewConstructor")
    private inner class WidgetFrame(
        ctx: Context, val item: Item, private val info: AppWidgetProviderInfo, val hostView: AppWidgetHostView,
    ) : FrameLayout(ctx) {

        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f).toFloat(); color = N9.ACCENT }
        private val shade = Paint().apply { color = 0x33000000 }
        private val bounds = RectF()
        private var editing = false
        /** True while a gesture that started on the ✕ badge or resize handle is in progress. */
        private var chromeTouch = false

        private var downRawX = 0f
        private var downRawY = 0f
        private var startLeft = 0
        private var startTop = 0
        private var dragging = false
        private var longPressed = false

        /** The page's hold started edit mode on this widget: it is held already. */
        fun pickUp() {
            longPressed = true
            hold.run()
        }
        /** In edit mode a widget moves only after a short hold; moving sooner scrolls the page. */
        private var held = false
        private val hold = Runnable {
            held = true
            parent?.requestDisallowInterceptTouchEvent(true)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            bringToFront()
            animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
        }

        /** The app grid's red ✕ squircle, at the top right. */
        private val close = CloseBadge(ctx).apply {
            visibility = GONE
            contentDescription = "Remove widget"
            setOnClickListener { remove(item) }
        }

        private val resizable = info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE
        /**
         * Resize handles on every corner but the top right (the ✕): 44dp touch targets around a
         * 22dp dot. [sx]/[sy]: which way a drag grows the widget (left/top corners grow it outwards
         * too).
         */
        private fun handle(gravity: Int, sx: Int, sy: Int) = FrameLayout(context).apply {
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(N9.ACCENT); setStroke(dp(2f), N9.TEXT) }
            }, LayoutParams(dp(22f), dp(22f), gravity))
            visibility = GONE
            setOnTouchListener(ResizeTouch(sx, sy))
        }
        private val handleGravities = listOf(
            Triple(Gravity.BOTTOM or Gravity.END, 1, 1),
            Triple(Gravity.BOTTOM or Gravity.START, -1, 1),
            Triple(Gravity.TOP or Gravity.START, -1, -1),
        )
        private val handles = handleGravities.map { (g, sx, sy) -> handle(g, sx, sy) }

        init {
            setWillNotDraw(false)
            clipChildren = false
            hostView.setPadding(0, 0, 0, 0)
            addView(hostView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(close, LayoutParams(dp(30f), dp(30f), Gravity.TOP or Gravity.END))
            handles.forEachIndexed { i, h -> addView(h, LayoutParams(dp(44f), dp(44f), handleGravities[i].first)) }
        }

        fun setEditing(on: Boolean) {
            editing = on
            close.visibility = if (on) VISIBLE else GONE
            handles.forEach { it.visibility = if (on && resizable) VISIBLE else GONE }
            invalidate()
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (!editing) return
            val inset = outline.strokeWidth / 2
            bounds.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(bounds, dp(14f).toFloat(), dp(14f).toFloat(), shade)
            canvas.drawRoundRect(bounds, dp(14f).toFloat(), dp(14f).toFloat(), outline)
            // Keep the badges above the outline.
            drawChild(canvas, close, drawingTime)
            handles.forEach { drawChild(canvas, it, drawingTime) }
        }

        private fun hits(v: View, x: Float, y: Float) =
            v.visibility == VISIBLE && x >= v.left && x < v.right && y >= v.top && y < v.bottom

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (editing) {
                // Badge and handle keep the whole gesture; elsewhere a hold picks the widget up.
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    chromeTouch = hits(close, ev.x, ev.y) || handles.any { hits(it, ev.x, ev.y) }
                    if (chromeTouch) parent?.requestDisallowInterceptTouchEvent(true)
                }
                // The widget's own content is inert while editing: the frame takes every touch.
                return !chromeTouch
            }
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                startTouch(ev)
                longPressed = false
                chromeTouch = false
            }
            // After the page's long-press, keep the gesture so the user can drag straight away.
            return longPressed
        }

        private fun startTouch(ev: MotionEvent) {
            downRawX = ev.rawX
            downRawY = ev.rawY
            startLeft = left
            startTop = top
            dragging = false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            // Takes the down so the gesture stays here and can become a drag after the hold.
            if (!editing) return ev.actionMasked == MotionEvent.ACTION_DOWN || longPressed
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startTouch(ev)
                    held = longPressed
                    // Straight after the long-press that started edit mode the widget is already held.
                    if (held) post(hold) else postDelayed(hold, MOVE_HOLD_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!held) {
                        // Moved before the hold: a scroll; the scroll view takes it from here.
                        if (abs(dx) > slop || abs(dy) > slop) removeCallbacks(hold)
                        return true
                    }
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        // Follow the finger even when a reorder moves our layout slot.
                        translationX = dx + startLeft - left
                        translationY = dy + startTop - top
                        dragOver(item, left + translationX + width / 2f, top + translationY + height / 2f)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(hold)
                    if (held || dragging) {
                        animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(180).start()
                        if (dragging) save()
                    }
                    dragging = false
                    held = false
                    longPressed = false
                }
            }
            return true
        }

        /** Drag a corner handle; the size snaps to whole grid cells within the widget's limits. */
        private inner class ResizeTouch(private val sx: Int, private val sy: Int) : OnTouchListener {
            private var x0 = 0f
            private var y0 = 0f
            private var cols0 = 0
            private var rows0 = 0

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val cell = grid.cell.takeIf { it > 0 } ?: return false
                val cellH = grid.cellH.takeIf { it > 0 } ?: cell
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        x0 = e.rawX; y0 = e.rawY; cols0 = item.cols; rows0 = item.rows
                        this@WidgetFrame.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
                        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
                        val minCols = min(COLUMNS, cellsFor(info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth, cell))
                        val minRows = min(maxRows, cellsFor(info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight, cellH))
                        val cols = if (horizontal) (cols0 + (sx * (e.rawX - x0) / cell).roundToInt()).coerceIn(min(minCols, cols0), COLUMNS) else item.cols
                        val rows = if (vertical) (rows0 + (sy * (e.rawY - y0) / cellH).roundToInt()).coerceIn(min(minRows, rows0), maxRows) else item.rows
                        resize(item, cols, rows)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        save()
                        post { reportSize(this@WidgetFrame) }
                    }
                }
                return true
            }
        }
    }
}
