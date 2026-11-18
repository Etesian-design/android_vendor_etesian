package com.bompotis.etesian.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** User settings, edited in [SettingsActivity]. */
object Prefs {
    const val PAGE_EVENTS = "page_events"
    const val PAGE_WIDGETS = "page_widgets"
    /** Widgets as one scrolling list, or as fixed screens that never scroll. */
    const val WIDGETS_LAYOUT = "widgets_layout"
    const val WIDGETS_LIST = "list"
    const val WIDGETS_FIXED = "screens"
    const val WIDGETS_SCREENS = "widgets_screens"
    const val SEARCH_ENABLED = "search_enabled"
    const val SEARCH_POSITION = "search_position"
    const val SEARCH_KEYBOARD = "search_keyboard"
    const val ICON_SHAPE = "icon_shape"
    /** Package name of the chosen ADW/Nova-format icon pack; "" for none. */
    const val ICON_PACK = "icon_pack"
    const val PAGE_DOTS = "page_dots"
    const val DOTS_HIDDEN = "hidden"
    const val DOTS_TOP = "top"
    const val DOTS_BOTTOM = "bottom"
    const val GRID_COLUMNS = "grid_columns"
    const val GRID_ROWS = "grid_rows"
    const val ICON_SIZE = "icon_size"
    const val ICON_SIZE_MAX = 70
    const val GESTURES = "gestures"
    const val BACK_GESTURE = "back_gesture"
    const val CLOSE_DISTANCE = "close_distance"

    // Gesture choices (ROM only).
    const val GESTURES_N9 = "n9"
    const val GESTURES_ANDROID = "android"
    /** Back by gesture is off by default: the N9 gestures and the toolbar's Back cover it. */
    const val BACK_DISABLED = "disabled"
    const val BACK_LEFT = "left"
    const val BACK_RIGHT = "right"
    /** Removed options: "short" and "curved" (swipe in, then down) now Disabled; "sidebar" now Breeze. */
    const val BACK_CURVED = "curved"
    private const val BACK_SIDEBAR_REMOVED = "sidebar"
    private const val BACK_SHORT_REMOVED = "short"
    const val ACTION_MINIMISE = "minimise"
    const val ACTION_BACK = "back"
    const val ACTION_CLOSE = "close"
    const val ACTION_NONE = "none"
    const val ACTION_SWITCHER = "switcher"
    /** Breeze only: its small menu of shortcuts and widgets, under the button. */
    const val ACTION_MENU = "menu"

    // Breeze: the edge bar, or a floating button when dragged away from the edges. Its place is set by
    // long-pressing it and dragging it.
    const val SIDE_SENSE = "side_sense"
    const val SIDEBAR_SIDE = "sidebar_side"
    const val SIDEBAR_POSITION = "sidebar_position"
    /** Horizontal centre of the floating button, as a percentage of the screen width (side "free"). */
    const val SIDEBAR_X = "sidebar_x"
    const val SIDEBAR_TAP = "sidebar_tap"
    const val SIDEBAR_DOUBLE_TAP = "sidebar_double_tap"
    /** Widgets in Breeze's menu: app widget ids, comma separated. */
    const val BREEZE_WIDGETS = "breeze_widgets"
    const val SIDE_FREE = "free"
    /** Breeze's look: "toolbar" (the bottom bar's theme), "light" or "dark". */
    const val BREEZE_THEME = "breeze_theme"
    const val SIDEBAR_FLICK_IN = "sidebar_flick_in"
    const val SIDEBAR_FLICK_UP = "sidebar_flick_up"
    const val SIDEBAR_FLICK_DOWN = "sidebar_flick_down"

    // Lock screen notifications (ROM only), read by SystemUI: "stack" shows the rest as stacked
    // cards (iPhone style), "more" as the N9's "More" label; max is how many show in full.
    const val SECURE_LOCK_STYLE = "etesian_lock_style"
    const val SECURE_LOCK_MAX = "etesian_lock_max"

