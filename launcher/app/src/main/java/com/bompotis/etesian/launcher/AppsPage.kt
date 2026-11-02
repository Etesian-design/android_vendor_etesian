package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.UserHandle
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import android.widget.GridLayout

/**
 * The Applications view: a 4-column grid of N9-shaped icons on black, with N9 folders.
 * Long-press (an icon or empty space) is edit mode: icons dim, uninstallable apps get the red ✕,
 * icons can be dragged (onto a folder to put them in it), and the Done / New folder bar shows.
 */
@SuppressLint("ViewConstructor")
class AppsPage(context: Context) : FrameLayout(context), HomePage {

    private val list = RecyclerView(context)
    private val adapter = Adapter()
    private var items = mutableListOf<GridEntry>()
    private var editMode = false
    /** The folder shown open under its row, if any. */
    private var openFolder: GridEntry.Folder? = null
    /** The folder an application being dragged is over (it goes in when dropped). */
    private var hoverFolder: GridEntry.Folder? = null
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    // ---- grid settings ----
    private val grid = GridLayoutManager(context, Prefs.gridColumns(context))
    private var rowsPerScreen = Prefs.gridRows(context)
    private var iconDp = Prefs.iconSizeDp(context)
    private var lastListHeight = 0

    /** Fixed row height when a rows-per-screen count is set, else wrap content. */
    private fun rowHeight(): Int {
        if (rowsPerScreen <= 0) return ViewGroup.LayoutParams.WRAP_CONTENT
        val h = list.height - list.paddingTop - list.paddingBottom
        return if (h > 0) h / rowsPerScreen else ViewGroup.LayoutParams.WRAP_CONTENT
    }

    // ---- search state ----
    private var searchEnabled = false
    private var searchAtTop = true
    private var searching = false
    private var query = ""
    private var filtered: List<GridEntry> = emptyList()
    /** What the grid shows: everything, or the search results (folders' apps included). */
    private val shown: List<GridEntry> get() = if (searching && query.isNotEmpty()) filtered else items

    /** The adapter's rows: [shown], with the open folder's panel after the folder's row. */
    private val rows: List<Any> get() {
        val s = shown
        val f = openFolder ?: return s
        val i = s.indexOf(f)
        if (i < 0) return s
        val cols = grid.spanCount
        val end = min(s.size, (i / cols + 1) * cols)
        return s.subList(0, end) + listOf(PANEL) + s.subList(end, s.size)
    }
    private var insetTop = 0
    private var insetBottom = 0
    private var imeInset = 0
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val pullThreshold = dp(110f)
    private var pullDownX = 0f
    private var pullDownY = 0f
    private var pulling = false
    private var pull = 0f

    private val pullHint = TextView(context).apply {
        setTextColor(N9.TEXT_SECONDARY)
        textSize = 14f
        typeface = N9.FONT
        gravity = Gravity.CENTER
        alpha = 0f
    }

