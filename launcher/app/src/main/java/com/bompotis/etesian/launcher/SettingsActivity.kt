package com.bompotis.etesian.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsActivity : AppCompatActivity() {

    /** A sub page of Etesian settings: its key, title, one-line summary and icon. */
    class Section(val key: String, val title: String, val summary: String, val icon: Int, val main: Boolean = true)

    companion object {
        const val EXTRA_SECTION = "section"
        /** Title of a pinned setting to scroll to on its sub page. */
        const val EXTRA_FOCUS = "focus"
        private const val PINS = "settings_pins"

        /** Settings pinned to the main page, as "section\u0001title", oldest first. */
        fun pins(ctx: android.content.Context): List<Pair<String, String>> =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx).getString(PINS, "").orEmpty()
                .split('\n').filter { it.isNotEmpty() }.map { it.substringBefore('\u0001') to it.substringAfter('\u0001') }

        fun setPins(ctx: android.content.Context, list: List<Pair<String, String>>) =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putString(PINS, list.distinct().joinToString("\n") { "${it.first}\u0001${it.second}" }).apply()
        val SECTIONS = listOf(
            Section("home", "Home screen", "Views, indicator dots", R.drawable.ic_set_home),
            Section("apps", "Applications", "Grid, icons, search, private space", R.drawable.ic_set_apps),
            Section("events", "Events and notifications", "Feeds, weather, notifications", R.drawable.ic_set_events),
            Section("status", "Status menu", "Toggles, sliders, music, modes", R.drawable.ic_set_status),
            Section("open", "Open applications", "Arranging, carousel, split screen", R.drawable.ic_set_open),
            Section("wallpapers", "Wallpapers & backgrounds", "Home wallpapers, app and menu backgrounds", R.drawable.ic_set_wallpaper),
            Section("gestures", "Gestures", "Swipes, back, edge bar", R.drawable.ic_set_gestures),
            Section("lock", "Lock screen", "Notifications, glance line, weather", R.drawable.ic_set_lock),
            Section("emergency", "Emergency information", "Medical details for first responders", R.drawable.ic_set_emergency),
            Section("sharing", "Sharing", "Share sheet, sharing a network", R.drawable.ic_set_share),
            Section("toolbar", "Toolbar", "The N9 bar under every app", R.drawable.ic_set_toolbar),
            Section("system", "System", "Permissions, default home app", R.drawable.ic_set_system),
            // Sub pages of a page: not listed on the main page.
            Section("priority", "Priority people", "", 0, main = false),
            Section("dismissed", "Dismissed notifications", "", 0, main = false),
        )
        /** Pages that start with a live preview of what their settings change. */
        val PREVIEWS = setOf("apps", "events", "status", "open", "lock", "emergency", "sharing", "priority", "dismissed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened from the launcher's own grid, Android treats this as an in-app screen and uses
        // its default slide from the right; like any other N9 application, it comes from the left.
        // A section is a sub page: as on the N9, it pushes in from the right and goes back to the right.
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            if (intent.getStringExtra(EXTRA_SECTION) == null) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.n9_task_enter, R.anim.n9_hold)
            } else {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.n9_page_in, R.anim.n9_page_out)
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.n9_page_back_in, R.anim.n9_page_back_out)
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = FrameLayout(this).apply { id = R.id.settings_container }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            addView(TextView(context).apply {
                text = SECTIONS.firstOrNull { it.key == intent.getStringExtra(EXTRA_SECTION) }?.title ?: "Etesian settings"
                setTextColor(N9.TEXT)
                // N9 page header (mocks 09b / 20): 30 px light title over a hairline (x1.36).
                textSize = 35f
                typeface = N9.FONT_LIGHT
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(16f), dp(16f), dp(10f))
            })
            addView(android.view.View(context).apply { setBackgroundColor(0xFF5A5A5A.toInt()) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1f)).apply {
                    marginStart = dp(16f); marginEnd = dp(16f)
                })
            addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settings_container, Screen().apply {
                arguments = Bundle().apply { putString(EXTRA_SECTION, intent.getStringExtra(EXTRA_SECTION)) }
            }).commit()
        }
    }

    /** As on the N9: the main settings page has no bottom bar, every page under it the dark one. */
    override fun onResume() {
        super.onResume()
        if (SystemToolbar.available(this)) {
            val main = intent.getStringExtra(EXTRA_SECTION) == null
            SystemToolbar(this).set(back = !main, items = emptyList(), style = "dark", hidden = main) {}
        }
    }

    /** A plain main-page row: bold title, chevron (or a small star for a pinned setting); [onLong] on long-press. */
    class QuickRow(ctx: android.content.Context, private val star: Boolean = false) : Preference(ctx) {
        var onLong: (() -> Unit)? = null
        init { layoutResource = R.layout.pref_quick_row; isPersistent = false }
        override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            holder.findViewById(R.id.quick_star)?.visibility = if (star) android.view.View.VISIBLE else android.view.View.GONE
            holder.itemView.setOnLongClickListener { onLong?.invoke(); onLong != null }
        }
    }

    class Screen : PreferenceFragmentCompat() {
        /** Priority people → Add a person: the contact picker, then the page again with them listed. */
        private val pickContact = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickContact()) { uri ->
            val id = uri?.lastPathSegment?.toLongOrNull() ?: return@registerForActivityResult
            Priority.set(requireContext(), id, true)
            activity?.recreate()
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val section = arguments?.getString(EXTRA_SECTION) ?: return
            val focus = arguments?.getString(EXTRA_FOCUS)
            focus?.let { t -> find(preferenceScreen, t)?.let { scrollToPreference(it) } }
            // Otherwise open at the top (the N9 switches must not pull the list down to themselves).
            if (focus == null) {
                // The list itself takes focus first, so no row pulls the page down to itself.
                listView.descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
                listView.isFocusableInTouchMode = true
                listView.requestFocus()
                listView.post { listView.scrollToPosition(0) }
                listView.postDelayed({ listView.scrollToPosition(0) }, 400)
            }
            // Long-press any setting on a sub page to pin it to the main page.
            listView.addOnChildAttachStateChangeListener(object : androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(v: android.view.View) {
                    v.setOnLongClickListener {
                        val pos = listView.getChildAdapterPosition(v)
                        val p = (listView.adapter as? androidx.preference.PreferenceGroupAdapter)?.getItem(pos)
                        val title = p?.title?.toString()
                        if (p == null || p is PreferenceCategory || title.isNullOrEmpty()) return@setOnLongClickListener false
                        val ctx = v.context
                        val pinned = (section to title) in pins(ctx)
                        android.app.AlertDialog.Builder(ctx).setItems(arrayOf(if (pinned) "Unpin" else "Pin to Settings")) { _, _ ->
                            setPins(ctx, if (pinned) pins(ctx) - (section to title) else pins(ctx) + (section to title))
                            android.widget.Toast.makeText(ctx, if (pinned) "Unpinned" else "Pinned to Settings", android.widget.Toast.LENGTH_SHORT).show()
                        }.show()
                        true
                    }
                }
                override fun onChildViewDetachedFromWindow(v: android.view.View) {}
            })
        }

        private var shownPins: List<Pair<String, String>>? = null

        /** Pins made on a sub page show up when coming back to the main page. */
        override fun onResume() {
            super.onResume()
            if (arguments?.getString(EXTRA_SECTION) != null) return
            val now = pins(requireContext())
            if (shownPins != null && shownPins != now) requireActivity().recreate()
            shownPins = now
        }

        private fun find(g: androidx.preference.PreferenceGroup, title: String): Preference? {
            for (i in 0 until g.preferenceCount) {
                val p = g.getPreference(i)
                if (p.isVisible && p !is PreferenceCategory && p.title?.toString() == title) return p
                if (p is androidx.preference.PreferenceGroup) find(p, title)?.let { return it }
            }
            return null
        }

        /** Which background ("light"/"dark") the picture being picked is for. */
        private var pickingFor = Backgrounds.LIGHT
        private val pickImage = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = requireContext().applicationContext
            val kind = pickingFor
            android.widget.Toast.makeText(ctx, "Preparing the wallpaper\u2026", android.widget.Toast.LENGTH_SHORT).show()
            Thread {
                val ok = Backgrounds.setImage(ctx, kind, uri)
                // A home wallpaper picked straight from the mode choice is also switched on.
                val home = kind == Backgrounds.HOME_SCROLL_KIND || Backgrounds.PAGES.any { it.first == kind }
                if (ok && home) android.provider.Settings.Secure.putInt(ctx.contentResolver, Backgrounds.setting(kind), 1)
                activity?.runOnUiThread {
                    if (ok && home) findPreference<ListPreference>("bg_$kind")?.value = "1"
                    android.widget.Toast.makeText(ctx, if (ok) "Wallpaper set" else "Couldn't use that picture",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        /** Background choice (stored in Settings.Secure), its wallpaper and effect. */
        private fun backgroundPrefs(cat: PreferenceCategory, kind: String, title: String, setting: String,
                                    labels: Array<String>, wallpaperValue: Int) {
            val ctx = cat.context
            val cr = ctx.contentResolver
            val wallpaper = Preference(ctx)
            val effect = ListPreference(ctx)
            fun refresh(v: Int) {
                val on = v == wallpaperValue
                wallpaper.isVisible = on
                effect.isVisible = on
            }
            ListPreference(ctx).apply {
                key = "bg_$kind"
                this.title = title
                isPersistent = false
                isIconSpaceReserved = false
                entries = labels
                entryValues = Array(labels.size) { it.toString() }
                value = android.provider.Settings.Secure.getInt(cr, setting, 0).toString()
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setOnPreferenceChangeListener { _, v ->
                    val n = (v as String).toInt()
                    android.provider.Settings.Secure.putInt(cr, setting, n)
                    refresh(n)
                    if (n == wallpaperValue && !Backgrounds.hasImage(ctx, kind)) {
                        pickingFor = kind; pickImage.launch(arrayOf("image/*"))
                    }
                    true
                }
                cat.addPreference(this)
            }
            wallpaper.apply {
                key = "bg_wallpaper_$kind"
                this.title = "Wallpaper"
                summary = "Choose a picture (separate from the home and lock screen wallpaper)"
                isIconSpaceReserved = false
                setOnPreferenceClickListener { pickingFor = kind; pickImage.launch(arrayOf("image/*")); true }
                cat.addPreference(this)
            }
            effect.apply {
                key = "bg_effect_$kind"
                this.title = "Wallpaper effect"
                isPersistent = false
                isIconSpaceReserved = false
                entries = arrayOf("None", "Blur", "Dim", "Ambience (blurred and darkened, like Silica)")
                entryValues = arrayOf("0", "1", "2", "3")
                value = Backgrounds.effect(ctx, kind).toString()
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setOnPreferenceChangeListener { _, v ->
                    val n = (v as String).toInt()
                    Thread { android.provider.Settings.Secure.putInt(cr, "etesian_${kind}_bg_effect", n); Backgrounds.process(ctx, kind) }.start()
                    true
                }
                cat.addPreference(this)
            }
            refresh(android.provider.Settings.Secure.getInt(cr, setting, 0))
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val ctx = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(ctx)
            preferenceScreen = screen
            val section = arguments?.getString(EXTRA_SECTION)
            if (section == null) {
                // N9 quick rows without icons on top: pinned settings, Flight mode, Internet, Mobile network, Brightness.
                pins(ctx).forEach { (sec, title) ->
                    screen.addPreference(QuickRow(ctx, star = true).apply {
                        this.title = title
                        intent = Intent(ctx, SettingsActivity::class.java).putExtra(EXTRA_SECTION, sec).putExtra(EXTRA_FOCUS, title)
                        onLong = {
                            android.app.AlertDialog.Builder(ctx).setItems(arrayOf("Unpin")) { _, _ ->
                                setPins(ctx, pins(ctx) - (sec to title)); screen.removePreference(this)
                            }.show()
                        }
                    })
                }
                screen.addPreference(object : SwitchPreferenceCompat(ctx) {
                    override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
                        super.onBindViewHolder(holder)
                        holder.findViewById(R.id.quick_chevron)?.visibility = android.view.View.GONE
                    }
                    override fun onClick() {
                        val on = !BelleSwitch1Widget.flightOn(ctx)
                        runCatching { ctx.getSystemService(android.net.ConnectivityManager::class.java).setAirplaneMode(on) }
                        isChecked = on
                    }
                }.apply {
                    key = "quick_flight"; title = "Flight mode"; isPersistent = false
                    layoutResource = R.layout.pref_quick_row
                    widgetLayoutResource = R.layout.pref_n9_switch_widget
                    isChecked = BelleSwitch1Widget.flightOn(ctx)
                })
                screen.addPreference(QuickRow(ctx).apply {
                    key = "quick_internet"; title = "Internet connection"
                    intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                })
                // Sharing → Share network (mock: Share network on the connected network; scanning is the camera's).
                if (Extras.on(ctx, Extras.WIFI_QR)) {
                    screen.addPreference(QuickRow(ctx).apply {
                        key = "quick_wifi_share"; title = "Share network"
                        setOnPreferenceClickListener { WifiQr.share(ctx); true }
                    })
                }
                screen.addPreference(QuickRow(ctx).apply {
                    key = "quick_mobile"; title = "Mobile network"
                    intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                })
                screen.addPreference(object : Preference(ctx) {
                    override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
                        super.onBindViewHolder(holder)
                        holder.itemView.isClickable = false
                        val cr = ctx.contentResolver
                        (holder.findViewById(R.id.quick_seek) as android.widget.SeekBar).apply {
                            progress = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
                            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(b: android.widget.SeekBar, v: Int, user: Boolean) {
                                    if (user) runCatching { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v.coerceAtLeast(1)) }
                                }
                                override fun onStartTrackingTouch(b: android.widget.SeekBar) {}
                                override fun onStopTrackingTouch(b: android.widget.SeekBar) {}
                            })
                        }
                    }
                }.apply { key = "quick_brightness"; layoutResource = R.layout.pref_quick_brightness; isSelectable = false })
                // Then one row per sub page, N9 style (icon, title, what's inside).
                SECTIONS.filter { it.main && (it.key !in setOf("gestures", "lock", "toolbar") || SystemTasks.available(ctx)) }.forEach { sec ->
                    screen.addPreference(Preference(ctx).apply {
                        key = "section_${sec.key}"
                        title = sec.title; summary = sec.summary
                        setIcon(sec.icon)
                        intent = Intent(ctx, SettingsActivity::class.java).putExtra(EXTRA_SECTION, sec.key)
                    })
                }
                return
            }

            /** Which sub page each category belongs to; the others are built but not shown. */
            val owner = mapOf(
                "Home screen views" to "home", "Appearance" to "apps", "App grid" to "apps", "Search" to "apps",
                "Backgrounds" to "wallpapers", "Home wallpaper" to "wallpapers", "Events view" to "events",
                "Gestures" to "gestures", "Edge bar" to "gestures", "Notifications on the lock screen" to "lock",
                "Bottom toolbar" to "toolbar", "System" to "system",
                "Notifications" to "events", "Today" to "events", "Over apps" to "events",
                "Status menu" to "status", "Arranging" to "open", "Carousel" to "open", "Split screen" to "open",
                "Private space" to "apps", "Under the clock" to "lock", "Emergency information" to "emergency",
                "Medical details" to "emergency", "Emergency contacts" to "emergency", "Share sheet" to "sharing",
                "Networks" to "sharing", "Priority people" to "priority", "Dismissed notifications" to "dismissed")
            if (section in PREVIEWS) screen.addPreference(SettingsPreview.Row(ctx, section!!))
            val hidden = preferenceManager.createPreferenceScreen(ctx)
            fun category(title: String) = PreferenceCategory(ctx).apply {
                this.title = title
                isIconSpaceReserved = false
                (if (owner[title] == section) screen else hidden).addPreference(this)
            }
            fun switch(cat: PreferenceCategory, key: String, title: String, summary: String, default: Boolean) =
                SwitchPreferenceCompat(ctx).apply {
                    this.key = key; this.title = title; this.summary = summary
                    setDefaultValue(default)
                    isIconSpaceReserved = false
                    cat.addPreference(this)
                }
            fun link(cat: PreferenceCategory, title: String, summary: String, intent: Intent) =
                Preference(ctx).apply {
                    this.title = title; this.summary = summary
                    this.intent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    isIconSpaceReserved = false
                    cat.addPreference(this)
                }

            val home = category("Home screen views")
            switch(home, Prefs.PAGE_EVENTS, "Events", "Notifications feed, left of Applications", true)
            switch(home, Prefs.PAGE_WIDGETS, "Widgets", "Widgets, left of Events", false)
            // N9: Open applications can keep your order (drag cards in edit mode) or arrange themselves.

            val look = category("Appearance")
            ListPreference(ctx).apply {
                key = Prefs.ICON_SHAPE
                title = "Icon shape"
                entries = arrayOf("N9 (squircle)", "System shape (Settings → Styles)", "Superellipse", "Rounded square")
                entryValues = arrayOf(IconFactory.SHAPE_N9, IconFactory.SHAPE_SYSTEM, IconFactory.SHAPE_SUPERELLIPSE, IconFactory.SHAPE_ROUNDED)
                setDefaultValue(IconFactory.SHAPE_N9)
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                isIconSpaceReserved = false
                look.addPreference(this)
            }
            val packs = IconPacks.installed(ctx)
            ListPreference(ctx).apply {
                key = Prefs.ICON_PACK
                title = "Icon pack"
                entries = arrayOf("None (default)") + packs.map { it.second }
                entryValues = arrayOf("") + packs.map { it.first }
                setDefaultValue("")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                isIconSpaceReserved = false
                look.addPreference(this)
            }

            fun list(cat: PreferenceCategory, key: String, title: String, labels: Array<String>,
                     values: Array<String>, default: String) =
                ListPreference(ctx).apply {
                    this.key = key; this.title = title
                    entries = labels; entryValues = values
                    setDefaultValue(default)
                    summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    isIconSpaceReserved = false
                    cat.addPreference(this)
                }
            // A fixed screen never scrolls, so lists inside widgets (mail, feeds) get the vertical drags.
            val widgetsLayout = list(home, Prefs.WIDGETS_LAYOUT, "Widgets layout",
                arrayOf("One scrolling list", "Fixed screens (no scrolling)"),
                arrayOf(Prefs.WIDGETS_LIST, Prefs.WIDGETS_FIXED), Prefs.WIDGETS_LIST)
            val widgetScreens = list(home, Prefs.WIDGETS_SCREENS, "Widget screens", arrayOf("1", "2", "3", "4", "5"),
                arrayOf("1", "2", "3", "4", "5"), "1")
            widgetScreens.isEnabled = Prefs.widgetsFixed(ctx)
            widgetsLayout.setOnPreferenceChangeListener { _, v -> widgetScreens.isEnabled = v == Prefs.WIDGETS_FIXED; true }
            list(home, Prefs.PAGE_DOTS, "View indicator dots", arrayOf("Hidden", "Top", "Bottom"),
                arrayOf(Prefs.DOTS_HIDDEN, Prefs.DOTS_TOP, Prefs.DOTS_BOTTOM), Prefs.DOTS_HIDDEN)
            val gridCat = category("App grid")
            list(gridCat, Prefs.GRID_COLUMNS, "Columns", arrayOf("3", "4", "5", "6"), arrayOf("3", "4", "5", "6"), "4")
            list(gridCat, Prefs.GRID_ROWS, "Rows per screen",
                arrayOf("Auto", "5", "6", "7", "8", "9", "10"), arrayOf("0", "5", "6", "7", "8", "9", "10"), "0")
            list(gridCat, Prefs.ICON_SIZE, "Icon size", arrayOf("Small", "Medium", "Large"), arrayOf("48", "58", "70"), "58")

            val backgrounds = category("Backgrounds")
            backgroundPrefs(backgrounds, Backgrounds.LIGHT, "Apps (light on the N9)", Backgrounds.SETTING_LIGHT,
                arrayOf("N9 light", "True black", "Dark grey (Google dark theme)", "Wallpaper"), 3)
            backgroundPrefs(backgrounds, Backgrounds.DARK, "Black screens (home, menus)", Backgrounds.SETTING_DARK,
                arrayOf("Black", "Wallpaper"), 1)

            val homeWall = category("Home wallpaper")
            val scrollCat = PreferenceCategory(ctx).apply { title = "Scrolling wallpaper"; isIconSpaceReserved = false }
            val pageCat = PreferenceCategory(ctx).apply { title = "Wallpaper per view"; isIconSpaceReserved = false }
            fun showMode(m: Int) { scrollCat.isVisible = m == Backgrounds.HOME_SCROLL; pageCat.isVisible = m == Backgrounds.HOME_PER_PAGE }
            ListPreference(ctx).apply {
                key = "home_wallpaper_mode"
                title = "Home screen"
                isPersistent = false
                isIconSpaceReserved = false
                entries = arrayOf("Same as black screens", "One wallpaper, scrolling with the views", "A wallpaper per view")
                entryValues = arrayOf("0", "1", "2")
                value = Backgrounds.homeMode(ctx).toString()
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setOnPreferenceChangeListener { _, v ->
                    val m = (v as String).toInt()
                    Settings.Secure.putInt(ctx.contentResolver, Backgrounds.SETTING_HOME_MODE, m)
                    showMode(m)
                    if (m == Backgrounds.HOME_SCROLL && !Backgrounds.hasImage(ctx, Backgrounds.HOME_SCROLL_KIND)) {
                        pickingFor = Backgrounds.HOME_SCROLL_KIND; pickImage.launch(arrayOf("image/*"))
                    } else if (m == Backgrounds.HOME_SCROLL) {
                        // The picture is there already: show it (the switch below may have been left off).
                        Settings.Secure.putInt(ctx.contentResolver, Backgrounds.setting(Backgrounds.HOME_SCROLL_KIND), 1)
                        findPreference<ListPreference>("bg_${Backgrounds.HOME_SCROLL_KIND}")?.value = "1"
                    }
                    true
                }
                homeWall.addPreference(this)
            }
            (if (section == "wallpapers") screen else hidden).apply { addPreference(scrollCat); addPreference(pageCat) }
            backgroundPrefs(scrollCat, Backgrounds.HOME_SCROLL_KIND, "Wallpaper", Backgrounds.setting(Backgrounds.HOME_SCROLL_KIND),
                arrayOf("Black", "Wallpaper"), 1)
            Backgrounds.PAGES.forEach { (kind, label) ->
                backgroundPrefs(pageCat, kind, label, Backgrounds.setting(kind), arrayOf("No wallpaper", "Wallpaper"), 1)
            }
            showMode(Backgrounds.homeMode(ctx))

            val events = category("Events view")
            link(events, "Feeds", "RSS and Atom feeds shown under Feed (the Feeds app)",
                Feeds.openApp())
            switch(events, Prefs.WEATHER, "Weather", "Current weather next to the date (Open-Meteo, uses your approximate location)", true)
            androidx.preference.EditTextPreference(ctx).apply {
                key = Prefs.WEATHER_CITY
                title = "Weather location"
                dialogTitle = "City (leave empty to use your location)"
                setSummaryProvider { p -> (p as androidx.preference.EditTextPreference).text?.ifBlank { null } ?: "Your location" }
                isIconSpaceReserved = false
                events.addPreference(this)
                if (section == "events") dependency = Prefs.WEATHER
            }
            list(events, Prefs.WEATHER_UNITS, "Temperature", arrayOf("Celsius", "Fahrenheit"), arrayOf("c", "f"), "c")
                .also { if (section == "events") it.dependency = Prefs.WEATHER }
            val styles = EventsClassifier.Style.entries
            EventsClassifier.Section.entries.filter { it != EventsClassifier.Section.CALENDAR }.forEach { section ->
                list(events, Prefs.eventsStyleKey(section), "${section.title} layout", styles.map { it.title }.toTypedArray(),
                    styles.map { it.key }.toTypedArray(), EventsClassifier.Style.GROUPED.key)
            }
            Preference(ctx).apply {
                title = "Sorting notifications"
                summary = "Long-press in the Events view to start tidying: swipe entries away to dismiss them, or tap one to show that app (or only that kind of its notifications) under Notifications, Calendar, Feed or Low priority. Tap the date to finish."
                isSelectable = false
                isIconSpaceReserved = false
                events.addPreference(this)
            }

            val search = category("Search")
            switch(search, Prefs.SEARCH_ENABLED, "Pull down to search",
                "Pull the Applications view down from the top to show a search bar", false)
            ListPreference(ctx).apply {
                key = Prefs.SEARCH_POSITION
                title = "Search bar position"
                entries = arrayOf("Top", "Bottom")
                entryValues = arrayOf("top", "bottom")
                setDefaultValue("top")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                isIconSpaceReserved = false
                search.addPreference(this)
                if (section == "apps") dependency = Prefs.SEARCH_ENABLED
            }
            switch(search, Prefs.SEARCH_KEYBOARD, "Open keyboard automatically",
                "Otherwise tap the search bar to start typing", true).also { if (section == "apps") it.dependency = Prefs.SEARCH_ENABLED }

            // Built into the ROM, Etesian owns the system gestures.
            if (SystemTasks.available(ctx)) {
                val gestures = category("Gestures")
                list(gestures, Prefs.GESTURES, "Gesture system", arrayOf("N9 swipe", "Android"),
                    arrayOf(Prefs.GESTURES_N9, Prefs.GESTURES_ANDROID), Prefs.GESTURES_N9).apply {
                    setOnPreferenceChangeListener { _, value ->
                        Settings.Secure.putInt(ctx.contentResolver, "etesian_gestures",
                            if (value == Prefs.GESTURES_N9) 1 else 0)
                        true
                    }
                }
                list(gestures, Prefs.CLOSE_DISTANCE, "Swipe distance to leave an app",
                    arrayOf("50% of the screen (N9)", "40%", "30%", "20%"),
                    arrayOf("50", "40", "30", "20"), "50")
                list(gestures, Prefs.BACK_GESTURE, "Back",
                    arrayOf("Swipe from the left side", "Swipe from the right side",
                        "Swipe in, then down", "Edge bar (Side Sense style)"),
                    arrayOf(Prefs.BACK_LEFT, Prefs.BACK_RIGHT, Prefs.BACK_CURVED, Prefs.BACK_SIDEBAR),
                    Prefs.BACK_CURVED)
                val barActions = arrayOf("Back", "Minimise", "Close", "Open applications", "Nothing")
                val barValues = arrayOf(Prefs.ACTION_BACK, Prefs.ACTION_MINIMISE, Prefs.ACTION_CLOSE,
                    Prefs.ACTION_SWITCHER, Prefs.ACTION_NONE)
                val bar = category("Edge bar")
                list(bar, Prefs.SIDEBAR_SIDE, "Side", arrayOf("Left", "Right"), arrayOf("left", "right"), "right")
                list(bar, Prefs.SIDEBAR_POSITION, "Height", arrayOf("Upper", "Middle", "Lower"),
                    arrayOf("30", "45", "65"), "45")
                list(bar, Prefs.SIDEBAR_DOUBLE_TAP, "Double tap", barActions, barValues, Prefs.ACTION_BACK)
                list(bar, Prefs.SIDEBAR_FLICK_IN, "Flick inwards", barActions, barValues, Prefs.ACTION_BACK)
                list(bar, Prefs.SIDEBAR_FLICK_UP, "Flick up", barActions, barValues, Prefs.ACTION_SWITCHER)
                list(bar, Prefs.SIDEBAR_FLICK_DOWN, "Flick down", barActions, barValues, Prefs.ACTION_NONE)
                list(gestures, Prefs.BOTTOM_ACTION, "Swipe up from the bottom",
                    arrayOf("Minimise", "Back", "Close", "Nothing"),
                    arrayOf(Prefs.ACTION_MINIMISE, Prefs.ACTION_BACK, Prefs.ACTION_CLOSE, Prefs.ACTION_NONE),
                    Prefs.ACTION_MINIMISE)

                val lock = category("Notifications on the lock screen")
                fun secureList(key: String, title: String, labels: Array<String>, values: Array<String>,
                               current: String) = ListPreference(ctx).apply {
                    // Without a key the choice dialog can't open (IllegalArgumentException).
                    this.key = key
                    this.title = title
                    isPersistent = false
                    entries = labels; entryValues = values
                    value = current
                    summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    isIconSpaceReserved = false
                    setOnPreferenceChangeListener { _, v ->
                        android.provider.Settings.Secure.putString(ctx.contentResolver, key, v as String)
                        true
                    }
                    lock.addPreference(this)
                }
                val cr = ctx.contentResolver
                secureList(Prefs.SECURE_LOCK_STYLE, "More notifications",
                    arrayOf("Stacked cards, slide up to see all", "\"More\" label (N9)"),
                    arrayOf("stack", "more"),
                    android.provider.Settings.Secure.getString(cr, Prefs.SECURE_LOCK_STYLE) ?: "stack")
                secureList(Prefs.SECURE_LOCK_MAX, "Notifications shown in full",
                    arrayOf("1", "2", "3 (N9)", "4", "5"), arrayOf("1", "2", "3", "4", "5"),
                    android.provider.Settings.Secure.getInt(cr, Prefs.SECURE_LOCK_MAX, 3).toString())

                val toolbar = category("Bottom toolbar")
                SwitchPreferenceCompat(ctx).apply {
                    title = "N9 toolbar"
                    summary = "The N9 bar under every app: Back, your shortcuts, or the app's own buttons"
                    isPersistent = false
                    isChecked = Prefs.bottomBar(ctx)
                    isIconSpaceReserved = false
                    setOnPreferenceChangeListener { _, value ->
                        Prefs.setBottomBar(ctx, value as Boolean)
                        true
                    }
                    toolbar.addPreference(this)
                }
                ListPreference(ctx).apply {
                    key = "secure_etesian_toolbar_mode"
                    title = "Toolbar"
                    isPersistent = false
                    isIconSpaceReserved = false
                    entries = arrayOf("Bar at the bottom", "Side strip (tap the edge handle)")
                    entryValues = arrayOf("0", "1")
                    value = android.provider.Settings.Secure.getInt(ctx.contentResolver, "etesian_toolbar_mode", 0).toString()
                    summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    setOnPreferenceChangeListener { _, v ->
                        android.provider.Settings.Secure.putInt(ctx.contentResolver, "etesian_toolbar_mode", (v as String).toInt())
                        true
                    }
                    toolbar.addPreference(this)
                }
                ListPreference(ctx).apply {
                    key = "toolbar_back_side"
                    title = "Back button (and side strip)"
                    isPersistent = false
                    isIconSpaceReserved = false
                    entries = arrayOf("Left", "Right")
                    entryValues = arrayOf("0", "1")
                    value = Prefs.toolbarBackSide(ctx).toString()
                    summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    setOnPreferenceChangeListener { _, v ->
                        Prefs.setToolbarBackSide(ctx, (v as String).toInt())
                        true
                    }
                    toolbar.addPreference(this)
                }
                ListPreference(ctx).apply {
                    key = "toolbar_style"
                    title = "Toolbar theme"
                    isPersistent = false
                    isIconSpaceReserved = false
                    entries = arrayOf("N9 light", "Symbian Belle dark")
                    entryValues = arrayOf("0", "1")
                    value = Prefs.toolbarStyle(ctx).toString()
                    summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    setOnPreferenceChangeListener { _, v ->
                        Prefs.setToolbarStyle(ctx, (v as String).toInt())
                        true
                    }
                    toolbar.addPreference(this)
                }
                link(toolbar, "Toolbar buttons", "Your shortcuts and macros, for all apps or per app",
                    Intent(ctx, ToolbarSettingsActivity::class.java))
            }

            // Additions the N9 did not have: ordinary settings on the page of what they change, each off
            // by default (Settings.Secure etesian_extra_*, see Extras), so a fresh phone is the pure N9.
            fun extra(cat: PreferenceCategory, name: String, title: String, summary: String) = SwitchPreferenceCompat(ctx).apply {
                key = "secure_" + Extras.key(name)
                this.title = title; this.summary = summary; isSingleLineTitle = false
                isPersistent = false; isIconSpaceReserved = false
                layoutResource = R.layout.pref_n9_switch_row
                widgetLayoutResource = R.layout.pref_n9_switch_widget
                isChecked = Extras.on(ctx, name)
                setOnPreferenceChangeListener { _, v ->
                    Extras.set(ctx, name, v as Boolean)
                    if (v && name == Extras.LOCK_WEATHER) Weather.refresh(ctx) {}
                    if (v && name == Extras.EMERGENCY &&
                        ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        activity?.requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), 7)
                    true
                }
                cat.addPreference(this)
            }
            fun page(cat: PreferenceCategory, key: String, summary: String) = Preference(ctx).apply {
                this.key = "page_$key"; title = SECTIONS.first { it.key == key }.title; this.summary = summary
                isIconSpaceReserved = false; isPersistent = false
                intent = Intent(ctx, SettingsActivity::class.java).putExtra(EXTRA_SECTION, key)
                cat.addPreference(this)
            }
            fun secureList(cat: PreferenceCategory, k: String, t: String, names: Array<String>, values: Array<String>, def: String) = ListPreference(ctx).apply {
                key = "secure_$k"; title = t; isIconSpaceReserved = false; isPersistent = false
                entries = names; entryValues = values
                value = Settings.Secure.getString(ctx.contentResolver, k)?.takeIf { it in values } ?: def
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setOnPreferenceChangeListener { _, v -> Settings.Secure.putString(ctx.contentResolver, k, v as String); true }
                cat.addPreference(this)
            }

            // Events and notifications
            val notes = category("Notifications")
            extra(notes, Extras.ACTIONS, "Actions and snooze", "Long-press a notification: Mark as read, Snooze, Dismiss")
            extra(notes, Extras.INLINE_REPLY, "Inline reply", "Reply to a message from Events")
            extra(notes, Extras.LIVE_UPDATES, "Live updates", "Ongoing progress pinned under Notifications")
            page(notes, "priority", "A person's messages, mail and calls first, grouped under them")
            page(notes, "dismissed", "Keep dismissed notifications as faded rows")
            val today = category("Today")
            extra(today, Extras.TODAY, "Today in Events", "One-line items above Notifications")
            androidx.preference.MultiSelectListPreference(ctx).apply {
                key = "secure_" + Today.KEY_ITEMS; title = "Items"
                isIconSpaceReserved = false; isPersistent = false
                entries = Today.ALL.map { it.second }.toTypedArray(); entryValues = Today.ALL.map { it.first }.toTypedArray()
                values = Today.shown(ctx)
                fun sum(v: Set<String>) = Today.ALL.filter { it.first in v }.joinToString(", ") { it.second }.ifEmpty { "None" }
                summary = sum(values)
                setOnPreferenceChangeListener { p, v ->
                    @Suppress("UNCHECKED_CAST") val set = v as Set<String>
                    Settings.Secure.putString(ctx.contentResolver, Today.KEY_ITEMS, Today.ALL.map { it.first }.filter { it in set }.joinToString(","))
                    p.summary = sum(set); true
                }
                today.addPreference(this)
            }
            val over = category("Over apps")
            extra(over, Extras.BUBBLES, "Notification bubbles", "A card under the status bar instead of the Android pop-up")
            extra(over, Extras.COPIED, "“Copied” bubble", "A short bubble when something is copied, instead of the clipboard overlay")

            // Events and notifications → Priority people
            val prio = category("Priority people")
            if (section == "priority") prio.title = null
            extra(prio, Extras.PRIORITY, "Priority people", "Their notifications from any app come first, grouped under them")
            Priority.ids(ctx).forEach { id ->
                val name = Priority.nameOf(ctx, id) ?: "Contact $id"
                prio.addPreference(Preference(ctx).apply {
                    key = "priority_$id"; title = "★ $name"; summary = "Tap to remove"
                    isIconSpaceReserved = false; isPersistent = false
                    setOnPreferenceClickListener { Priority.set(ctx, id, false); prio.removePreference(this); true }
                })
            }
            prio.addPreference(Preference(ctx).apply {
                key = "priority_add"; title = "Add a person"; summary = "Or long-press their notification in Events"
                isIconSpaceReserved = false; isPersistent = false
                setOnPreferenceClickListener {
                    pickContact.launch(null); true
                }
            })

            // Events and notifications → Dismissed notifications (ghost rows)
            val ghosts = category("Dismissed notifications")
            if (section == "dismissed") ghosts.title = null
            extra(ghosts, Extras.GHOST_ROWS, "Keep dismissed notifications", "Faded rows at the end of Notifications; tap them for Earlier")
            secureList(ghosts, Ghosts.KEY_SIZE, "Size", arrayOf("Small", "Big"), arrayOf("small", "big"), "small")
            secureList(ghosts, Ghosts.KEY_ROWS, "Rows shown",
                arrayOf("0.5 (Big only)", "1", "1.5", "2", "2.5", "3"), arrayOf("0.5", "1", "1.5", "2", "2.5", "3"), "2.5")
            secureList(ghosts, Ghosts.KEY_KEEP, "Keep for",
                arrayOf("1 hour", "4 hours", "12 hours", "24 hours", "Forever"), arrayOf("1", "4", "12", "24", "0"), "24")

            // Status menu (the options apply while your own layout is on; pure N9: all rows).
            val status = category("Status menu")
            if (section == "status") status.title = null
            extra(status, Extras.STATUS_MENU, "Your own layout", "A toggle strip, sliders and fewer rows; edit with the pencil")
            ListPreference(ctx).apply {
                key = StatusLayout.MAX_ROWS; title = "Visible rows"; isIconSpaceReserved = false
                entries = arrayOf("3", "4", "5", "All"); entryValues = arrayOf("3", "4", "5", "0"); setDefaultValue("0")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                status.addPreference(this)
            }
            ListPreference(ctx).apply {
                key = StatusLayout.REVEAL; title = "Reveal hidden rows"; isIconSpaceReserved = false
                entries = arrayOf("Button", "Swipe down", "Both"); entryValues = arrayOf("button", "swipe", "both"); setDefaultValue("both")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                status.addPreference(this)
            }
            extra(status, Extras.MUSIC, "Music", "The small music player at the top")
            extra(status, Extras.MODES, "Do Not Disturb and Modes", "A Mode strip under Profile")
            extra(status, Extras.PRIVACY, "Privacy indicators", "Camera and microphone in use, in the status bar")

            // Open applications
            val arranging = category("Arranging")
            switch(arranging, RunningApps.AUTO_ARRANGE, "Auto-arrange", "The last opened application moves to the top", true)
            val carousel = category("Carousel")
            extra(carousel, Extras.CARD_ACTIONS, "Actions under the card", "Split view, Pop-up, Screenshot and App info")
            extra(carousel, Extras.PINNING, "App pinning", "Pin under the card keeps one app on screen")
            val split = category("Split screen")
            extra(split, Extras.SPLIT_MERGE, "Two-finger merge", "In edit mode, pull two cards together with two fingers; pull a pair apart to separate")

            // Applications
            val priv = category("Private space")
            extra(priv, Extras.PRIVATE, "Private space", "Android's private space as a locked folder at the end of the grid")

            // Lock screen
            val under = category("Under the clock")
            extra(under, Extras.GLANCE, "Glance line", "Next alarm and event under the date")
            extra(under, Extras.LOCK_WEATHER, "Weather", "Current weather under the date (from the Events weather)")
            page(under, "emergency", "Owner and medical essentials beside the clock")

            // Emergency information: our own fields (Android's Emergency Information app keeps its data private).
            val emergency = category("Emergency information")
            if (section == "emergency") emergency.title = null
            extra(emergency, Extras.EMERGENCY, "Show on the lock screen", "Owner and essentials beside the clock; tap for the full card")
            androidx.preference.MultiSelectListPreference(ctx).apply {
                key = "secure_etesian_emergency_show"; title = "Shown beside the clock"
                isIconSpaceReserved = false; isPersistent = false
                entries = arrayOf("Name", "Blood type", "Allergies", "Medication", "Medical notes", "Organ donor")
                entryValues = arrayOf("name", "blood", "allergies", "medication", "notes", "donor")
                values = (Settings.Secure.getString(ctx.contentResolver, "etesian_emergency_show") ?: "name,blood,allergies").split(',').toSet()
                fun sum(v: Set<String>) = entryValues.indices.filter { entryValues[it].toString() in v }.joinToString(", ") { entries[it] }
                summary = sum(values)
                setOnPreferenceChangeListener { p, v ->
                    @Suppress("UNCHECKED_CAST") val set = v as Set<String>
                    val ordered = entryValues.map { it.toString() }.filter { it in set }
                    Settings.Secure.putString(ctx.contentResolver, "etesian_emergency_show", ordered.joinToString(","))
                    p.summary = sum(set); true
                }
                emergency.addPreference(this)
            }
            val medical = category("Medical details")
            fun secureText(cat: PreferenceCategory, k: String, t: String, hint: String) = androidx.preference.EditTextPreference(ctx).apply {
                key = "secure_etesian_emergency_$k"; title = t; isIconSpaceReserved = false; isPersistent = false
                text = Emergency.get(ctx, k); dialogMessage = hint
                summaryProvider = androidx.preference.Preference.SummaryProvider<androidx.preference.EditTextPreference> { p ->
                    p.text?.takeIf { it.isNotBlank() }?.lines()?.joinToString(", ") ?: "Not set" }
                setOnPreferenceChangeListener { _, v -> Emergency.put(ctx, k, (v as String).trim()); true }
                cat.addPreference(this)
            }
            secureText(medical, "name", "Name", "Your name, shown beside the clock")
            secureText(medical, "blood", "Blood type", "For example A+")
            secureText(medical, "allergies", "Allergies", "")
            secureText(medical, "medication", "Medication", "")
            secureText(medical, "notes", "Medical notes", "")
            secureText(medical, "donor", "Organ donor", "Yes or No")
            val econtacts = category("Emergency contacts")
            secureText(econtacts, "contacts", "Contacts", "One per line: Name | relation | number")

            // Sharing
            val sheet = category("Share sheet")
            extra(sheet, Extras.SHARE, "N9 share sheet", "With a filter and pinned apps, instead of Android's")
            extra(sheet, Extras.SHARE_PEOPLE, "Suggested people", "A row of people at the top of the sheet")
            val nets = category("Networks")
            extra(nets, Extras.WIFI_QR, "Share network", "On the main settings page: a QR code and the password of your Wi-Fi")

            val system = category("System")
            if (!SystemTasks.available(ctx)) {
                link(system, "Swipe gestures & live previews", "Enable \"Etesian swipe gestures\" in Accessibility",
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            link(system, "Notification access", "Lets the Events view show your notifications",
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            link(system, "Default home app", "Choose Etesian as your home screen",
                Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
}