    // N9 bottom toolbar (ROM only): stored directly in Settings.Secure, read by SystemUI.
    const val SECURE_BOTTOM_BAR = "etesian_bottom_bar"
    const val SECURE_BOTTOM_BAR_BUTTONS = "etesian_bottom_bar_buttons"
    fun bottomBar(ctx: Context) =
        android.provider.Settings.Secure.getInt(ctx.contentResolver, SECURE_BOTTOM_BAR, 1) == 1
    fun setBottomBar(ctx: Context, on: Boolean) =
        android.provider.Settings.Secure.putInt(ctx.contentResolver, SECURE_BOTTOM_BAR, if (on) 1 else 0)
    /** Entries are "pkg/cls" components or "settings:<intent action>". */
    // Which side of the toolbar Back sits on: 0 left, 1 right.
    const val SECURE_TOOLBAR_BACK_SIDE = "etesian_toolbar_back_side"
    fun toolbarBackSide(ctx: Context) =
        android.provider.Settings.Secure.getInt(ctx.contentResolver, SECURE_TOOLBAR_BACK_SIDE, 0)
    fun setToolbarBackSide(ctx: Context, side: Int) =
        android.provider.Settings.Secure.putInt(ctx.contentResolver, SECURE_TOOLBAR_BACK_SIDE, side)

    // N9 toolbar colours, shared by the system toolbar and the apps' own bars: 0 light, 1 black.
    const val SECURE_TOOLBAR_STYLE = "etesian_toolbar_style"
    fun toolbarStyle(ctx: Context) =
        android.provider.Settings.Secure.getInt(ctx.contentResolver, SECURE_TOOLBAR_STYLE, 0)
    fun setToolbarStyle(ctx: Context, style: Int) =
        android.provider.Settings.Secure.putInt(ctx.contentResolver, SECURE_TOOLBAR_STYLE, style)

    fun bottomBarButtons(ctx: Context): List<String> =
        android.provider.Settings.Secure.getString(ctx.contentResolver, SECURE_BOTTOM_BAR_BUTTONS)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    fun setBottomBarButtons(ctx: Context, list: List<String>) =
        android.provider.Settings.Secure.putString(ctx.contentResolver, SECURE_BOTTOM_BAR_BUTTONS,
            list.joinToString(","))

    fun of(ctx: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)

    /** Where an application swiped away from an edge ("left", "right", "bottom") goes: one of the LEAVE_ values. */
    const val LEAVE_TO = "leave_to_"
    const val LEAVE_SWITCHER = "switcher"
    const val LEAVE_LAST = "last"
    const val LEAVE_EVENTS = "events"
    const val LEAVE_APPS = "apps"
    const val LEAVE_WIDGETS = "widgets"
    /** Open applications unless the user picked another view for that edge (one that is turned on). */
    fun leaveTo(ctx: Context, edge: String): String {
        val v = of(ctx).getString(LEAVE_TO + edge, LEAVE_SWITCHER) ?: LEAVE_SWITCHER
        return when {
            v == LEAVE_EVENTS && !eventsPage(ctx) -> LEAVE_SWITCHER
            v == LEAVE_WIDGETS && !widgetsPage(ctx) -> LEAVE_SWITCHER
            else -> v
        }
    }

