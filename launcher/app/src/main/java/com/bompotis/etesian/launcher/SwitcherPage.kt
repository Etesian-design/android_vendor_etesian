package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The Open applications view: live thumbnails, pinch for 3 columns / 2 columns / a carousel of
 * single large cards (Android Recents style, with screenshot and app info), long-press to close.
 */
@SuppressLint("ViewConstructor")
class SwitcherPage(context: Context) : FrameLayout(context), HomePage {

    private val prefs = context.getSharedPreferences("switcher", Context.MODE_PRIVATE)
    private val grid = GridLayoutManager(context, prefs.getInt("span", 2).coerceIn(2, 3))
    private val carouselLayout = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
    private val snap = androidx.recyclerview.widget.PagerSnapHelper()
    /** The most zoomed-in level: one card at a time, swiped sideways. */
    private var carousel = prefs.getInt("span", 2) == 1
    private fun barButton(label: String, onClick: () -> Unit) = N9.editButton(context, label, onClick)
    private val clearAll = barButton("Close all") {
        N9Sounds.play(context, N9Sounds.CLOSE_MULTIPLE)
        RunningApps.entries.toList().forEach { RunningApps.close(context, it.pkg) }
    }
    /** Leaves edit mode, and the carousel back to two cards a row. */
    private val done = barButton("Done") {
        exitModes()
        if (carousel) setSpan(2)
    }
    /** The N9's edit-mode toolbar: Done and Close all, also shown in the carousel. */
    private val bottomBar = N9.editBar(context, done, clearAll)
    private fun actionButton(label: String, onClick: (RunningApps.Entry) -> Unit) = TextView(context).apply {
        text = label
        setTextColor(N9.TEXT); textSize = 13f; typeface = N9.FONT
        gravity = Gravity.CENTER
        setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        background = N9.button(context)
        setOnClickListener { currentEntry()?.let(onClick) }
    }
    private val splitAction = actionButton("Split view") { e -> setSplitFirst(if (splitFirst === e) null else e) }
    private val popupAction = actionButton("Pop-up") { e ->
        setSplitFirst(null)
        if (Platform.popupTask?.invoke(e.taskId) == true) RunningApps.touch(context, e.pkg)
    }
    private val shotAction = actionButton("Screenshot") { e -> saveScreenshot(e) }
    /** Android extras → App pinning: pins the card in the middle (never on long-press, which is N9 edit mode). */
    private val pinAction = actionButton("Pin") { e ->
        if (e.taskId < 0) return@actionButton
        runCatching {
            Settings.Secure.putInt(context.contentResolver, "lock_to_app_enabled", 1)
            Settings.Secure.putInt(context.contentResolver, "lock_to_app_exit_locked", 1)
        }
        SystemTasks.pin(e.taskId)
    }
    private val infoAction = actionButton("App info") { e ->
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", e.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    /** Carousel: one set of actions for the card in the middle, as in Android's recents. */
    private val actionRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        for (b in listOf(splitAction, popupAction, shotAction, pinAction, infoAction)) {
            addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(4f); marginEnd = dp(4f) })
        }
    }
    private val list = PinchRecycler(context)
    private val adapter = Adapter()
    private var closeMode = false
    private var insets = intArrayOf(0, 0, 0, 0)
    /** The first application chosen for split screen, waiting for the second. */
    private var splitFirst: RunningApps.Entry? = null
    private val empty = TextView(context).apply {
        text = "No open applications"
        setTextColor(N9.TEXT_SECONDARY)
        textSize = 20f
        typeface = N9.FONT_LIGHT
        gravity = Gravity.CENTER
    }
    private val prompt = TextView(context).apply {
        text = "Enable Etesian swipe gestures for edge swipes and live previews"
        setTextColor(N9.TEXT)
        textSize = 14f
        typeface = N9.FONT
        gravity = Gravity.CENTER
        setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        setBackgroundColor(N9.CARD)
        setOnClickListener {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    /** A card is being dragged or merged: refreshes wait (a rebind would cancel the gesture). */
    private var dragging = false
    private var pendingRefresh = false
    private val listener: () -> Unit = { if (merge.busy || dragging) pendingRefresh = true else refresh() }

    /** N9 edit mode: hold a card and drag it anywhere to set your own order. */
    private val reorder = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
        androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN or
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT, 0) {
        override fun isLongPressDragEnabled() = closeMode && !carousel && !merge.busy
        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val f = from.bindingAdapterPosition; val t = to.bindingAdapterPosition
            if (f < 0 || t < 0) return false
            android.util.Log.i("EtesianSwitcher", "move $f->$t")
            RunningApps.move(context, f, t)
            adapter.notifyItemMoved(f, t)
            return true
        }
        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        /** The card under the dragged card's middle takes its place (not only past its far edge). */
        override fun chooseDropTarget(selected: RecyclerView.ViewHolder, targets: MutableList<RecyclerView.ViewHolder>,
                                      curX: Int, curY: Int): RecyclerView.ViewHolder? {
            val cx = curX + selected.itemView.width / 2; val cy = curY + selected.itemView.height / 2
            return targets.firstOrNull { val v = it.itemView; cx in v.left..v.right && cy in v.top..v.bottom }
        }
        override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, state: Int) {
            super.onSelectedChanged(vh, state)
            if (state == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) { dragging = true; android.util.Log.i("EtesianSwitcher", "drag start") }
            vh?.itemView?.animate()?.scaleX(1.06f)?.scaleY(1.06f)?.setDuration(120)?.start()
        }
        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            vh.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            dragging = false
            if (pendingRefresh) { pendingRefresh = false; rv.post { refresh() } }
        }
    })
    private val merge = Merge()

    init {
        list.layoutManager = if (carousel) carouselLayout else grid
        if (carousel) snap.attachToRecyclerView(list)
        list.adapter = adapter
        list.clipToPadding = false
        list.overScrollMode = OVER_SCROLL_NEVER
        list.itemAnimator?.changeDuration = 0
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(prompt, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(actionRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        reorder.attachToRecyclerView(list)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, state: Int) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) updateActions()
            }
        })
        val tap = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (closeMode && list.findChildViewUnder(e.x, e.y) == null) exitModes()
                return false
            }
            // N9: holding anywhere on the view (not only on a card) enters edit mode, when apps are open.
            override fun onLongPress(e: MotionEvent) {
                if (closeMode || merge.busy || dragging || RunningApps.entries.isEmpty()) return
                list.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                closeMode = true; adapter.notifyItemRangeChanged(0, adapter.itemCount, "mode"); updateClearAll()
            }
        })
        list.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean { tap.onTouchEvent(e); return false }
        })
        refresh()
    }

    /** A card's toolbar changed: rebind that card. */
    private val toolbarListener: (Int) -> Unit = toolbar@{ task ->
        if (dragging || merge.busy) { pendingRefresh = true; return@toolbar }
        val i = RunningApps.entries.indexOfFirst { it.taskId == task }
        if (i >= 0) adapter.notifyItemChanged(i)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        RunningApps.addListener(listener)
        TaskToolbars.addListener(context, toolbarListener)
    }
    override fun onDetachedFromWindow() {
        RunningApps.removeListener(listener)
        TaskToolbars.removeListener(toolbarListener)
        super.onDetachedFromWindow()
    }

    override fun onShown() = refresh()

    /** The carousel owns every horizontal swipe; it is left by pinching out or by edge swipes. */
    override fun holdsPaging(): Boolean = (carousel && RunningApps.entries.isNotEmpty()) || dragging || merge.busy ||
        // Edit mode: a card held for dragging must not page the home views away.
        (closeMode && !carousel)

    @SuppressLint("NotifyDataSetChanged")
    private fun refresh() {
        adapter.notifyDataSetChanged()
        val none = RunningApps.entries.isEmpty()
        empty.visibility = if (none) VISIBLE else GONE
        if (none && closeMode) closeMode = false
        updateClearAll()
        prompt.visibility = if (SystemTasks.available(context) || GestureService.isEnabled(context)) GONE else VISIBLE
    }

    /** In the carousel the first and last cards sit centred, so the sides get extra room. */
    private fun applyListPadding() {
        val (l, t, r, b) = insets.toList()
        val side = if (carousel) (resources.displayMetrics.widthPixels * 0.11f).toInt() else dp(10f)
        val bars = (if (bottomBar.visibility == VISIBLE) dp(68f) else 0) + (if (actionRow.visibility == VISIBLE) dp(56f) else 0)
        list.setPadding(l + side, t, r + side, b + dp(24f) + bars)
    }

    private fun updateClearAll() {
        val any = RunningApps.entries.isNotEmpty()
        bottomBar.visibility = if (any && (carousel || closeMode)) VISIBLE else GONE
        // Pure N9: the carousel has only Done / Close all. Android extras add the action row.
        val cardActions = Extras.on(context, Extras.CARD_ACTIONS)
        val pin = Extras.on(context, Extras.PINNING)
        for (b in listOf(splitAction, popupAction, shotAction, infoAction)) b.visibility = if (cardActions) VISIBLE else GONE
        pinAction.visibility = if (pin) VISIBLE else GONE
        actionRow.visibility = if (any && carousel && (cardActions || pin)) VISIBLE else GONE
        if (!cardActions && splitFirst != null) setSplitFirst(null)
        applyListPadding()
        list.post { updateActions() }
        centreCarousel()
    }

    /** Carousel: brings the nearest card to the middle (it opens with the first at the edge). */
    private fun centreCarousel() {
        if (!carousel) return
        list.post {
            val v = snap.findSnapView(carouselLayout) ?: return@post
            val d = snap.calculateDistanceToFinalSnap(carouselLayout, v) ?: return@post
            if (d[0] != 0) list.scrollBy(d[0], 0)
            updateActions()
        }
    }

    /** The card in the middle of the carousel. */
    private fun currentEntry(): RunningApps.Entry? {
        if (!carousel) return null
        val v = snap.findSnapView(carouselLayout) ?: return null
        return RunningApps.entries.getOrNull(list.getChildAdapterPosition(v))
    }

    /** Split view and pop-up need the system's task (the Gradle build has neither). */
    private fun updateActions() {
        val e = currentEntry()
        val real = e != null && e.taskId >= 0 && Platform.splitTasks != null
        popupAction.visibility = if (real) VISIBLE else GONE
        splitAction.visibility = if (real && RunningApps.entries.count { it.taskId >= 0 } > 1) VISIBLE else GONE
    }

    /** Carousel action: saves the application's current screen to Pictures/Screenshots. */
    private fun saveScreenshot(e: RunningApps.Entry) {
        val bmp = (if (e.taskId >= 0) SystemTasks.snapshot(e.taskId, resources.displayMetrics.widthPixels) else null) ?: e.thumb
        if (bmp == null) { android.widget.Toast.makeText(context, "No screenshot available", android.widget.Toast.LENGTH_SHORT).show(); return }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_${System.currentTimeMillis()}_${e.pkg}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
        }
        val ok = runCatching {
            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
        android.widget.Toast.makeText(context, if (ok) "Screenshot saved" else "Couldn't save the screenshot", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun applyInsets(left: Int, top: Int, right: Int, bottom: Int) {
        bottomBar.setPadding(dp(8f), dp(10f), dp(8f), dp(10f) + bottom)
        (actionRow.layoutParams as LayoutParams).bottomMargin = bottom + dp(68f + 8f)
        insets = intArrayOf(left, top, right, bottom)
        applyListPadding()
        (prompt.layoutParams as LayoutParams).setMargins(left + dp(16f), 0, right + dp(16f), bottom + dp(16f))
        prompt.requestLayout()
    }

    override fun exitModes(): Boolean {
        if (splitFirst != null) { setSplitFirst(null); return true }
        if (!closeMode) return false
        closeMode = false
        adapter.notifyItemRangeChanged(0, adapter.itemCount, "mode")
        updateClearAll()
        return true
    }

    private fun currentSpan() = if (carousel) 1 else grid.spanCount

    private fun setSpan(span: Int, animate: Boolean = true) {
        if (currentSpan() == span) return
        if (animate) TransitionManager.beginDelayedTransition(list, ChangeBounds().setDuration(220))
        val wasCarousel = carousel
        carousel = span == 1
        if (carousel != wasCarousel) {
            list.layoutManager = if (carousel) carouselLayout else grid
            snap.attachToRecyclerView(if (carousel) list else null)
            applyListPadding()
            updateClearAll()
        }
        if (!carousel) grid.spanCount = span
        prefs.edit().putInt("span", span).apply()
        @SuppressLint("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    /**
     * N9 restore: the card is sucked away from behind (a very fast zoom out), then the
     * application slides in from the left.
     */
    private fun open(e: RunningApps.Entry, from: View) {
        if (closeMode) return
        splitFirst?.let { first ->
            setSplitFirst(null)
            if (first !== e) Platform.splitTasks?.invoke(first.taskId, e.taskId)
            return
        }
        from.animate().scaleX(0f).scaleY(0f).setDuration(110)
            .setInterpolator(android.view.animation.AccelerateInterpolator(1.5f))
            .withEndAction {
                launch(e)
                // Back in place for when Open applications is next shown.
                from.postDelayed({ from.scaleX = 1f; from.scaleY = 1f }, 400)
            }.start()
    }

    private fun setSplitFirst(e: RunningApps.Entry?) {
        splitFirst = e
        if (e != null) android.widget.Toast.makeText(context, "Choose the second application", android.widget.Toast.LENGTH_SHORT).show()
        adapter.notifyItemRangeChanged(0, adapter.itemCount, "mode")
    }

    private fun launch(e: RunningApps.Entry) {
        val opts = ActivityOptions.makeCustomAnimation(context, R.anim.n9_task_enter, R.anim.n9_hold)
        val restore = Platform.restoreOptions?.invoke() ?: opts.toBundle()
        if (e.taskId >= 0 && SystemTasks.start(e.taskId, restore)) return
        val intent = context.packageManager.getLaunchIntentForPackage(e.pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return
        runCatching { context.startActivity(intent, opts.toBundle()) }
        RunningApps.touch(context, e.pkg)
    }

    private fun buzz(ms: Long) = runCatching {
        context.getSystemService(android.os.Vibrator::class.java)
            .vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** One app's half of a split card: its snapshot from the top, or its icon on a tile. */
    private fun half(c: android.graphics.Canvas, e: RunningApps.Entry, r: android.graphics.RectF) {
        val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        c.save(); c.clipRect(r)
        val t = e.thumb
        if (t != null) {
            val s = r.width() / t.width
            c.translate(r.left, r.top); c.scale(s, s); c.drawBitmap(t, 0f, 0f, p)
        } else {
            c.drawColor(N9.TILE)
            AppRepository.iconFor(context, e.pkg)?.let { ic ->
                val sz = r.height() * 0.45f
                c.drawBitmap(ic, null, android.graphics.RectF(r.centerX() - sz / 2, r.centerY() - sz / 2, r.centerX() + sz / 2, r.centerY() + sz / 2), p)
            }
        }
        c.restore()
    }

    /** One app's card as drawn in the grid: its snapshot from the top, or its icon on a tile. */
    private fun cardBitmap(e: RunningApps.Entry, w: Int = 360): android.graphics.Bitmap {
        val dm = resources.displayMetrics
        val h = (w * maxOf(dm.widthPixels, dm.heightPixels).toFloat() / minOf(dm.widthPixels, dm.heightPixels)).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        if (e.thumb != null) half(c, e, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()))
        else {
            c.drawColor(N9.TILE)
            AppRepository.iconFor(context, e.pkg)?.let { ic ->
                val sz = w * 0.36f
                c.drawBitmap(ic, null, android.graphics.RectF(w / 2f - sz / 2, h / 2f - sz / 2, w / 2f + sz / 2, h / 2f + sz / 2),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
        }
        return bmp
    }

    /** The portrait split card: top app over bottom app, a thin gap and the split-view handle. */
    private fun pairBitmap(top: RunningApps.Entry, bottom: RunningApps.Entry? = top.second, w: Int = 360, hh0: Int = 0): android.graphics.Bitmap {
        val dm = resources.displayMetrics
        val h = if (hh0 > 0) hh0 else (w * maxOf(dm.widthPixels, dm.heightPixels).toFloat() / minOf(dm.widthPixels, dm.heightPixels)).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(0xFF000000.toInt())
        val gap = w * 0.012f
        half(c, top, android.graphics.RectF(0f, 0f, w.toFloat(), h / 2f - gap / 2))
        bottom?.let { half(c, it, android.graphics.RectF(0f, h / 2f + gap / 2, w.toFloat(), h.toFloat())) }
        val hw = w * 0.18f; val hh = w * 0.022f
        c.drawRoundRect(android.graphics.RectF(w / 2f - hw / 2, h / 2f - hh / 2, w / 2f + hw / 2, h / 2f + hh / 2), hh, hh,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt() })
        return bmp
    }

    /**
     * Android extras → Split by two-finger merge (story split-merge; XMerge is the reference):
     * in edit mode, one finger on each of two cards pulls them together; each follows its finger.
     * Overlapping by half a card they snap into ONE card showing the portrait split (buzz 30).
     * Still held, pulling apart stretches it elastically; past 0.45 of a card's width it tears back
     * into the two cards (buzz 10). Lifting while merged opens the two in split screen for real.
     * The opposite: two fingers on a split card stretch it, then tear it into two cards (buzz 30;
     * the upper finger keeps the top app); pushing back re-joins it (buzz 10); lifting while torn
     * separates the pair.
     */
    private inner class Merge {
        var busy = false
        private var mode = 0 // 1 merging two cards, 2 pulling a pair apart
        private val ids = IntArray(2); private val sx = FloatArray(2); private val sy = FloatArray(2)
        private val x = FloatArray(2); private val y = FloatArray(2)
        private var a: View? = null; private var b: View? = null
        private var ea: RunningApps.Entry? = null; private var eb: RunningApps.Entry? = null
        private var snapped = false; private var torn = false
        private var snapDist = 0f; private var d0 = 0f
        private var live: ImageView? = null
        private val tornViews = mutableListOf<View>()
        private var firstId = -1; private var firstX = 0f; private var firstY = 0f

        fun enabled() = closeMode && !carousel && Extras.on(context, Extras.SPLIT_MERGE) && Platform.splitTasks != null

        private fun cardRect(v: View): android.graphics.RectF {
            val h = list.getChildViewHolder(v) as Holder
            val l = v.left + v.translationX + h.card.left + v.paddingLeft
            val t = v.top + v.translationY + h.card.top + v.paddingTop
            return android.graphics.RectF(l, t, l + h.card.width, t + h.card.height)
        }
        private fun cw() = a?.let { (list.getChildViewHolder(it) as Holder).card.width.toFloat() } ?: 1f
        private fun tear() = 0.45f * cw()
        private fun entryOf(v: View) = RunningApps.entries.getOrNull(list.getChildAdapterPosition(v))

        fun onTouch(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { firstId = ev.getPointerId(0); firstX = ev.x; firstY = ev.y; return false }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (busy || ev.pointerCount != 2) return busy
                    val i1 = ev.findPointerIndex(firstId).takeIf { it >= 0 } ?: 0
                    val i2 = ev.actionIndex
                    val v1 = list.findChildViewUnder(ev.getX(i1), ev.getY(i1)) ?: return false
                    val v2 = list.findChildViewUnder(ev.getX(i2), ev.getY(i2)) ?: return false
                    val e1 = entryOf(v1) ?: return false; val e2 = entryOf(v2) ?: return false
                    mode = when {
                        v1 !== v2 && e1.second == null && e2.second == null && e1.taskId >= 0 && e2.taskId >= 0 -> 1
                        v1 === v2 && e1.second != null -> 2
                        else -> return false
                    }
                    busy = true
                    // Cancel the card's pending tap, long-press and any drag.
                    list.superDispatch(MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL })
                    list.parent?.requestDisallowInterceptTouchEvent(true)
                    ids[0] = ev.getPointerId(i1); ids[1] = ev.getPointerId(i2)
                    sx[0] = ev.getX(i1); sy[0] = ev.getY(i1); sx[1] = ev.getX(i2); sy[1] = ev.getY(i2)
                    x[0] = sx[0]; y[0] = sy[0]; x[1] = sx[1]; y[1] = sy[1]
                    a = v1; b = v2; ea = e1; eb = e2; snapped = false; torn = false
                    d0 = dist()
                    v1.elevation = dp(8f).toFloat(); v2.elevation = dp(6f).toFloat()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!busy) return false
                    for (k in 0..1) { val i = ev.findPointerIndex(ids[k]); if (i >= 0) { x[k] = ev.getX(i); y[k] = ev.getY(i) } }
                    if (mode == 1) moveMerge() else moveSplit()
                    return true
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!busy) return false
                    if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP && ev.actionMasked != MotionEvent.ACTION_CANCEL) {
                        // Wait for the last finger, as the mock does.
                        return true
                    }
                    finish(ev.actionMasked != MotionEvent.ACTION_CANCEL)
                    return true
                }
            }
            return busy
        }

        private fun dist() = kotlin.math.hypot(x[0] - x[1], y[0] - y[1])

        private fun moveMerge() {
            val va = a ?: return; val vb = b ?: return
            if (!snapped) {
                va.translationX = x[0] - sx[0]; va.translationY = y[0] - sy[0]
                vb.translationX = x[1] - sx[1]; vb.translationY = y[1] - sy[1]
                val A = cardRect(va); val B = cardRect(vb)
                val ox = (minOf(A.right, B.right) - maxOf(A.left, B.left)).coerceAtLeast(0f) / A.width()
                val oy = (minOf(A.bottom, B.bottom) - maxOf(A.top, B.top)).coerceAtLeast(0f) / A.height()
                if (ox * oy >= 0.5f) snap(A, B)
            } else {
                val stretch = (dist() - snapDist).coerceAtLeast(0f)
                val k = stretch / tear()
                live?.apply { scaleX = 1f + 0.35f * k; scaleY = 1f - 0.12f * k }
                if (stretch > tear()) unsnap()
            }
        }

        /** Taken from the fingers: one card at the meeting point showing the split. */
        private fun snap(A: android.graphics.RectF, B: android.graphics.RectF) {
            snapped = true; buzz(30); android.util.Log.i("EtesianSwitcher", "snap")
            snapDist = dist()
            a?.alpha = 0f; b?.alpha = 0f
            val v = ImageView(context).apply {
                setImageBitmap(pairBitmap(ea!!, eb!!)); scaleType = ImageView.ScaleType.FIT_XY
                contentDescription = "Split: ${ea!!.label} + ${eb!!.label}"
                background = GradientDrawable().apply { setStroke(dp(2f), 0xFF2A86D8.toInt()); setColor(0) }
                foreground = GradientDrawable().apply { setStroke(dp(2f), 0xFF2A86D8.toInt()); setColor(0) }
                elevation = dp(12f).toFloat()
            }
            // XMerge (approved): ONE card of normal card width, centred where the cards meet.
            addView(v, LayoutParams(A.width().toInt(), A.height().toInt()))
            v.x = list.left + (A.left + B.left) / 2; v.y = list.top + (A.top + B.top) / 2
            android.util.Log.i("EtesianSwitcher", "merged w=${A.width().toInt()} h=${A.height().toInt()} card=${cw().toInt()}")
            live = v
            // Pulled out of the fingers: 1.12 → 0.94 → 1 (cubic-bezier(.3, 1.6, .5, 1), 220 ms).
            v.scaleX = 1.12f; v.scaleY = 1.12f
            v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
        }

        /** Torn apart: the cards go back under the fingers. */
        private fun unsnap() {
            snapped = false; buzz(10); android.util.Log.i("EtesianSwitcher", "tear back")
            live?.let { removeView(it) }; live = null
            a?.alpha = 1f; b?.alpha = 1f
            moveMerge()
        }

        private fun moveSplit() {
            val va = a ?: return; val e = ea ?: return
            val d = dist(); val stretch = (d - d0).coerceAtLeast(0f)
            val ax = kotlin.math.abs(x[0] - x[1]) / d.coerceAtLeast(1f); val ay = kotlin.math.abs(y[0] - y[1]) / d.coerceAtLeast(1f)
            val k = stretch / tear()
            if (!torn) { va.scaleX = 1f + 0.35f * k * ax + 0.04f * k; va.scaleY = 1f + 0.35f * k * ay + 0.04f * k }
            // The upper finger takes the top app.
            val upper = if (y[0] <= y[1]) 0 else 1
            if (!torn && stretch > tear()) {
                torn = true; buzz(30); android.util.Log.i("EtesianSwitcher", "torn")
                va.alpha = 0f; va.scaleX = 1f; va.scaleY = 1f
                val r = cardRect(va)
                // As in the story: two normal cards under the fingers, snapshot or icon at normal size.
                val iconPx = dp(58f)
                for (part in listOf(e, e.second!!)) tornViews += FrameLayout(context).apply {
                    contentDescription = "Torn: ${part.label}"
                    setBackgroundColor(N9.TILE)
                    clipToOutline = true
                    if (part.thumb != null) addView(TopCropImageView(context).apply { setImageBitmap(part.thumb) }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    else addView(ImageView(context).apply { setImageBitmap(AppRepository.iconFor(context, part.pkg)) },
                        FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER))
                    foreground = GradientDrawable().apply { setStroke(dp(2f), 0xFF2A86D8.toInt()); setColor(0) }
                    elevation = dp(12f).toFloat()
                }.also { addView(it, LayoutParams(r.width().toInt(), r.height().toInt())) }
                android.util.Log.i("EtesianSwitcher", "torn w=${r.width().toInt()} card=${cw().toInt()} icon=$iconPx gridicon=${dp(58f)}")
            } else if (torn && stretch < tear() * 0.6f) {
                torn = false; buzz(10); android.util.Log.i("EtesianSwitcher", "rejoin")
                tornViews.forEach { removeView(it) }; tornViews.clear()
                va.alpha = 1f
            }
            if (torn) {
                val r = cardRect(va)
                tornViews.forEachIndexed { i, v ->
                    val f = if (i == 0) upper else 1 - upper
                    v.x = list.left + x[f] - r.width() / 2; v.y = list.top + y[f] - r.height() / 2
                }
            }
        }

        private fun finish(commit: Boolean) {
            val va = a; val vb = b; val e1 = ea; val e2 = eb
            when {
                commit && mode == 1 && snapped && e1 != null && e2 != null -> {
                    // A real split-screen launch: the first card's app on top.
                    val splitOk = Platform.splitTasks?.invoke(e1.taskId, e2.taskId) == true
                    android.util.Log.i("EtesianSwitcher", "merge ${e1.taskId}+${e2.taskId} split=$splitOk")
                    postDelayed({ exitModes(); RunningApps.sync(context) }, 900)
                }
                commit && mode == 2 && torn && e1 != null -> {
                    val splitOff = Platform.unsplit?.invoke(e1.taskId) == true
                    android.util.Log.i("EtesianSwitcher", "separate ${e1.taskId}+${e1.second?.taskId} unsplit=$splitOff")
                    // The shell brings the top app to the front: stay in Open applications, as in the mock.
                    postDelayed({
                        runCatching { context.startActivity(Intent(context, HomeActivity::class.java)
                            .putExtra(HomeActivity.EXTRA_PAGE, HomeActivity.PAGE_SWITCHER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
                    }, 350)
                    postDelayed({ RunningApps.sync(context) }, 900)
                }
            }
            live?.let { removeView(it) }; live = null
            tornViews.forEach { removeView(it) }; tornViews.clear()
            for (v in listOfNotNull(va, vb)) {
                v.alpha = 1f; v.elevation = 0f
                v.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(250).start()
            }
            a = null; b = null; ea = null; eb = null; busy = false; mode = 0; snapped = false; torn = false
            pendingRefresh = false
            refresh()
        }

    }

    private companion object {
        /** How far a pinch must go before the zoom level changes on release. */
        const val ZOOM_IN_STOP = 1.4f
        const val ZOOM_OUT_STOP = 0.72f
        /** Pinch amount at which the live reflow reaches the neighbouring layout (low sensitivity). */
        const val ZOOM_IN_FULL = 1.8f
        const val ZOOM_OUT_FULL = 0.55f
    }

    /**
     * RecyclerView with a live N9 pinch: while pinching, each card is moved and resized
     * between its place in the current layout and its place in the neighbouring one.
     */
    private inner class PinchRecycler(ctx: Context) : RecyclerView(ctx) {
        /** Total pinch so far in this gesture; >1 = fingers apart (zoom in, fewer cards). */
        private var scale = 1f
        /** The card under the fingers, kept in view across the level change. */
        private var anchor = NO_POSITION
        /** Current-layout frames of the attached cards, captured when the pinch starts. */
        private val starts = HashMap<View, android.graphics.RectF>()
        private val positions = HashMap<View, Int>()
        /** Target frames per target span, computed lazily. */
        private val targets = HashMap<Int, Map<View, android.graphics.RectF>>()
        private var settleAnim: android.animation.ValueAnimator? = null
        private var pinching = false

        private fun targetSpan(zoomIn: Boolean): Int {
            val cur = currentSpan()
            return if (zoomIn) (cur - 1).coerceAtLeast(1) else (cur + 1).coerceAtMost(3)
        }

        /** Where every captured card sits in the layout with [span] columns (1 = carousel). */
        private fun targetsFor(span: Int): Map<View, android.graphics.RectF> = targets.getOrPut(span) {
            val out = HashMap<View, android.graphics.RectF>()
            val ref = starts.entries.firstOrNull { positions[it.key] == anchor } ?: starts.entries.firstOrNull() ?: return@getOrPut out
            val refRect = ref.value
            val aspect = refRect.height() / refRect.width().coerceAtLeast(1f)
            val (l, t, r, _) = insets.toList()
            val listW = width.toFloat()
            if (span == 1) {
                val side = resources.displayMetrics.widthPixels * 0.11f
                val itemW = ((listW - l - r - 2 * side) * 0.78f)
                val itemH = itemW * aspect
                val x0 = (listW - itemW) / 2f
                for ((v, p) in positions) {
                    val x = x0 + (p - anchor) * itemW
                    out[v] = android.graphics.RectF(x, t.toFloat(), x + itemW, t + itemH)
                }
            } else {
                val padL = l + dp(10f).toFloat()
                val innerW = listW - padL - (r + dp(10f))
                val cw = innerW / span
                val rowH = cw * aspect
                var anchorTop = (starts.entries.firstOrNull { positions[it.key] == anchor }?.value?.top ?: t.toFloat())
                // Don't leave a gap above the first row.
                val firstTop = anchorTop - (anchor / span) * rowH
                if (firstTop > t) anchorTop -= firstTop - t
                for ((v, p) in positions) {
                    val x = padL + (p % span) * cw
                    val y = anchorTop + (p / span - anchor / span) * rowH
                    out[v] = android.graphics.RectF(x, y, x + cw, y + rowH)
                }
            }
            out
        }

        private fun capture(fx: Float, fy: Float) {
            starts.clear(); positions.clear(); targets.clear()
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                val p = getChildAdapterPosition(c)
                if (p == NO_POSITION) continue
                c.translationX = 0f; c.translationY = 0f; c.scaleX = 1f; c.scaleY = 1f
                c.pivotX = 0f; c.pivotY = 0f
                starts[c] = android.graphics.RectF(c.left.toFloat(), c.top.toFloat(), c.right.toFloat(), c.bottom.toFloat())
                positions[c] = p
            }
            val under = if (carousel) snap.findSnapView(layoutManager) else findChildViewUnder(fx, fy)
            anchor = under?.let { getChildAdapterPosition(it) } ?: NO_POSITION
            if (anchor == NO_POSITION) {
                // Nearest card to the fingers.
                anchor = starts.minByOrNull { (_, rc) -> kotlin.math.hypot(rc.centerX() - fx, rc.centerY() - fy) }
                    ?.let { positions[it.key] } ?: NO_POSITION
            }
        }

        /** Progress towards the neighbouring layout: >0 zoom in, <0 zoom out, magnitude 0..1. */
        private fun progressOf(s: Float): Float = if (s >= 1f)
            ((s - 1f) / (ZOOM_IN_FULL - 1f)).coerceIn(0f, 1f) else -((1f - s) / (1f - ZOOM_OUT_FULL)).coerceIn(0f, 1f)

        private fun applyProgress(progress: Float) {
            if (anchor == NO_POSITION) return
            val span = targetSpan(progress > 0f)
            val t = kotlin.math.abs(progress)
            val tg = if (span == currentSpan()) null else targetsFor(span)
            for ((v, a) in starts) {
                val b = tg?.get(v)
                if (b == null || a.width() <= 0f) {
                    v.translationX = 0f; v.translationY = 0f; v.scaleX = 1f; v.scaleY = 1f
                    continue
                }
                val w = a.width() + (b.width() - a.width()) * t
                val s = w / a.width()
                v.pivotX = 0f; v.pivotY = 0f
                v.scaleX = s; v.scaleY = s
                v.translationX = (b.left - a.left) * t
                v.translationY = (b.top - a.top) * t
            }
        }

        private fun resetChildren() {
            for (v in starts.keys) {
                v.translationX = 0f; v.translationY = 0f; v.scaleX = 1f; v.scaleY = 1f
            }
            for (i in 0 until childCount) getChildAt(i).apply {
                translationX = 0f; translationY = 0f; scaleX = 1f; scaleY = 1f
            }
        }

        /** Switches to [span] with the anchor card exactly where it was last drawn. */
        private fun commit(span: Int) {
            val anchorRect = targetsFor(span).entries.firstOrNull { positions[it.key] == anchor }?.value
            resetChildren()
            setSpan(span, animate = false)
            if (anchor != NO_POSITION) {
                val lm = layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                if (carousel) {
                    val off = anchorRect?.left?.minus(paddingLeft)?.toInt() ?: 0
                    lm.scrollToPositionWithOffset(anchor, off)
                } else {
                    val off = anchorRect?.top?.minus(paddingTop)?.toInt() ?: 0
                    lm.scrollToPositionWithOffset(anchor, off)
                }
            }
            starts.clear(); positions.clear(); targets.clear()
        }

        private val detector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                settleAnim?.cancel()
                stopScroll()
                scale = 1f
                pinching = true
                capture(d.focusX, d.focusY)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(0.4f, 2.2f)
                applyProgress(progressOf(scale))
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                pinching = false
                val progress = progressOf(scale)
                val zoomIn = progress > 0f
                val span = targetSpan(zoomIn)
                // Past the stop point: one level, never more per pinch. Short of it: snap back, slowly.
                val go = span != currentSpan() && anchor != NO_POSITION &&
                    (if (zoomIn) scale > ZOOM_IN_STOP else scale < ZOOM_OUT_STOP)
                val from = kotlin.math.abs(progress)
                if (go) {
                    settleInto(span, from)
                    return
                }
                val sign = if (zoomIn) 1f else -1f
                // Short of the stop point: back to the current layout, slowly.
                settleAnim = android.animation.ValueAnimator.ofFloat(from, 0f).apply {
                    duration = (450 + 150 * from).toLong()
                    interpolator = android.view.animation.DecelerateInterpolator(2f)
                    addUpdateListener { applyProgress(sign * (it.animatedValue as Float)) }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            resetChildren(); starts.clear(); positions.clear(); targets.clear()
                        }
                    })
                    start()
                }
            }
        })

        /**
         * Past the stop point: switch to the real [span] layout right away, then move every card
         * from where it is drawn now to where that layout actually put it. The animation ends
         * exactly on the final layout, so nothing jumps when it finishes.
         */
        private fun settleInto(span: Int, from: Float) {
            // Where each card is drawn at the moment the fingers lift, by adapter position.
            val drawn = HashMap<Int, android.graphics.RectF>()
            for ((v, a) in starts) {
                val p = positions[v] ?: continue
                drawn[p] = android.graphics.RectF(a.left + v.translationX, a.top + v.translationY,
                    a.left + v.translationX + a.width() * v.scaleX,
                    a.top + v.translationY + a.height() * v.scaleY)
            }
            commit(span)
            viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver.removeOnPreDrawListener(this)
                    val moves = ArrayList<Pair<View, FloatArray>>()
                    for (i in 0 until childCount) {
                        val c = getChildAt(i)
                        val p = getChildAdapterPosition(c)
                        val old = if (p == NO_POSITION) null else drawn[p]
                        c.pivotX = 0f; c.pivotY = 0f
                        if (old == null || c.width == 0) {
                            // Wasn't on screen before: fade in where it belongs.
                            c.alpha = 0f
                            moves.add(c to floatArrayOf(0f, 0f, 1f, 0f))
                            continue
                        }
                        val sc = old.width() / c.width
                        moves.add(c to floatArrayOf(old.left - c.left, old.top - c.top, sc, 1f))
                    }
                    settleAnim = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = (200 + 180 * (1f - from)).toLong()
                        interpolator = android.view.animation.DecelerateInterpolator(1.5f)
                        addUpdateListener {
                            val k = it.animatedValue as Float
                            for ((v, m) in moves) {
                                v.translationX = m[0] * k
                                v.translationY = m[1] * k
                                val sc = 1f + (m[2] - 1f) * k
                                v.scaleX = sc; v.scaleY = sc
                                if (m[3] == 0f) v.alpha = 1f - k
                            }
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                for ((v, _) in moves) {
                                    v.translationX = 0f; v.translationY = 0f
                                    v.scaleX = 1f; v.scaleY = 1f; v.alpha = 1f
                                }
                            }
                        })
                        start()
                    }
                    for ((v, m) in moves) {
                        v.translationX = m[0]; v.translationY = m[1]
                        v.scaleX = m[2]; v.scaleY = m[2]
                    }
                    return true
                }
            })
        }

        fun superDispatch(ev: MotionEvent) { super.dispatchTouchEvent(ev); ev.recycle() }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            // Android extras → two-finger split: edit mode has no pinch; two fingers merge or separate cards.
            if (merge.enabled() || merge.busy) {
                if (merge.onTouch(ev)) return true
                if (ev.pointerCount > 1) return true
                return super.dispatchTouchEvent(ev)
            }
            detector.onTouchEvent(ev)
            if (detector.isInProgress || pinching || ev.pointerCount > 1) {
                if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    // Cancel any pending click/long-press/scroll on the cards.
                    super.dispatchTouchEvent(MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL })
                }
                return true
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    /** Thumbnail scaled to fill the width and anchored to the top of the window, like N9 cards. */
    private class TopCropImageView(ctx: Context) : ImageView(ctx) {
        init { scaleType = ScaleType.MATRIX }
        override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
            val changed = super.setFrame(l, t, r, b)
            updateMatrix()
            return changed
        }
        fun updateMatrix() {
            val d = drawable ?: return
            if (width == 0 || d.intrinsicWidth <= 0) return
            val s = maxOf(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
            imageMatrix = Matrix().apply { setScale(s, s) }
        }
    }

    /** Every card has the screen's own proportions, so snapshots show whole, uncropped. */
    private class CardFrame(ctx: Context) : FrameLayout(ctx) {
        override fun onMeasure(w: Int, h: Int) {
            val width = MeasureSpec.getSize(w)
            val (sw, sh) = if (android.os.Build.VERSION.SDK_INT >= 30) {
                context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
                    .let { it.width() to it.height() }
            } else resources.displayMetrics.let { it.widthPixels to it.heightPixels }
            val ratio = maxOf(sw, sh).toFloat() / minOf(sw, sh)
            super.onMeasure(w, MeasureSpec.makeMeasureSpec((width * ratio).toInt(), MeasureSpec.EXACTLY))
        }
    }

    private inner class Holder(
        root: View, val card: CardFrame, val thumb: TopCropImageView, val icon: ImageView,
        val label: TextView, val close: View, val bar: CardBar,
    ) : RecyclerView.ViewHolder(root)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = RunningApps.entries.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val thumb = TopCropImageView(ctx)
            val icon = ImageView(ctx)
            val bar = CardBar(ctx)
            val card = CardFrame(ctx).apply {
                background = GradientDrawable().apply { cornerRadius = dp(4f).toFloat(); setColor(N9.TILE) }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                addView(thumb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                addView(icon, FrameLayout.LayoutParams(dp(58f), dp(58f), Gravity.CENTER))
                // The snapshot is only the app's window; the card shows it with its toolbar.
                addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            val label = TextView(ctx).apply {
                setTextColor(N9.TEXT)
                typeface = N9.FONT
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(6f), 0, 0)
            }
            val column = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            val close = CloseBadge(ctx).apply { contentDescription = "Close" }
            val root = FrameLayout(ctx).apply {
                clipChildren = false
                setPadding(dp(8f), dp(8f), dp(8f), dp(10f))
                addView(column)
                // As on the N9: top-right, mostly inside the card, its edge just past the card's.
                addView(close, FrameLayout.LayoutParams(dp(36f), dp(36f), Gravity.TOP or Gravity.END))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val h = Holder(root, card, thumb, icon, label, close, bar)
            card.setOnClickListener { RunningApps.entries.getOrNull(h.bindingAdapterPosition)?.let { open(it, card) } }
            card.setOnLongClickListener {
                // N9: long-press always enters edit mode (pinning lives in the carousel action row).
                if (!closeMode) { closeMode = true; notifyItemRangeChanged(0, itemCount, "mode"); updateClearAll() }
                true
            }
            close.setOnClickListener {
                val e = RunningApps.entries.getOrNull(h.bindingAdapterPosition) ?: return@setOnClickListener
                N9Sounds.play(context, N9Sounds.CLOSE)
                h.itemView.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(160).withEndAction {
                    h.itemView.scaleX = 1f; h.itemView.scaleY = 1f; h.itemView.alpha = 1f
                    RunningApps.closeEntry(context, e)
                }.start()
            }
            return h
        }

        override fun onBindViewHolder(h: Holder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) onBindViewHolder(h, position) else bindMode(h)
        }

        override fun onBindViewHolder(h: Holder, position: Int) {
            val e = RunningApps.entries[position]
            h.label.text = e.title
            h.itemView.contentDescription = e.title
            h.label.textSize = if (carousel) 16f else if (grid.spanCount == 3) 11f else 13f
            // Carousel: one large card per page, with its neighbours peeking in.
            h.itemView.layoutParams = (h.itemView.layoutParams as RecyclerView.LayoutParams).apply {
                width = if (carousel) (resources.displayMetrics.widthPixels * 0.78f).toInt().coerceAtLeast(1)
                    else ViewGroup.LayoutParams.MATCH_PARENT
            }
            if (e.second != null) {
                // A split pair: the portrait split as it opens, with the split-view handle.
                h.thumb.setImageBitmap(pairBitmap(e))
                h.thumb.updateMatrix()
                h.icon.visibility = View.GONE
                h.bar.visibility = View.GONE
            } else if (e.thumb != null) {
                h.thumb.setImageBitmap(e.thumb)
                h.thumb.updateMatrix()
                h.icon.visibility = View.GONE
                val tb = TaskToolbars.get(e.taskId)
                if (tb == null) TaskToolbars.query(context, e.taskId)
                h.bar.visibility = View.VISIBLE
                h.bar.bind(tb ?: TaskToolbars.State(true, emptyList(), null, false, dp(64f)),
                    TaskToolbars.isDark(context, e.pkg, tb), resources.displayMetrics.heightPixels)
            } else {
                h.thumb.setImageDrawable(null)
                h.bar.visibility = View.GONE
                h.icon.setImageBitmap(AppRepository.iconFor(context, e.pkg))
                h.icon.visibility = View.VISIBLE
            }
            bindMode(h)
        }

        private fun bindMode(h: Holder) {
            h.close.visibility = if (closeMode) View.VISIBLE else View.GONE
            val e = RunningApps.entries.getOrNull(h.bindingAdapterPosition)
            h.card.alpha = if (splitFirst != null && splitFirst === e) 0.5f else 1f
            // Edit mode dims the previews to half brightness, as the N9 does; the ✕ stays bright.
            h.card.foreground = if (closeMode) android.graphics.drawable.ColorDrawable(0x80000000.toInt()) else null
        }
    }
}

/**
 * The N9's close button in edit mode: a red squircle, lit from the top like the icons
 * (#F78673 to #EF2C10, measured), with a thick white cross with rounded ends.
 */
internal class CloseBadge(ctx: Context) : View(ctx) {
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val cross = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private var shape = android.graphics.Path()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        shape = IconFactory.superellipse(w.toFloat(), 2.8)
        fill.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(),
            0xFFF78673.toInt(), 0xFFEF2C10.toInt(), android.graphics.Shader.TileMode.CLAMP)
        cross.strokeWidth = w * 0.13f
    }

    override fun onDraw(c: android.graphics.Canvas) {
        c.drawPath(shape, fill)
        val a = width * 0.31f
        val b = width - a
        c.drawLine(a, a, b, b, cross)
        c.drawLine(b, a, a, b, cross)
    }
}
