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
        const val EXTRA_PAGE = "etesian.page"

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
        fun showSwitcherStill(ctx: android.content.Context) {
            val home = live?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
            if (home != null) home.showMinimised()
            else ctx.startActivity(switcherIntent(ctx))
        }
    }

    private lateinit var pager: HomePager
    private lateinit var apps: AppsPage
    private lateinit var switcher: SwitcherPage
    private var events: EventsPage? = null
    /** The widget views, nearest Events first: one list, or the fixed screens. */
    private var widgets: List<WidgetsPage> = emptyList()
    private var widgetsKey = ""

    /** Visible views in swipe order, as (page id, view). */
    private var pages: List<Pair<Int, HomePage>> = emptyList()
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
        }
        setContentView(pager)
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
            override fun handleOnBackPressed() { pages.forEach { it.second.exitModes() } }
        })

        buildPages()
        // Started straight into a view (e.g. a minimise while home wasn't running yet).
        showPage(intent.getIntExtra(EXTRA_PAGE, PAGE_APPS), animate = false)
    }

    private fun applyInsets() {
        val top = bars.top + dp(28f)
        pages.forEach { it.second.applyInsets(bars.left, top, bars.right, bars.bottom) }
    }

    /** (Re)creates the view list from settings: [Widgets] [Events] Applications, Open applications. */
    private fun buildPages() {
        val wantWidgets = Prefs.widgetsPage(this)
        val wantEvents = Prefs.eventsPage(this)
        val fixed = Prefs.widgetsFixed(this)
        val screens = if (fixed) Prefs.widgetScreens(this) else 1
        val wKey = if (wantWidgets) "$fixed/$screens" else ""
        val key = "$wKey/$wantEvents"
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

        pages = buildList {
            // Screen 1 sits next to Events; further screens are further left.
            widgets.asReversed().forEach { add(PAGE_WIDGETS to it) }
            events?.let { add(PAGE_EVENTS to it) }
            add(PAGE_APPS to apps)
            add(PAGE_SWITCHER to switcher)
        }
        pager.removeAllViews()
        pages.forEach { pager.addView(it.second as View) }
        applyInsets()
        showPage(if (pages.any { it.first == current }) current else PAGE_APPS, animate = false)
    }

    private fun showPage(id: Int, animate: Boolean = true) {
        // indexOfLast: of the widget screens, the first one (next to Events).
        val index = pages.indexOfLast { it.first == id }
        if (index >= 0) pager.showPage(index, animate)
        if (index >= 0 && !animate) describePane(index)
    }

    /** Names the pane showing ("pane:events"...), for accessibility and the device tests. */
    private fun describePane(index: Int) {
        val name = when (pages.getOrNull(index)?.first) {
            PAGE_EVENTS -> "events"; PAGE_APPS -> "apps"; PAGE_SWITCHER -> "switcher"; PAGE_WIDGETS -> "widgets"
            else -> return
        }
        pager.contentDescription = "pane:$name"
    }

    private fun showMinimised() {
        pages.forEach { it.second.exitModes() }
        RunningApps.sync(this)
        showPage(PAGE_SWITCHER, animate = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val wasEditing = pages.map { it.second.exitModes() }.any { it }
        RunningApps.sync(this)
        val requested = intent.getIntExtra(EXTRA_PAGE, -1)
        when {
            requested >= 0 -> showPage(requested, animate = resumed)
            wasEditing -> Unit
            // Home pressed while already home: back to Applications, scrolled to the top.
            resumed -> { showPage(PAGE_APPS); apps.scrollToTop() }
            // Leaving an application minimises it into Open applications, as on the N9.
            RunningApps.entries.isNotEmpty() -> showPage(PAGE_SWITCHER, animate = false)
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (Platform.edgeGestureActive) return true
        return super.dispatchTouchEvent(ev)
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
                PAGE_WIDGETS -> "page_widgets"; PAGE_EVENTS -> "page_events"
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
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }

    @Deprecated("Widget bind/configure results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        widgets.forEach { it.onActivityResult(requestCode, resultCode) }
    }
}