    fun eventsPage(ctx: Context) = of(ctx).getBoolean(PAGE_EVENTS, true)
    fun pageDots(ctx: Context) = of(ctx).getString(PAGE_DOTS, DOTS_HIDDEN) ?: DOTS_HIDDEN
    fun widgetsPage(ctx: Context) = of(ctx).getBoolean(PAGE_WIDGETS, false)
    fun widgetsFixed(ctx: Context) = of(ctx).getString(WIDGETS_LAYOUT, WIDGETS_LIST) == WIDGETS_FIXED
    fun widgetScreens(ctx: Context) = of(ctx).getString(WIDGETS_SCREENS, "1")!!.toIntOrNull()?.coerceIn(1, 5) ?: 1
    fun searchEnabled(ctx: Context) = of(ctx).getBoolean(SEARCH_ENABLED, false)
    fun searchAtTop(ctx: Context) = of(ctx).getString(SEARCH_POSITION, "top") == "top"
    fun searchKeyboard(ctx: Context) = of(ctx).getBoolean(SEARCH_KEYBOARD, true)
    fun gridColumns(ctx: Context) = of(ctx).getString(GRID_COLUMNS, "4")!!.toInt()
    /** 0 = automatic spacing. */
    fun gridRows(ctx: Context) = of(ctx).getString(GRID_ROWS, "0")!!.toInt()
    fun iconSizeDp(ctx: Context) = of(ctx).getString(ICON_SIZE, "58")!!.toInt()
    fun gestures(ctx: Context) = of(ctx).getString(GESTURES, GESTURES_N9)!!
    /** How far an application must be swiped to send it to the background (N9: half the screen). */
    fun closeDistance(ctx: Context) =
        (of(ctx).getString(CLOSE_DISTANCE, "50") ?: "50").toFloatOrNull()?.div(100f) ?: 0.5f
    fun backGesture(ctx: Context): String {
        val v = of(ctx).getString(BACK_GESTURE, BACK_DISABLED)!!
        if (v == BACK_LEFT || v == BACK_RIGHT || v == BACK_DISABLED) return v
        // A removed option: Back by the edge bar becomes Breeze, the others Disabled.
        of(ctx).edit().putString(BACK_GESTURE, BACK_DISABLED).apply {
            if (v == BACK_SIDEBAR_REMOVED) putBoolean(SIDE_SENSE, true)
        }.apply()
        return BACK_DISABLED
    }
    /** Breeze: the one edge bar, its own setting (on with "sidebar" as Back before). */
    fun breeze(ctx: Context): Boolean {
        backGesture(ctx)   // migrates an old "sidebar" Back first
        // The toolbar's side strip is gone (Breeze is the one edge bar): an old choice of it turns Breeze on.
        runCatching {
            if (android.provider.Settings.Secure.getInt(ctx.contentResolver, "etesian_toolbar_mode", 0) == 1) {
                android.provider.Settings.Secure.putInt(ctx.contentResolver, "etesian_toolbar_mode", 0)
                of(ctx).edit().putBoolean(SIDE_SENSE, true).apply()
            }
        }
        return of(ctx).getBoolean(SIDE_SENSE, false)
    }
    fun setSideBarPlace(ctx: Context, side: String, percent: Int, xPercent: Int = 50) =
        of(ctx).edit().putString(SIDEBAR_SIDE, side).putString(SIDEBAR_POSITION, percent.coerceIn(5, 95).toString())
            .putString(SIDEBAR_X, xPercent.coerceIn(5, 95).toString()).apply()
    fun sideBarX(ctx: Context) = of(ctx).getString(SIDEBAR_X, "50")!!.toInt()
    fun breezeWidgets(ctx: Context): List<Int> =
        of(ctx).getString(BREEZE_WIDGETS, "")!!.split(',').mapNotNull { it.toIntOrNull() }
    fun setBreezeWidgets(ctx: Context, ids: List<Int>) = of(ctx).edit().putString(BREEZE_WIDGETS, ids.joinToString(",")).apply()
    fun breezeDark(ctx: Context) = when (of(ctx).getString(BREEZE_THEME, "toolbar")) {
        "dark" -> true
        "light" -> false
        else -> com.bompotis.etesian.toolbar.EtesianToolbar.userDark(ctx)
    }
    fun sideBarSide(ctx: Context) = of(ctx).getString(SIDEBAR_SIDE, "right")!!
    /** Bar centre as a percentage of the screen height. */
    fun sideBarPosition(ctx: Context) = of(ctx).getString(SIDEBAR_POSITION, "45")!!.toInt()
    fun sideBarAction(ctx: Context, gesture: String) = of(ctx).getString(gesture, when (gesture) {
        SIDEBAR_FLICK_IN, SIDEBAR_DOUBLE_TAP -> ACTION_BACK
        SIDEBAR_FLICK_UP -> ACTION_SWITCHER
        SIDEBAR_TAP -> ACTION_MENU
        else -> ACTION_NONE
    })!!
    const val WEATHER = "weather"
    const val WEATHER_UNITS = "weather_units"
    fun weatherEnabled(ctx: Context) = of(ctx).getBoolean(WEATHER, true)
    const val WEATHER_CITY = "weather_city"
    fun weatherCity(ctx: Context) = of(ctx).getString(WEATHER_CITY, "")!!
    /** "lat,lon" of the place picked in the place picker, kept with its name (two towns can share one). */
    const val WEATHER_PLACE = "weather_place"
    fun weatherFahrenheit(ctx: Context) = of(ctx).getString(WEATHER_UNITS, "c") == "f"
    fun eventsStyleKey(s: EventsClassifier.Section) = "events_style_${s.key}"
    fun eventsStyle(ctx: Context, s: EventsClassifier.Section): EventsClassifier.Style {
        val key = of(ctx).getString(eventsStyleKey(s), null)
        return EventsClassifier.Style.entries.firstOrNull { it.key == key } ?: EventsClassifier.Style.GROUPED
    }
    fun iconPack(ctx: Context) = of(ctx).getString(ICON_PACK, "")!!
    fun iconShape(ctx: Context) = of(ctx).getString(ICON_SHAPE, IconFactory.SHAPE_N9)!!
}