    private val searchBar = EditText(context).apply {
        hint = "Search applications"
        setHintTextColor(N9.TEXT_SECONDARY)
        setTextColor(N9.TEXT)
        textSize = 17f
        typeface = N9.FONT
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
        background = GradientDrawable().apply { cornerRadius = dp(22f).toFloat(); setColor(0xFF262626.toInt()) }
        visibility = GONE
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) { filter(e?.toString().orEmpty()) }
        })
        setOnEditorActionListener { _, action, event ->
            val go = action == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (go) (shown.firstOrNull() as? GridEntry.App)?.let { e ->
                list.findViewHolderForAdapterPosition(0)?.itemView?.let { launch(e.app, it) }
            }
            go
        }
    }

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
    ) {
        override fun isLongPressDragEnabled() = false
        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val f = from.bindingAdapterPosition
            val t = to.bindingAdapterPosition
            if (f !in items.indices || t !in items.indices) return false
            // Over a folder an application doesn't push it aside: it's dropped in (clearView).
            if (items[f] is GridEntry.App && items[t] is GridEntry.Folder) return false
            // The private space stays last.
            if ((items[t] as? GridEntry.Folder)?.private == true || (items[f] as? GridEntry.Folder)?.private == true) return false
            items.add(t, items.removeAt(f))
            adapter.notifyItemMoved(f, t)
            return true
        }
        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, state: Int) {
            super.onSelectedChanged(vh, state)
            vh?.itemView?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.alpha(0.85f)?.setDuration(120)?.start()
        }
        override fun onChildDraw(c: android.graphics.Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                 dX: Float, dY: Float, state: Int, active: Boolean) {
            super.onChildDraw(c, rv, vh, dX, dY, state, active)
            if (state != ItemTouchHelper.ACTION_STATE_DRAG || !active) return
            if (items.getOrNull(vh.bindingAdapterPosition) !is GridEntry.App) return
            // Which folder is under the middle of the dragged icon.
            val cx = vh.itemView.left + dX + vh.itemView.width / 2f
            val cy = vh.itemView.top + dY + vh.itemView.height / 2f
            var over: GridEntry.Folder? = null
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                if (child === vh.itemView) continue
                val e = items.getOrNull(rv.getChildAdapterPosition(child)) as? GridEntry.Folder ?: continue
                if (e.private) continue
                if (cx >= child.left && cx <= child.right && cy >= child.top && cy <= child.bottom) over = e
                val lit = over === e
                child.animate().scaleX(if (lit) 1.12f else 1f).scaleY(if (lit) 1.12f else 1f).setDuration(100).start()
            }
            hoverFolder = over
        }
        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            vh.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
            for (i in 0 until rv.childCount) rv.getChildAt(i).let { if (it !== vh.itemView) { it.scaleX = 1f; it.scaleY = 1f } }
            val folder = hoverFolder
            hoverFolder = null
            val dragged = items.getOrNull(vh.bindingAdapterPosition) as? GridEntry.App
            if (folder != null && dragged != null) {
                items.remove(dragged)
                folder.apps.add(dragged.app)
                @SuppressLint("NotifyDataSetChanged")
                adapter.notifyDataSetChanged()
            }
            saveLayout()
        }
    })

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) { IconPacks.invalidate(packageName); reload() }
        override fun onPackageAdded(packageName: String, user: UserHandle) { IconPacks.invalidate(packageName); reload() }
        override fun onPackageChanged(packageName: String, user: UserHandle) { IconPacks.invalidate(packageName); reload() }
        override fun onPackagesAvailable(p: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(p: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    /** Edit mode's toolbar, as on the N9: Done and New folder. */
    private val editBar = N9.editBar(context,
        N9.editButton(context, "Done") { exitModes() },
        N9.editButton(context, "New folder") { newFolder() }).apply { visibility = GONE }

    init {
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (rows.getOrNull(position) === PANEL) grid.spanCount else 1
        }
        list.layoutManager = grid
        // Rows-per-screen depends on the visible height (insets, search bar).
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val h = list.height - list.paddingTop - list.paddingBottom
            if (rowsPerScreen > 0 && h != lastListHeight) {
                lastListHeight = h
                list.post { adapter.notifyItemRangeChanged(0, rows.size, "size") }
            }
        }
        list.adapter = adapter
        list.clipToPadding = false
        list.overScrollMode = OVER_SCROLL_NEVER
        list.isVerticalScrollBarEnabled = false
        touchHelper.attachToRecyclerView(list)
        // Tapping empty space leaves edit mode.
        val tap = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if ((editMode || openFolder != null) && list.findChildViewUnder(e.x, e.y) == null) exitModes()
                return false
            }
            // N9: a long-press anywhere (icon, label, gap, empty space, an open folder) puts the
            // grid in edit mode; icons also start dragging from their own long-click.
            override fun onLongPress(e: MotionEvent) {
                if (!editMode && !searching) {
                    list.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    setEditMode(true)
                }
            }
        })
        list.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean { tap.onTouchEvent(e); return false }
        })
        setOnClickListener { exitModes() }
        setOnLongClickListener { if (!editMode && !searching) setEditMode(true); true }
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(pullHint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        addView(editBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        reloadSettings()
        reload()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); launcherApps.registerCallback(callback)
        context.registerReceiver(privateReceiver, android.content.IntentFilter().apply {
            addAction(PrivateCodeActivity.ACTION_OPEN)
            addAction(android.content.Intent.ACTION_PROFILE_AVAILABLE); addAction(android.content.Intent.ACTION_PROFILE_UNAVAILABLE)
        }, Context.RECEIVER_NOT_EXPORTED)
    }
    override fun onDetachedFromWindow() {
        launcherApps.unregisterCallback(callback); runCatching { context.unregisterReceiver(privateReceiver) }; super.onDetachedFromWindow()
    }

    /** Android extras → Private space: locked → the security code screen; open → the folder. */
    private fun privateTap(f: GridEntry.Folder) {
        if (editMode) return
        val u = PrivateSpace.profile(context) ?: return
        if (openFolder === f) { toggleFolder(f); return }
        if (PrivateSpace.locked(context, u)) PrivateSpace.open(context) else { openPrivate = true; reload() }
    }
    private var openPrivate = false
    private val privateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: android.content.Intent) {
            if (i.action == PrivateCodeActivity.ACTION_OPEN) openPrivate = true
            // The profile takes a moment to come up: reload now and once it is available.
            reload(); postDelayed({ reload() }, 1200)
        }
    }

    private fun reload() = AppRepository.loadAsync(context) { apps ->
        items = arrange(apps)
        openFolder = openFolder?.let { f -> items.firstOrNull { it.key == f.key } as? GridEntry.Folder }
        if (openPrivate) (items.lastOrNull() as? GridEntry.Folder)?.takeIf { it.private && it.apps.isNotEmpty() }?.let {
            openPrivate = false
            if (openFolder !== it) { openFolder = null; openNow(it) }
        }
        if (searching) filter(query)
        @SuppressLint("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    override fun applyInsets(left: Int, top: Int, right: Int, bottom: Int) {
        insetTop = top
        insetBottom = bottom
        editBar.setPadding(dp(8f), dp(10f), dp(8f), dp(10f) + bottom)
        list.setPadding(left + dp(8f), top, right + dp(8f), bottom + dp(16f))
        (pullHint.layoutParams as LayoutParams).topMargin = top - dp(8f)
        pullHint.requestLayout()
        layoutSearch()
    }

    fun setImeInset(px: Int) {
        if (px == imeInset) return
        imeInset = px
        layoutSearch()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun reloadSettings() {
        if (Prefs.iconShape(context) != IconFactory.style || Prefs.iconPack(context) != IconFactory.packName) reload()
        val cols = Prefs.gridColumns(context)
        val rows = Prefs.gridRows(context)
        val icon = Prefs.iconSizeDp(context)
        if (cols != grid.spanCount || rows != rowsPerScreen || icon != iconDp) {
            grid.spanCount = cols
            rowsPerScreen = rows
            iconDp = icon
            lastListHeight = 0
            adapter.notifyDataSetChanged()
        }
        searchEnabled = Prefs.searchEnabled(context)
        val top = Prefs.searchAtTop(context)
        if (top != searchAtTop || !searchEnabled) closeSearch()
        searchAtTop = top
        layoutSearch()
    }

    override fun exitModes(): Boolean {
        if (searching) { closeSearch(); return true }
        if (openFolder != null && !editMode) { closeFolder(); return true }
        if (!editMode) return false
        closeFolder()
        setEditMode(false)
        return true
    }

    // ---- pull down to search ----

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!searchEnabled || searching || editMode) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pullDownX = ev.x; pullDownY = ev.y; pulling = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - pullDownY
                if (!list.canScrollVertically(-1) && dy > slop && dy > abs(ev.x - pullDownX) * 1.5f) {
                    pulling = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return pulling
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!pulling) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                // Heavy resistance so an ordinary scroll never opens search by accident.
                pull = max(0f, ev.y - pullDownY - slop) * 0.45f
                list.translationY = pull
                pullHint.alpha = min(1f, pull / pullThreshold)
                pullHint.text = if (pull >= pullThreshold) "Release to search" else "Pull down to search"
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val open = ev.actionMasked == MotionEvent.ACTION_UP && pull >= pullThreshold
                pulling = false
                pull = 0f
                list.animate().translationY(0f).setDuration(180).start()
                pullHint.animate().alpha(0f).setDuration(120).start()
                if (open) openSearch()
            }
        }
        return true
    }

    private fun layoutSearch() {
        val lp = searchBar.layoutParams as LayoutParams
        lp.gravity = if (searchAtTop) Gravity.TOP else Gravity.BOTTOM
        lp.setMargins(dp(14f), if (searchAtTop) insetTop - dp(4f) else 0, dp(14f),
            if (searchAtTop) 0 else max(insetBottom, imeInset) + dp(10f))
        searchBar.layoutParams = lp
        val barSpace = if (searching) dp(56f) else 0
        val editSpace = if (editMode) dp(68f) + dp(28f) else 0
        list.setPadding(list.paddingLeft,
            insetTop + if (searchAtTop) barSpace else 0,
            list.paddingRight,
            insetBottom + dp(16f) + editSpace + if (!searchAtTop) barSpace + imeInset else 0)
    }

    private fun openSearch() {
        if (searching) return
        searching = true
        searchBar.setText("")
        searchBar.visibility = VISIBLE
        searchBar.alpha = 0f
        searchBar.translationY = (if (searchAtTop) -1 else 1) * dp(24f).toFloat()
        searchBar.animate().alpha(1f).translationY(0f).setDuration(180).start()
        layoutSearch()
        list.scrollToPosition(0)
        if (Prefs.searchKeyboard(context)) {
            searchBar.requestFocus()
            searchBar.post {
                context.getSystemService(InputMethodManager::class.java).showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun closeSearch() {
        if (!searching) return
        searching = false
        query = ""
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(searchBar.windowToken, 0)
        searchBar.clearFocus()
        searchBar.visibility = GONE
        layoutSearch()
        @SuppressLint("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    private fun normalize(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun filter(q: String) {
        query = q.trim()
        val n = normalize(query)
        filtered = items.flatMap { e -> if (e is GridEntry.Folder) e.apps.map { GridEntry.App(it) } else listOf(e) }
            .filter { normalize(it.label).contains(n) }
            .sortedBy { if (normalize(it.label).startsWith(n)) 0 else 1 }
        @SuppressLint("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    fun scrollToTop() = list.smoothScrollToPosition(0)

    @SuppressLint("NotifyDataSetChanged")
    private fun setEditMode(on: Boolean) {
        if (editMode == on) return
        editMode = on
        if (on) N9Sounds.play(context, N9Sounds.EDIT)
        editBar.visibility = if (on) VISIBLE else GONE
        layoutSearch()
        adapter.notifyDataSetChanged()
    }

    /** Places folders and applications in the stored order; new installs go at the end. */
    private fun arrange(apps: List<AppInfo>): MutableList<GridEntry> {
        if (Demo.on(context)) return apps.map { GridEntry.App(it) }.toMutableList()
        val byKey = apps.associateBy { it.component.flattenToString() }
        val stored = Folders.load(context)
        val inFolder = stored.values.flatMap { it.second }.toSet()
        val folders = stored.mapValues { (id, v) ->
            GridEntry.Folder(id, v.first, v.second.mapNotNull { byKey[it] }.toMutableList())
        }
        val out = mutableListOf<GridEntry>()
        val placed = HashSet<String>()
        for (k in AppRepository.order(context)) {
            if (k.startsWith(GridEntry.KEY_PREFIX)) {
                folders[k.removePrefix(GridEntry.KEY_PREFIX)]?.let { out += it; placed += k }
            } else if (k !in inFolder) {
                byKey[k]?.let { out += GridEntry.App(it); placed += k }
            }
        }
        folders.values.filter { it.key !in placed }.forEach { out += it; placed += it.key }
        apps.forEach { a ->
            val k = a.component.flattenToString()
            if (k !in placed && k !in inFolder) out += GridEntry.App(a)
        }
        // Android extras → Private space: the locked folder, always at the end.
        if (PrivateSpace.on(context) && PrivateSpace.profile(context) != null)
            out += GridEntry.Folder(PrivateSpace.FOLDER_ID, "Private", AppRepository.privateApps.toMutableList(), private = true)
        return out
    }

    private fun saveLayout() {
        if (Demo.on(context)) return
        AppRepository.saveLayout(context, items.filterNot { (it as? GridEntry.Folder)?.private == true }.map { it.key })
        Folders.save(context, items.filterIsInstance<GridEntry.Folder>())
    }

    /** N9: a new, empty folder at the end of the grid. */
    @SuppressLint("NotifyDataSetChanged")
    private fun newFolder() {
        items += GridEntry.Folder(Folders.newId(), "Folder", mutableListOf())
        saveLayout()
        adapter.notifyDataSetChanged()
        list.post { list.smoothScrollToPosition(rows.size - 1) }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleFolder(f: GridEntry.Folder) {
        val opening = openFolder !== f
        // Another folder open: fold it away first, then open this one.
        closeFolder { if (opening) openNow(f) }
    }

    /** The next panel bind grows out of the folder's row (N9 expand animation). */
    private var revealNext = false
    private var revealAnim: android.animation.ValueAnimator? = null

    @SuppressLint("NotifyDataSetChanged")
    private fun openNow(f: GridEntry.Folder) {
        openFolder = f
        revealNext = true
        adapter.notifyDataSetChanged()
        list.postDelayed({
            val panel = rows.indexOf(PANEL)
            if (panel >= 0) list.smoothScrollToPosition(panel)
        }, FOLDER_MS)
    }

    /** Folds the open panel back into its row, then drops it; [then] runs when it has gone. */
    @SuppressLint("NotifyDataSetChanged")
    private fun closeFolder(then: () -> Unit = {}) {
        if (openFolder == null) { then(); return }
        val holder = rows.indexOf(PANEL).takeIf { it >= 0 }?.let { list.findViewHolderForAdapterPosition(it) } as? PanelHolder
        val closing = openFolder
        val drop = {
            openFolder = null
            // Closing the private space locks it again.
            if (closing?.private == true) PrivateSpace.profile(context)?.let { PrivateSpace.lock(context, it) }
            adapter.notifyDataSetChanged()
            then()
        }
        if (holder == null) { drop(); return }
        reveal(holder.root, 0f, drop)
    }

    private fun reveal(root: RevealLayout, to: Float, end: () -> Unit = {}) {
        revealAnim?.cancel()
        revealAnim = android.animation.ValueAnimator.ofFloat(root.reveal, to).apply {
            duration = FOLDER_MS
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { root.reveal = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) { if (!cancelled) end() }
            })
            start()
        }
    }

    /** Edit mode's ✕ on a folder: the folder goes, its applications take its place. */
    @SuppressLint("NotifyDataSetChanged")
    private fun removeFolder(f: GridEntry.Folder) {
        val i = items.indexOf(f)
        if (i < 0) return
        items.removeAt(i)
        items.addAll(i, f.apps.map { GridEntry.App(it) })
        if (openFolder === f) openFolder = null
        saveLayout()
        adapter.notifyDataSetChanged()
    }

    /** Long-press on an application in an open folder takes it out, next to the folder. */
    @SuppressLint("NotifyDataSetChanged")
    private fun takeOut(f: GridEntry.Folder, app: AppInfo) {
        f.apps.remove(app)
        items.add(items.indexOf(f) + 1, GridEntry.App(app))
        saveLayout()
        adapter.notifyDataSetChanged()
    }

    private fun launch(app: AppInfo, from: View) {
        if (editMode) return
        val icon = from.findViewWithTag<View>("icon") ?: from
        val loc = IntArray(2).also { icon.getLocationOnScreen(it) }
        val bounds = Rect(loc[0], loc[1], loc[0] + icon.width, loc[1] + icon.height)
        // N9: the icon shows the loading ring while the application starts, and the
        // application only slides in once it's ready (no splash screen).
        val ring = Platform.launchOptions?.let { LoadingRing.start(icon) }
        val opts = Platform.launchOptions?.invoke { ring?.stop() }
            ?: ActivityOptions.makeScaleUpAnimation(icon, 0, 0, icon.width, icon.height).toBundle()
        runCatching { launcherApps.startMainActivity(app.component, app.user, bounds, opts) }
            .onSuccess { RunningApps.touch(context, app.component.packageName) }
            .onFailure { ring?.stop() }
    }

    private fun uninstall(app: AppInfo) {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private inner class Holder(val root: LinearLayout, val icon: ImageView, val badge: View, val label: TextView) :
        RecyclerView.ViewHolder(root)

    /** A vertical layout that shows only the top [reveal] of its height (the folder panel unfolding);
     *  its children keep their full size and the rest is clipped. */
    class RevealLayout(ctx: Context) : LinearLayout(ctx) {
        var reveal = 1f
            set(v) { field = v; requestLayout(); invalidate() }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (reveal < 1f) setMeasuredDimension(measuredWidth, (measuredHeight * reveal).toInt())
        }

        override fun dispatchDraw(canvas: android.graphics.Canvas) {
            canvas.save(); canvas.clipRect(0, 0, width, height); super.dispatchDraw(canvas); canvas.restore()
        }
    }

    private inner class PanelHolder(val root: RevealLayout, val notch: NotchRule, val name: EditText,
                                    val apps: GridLayout) : RecyclerView.ViewHolder(root)

    /** An application or folder cell: the squircle icon, the edit-mode ✕ and the label. */
    private fun cell(ctx: Context): Holder {
        val icon = ImageView(ctx).apply { tag = "icon" }
        val badge = CloseBadge(ctx)
        val iconFrame = FrameLayout(ctx).apply {
            clipChildren = false
            addView(icon, FrameLayout.LayoutParams(dp(58f), dp(58f), Gravity.CENTER).apply { setMargins(dp(8f), dp(8f), dp(8f), 0) })
            // N9: the red lit squircle ✕ at the icon's top right.
            addView(badge, FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.TOP or Gravity.END))
        }
        val label = TextView(ctx).apply {
            setTextColor(N9.TEXT)
            textSize = 12.5f
            typeface = N9.FONT
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2f), dp(6f), dp(2f), 0)
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            setPadding(0, dp(6f), 0, dp(14f))
            addView(iconFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return Holder(root, icon, badge, label)
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = if (rows.getOrNull(position) === PANEL) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == 1) return panel(parent.context)
            val h = cell(parent.context)
            h.root.setOnClickListener {
                when (val e = rows.getOrNull(h.bindingAdapterPosition)) {
                    is GridEntry.Folder -> if (e.private) privateTap(e) else toggleFolder(e)
                    is GridEntry.App -> if (!editMode) launch(e.app, h.root)
                }
            }
            h.root.setOnLongClickListener {
                if (searching) return@setOnLongClickListener false
                if (!editMode) {
                    h.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    setEditMode(true)
                }
                // Dragging with a folder open would move the panel too: close it first.
                if (openFolder == null && (rows.getOrNull(h.bindingAdapterPosition) as? GridEntry.Folder)?.private != true) touchHelper.startDrag(h)
                true
            }
            h.badge.setOnClickListener {
                when (val e = rows.getOrNull(h.bindingAdapterPosition)) {
                    is GridEntry.Folder -> removeFolder(e)
                    is GridEntry.App -> uninstall(e.app)
                }
            }
            return h
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty() || h !is Holder) onBindViewHolder(h, position)
            else if ("size" in payloads) bindSize(h)
            else (rows[position] as? GridEntry)?.let { bindMode(h, it) }
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            if (h is PanelHolder) {
                bindPanel(h)
                if (revealNext) { revealNext = false; h.root.reveal = 0f; reveal(h.root, 1f) }
                else if (revealAnim?.isRunning != true) h.root.reveal = 1f
                return
            }
            h as Holder
            val e = rows[position] as GridEntry
            when (e) {
                is GridEntry.App -> h.icon.setImageBitmap(e.app.icon)
                is GridEntry.Folder -> h.icon.setImageBitmap(Folders.icon(context, e, dp(Prefs.ICON_SIZE_MAX.toFloat())))
            }
            h.label.text = e.label
            h.root.contentDescription = e.label
            bindSize(h)
            bindMode(h, e)
        }

        private fun bindSize(h: Holder) {
            val px = dp(iconDp.toFloat())
            h.icon.layoutParams = (h.icon.layoutParams as FrameLayout.LayoutParams).apply { width = px; height = px }
            h.label.textSize = when {
                grid.spanCount >= 6 -> 10.5f
                grid.spanCount == 5 -> 11.5f
                else -> 12.5f
            }
            val fixed = rowsPerScreen > 0
            h.root.gravity = if (fixed) Gravity.CENTER else Gravity.CENTER_HORIZONTAL
            h.root.setPadding(0, if (fixed) 0 else dp(6f), 0, if (fixed) 0 else dp(14f))
            h.root.layoutParams = h.root.layoutParams.apply { height = rowHeight() }
        }

        private fun bindMode(h: Holder, e: GridEntry) {
            // ✕ on uninstallable applications and on folders, in edit mode only.
            val removable = (e is GridEntry.Folder && !e.private) || (e is GridEntry.App && e.app.removable)
            h.badge.visibility = if (editMode && removable) View.VISIBLE else View.GONE
            // N9: edit mode dims the grid to half; an open folder dims everything but itself.
            val open = openFolder
            val dim = (open != null && e !== open) || (open == null && editMode)
            h.icon.alpha = if (dim) 0.5f else 1f
            h.label.alpha = if (dim) 0.5f else 1f
        }
    }

    // ---- open folder panel ----

    private fun panel(ctx: Context): PanelHolder {
        val notch = NotchRule(ctx)
        val name = EditText(ctx).apply {
            setTextColor(N9.TEXT)
            setHintTextColor(N9.TEXT_SECONDARY)
            hint = "Folder"
            textSize = 20f
            typeface = N9.FONT_LIGHT
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        val apps = GridLayout(ctx)
        val bottom = View(ctx).apply { setBackgroundColor(RULE) }
        val root = RevealLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            addView(notch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(9f)))
            addView(name, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(4f), dp(10f), dp(4f), dp(6f)) })
            addView(apps, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1f) / 2 + 1)
                .apply { topMargin = dp(10f) })
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            // Taps between the panel's icons leave it alone (they don't close the folder).
            isClickable = true
        }
        val h = PanelHolder(root, notch, name, apps)
        fun commitName() {
            val f = openFolder ?: return
            val n = name.text.toString().trim().ifEmpty { "Folder" }
            if (n != f.name) {
                f.name = n
                saveLayout()
                val i = rows.indexOf(f)
                if (i >= 0) list.post { adapter.notifyItemChanged(i) }
            }
        }
        name.setOnEditorActionListener { v, _, _ ->
            commitName()
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(v.windowToken, 0)
            v.clearFocus()
            true
        }
        name.setOnFocusChangeListener { _, has -> if (!has) commitName() }
        return h
    }

    private fun bindPanel(h: PanelHolder) {
        val f = openFolder ?: return
        val cols = grid.spanCount
        val index = shown.indexOf(f)
        val inner = list.width - list.paddingLeft - list.paddingRight
        h.notch.x0 = list.paddingLeft + (index % cols + 0.5f) * inner / cols
        h.name.setText(f.name)
        // N9: the name is plain text; in edit mode it becomes a field for renaming.
        h.name.isEnabled = editMode
        h.name.background = if (editMode) GradientDrawable().apply {
            cornerRadius = dp(20f).toFloat()
            setColor(0x59FFFFFF)
        } else null
        h.name.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        h.apps.removeAllViews()
        h.apps.columnCount = cols
        val cellWidth = inner / cols
        f.apps.forEach { app ->
            val c = cell(context)
            c.root.layoutParams = GridLayout.LayoutParams().apply { width = cellWidth }
            c.icon.setImageBitmap(app.icon)
            c.label.text = app.label
            c.root.contentDescription = app.label
            val px = dp(iconDp.toFloat())
            c.icon.layoutParams = (c.icon.layoutParams as FrameLayout.LayoutParams).apply { width = px; height = px }
            c.badge.visibility = if (editMode && app.removable) View.VISIBLE else View.GONE
            c.badge.setOnClickListener { uninstall(app) }
            c.root.setOnClickListener { if (!editMode) launch(app, c.root) }
            c.root.setOnLongClickListener {
                if (f.private) return@setOnLongClickListener true
                c.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                takeOut(f, app)
                true
            }
            h.apps.addView(c.root)
        }
        if (f.apps.isEmpty()) h.apps.minimumHeight = dp(64f)
    }

    /** The panel's top rule, with the N9's small notch pointing up at the open folder. */
    private class NotchRule(ctx: Context) : View(ctx) {
        var x0 = 0f
            set(v) { field = v; invalidate() }
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = RULE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = ctx.resources.displayMetrics.density
        }
        private val path = android.graphics.Path()
        override fun onDraw(c: android.graphics.Canvas) {
            val y = height - paint.strokeWidth / 2
            val w = height * 0.9f
            path.reset()
            path.moveTo(0f, y)
            path.lineTo(x0 - w, y)
            path.lineTo(x0, y - height + paint.strokeWidth)
            path.lineTo(x0 + w, y)
            path.lineTo(width.toFloat(), y)
            c.drawPath(path, paint)
        }
    }

    private companion object {
        /** Marks the open folder's panel among the adapter's rows. */
        val PANEL = Any()
        /** Folder expand / collapse time. */
        const val FOLDER_MS = 260L
        const val RULE = 0xFFD8D8D8.toInt()
    }
}
