package com.bompotis.etesian.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class HomeActivity : ComponentActivity() {

    companion object {
        const val PAGE_EVENTS = 0
        const val PAGE_APPS = 1
        const val PAGE_SWITCHER = 2
        const val PAGE_WIDGETS = 3
        /** Glance's news (Settings → Views → News), furthest left. */
        const val PAGE_NEWS = 4
        const val EXTRA_PAGE = "etesian.page"
        /** Read by SystemUI (EtesianBubble): 1 while the Events view is on screen. */
        const val EVENTS_SHOWING = "etesian_events_showing"

        /** Home, showing Open applications; where minimised applications go. */
        fun switcherIntent(ctx: android.content.Context): Intent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(ctx, HomeActivity::class.java)
                .putExtra(EXTRA_PAGE, PAGE_SWITCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** The live home screen, so a minimise can switch its pane without starting it again. */
        private var live: java.lang.ref.WeakReference<HomeActivity>? = null

        /**
         * Shows Open applications after a minimise. The swipe has already slid the app away and
         * home is in front, so switch the pane in place: starting HomeActivity again would play
         * the system's default window animation, a second slide (regression 2026-10-28).
         */
        fun showSwitcherStill(ctx: android.content.Context) = showStill(ctx, PAGE_SWITCHER)

        /** The home view an application swiped away from [edge] goes to (Settings → Gestures, Open applications by default). */
        fun pageAfterLeaving(ctx: android.content.Context, edge: String): Int = when (Prefs.leaveTo(ctx, edge)) {
            Prefs.LEAVE_EVENTS -> PAGE_EVENTS
            Prefs.LEAVE_APPS -> PAGE_APPS
            Prefs.LEAVE_WIDGETS -> PAGE_WIDGETS
            Prefs.LEAVE_NEWS -> PAGE_NEWS
            Prefs.LEAVE_LAST -> lastPage
            else -> PAGE_SWITCHER
        }

        /** How home looks behind a window on top of it: platform WindowDrag.HOME_SCALE and HOME_LIGHT, the same. */
        private const val BEHIND_SCALE = 0.9f
        private const val BEHIND_LIGHT = 0.25f

        /** The view home showed when it was last left (for "Where you were"). */
        @Volatile var lastPage = PAGE_APPS


        /**
         * The edge view under the finger (2026-11-24, user: it must move with the finger as an application does, never
         * jump in or drop away on its own). [edgeDragBegin] at the swipe's start, [edgeDragMove] with how far in it is
         * (0..1 of the screen), [edgeDragEnd] with the inward speed (dp/s) when it lets go. False: nothing to drag.
         */
        fun edgeDragBegin(edge: String): Boolean = live?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }?.takeOver(edge) == true
        fun edgeDragMove(progress: Float) { live?.get()?.dragMove(progress) }
        fun edgeDragEnd(velocityDp: Float) { live?.get()?.dragEnd(velocityDp) }

        /**
         * An application is being swiped away from [edge], which has a view of its own: that view is put in, settled,
         * before home's window comes over the application (WindowDrag), so it arrives with the finger (2026-11-24).
         */
        fun prepareEdgeView(edge: String) {
            live?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }?.let { h ->
                h.edgeViews.entries.firstOrNull { it.value.second == edge }?.key?.let { h.openSheet(it, animate = false) }
            }
        }

        /** A view of an edge's own is in front: every other edge must still lead somewhere (EdgeGestures asks). */
        fun sheetShowing(): Boolean = live?.get()?.let { it.sheetPage >= 0 } == true

        /** [showSwitcherStill] for any view: home already in front, switch its pane in place. */
        fun showStill(ctx: android.content.Context, page: Int) {
            val home = live?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
            if (home != null) home.showMinimised(page)
            else ctx.startActivity(switcherIntent(ctx).putExtra(EXTRA_PAGE, page))
        }
    }

    private lateinit var pager: HomePager
    private lateinit var apps: AppsPage
    private lateinit var switcher: SwitcherPage
    private var events: EventsPage? = null
    private var news: NewsPage? = null
    /** The widget views, nearest Events first: one list, or the fixed screens. */
    private var widgets: List<WidgetsPage> = emptyList()
    private var widgetsKey = ""

    /** Visible views in swipe order, as (page id, view). */
    private var pages: List<Pair<Int, HomePage>> = emptyList()
    /** Views taken out of the row ("Only by this swipe"): page id → (view, the edge it comes in from). */
    private var edgeViews: Map<Int, Pair<HomePage, String>> = emptyMap()
    /** Holds an edge view while it is shown, over the row. */
    private lateinit var sheet: android.widget.FrameLayout
    private var sheetPage = -1
    private var layoutKey = ""
    private var bars = Insets.NONE
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        live = java.lang.ref.WeakReference(this)
        super.onCreate(savedInstanceState)
        ShareActivity.sync(this)
        // Follow the switch even when it is set from outside the settings page.
        Extras.observe(applicationContext, Extras.SHARE) { ShareActivity.sync(applicationContext) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        RunningApps.ensureLoaded(this)
        BelleWidgets.init(this)
        // The feeds moved to the Feeds app: hand it the old subscriptions once.
        Feeds.migrate(this)

        pager = HomePager(this)
        apps = AppsPage(this)
        switcher = SwitcherPage(this)
        pager.onPageSettled = { index ->
            describePane(index)
            pages.forEachIndexed { i, p -> if (i != index) p.second.exitModes() }
            pages.getOrNull(index)?.second?.onShown()
            eventsShowing(resumed)
        }
        sheet = android.widget.FrameLayout(this).apply { visibility = View.GONE; setBackgroundColor(android.graphics.Color.BLACK) }
        setContentView(android.widget.FrameLayout(this).apply {
            addView(pager); addView(sheet)
        })
        Platform.homeContent = pager

        ViewCompat.setOnApplyWindowInsetsListener(pager) { _, insets ->
            bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            pager.topInset = bars.top
            applyInsets()
            apps.setImeInset(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // A launcher never finishes on back; it only leaves edit/close/search modes.
            override fun handleOnBackPressed() {
                // An edge view leaves its own modes first, then goes back the way it came.
                val shown = edgeViews[sheetPage]?.first
                if (shown != null) { if (!shown.exitModes()) closeSheet(); return }
                pages.forEach { it.second.exitModes() }
            }
        })

        buildPages()
        // Started straight into a view (e.g. a minimise while home wasn't running yet).
        showPage(intent.getIntExtra(EXTRA_PAGE, PAGE_APPS), animate = false)
    }

    private fun applyInsets() {
        val top = bars.top + dp(28f)
        pages.forEach { it.second.applyInsets(bars.left, top, bars.right, bars.bottom) }
        edgeViews.values.forEach { it.first.applyInsets(bars.left, top, bars.right, bars.bottom) }
    }

    /** (Re)creates the view list from settings: [Widgets] [Events] Applications, Open applications. */
    private fun buildPages() {
        val wantWidgets = Prefs.widgetsPage(this)
        val wantEvents = Prefs.eventsPage(this)
        val fixed = Prefs.widgetsFixed(this)
        val screens = if (fixed) Prefs.widgetScreens(this) else 1
        val wKey = if (wantWidgets) "$fixed/$screens" else ""
        val wantNews = Prefs.newsPage(this)
        val only = Prefs.edgeOnlyViews(this)
        val key = "$wKey/$wantEvents/$wantNews/$only"
        if (key == layoutKey) return
        layoutKey = key
        val current = pages.getOrNull(pager.currentPage)?.first ?: PAGE_APPS

        if (wKey != widgetsKey) {
            widgetsKey = wKey
            widgets.forEach { it.stopListening() }
            widgets = if (!wantWidgets) emptyList() else {
                WidgetsPage.arrange(this, fixed, screens)
                (0 until screens).map { WidgetsPage(this, it, fixed) }.also { list ->
                    list.zipWithNext { a, b -> a.next = b }
                    if (resumed) list.forEach { it.startListening() }
                }
            }
        }
        if (wantEvents && events == null) events = EventsPage(this)
        if (!wantEvents) events = null
        if (wantNews && news == null) news = NewsPage(this)
        if (!wantNews) news = null

        val all = buildList {
            news?.let { add(PAGE_NEWS to it) }
            // Screen 1 sits next to Events; further screens are further left.
            widgets.asReversed().forEach { add(PAGE_WIDGETS to it) }
            events?.let { add(PAGE_EVENTS to it) }
            add(PAGE_APPS to apps)
            add(PAGE_SWITCHER to switcher)
        }
        // "Only by this swipe": a view the user gave to an edge leaves the row (widget screens: the first one).
        val leave = mapOf(Prefs.LEAVE_APPS to PAGE_APPS, Prefs.LEAVE_EVENTS to PAGE_EVENTS,
            Prefs.LEAVE_WIDGETS to PAGE_WIDGETS, Prefs.LEAVE_NEWS to PAGE_NEWS)
        if (sheetPage >= 0) closeSheet(animate = false)
        edgeViews = only.mapNotNull { (view, edge) ->
            val id = leave[view] ?: return@mapNotNull null
            all.lastOrNull { it.first == id }?.let { id to (it.second to edge) }
        }.toMap()
        pages = all.filter { p -> edgeViews[p.first]?.first !== p.second }
        pager.removeAllViews()
        pages.forEach { pager.addView(it.second as View) }
        applyInsets()
        showPage(if (pages.any { it.first == current }) current else pages.firstOrNull { it.first == PAGE_APPS }?.first
            ?: PAGE_SWITCHER, animate = false)
    }

    /** Brings an edge view in from its edge, over the row (it stays until Back, Home or leaving home). */
    private fun openSheet(id: Int, animate: Boolean) {
        val (page, edge) = edgeViews[id] ?: return
        if (sheetPage == id) return
        if (sheetPage >= 0) closeSheet(animate = false)
        pages.forEach { it.second.exitModes() }
        val v = page as View
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        // The pager hides the pages it is not showing; out of it, the view must be shown again or the sheet is black.
        v.visibility = View.VISIBLE
        // It also keeps the pager's sideways offset (a screen width for a view that was beside the shown one): reset,
        // or it is drawn off the sheet and the sheet is black (2026-11-24, user: apps on the bottom swipe).
        v.translationX = 0f; v.translationY = 0f
        sheet.addView(v, android.widget.FrameLayout.LayoutParams(-1, -1))
        sheetPage = id
        sheet.visibility = View.VISIBLE
        sheet.animate().cancel()
        val (dx, dy) = offscreen(edge)
        if (animate) {
            sheet.translationX = dx; sheet.translationY = dy
            sheet.animate().translationX(0f).translationY(0f).setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        } else { sheet.translationX = 0f; sheet.translationY = 0f }
        sheet.contentDescription = "pane:" + paneName(id)
        page.onShown()
    }

    private fun closeSheet(animate: Boolean = true) {
        val id = sheetPage.takeIf { it >= 0 } ?: return
        sheetPage = -1
        val done = { sheet.removeAllViews(); sheet.visibility = View.GONE }
        // Never left open with no view in it (a view no longer on an edge): that would toggle the next swipe shut.
        val (page, edge) = edgeViews[id] ?: run { sheet.animate().cancel(); done(); return }
        page.exitModes()
        if (!animate) { sheet.animate().cancel(); done(); return }
        val (dx, dy) = offscreen(edge)
        sheet.animate().translationX(dx).translationY(dy).setDuration(200).withEndAction(done).start()
    }

    /** Where a view coming in from [edge] starts (and goes back to). */
    private fun offscreen(edge: String): Pair<Float, Float> {
        val w = window.decorView.width.toFloat(); val h = window.decorView.height.toFloat()
        return when (edge) { "left" -> -w to 0f; "right" -> w to 0f; else -> 0f to h }
    }

    private fun showPage(id: Int, animate: Boolean = true) {
        if (id in edgeViews) { openSheet(id, animate); return }
        if (sheetPage >= 0) closeSheet(animate)
        // indexOfLast: of the widget screens, the first one (next to Events).
        val index = pages.indexOfLast { it.first == id }
        if (index >= 0) pager.showPage(index, animate)
        if (index >= 0 && !animate) describePane(index)
    }

    /** Names the pane showing ("pane:events"...), for accessibility and the device tests. */
    private fun describePane(index: Int) {
        val name = paneName(pages.getOrNull(index)?.first ?: return) ?: return
        pager.contentDescription = "pane:$name"
    }

    private fun paneName(id: Int) = when (id) {
        PAGE_EVENTS -> "events"; PAGE_APPS -> "apps"; PAGE_SWITCHER -> "switcher"; PAGE_WIDGETS -> "widgets"
        PAGE_NEWS -> "news"
        else -> null
    }

    private fun showMinimised(page: Int = PAGE_SWITCHER) {
        pages.forEach { it.second.exitModes() }
        RunningApps.sync(this)
        showPage(page, animate = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val wasEditing = pages.map { it.second.exitModes() }.any { it }
        RunningApps.sync(this)
        val requested = intent.getIntExtra(EXTRA_PAGE, -1)
        when {
            requested >= 0 -> showPage(requested, animate = resumed)
            wasEditing -> Unit
            // Home pressed while already home: back to Applications, scrolled to the top. Android
            // pauses us before delivering the intent, so "already home" is the window's focus, not
            // [resumed] (2026-11-04: Home on Open applications or Events did nothing).
            resumed || focused -> if (sheetPage >= 0) closeSheet() else { showPage(PAGE_APPS); apps.scrollToTop() }
            // Leaving an application minimises it into Open applications, as on the N9.
            RunningApps.entries.isNotEmpty() -> showPage(PAGE_SWITCHER, animate = false)
        }
    }

    /** A swipe in from an edge that has an edge view, on the home screen itself: (edge, down x, down y). */
    private var edgeSwipe: Triple<String, Float, Float>? = null
    private var swipeVelocity: android.view.VelocityTracker? = null
    private var swipeDragging = false

    /**
     * An edge view moved by the finger. [opening]: it comes in from its edge over the row, which goes back into the dark
     * (as home does behind an application coming up). Otherwise it leaves as an application does: it slides along with
     * the finger, out the opposite side, while the view it leaves to (Settings → Gestures) comes out of the dark behind
     * it, zooming in from [WindowDrag]'s scale. [toPage]: the row's view to end on; [backTo]: the row's view before.
     */
    private class SheetDrag(val edge: String, val opening: Boolean, val backTo: Int) {
        var progress = 0f
        /** The system's edge gestures took the touch over from home's own (they cancel it here): theirs from then on. */
        var bySystem = false
        var animator: android.animation.ValueAnimator? = null
    }
    private var sheetDrag: SheetDrag? = null

    /**
     * The system's edge gestures (platform EdgeGestures) start a swipe. Home may already be moving the view for the same
     * touch, before they took it over: they carry on with that drag, never a second one from the start.
     */
    private fun takeOver(edge: String): Boolean {
        val running = sheetDrag?.takeIf { it.edge == edge && it.animator == null }
        if (running != null) {
            running.bySystem = true
            edgeSwipe = null; swipeDragging = false; swipeVelocity?.recycle(); swipeVelocity = null
            return true
        }
        return dragBegin(edge)?.also { it.bySystem = true } != null
    }

    private fun dragBegin(edge: String): SheetDrag? {
        sheetDrag?.animator?.end()
        val own = edgeViews.entries.firstOrNull { it.value.second == edge }?.key
        val before = pager.currentPage
        val drag = when {
            sheetPage >= 0 -> {
                // Leaving: the same edge back to the row as it was; any other edge to its view (Settings → Gestures).
                if (own != sheetPage) {
                    val page = pageAfterLeaving(this, edge).let { if (it in edgeViews) PAGE_SWITCHER else it }
                    pages.indexOfLast { it.first == page }.takeIf { it >= 0 }?.let { pager.showPage(it, animate = false) }
                }
                SheetDrag(edge, opening = false, backTo = before)
            }
            own != null -> {
                openSheet(own, animate = false)
                SheetDrag(edge, opening = true, backTo = before)
            }
            else -> return null
        }
        sheetDrag = drag
        placeSheet(drag, 0f)
        return drag
    }

    private fun dragMove(progress: Float) {
        val drag = sheetDrag ?: return
        if (drag.animator != null) return
        drag.progress = progress.coerceIn(0f, 1f)
        placeSheet(drag, drag.progress)
    }

    /** Lets go: on past [Prefs.closeDistance] or with a flick in, as an application does; back otherwise. */
    private fun dragEnd(velocityDp: Float) {
        val drag = sheetDrag ?: return
        val p = drag.progress
        val commit = when {
            velocityDp < -900f / 3 -> false
            velocityDp > 900f -> p > 0.08f
            else -> p > Prefs.closeDistance(this)
        }
        val to = if (commit) 1f else 0f
        drag.animator = android.animation.ValueAnimator.ofFloat(p, to).apply {
            duration = (220 + 180 * kotlin.math.abs(to - p)).toLong()
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { placeSheet(drag, it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) = settleDrag(drag, commit)
            })
            start()
        }
    }

    private fun settleDrag(drag: SheetDrag, commit: Boolean) {
        if (sheetDrag !== drag) return
        sheetDrag = null
        pager.scaleX = 1f; pager.scaleY = 1f; pager.alpha = 1f
        // Gone: an opening let go early, or a leaving carried through. Otherwise the view stays and the row is put back.
        if (drag.opening != commit) closeSheet(animate = false)
        sheet.animate().cancel(); sheet.translationX = 0f; sheet.translationY = 0f
        if (drag.opening || !commit) pager.showPage(drag.backTo, animate = false)
        if (sheetPage < 0) describePane(pager.currentPage)
    }

    /** Everything for [p] (0..1). */
    private fun placeSheet(drag: SheetDrag, p: Float) {
        val w = window.decorView.width.toFloat(); val h = window.decorView.height.toFloat()
        // The edge view is the window on top: how far it is out of place, and where it goes.
        val out = if (drag.opening) 1 - p else p
        val (dx, dy) = if (drag.opening) offscreen(drag.edge).let { it.first * out to it.second * out }
            else when (drag.edge) { "left" -> w * out to 0f; "right" -> -w * out to 0f; else -> 0f to -h * out }
        sheet.translationX = dx; sheet.translationY = dy
        // The row behind it: in the dark and a little small while the view covers it, as home behind an application.
        val light = if (drag.opening) 1 - p else p
        val scale = BEHIND_SCALE + (1 - BEHIND_SCALE) * light
        pager.scaleX = scale; pager.scaleY = scale
        pager.alpha = BEHIND_LIGHT + (1 - BEHIND_LIGHT) * light
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (Platform.edgeGestureActive) return true
        if (edgeViews.isNotEmpty() && edgeSwipeOnHome(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    /**
     * On the home screen an edge with a view of its own brings that view in, with the finger ([dragBegin]); the same edge
     * sends it back, and any other edge, while it is in, leaves it for that edge's view (2026-11-24, user: the other sides
     * did nothing and the user was stuck in it). True once the swipe is taken.
     */
    private fun edgeSwipeOnHome(ev: android.view.MotionEvent): Boolean {
        val d = resources.displayMetrics.density
        val w = window.decorView.width; val h = window.decorView.height
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val zone = 18 * d
                val edge = when {
                    ev.rawX < zone -> "left"; ev.rawX > w - zone -> "right"; ev.rawY > h - zone -> "bottom"; else -> null
                }
                edgeSwipe = edge?.takeIf { e -> sheetPage >= 0 || edgeViews.values.any { it.second == e } }?.let { Triple(it, ev.rawX, ev.rawY) }
                swipeVelocity?.recycle(); swipeVelocity = android.view.VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val (edge, x0, y0) = edgeSwipe ?: return false
                swipeVelocity?.addMovement(ev)
                val inward = when (edge) { "left" -> ev.rawX - x0; "right" -> x0 - ev.rawX; else -> y0 - ev.rawY }
                val across = if (edge == "bottom") ev.rawX - x0 else ev.rawY - y0
                if (!swipeDragging) {
                    val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
                    if (inward > slop && inward > kotlin.math.abs(across)) {
                        super.dispatchTouchEvent(android.view.MotionEvent.obtain(ev).apply { action = android.view.MotionEvent.ACTION_CANCEL })
                        swipeDragging = dragBegin(edge) != null
                        if (!swipeDragging) { edgeSwipe = null; return true }
                    } else if (kotlin.math.abs(across) > slop * 2) { edgeSwipe = null; return false }
                    else return false
                }
                dragMove(inward / (if (edge == "bottom") h else w).toFloat())
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                val (edge, _, _) = edgeSwipe ?: return false
                edgeSwipe = null
                if (!swipeDragging) return false
                swipeDragging = false
                val vt = swipeVelocity ?: return true
                swipeVelocity = null
                vt.addMovement(ev); vt.computeCurrentVelocity(1000)
                val v = when (edge) { "left" -> vt.xVelocity; "right" -> -vt.xVelocity; else -> -vt.yVelocity }
                vt.recycle()
                if (ev.actionMasked != android.view.MotionEvent.ACTION_CANCEL) { dragEnd(v / d); return true }
                // A cancel is mostly the system's edge gestures taking the touch over (they carry the drag on, takeOver);
                // only if they don't, shortly, does it go back.
                val drag = sheetDrag
                window.decorView.postDelayed({ if (drag != null && sheetDrag === drag && !drag.bySystem) dragEnd(-Float.MAX_VALUE) }, 200)
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        if (Platform.homeContent === pager) {
            Platform.homeContent = null
        }
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        widgets.forEach { it.startListening() }
    }

    override fun onStop() {
        // An edge view is a moment's visit: home comes back to the row (an application left from that edge brings it
        // back in, through pageAfterLeaving).
        closeSheet(animate = false)
        widgets.forEach { it.stopListening() }
        super.onStop()
    }

    /** The black-screen background last applied (setting and image version). */
    private var backgroundKey = ""

    /** Home is a black screen: black on the N9, or the user's black-screen wallpaper. */
    private fun applyBackground() {
        val cr = contentResolver
        val wall = android.provider.Settings.Secure.getInt(cr, Backgrounds.SETTING_DARK, 0) == 1
        val mode = Backgrounds.homeMode(this)
        val key = "$wall:$mode:$layoutKey:" + android.provider.Settings.Secure.getLong(cr, Backgrounds.SETTING_VERSION, 0) +
            Backgrounds.PAGES.joinToString("") { if (Backgrounds.enabled(this, it.first)) "1" else "0" } +
            Backgrounds.enabled(this, Backgrounds.HOME_SCROLL_KIND)
        if (key == backgroundKey) return
        backgroundKey = key
        fun load(kind: String) = if (Backgrounds.enabled(this, kind))
            android.graphics.BitmapFactory.decodeFile(Backgrounds.processed(this, kind).path) else null
        // The home wallpapers are drawn by the pager, over black, moving with the views.
        pager.panorama = if (mode == Backgrounds.HOME_SCROLL) load(Backgrounds.HOME_SCROLL_KIND) else null
        pager.perPage = if (mode == Backgrounds.HOME_PER_PAGE) pages.map { (id, _) ->
            when (id) {
                PAGE_WIDGETS -> "page_widgets"; PAGE_EVENTS, PAGE_NEWS -> "page_events"
                PAGE_APPS -> "page_apps"; else -> "page_switcher"
            }.let(::load)
        } else emptyList()
        if (mode != Backgrounds.HOME_SAME) {
            pager.setBackgroundColor(N9.BACKGROUND)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(N9.BACKGROUND))
            return
        }
        val bmp = if (wall) android.graphics.BitmapFactory.decodeFile(
            Backgrounds.processed(this, Backgrounds.DARK).path) else null
        pager.setBackgroundColor(if (bmp != null) android.graphics.Color.TRANSPARENT else N9.BACKGROUND)
        window.setBackgroundDrawable(if (bmp != null) CroppedBitmap(bmp)
            else android.graphics.drawable.ColorDrawable(N9.BACKGROUND))
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (Extras.on(this, Extras.GLANCE)) Thread { Agenda.publishGlance(applicationContext) }.start()
        buildPages()
        applyBackground()
        apps.reloadSettings()
        RunningApps.prune(this)
        N9Defaults.applyOnce(this)
        pages.getOrNull(pager.currentPage)?.second?.onShown()
        eventsShowing(true)
    }

    /** Home has the input focus: it is what's on screen, with no application or dialog over it. */
    private var focused = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        focused = hasFocus
    }

    override fun onPause() {
        super.onPause()
        pages.getOrNull(pager.currentPage)?.first?.let { lastPage = it }
        resumed = false
        eventsShowing(false)
    }

    /**
     * Tells SystemUI the Events view is on screen (Settings.Secure etesian_events_showing): a new
     * notification then slides into its section here instead of popping up as a bubble.
     */
    private fun eventsShowing(resumed: Boolean) {
        val on = resumed && pages.getOrNull(pager.currentPage)?.second is EventsPage
        runCatching { android.provider.Settings.Secure.putInt(contentResolver, EVENTS_SHOWING, if (on) 1 else 0) }
    }

    @Deprecated("Widget bind/configure results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        widgets.forEach { it.onActivityResult(requestCode, resultCode) }
    }
}
