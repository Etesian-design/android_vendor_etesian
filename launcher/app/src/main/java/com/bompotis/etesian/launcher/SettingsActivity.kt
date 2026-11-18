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
            Section("gestures", "Gestures", "Swipes, back", R.drawable.ic_set_gestures),
            Section("sidesense", "Breeze", "The edge bar or floating button: taps, flicks, its menu", R.drawable.ic_set_gestures),
            Section("lock", "Lock screen", "Notifications, glance line, weather", R.drawable.ic_set_lock),
            Section("emergency", "Emergency information", "Medical details for first responders", R.drawable.ic_set_emergency),
            Section("sharing", "Sharing", "Share sheet, sharing a network", R.drawable.ic_set_share),
            Section("toolbar", "Toolbar", "The N9 bar under every app", R.drawable.ic_set_toolbar),
            Section("system", "System", "Permissions, default home app", R.drawable.ic_set_system),
            // Sub pages of a page: not listed on the main page.
            Section("priority", "Priority people", "", 0, main = false),
            Section("dismissed", "Dismissed notifications", "", 0, main = false),
            Section("feeds", "Feeds", "", 0, main = false),
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
                        N9Sheet.show(ctx, listOf(N9Sheet.Item(if (pinned) "Unpin" else "Pin to Settings") {
                            setPins(ctx, if (pinned) pins(ctx) - (section to title) else pins(ctx) + (section to title))
                            android.widget.Toast.makeText(ctx, if (pinned) "Unpinned" else "Pinned to Settings", android.widget.Toast.LENGTH_SHORT).show()
                        }), title = title)
                        true
                    }
                }
                override fun onChildViewDetachedFromWindow(v: android.view.View) {}
            })
        }

        private var shownPins: List<Pair<String, String>>? = null

        /** Every choice in Etesian settings opens the N9 selection sheet, not Android's radio-button dialog. */
        override fun onDisplayPreferenceDialog(preference: Preference) {
            val ctx = requireContext()
            when (preference) {
                is ListPreference -> {
                    val values = preference.entryValues?.map { it.toString() } ?: return
                    N9Sheet.choose(ctx, preference.title?.toString(), preference.entries.toList(),
                        values.indexOf(preference.value)) { i ->
                        if (preference.callChangeListener(values[i])) preference.value = values[i]
                    }
                }
                is androidx.preference.MultiSelectListPreference -> {
                    val values = preference.entryValues.map { it.toString() }
                    N9Sheet.chooseMany(ctx, preference.title?.toString(), preference.entries.toList(),
                        values.indices.filter { values[it] in preference.values }.toSet()) { picked ->
                        val set = picked.map { values[it] }.toSet()
                        if (preference.callChangeListener(set)) preference.values = set
                    }
                }
                else -> super.onDisplayPreferenceDialog(preference)
            }
        }

        /** Pins made on a sub page show up when coming back to the main page. */
        override fun onResume() {
            super.onResume()
            if (arguments?.getString(EXTRA_SECTION) == "sidesense") fillBreeze()
            if (arguments?.getString(EXTRA_SECTION) != null) return
            val now = pins(requireContext())
            if (shownPins != null && shownPins != now) requireActivity().recreate()
            shownPins = now
        }

        private var breezeShortcuts: PreferenceCategory? = null
        private var breezeWidgets: PreferenceCategory? = null

        /** Breeze's menu buttons and widgets, each a row: tap one to move it or remove it; the last row adds one. */
        private fun fillBreeze() {
            val ctx = context ?: return
            breezeShortcuts?.let { cat ->
                cat.removeAll()
                val buttons = ToolbarConfig.buttons(ctx, ToolbarConfig.BREEZE).toMutableList()
                buttons.forEachIndexed { i, b ->
                    cat.addPreference(Preference(ctx).apply {
                        key = "breeze_button_$i"; title = b.label; summary = ToolbarActions.describe(ctx, b.action)
                        isPersistent = false
                        icon = ToolbarConfig.decode(b.icon)?.let { android.graphics.drawable.BitmapDrawable(ctx.resources, it) }
                            ?.apply { if (b.tint) setTint(N9.TEXT) }
                        if (i >= platformMenuMax) summary = "Not shown: the menu holds $platformMenuMax"
                        setOnPreferenceClickListener {
                            reorderSheet(ctx, b.label, i, buttons.size) { to ->
                                val list = ToolbarConfig.buttons(ctx, ToolbarConfig.BREEZE).toMutableList()
                                if (to == null) list.removeAt(i) else list.add(to, list.removeAt(i))
                                ToolbarConfig.save(ctx, ToolbarConfig.BREEZE, list)
                                fillBreeze()
                            }
                            true
                        }
                    })
                }
                cat.addPreference(Preference(ctx).apply {
                    key = "breeze_add_button"; title = "Add and arrange buttons"; isPersistent = false
                    summary = if (buttons.isEmpty()) "No buttons yet: drag some onto Breeze"
                        else "Drag buttons onto Breeze, along it, or off it"
                    isIconSpaceReserved = buttons.isNotEmpty()
                    intent = Intent(ctx, BreezeButtonsActivity::class.java)
                })
            }
            breezeWidgets?.let { cat ->
                cat.removeAll()
                val awm = android.appwidget.AppWidgetManager.getInstance(ctx)
                val ids = Prefs.breezeWidgets(ctx)
                ids.forEachIndexed { i, id ->
                    val info = awm.getAppWidgetInfo(id)
                    val name = info?.loadLabel(ctx.packageManager) ?: "Widget no longer available"
                    cat.addPreference(Preference(ctx).apply {
                        key = "breeze_widget_$i"; title = name; isPersistent = false; isIconSpaceReserved = false
                        summary = info?.provider?.packageName?.let { pkg ->
                            runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
                        }
                        setOnPreferenceClickListener {
                            reorderSheet(ctx, name, i, ids.size) { to ->
                                val list = Prefs.breezeWidgets(ctx).toMutableList()
                                if (to == null) {
                                    list.removeAt(i)
                                    runCatching { BreezeWidgetActivity.host(ctx).deleteAppWidgetId(id) }
                                } else list.add(to, list.removeAt(i))
                                Prefs.setBreezeWidgets(ctx, list)
                                fillBreeze()
                            }
                            true
                        }
                    })
                }
                cat.addPreference(Preference(ctx).apply {
                    key = "breeze_add_widget"; title = "Add widget"; isPersistent = false; isIconSpaceReserved = false
                    summary = if (ids.isEmpty()) "Widgets show under the buttons, and scroll when there are many" else null
                    intent = Intent(ctx, BreezeWidgetActivity::class.java)
                })
            }
        }

        /** The N9 sheet for a row in a list: move it up or down, or remove it ([done] gets null). */
        private fun reorderSheet(ctx: android.content.Context, title: String, i: Int, n: Int, done: (Int?) -> Unit) {
            val items = mutableListOf<N9Sheet.Item>()
            if (i > 0) items += N9Sheet.Item("Move up") { done(i - 1) }
            if (i < n - 1) items += N9Sheet.Item("Move down") { done(i + 1) }
            items += N9Sheet.Item("Remove") { done(null) }
            N9Sheet.show(ctx, items, title = title, label = "Breeze item")
        }

        /** Breeze's menu shows this many buttons (BreezeMenu.MAX_BUTTONS, in the platform sources). */
        private val platformMenuMax = 5

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
                            N9Sheet.show(ctx, listOf(N9Sheet.Item("Unpin") {
                                setPins(ctx, pins(ctx) - (sec to title)); screen.removePreference(this)
                            }), title = title)
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
                "Gestures" to "gestures", "Leaving an app" to "gestures", "Breeze" to "sidesense", "Breeze gestures" to "sidesense",
                "Breeze shortcuts" to "sidesense", "Breeze widgets" to "sidesense", "Notifications on the lock screen" to "lock",
                "Bottom toolbar" to "toolbar", "System" to "system",
                "Notifications" to "events", "Today" to "events", "Over apps" to "events",
                "Status menu" to "status", "Arranging" to "open", "Carousel" to "open", "Split screen" to "open",
                "Private space" to "apps", "Under the clock" to "lock", "Emergency information" to "emergency",
                "Medical details" to "emergency", "Emergency contacts" to "emergency", "Share sheet" to "sharing",
                "Networks" to "sharing", "Priority people" to "priority", "Dismissed notifications" to "dismissed",
                "Feeds" to "feeds", "Notifications shown as feeds" to "feeds")
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
            EventsClassifier.Section.shown.filter { it != EventsClassifier.Section.CALENDAR }.forEach { section ->
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
                list(gestures, Prefs.BACK_GESTURE, "Back by swiping in",
                    arrayOf("Disabled", "Swipe from the left side", "Swipe from the right side"),
                    arrayOf(Prefs.BACK_DISABLED, Prefs.BACK_LEFT, Prefs.BACK_RIGHT),
                    Prefs.backGesture(ctx))   // also migrates a removed choice
                // Where an application swiped away goes, per edge (Back by swiping in takes over its side).
                val leave = category("Leaving an app")
                leave.title = "After swiping an app away"
                val leaveNames = mutableListOf("Open applications (N9)", "Where you were", "Applications")
                val leaveValues = mutableListOf(Prefs.LEAVE_SWITCHER, Prefs.LEAVE_LAST, Prefs.LEAVE_APPS)
                if (Prefs.eventsPage(ctx)) { leaveNames += "Events"; leaveValues += Prefs.LEAVE_EVENTS }
                if (Prefs.widgetsPage(ctx)) { leaveNames += "Widgets"; leaveValues += Prefs.LEAVE_WIDGETS }
                for ((edge, t) in listOf("left" to "From the left side", "right" to "From the right side", "bottom" to "From the bottom")) {
                    list(leave, Prefs.LEAVE_TO + edge, t, leaveNames.toTypedArray(), leaveValues.toTypedArray(), Prefs.LEAVE_SWITCHER)
                }
                val barActions = arrayOf("Back", "Minimise", "Close", "Open applications", "Breeze menu", "Nothing")
                val barValues = arrayOf(Prefs.ACTION_BACK, Prefs.ACTION_MINIMISE, Prefs.ACTION_CLOSE,
                    Prefs.ACTION_SWITCHER, Prefs.ACTION_MENU, Prefs.ACTION_NONE)
                val bar = category("Breeze")
                if (section == "sidesense") bar.title = null
                switch(bar, Prefs.SIDE_SENSE, "Breeze",
                    "A handle on the screen edge, or a floating button. Long-press it and drag it to move it",
                    Prefs.breeze(ctx))
                list(bar, Prefs.SIDEBAR_SIDE, "Place", arrayOf("Right edge", "Left edge", "Floating button"),
                    arrayOf("right", "left", Prefs.SIDE_FREE), "right")
                list(bar, Prefs.BREEZE_THEME, "Theme", arrayOf("Same as the toolbar", "Light", "Dark"),
                    arrayOf("toolbar", "light", "dark"), "toolbar")
                val barGestures = category("Breeze gestures")
                barGestures.title = "Gestures"
                list(barGestures, Prefs.SIDEBAR_TAP, "Tap", barActions, barValues, Prefs.ACTION_MENU)
                list(barGestures, Prefs.SIDEBAR_DOUBLE_TAP, "Double tap", barActions, barValues, Prefs.ACTION_BACK)
                list(barGestures, Prefs.SIDEBAR_FLICK_IN, "Flick inwards", barActions, barValues, Prefs.ACTION_BACK)
                list(barGestures, Prefs.SIDEBAR_FLICK_UP, "Flick up", barActions, barValues, Prefs.ACTION_SWITCHER)
                list(barGestures, Prefs.SIDEBAR_FLICK_DOWN, "Flick down", barActions, barValues, Prefs.ACTION_NONE)
                breezeShortcuts = category("Breeze shortcuts").apply { title = "Menu buttons" }
                breezeWidgets = category("Breeze widgets").apply { title = "Menu widgets" }
                if (section == "sidesense") fillBreeze()

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
                    key = "toolbar_back_side"
                    title = "Back button"
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
            page(notes, "feeds", "How often feeds update; notifications shown as feeds")
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
            // Over apps: how a notification shows while an app is open (SystemUI EtesianOverStyles); the
            // lock screen keeps its cards whatever is chosen. Discreet, the N9's one line, is the default.
            val over = category("Over apps")
            val style = secureList(over, NotifOver.STYLE, "Notifications over apps", NotifOver.STYLE_NAMES, NotifOver.STYLES, "n9")
            val shade = secureList(over, NotifOver.SHADE, "Strip transparency", NotifOver.SHADE_NAMES, NotifOver.SHADES, "medium")
            val colour = secureList(over, NotifOver.COLOUR, "Bubble colour", NotifOver.COLOUR_NAMES, NotifOver.COLOURS, "app")
            fun overOptions(v: String) { shade.isVisible = v == "belle"; colour.isVisible = v == "colour" }
            overOptions(NotifOver.style(ctx))
            style.setOnPreferenceChangeListener { _, v ->
                Settings.Secure.putString(ctx.contentResolver, NotifOver.STYLE, v as String); overOptions(v); true
            }
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

            // Events and notifications → Feeds: the Feeds app's update interval, and the apps filed under Feed.
            val feeds = category("Feeds")
            if (section == "feeds") feeds.title = null
            val every = if (section == "feeds") Feeds.interval(ctx) else -1
            if (every >= 0) ListPreference(ctx).apply {
                key = "feeds_interval"; title = "Update feeds"; isIconSpaceReserved = false; isPersistent = false
                entries = arrayOf("Every 15 minutes", "Every 30 minutes", "Every hour", "Every 3 hours", "Every 6 hours",
                    "Every 12 hours", "Once a day", "Only when opened")
                entryValues = arrayOf("15", "30", "60", "180", "360", "720", "1440", "0")
                value = every.toString().takeIf { it in entryValues } ?: "60"
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setOnPreferenceChangeListener { _, v -> Feeds.setInterval(ctx, (v as String).toInt()); true }
                feeds.addPreference(this)
            }
            val asFeeds = category("Notifications shown as feeds")
            if (section == "feeds") {
                val sections = setOf(EventsClassifier.Section.FEED, EventsClassifier.Section.HIDDEN)
                // The user's choices, and apps Etesian files under Feed by itself (from the notifications there now).
                val keys = LinkedHashMap<String, EventsClassifier.Section>()
                EventsClassifier.filed(ctx, sections).forEach { keys[it.key] = it.section }
                EventsStore.items.forEach { e ->
                    val sec = EventsClassifier.classify(ctx, e)
                    if (sec in sections) keys.putIfAbsent(EventsClassifier.keyOf(ctx, e), sec)
                }
                fun label(key: String): String {
                    val pkg = key.substringBefore('|')
                    val app = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }
                        .getOrDefault(pkg)
                    val channel = key.substringAfter('|', "")
                    val name = EventsStore.items.firstOrNull { it.pkg == pkg && it.channelId == channel }?.channelName ?: channel
                    return if (channel.isEmpty()) app else "$app: $name"
                }
                fun summaryOf(sec: EventsClassifier.Section?) = when (sec) {
                    EventsClassifier.Section.FEED -> "Shown as feeds"
                    EventsClassifier.Section.HIDDEN -> "Hidden from Events"
                    else -> "Shown as notifications"
                }
                if (keys.isEmpty()) asFeeds.addPreference(Preference(ctx).apply {
                    title = "None"; summary = "Apps you show under Feed are listed here"; isSelectable = false; isIconSpaceReserved = false
                })
                keys.forEach { (k, sec) ->
                    asFeeds.addPreference(Preference(ctx).apply {
                        title = label(k); summary = summaryOf(sec); isIconSpaceReserved = false; isPersistent = false
                        setOnPreferenceClickListener { p ->
                            fun to(s: EventsClassifier.Section) = N9Sheet.Item(summaryOf(s)) {
                                EventsClassifier.set(ctx, k, s); p.summary = summaryOf(s)
                            }
                            N9Sheet.show(ctx, listOf(to(EventsClassifier.Section.NOTIFICATIONS),
                                to(EventsClassifier.Section.FEED), to(EventsClassifier.Section.HIDDEN)), title = p.title.toString(),
                                label = "Feed notifications")
                            true
                        }
                    })
                }
            }

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
