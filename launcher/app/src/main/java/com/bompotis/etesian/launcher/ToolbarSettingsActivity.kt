package com.bompotis.etesian.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sets up the user's own N9 toolbar buttons: the default set shown under every app, sets for
 * particular apps, and the buttons in each (an app, an app shortcut, a settings page or a
 * macro), each with an automatic, symbol or picture icon.
 */
class ToolbarSettingsActivity : Activity() {

    private lateinit var content: LinearLayout
    private lateinit var title: TextView
    private lateinit var actions: LinearLayout

    /** The set being edited (null: the list of sets), and its buttons. */
    private var editing: String? = null
    private var buttons = mutableListOf<ToolbarConfig.Button>()
    /** A button waiting for its picture icon to be picked. */
    private var pendingImage: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ToolbarConfig.migrate(this)
        window.setBackgroundDrawable(Backgrounds.blackScreen(this))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(this).apply {
            setTextColor(N9.TEXT); textSize = 30f; typeface = N9.FONT_LIGHT
            setPadding(dp(16f), dp(16f), dp(16f), dp(8f))
        }
        root.addView(title)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(12f), dp(16f), dp(16f))
        }
        root.addView(actions)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        showSets()
        // Back from a set being edited returns to the list (targetSdk 37 never calls onBackPressed).
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (editing != null) { showSets(); slide(fromRight = false) } else finish()
        }
    }



    // --- Views -----------------------------------------------------------------------------

    private fun pill(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(N9.TEXT); textSize = 17f; typeface = N9.FONT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        background = N9.button(context)
        setOnClickListener { onClick() }
    }

    private fun setActions(vararg pills: Pair<String, () -> Unit>) {
        actions.removeAllViews()
        pills.forEachIndexed { i, (label, action) ->
            actions.addView(pill(label, action), LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i > 0) marginStart = dp(12f)
            })
        }
    }

    private fun row(icon: android.graphics.drawable.Drawable?, tint: Boolean, text: String, sub: String, onClick: () -> Unit) =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            background = getDrawable(android.R.drawable.list_selector_background)
            if (icon != null) addView(ImageView(context).apply {
                setImageDrawable(icon)
                if (tint) setColorFilter(N9.TEXT)
                background = IconFactory.tile(N9.TILE)
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            }, LinearLayout.LayoutParams(dp(48f), dp(48f)).apply { marginEnd = dp(14f) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    this.text = text; setTextColor(N9.TEXT); textSize = 19f; typeface = N9.FONT_BOLD
                })
                if (sub.isNotEmpty()) addView(TextView(context).apply {
                    this.text = sub; setTextColor(N9.TEXT_SECONDARY); textSize = 15f; typeface = N9.FONT
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { onClick() }
        }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(N9.TEXT_SECONDARY); textSize = 16f; typeface = N9.FONT
        setPadding(dp(16f), dp(8f), dp(16f), dp(12f))
    }

    private fun appLabel(pkg: String) = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    // --- The list of sets ------------------------------------------------------------------

    private fun showSets() {
        editing = null
        title.text = "Toolbar buttons"
        content.removeAllViews()
        content.addView(note("Shown after Back on the N9 toolbar. Apps that put their own buttons on the toolbar replace these while they're open."))
        val n = ToolbarConfig.buttons(this, ToolbarConfig.DEFAULT).size
        content.addView(row(getDrawable(R.drawable.tb_star), true, "All apps", count(n)) { edit(ToolbarConfig.DEFAULT) })
        for (pkg in ToolbarConfig.apps(this)) {
            val icon = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
            content.addView(row(icon, false, appLabel(pkg), count(ToolbarConfig.buttons(this, pkg).size)) { edit(pkg) })
        }
        setActions("Set up an app" to { pickApp("Set up buttons for") { cn -> edit(cn.packageName) } })
    }

    private fun count(n: Int) = if (n == 0) "No buttons" else "$n button" + if (n == 1) "" else "s"

    // --- One set ---------------------------------------------------------------------------

    private fun edit(pkg: String) {
        editing = pkg
        buttons = ToolbarConfig.buttons(this, pkg).toMutableList()
        if (!ToolbarConfig.hasSet(this, pkg)) save()
        showButtons()
        slide(fromRight = true)
    }

    /** N9 sub page: a set comes in from the right; Back brings the list back from the left. */
    private fun slide(fromRight: Boolean) {
        val w = window.decorView.width.toFloat()
        for (v in listOf(title, content)) {
            v.translationX = if (fromRight) w else -w
            v.animate().translationX(0f).setDuration(300).setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)).start()
        }
    }

    private fun save() = ToolbarConfig.save(this, editing ?: ToolbarConfig.DEFAULT, buttons)

    private fun showButtons() {
        val pkg = editing ?: return
        title.text = if (pkg == ToolbarConfig.DEFAULT) "All apps" else appLabel(pkg)
        content.removeAllViews()
        if (buttons.isEmpty()) content.addView(note("No buttons yet: only Back is shown."))
        buttons.forEachIndexed { i, b ->
            val icon = ToolbarConfig.decode(b.icon)?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
            content.addView(row(icon, b.tint, b.label, ToolbarActions.describe(this, b.action)) { buttonMenu(i) })
        }
        if (pkg == ToolbarConfig.DEFAULT) setActions("Add button" to { addButton() })
        else setActions("Add button" to { addButton() }, "Remove app" to {
            ToolbarConfig.deleteSet(this, pkg); showSets(); slide(fromRight = false)
        })
    }

    private fun buttonMenu(i: Int) {
        val b = buttons[i]
        val items = arrayOf("Move earlier", "Move later", "Rename", "Change icon", "Remove")
        dialog().setTitle(b.label).setItems(items) { _, which ->
            when (which) {
                0 -> if (i > 0) { buttons.add(i - 1, buttons.removeAt(i)); save() }
                1 -> if (i < buttons.size - 1) { buttons.add(i + 1, buttons.removeAt(i)); save() }
                2 -> textDialog("Rename", b.label) { buttons[i] = ToolbarConfig.Button(it, b.icon, b.tint, b.action); save(); showButtons() }
                3 -> chooseIcon(b.action) { icon, tint -> buttons[i] = ToolbarConfig.Button(b.label, icon, tint, b.action); save(); showButtons() }
                4 -> { buttons.removeAt(i); save() }
            }
            showButtons()
        }.show()
    }

    private fun textDialog(title: String, value: String, done: (String) -> Unit) {
        val input = EditText(this).apply { setText(value); setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT }
        dialog().setTitle(title).setView(LinearLayout(this).apply {
            setPadding(dp(20f), dp(8f), dp(20f), 0); addView(input, LinearLayout.LayoutParams(-1, -2))
        }).setPositiveButton(android.R.string.ok) { _, _ ->
            input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(done)
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    /** Add: pick what it does, then its icon (automatic by default). */
    private fun addButton() {
        dialog().setTitle("Add button")
            .setItems(arrayOf("App", "App shortcut", "Settings page", "Macro")) { _, which ->
                when (which) {
                    0 -> pickApp("Open which app?") { cn ->
                        val label = runCatching { packageManager.getActivityInfo(cn, 0).loadLabel(packageManager).toString() }.getOrDefault(cn.packageName)
                        val (icon, tint) = ToolbarConfig.appIcon(this, cn.packageName, cn)
                        add(label, icon, tint, JSONObject().put("t", "app").put("c", cn.flattenToString()))
                    }
                    1 -> pickShortcut { label, icon, tint, action -> add(label, icon, tint, action) }
                    2 -> pickSettings { label, action ->
                        add(label, ToolbarConfig.glyph(this, "tune"), true, action)
                    }
                    3 -> editMacro(JSONObject().put("t", "macro").put("steps", JSONArray())) { name, action ->
                        add(name, ToolbarConfig.glyph(this, "macro"), true, action)
                    }
                }
            }.show()
    }

    private fun add(label: String, icon: String, tint: Boolean, action: JSONObject) {
        buttons += ToolbarConfig.Button(label, icon, tint, action)
        save()
        showButtons()
    }

    /** Automatic (the app's icon), one of the symbols, or a picture. */
    private fun chooseIcon(action: JSONObject, done: (String, Boolean) -> Unit) {
        dialog().setTitle("Icon").setItems(arrayOf("Automatic", "Symbol", "Picture")) { _, which ->
            when (which) {
                0 -> {
                    val (icon, tint) = when (action.optString("t")) {
                        "app" -> ComponentName.unflattenFromString(action.optString("c"))
                            ?.let { ToolbarConfig.appIcon(this, it.packageName, it) }
                            ?: (ToolbarConfig.glyph(this, "star") to true)
                        "shortcut" -> shortcutIcon(action.optString("p"), action.optString("id"))
                            ?: ToolbarConfig.appIcon(this, action.optString("p"))
                        "macro" -> ToolbarConfig.glyph(this, "macro") to true
                        else -> ToolbarConfig.glyph(this, "tune") to true
                    }
                    done(icon, tint)
                }
                1 -> pickGlyph { done(ToolbarConfig.glyph(this, it), true) }
                2 -> {
                    pendingImage = { done(it, false) }
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*"), REQUEST_IMAGE)
                }
            }
        }.show()
    }

    private fun pickGlyph(done: (String) -> Unit) {
        var d: AlertDialog? = null
        val grid = GridLayout(this).apply {
            columnCount = 6
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        }
        for ((name, res) in ToolbarConfig.GLYPHS) {
            grid.addView(ImageView(this).apply {
                setImageResource(res)
                setColorFilter(N9.TEXT)
                setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
                contentDescription = name
                setOnClickListener { d?.dismiss(); done(name) }
            }, GridLayout.LayoutParams().apply { width = dp(48f); height = dp(48f) })
        }
        d = dialog().setTitle("Symbol").setView(grid).setNegativeButton(android.R.string.cancel, null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && uri != null) {
            ToolbarConfig.imageIcon(this, uri)?.let { pendingImage?.invoke(it) }
        }
        pendingImage = null
    }

    // --- Pickers ---------------------------------------------------------------------------

    private fun launchable() = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) to it.loadLabel(packageManager).toString() }
        .sortedBy { it.second.lowercase() }

    private fun pickApp(prompt: String, done: (ComponentName) -> Unit) {
        val apps = launchable()
        dialog().setTitle(prompt).setItems(apps.map { it.second }.toTypedArray()) { _, i -> done(apps[i].first) }.show()
    }

    private fun shortcuts(pkg: String) = runCatching {
        getSystemService(LauncherApps::class.java).getShortcuts(LauncherApps.ShortcutQuery()
            .setPackage(pkg)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
            Process.myUserHandle()).orEmpty()
    }.getOrDefault(emptyList())

    private fun shortcutIcon(pkg: String, id: String): Pair<String, Boolean>? {
        val s = shortcuts(pkg).firstOrNull { it.id == id } ?: return null
        val d = getSystemService(LauncherApps::class.java).getShortcutIconDrawable(s, resources.displayMetrics.densityDpi)
            ?: return null
        val mono = (d as? android.graphics.drawable.AdaptiveIconDrawable)?.monochrome
        return if (mono != null) ToolbarConfig.colourIcon(mono) to true else ToolbarConfig.colourIcon(d) to false
    }

    private fun pickShortcut(done: (String, String, Boolean, JSONObject) -> Unit) {
        val apps = launchable().filter { shortcuts(it.first.packageName).isNotEmpty() }
            .distinctBy { it.first.packageName }
        if (apps.isEmpty()) {
            dialog().setMessage("No app offers shortcuts.").setPositiveButton(android.R.string.ok, null).show()
            return
        }
        dialog().setTitle("Shortcut from which app?").setItems(apps.map { it.second }.toTypedArray()) { _, i ->
            val pkg = apps[i].first.packageName
            val list = shortcuts(pkg)
            dialog().setTitle(apps[i].second).setItems(list.map { (it.shortLabel ?: it.id).toString() }.toTypedArray()) { _, j ->
                val s = list[j]
                val (icon, tint) = shortcutIcon(pkg, s.id) ?: ToolbarConfig.appIcon(this, pkg)
                done((s.shortLabel ?: s.id).toString(), icon, tint, JSONObject().put("t", "shortcut").put("p", pkg).put("id", s.id))
            }.show()
        }.show()
    }

    private fun pickSettings(done: (String, JSONObject) -> Unit) {
        val pages = ToolbarConfig.SETTINGS_PAGES
        dialog().setTitle("Settings page").setItems(pages.map { it.first }.toTypedArray()) { _, i ->
            done(pages[i].first, JSONObject().put("t", "settings").put("a", pages[i].second))
        }.show()
    }

    // --- Macros ----------------------------------------------------------------------------

    /** A macro's steps, added one by one; [done] gets its name and action. */
    private fun editMacro(macro: JSONObject, done: (String, JSONObject) -> Unit) {
        val steps = macro.optJSONArray("steps") ?: JSONArray().also { macro.put("steps", it) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = EditText(this).apply { hint = "Name"; setSingleLine(true); setText("Macro") }
        fun refresh() {
            list.removeAllViews()
            if (steps.length() == 0) list.addView(TextView(this).apply {
                text = "No steps yet"; setPadding(0, dp(8f), 0, dp(8f))
            })
            for (i in 0 until steps.length()) {
                list.addView(TextView(this).apply {
                    text = "${i + 1}. " + ToolbarActions.describe(context, steps.getJSONObject(i))
                    textSize = 16f
                    setPadding(0, dp(8f), 0, dp(8f))
                    setOnClickListener { steps.remove(i); refresh() }
                })
            }
        }
        refresh()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), 0)
            addView(name)
            addView(list)
            addView(TextView(this@ToolbarSettingsActivity).apply {
                text = "Tap a step to remove it"; textSize = 13f; alpha = 0.6f
            })
        }
        val d = dialog().setTitle("Macro").setView(ScrollView(this).apply { addView(body) })
            .setNeutralButton("Add step", null)
            .setPositiveButton("Save", null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { addStep { steps.put(it); refresh() } }
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (steps.length() == 0) return@setOnClickListener
            d.dismiss()
            done(name.text.toString().trim().ifEmpty { "Macro" }, macro)
        }
    }

    private fun addStep(done: (JSONObject) -> Unit) {
        val kinds = arrayOf("Open app", "Open shortcut", "Open settings page", "Press a key",
            "Torch on/off", "Wi-Fi on/off", "Bluetooth on/off", "Auto-rotate on/off", "Wait")
        dialog().setTitle("Add step").setItems(kinds) { _, which ->
            when (which) {
                0 -> pickApp("Open which app?") { done(JSONObject().put("t", "app").put("c", it.flattenToString())) }
                1 -> pickShortcut { _, _, _, action -> done(action) }
                2 -> pickSettings { _, action -> done(action) }
                3 -> dialog().setTitle("Key").setItems(ToolbarActions.KEYS.map { it.first }.toTypedArray()) { _, i ->
                    done(JSONObject().put("t", "key").put("k", ToolbarActions.KEYS[i].second))
                }.show()
                4 -> done(JSONObject().put("t", "torch"))
                5 -> done(JSONObject().put("t", "wifi"))
                6 -> done(JSONObject().put("t", "bluetooth"))
                7 -> done(JSONObject().put("t", "rotation"))
                8 -> dialog().setTitle("Wait").setItems(arrayOf("0.5 seconds", "1 second", "2 seconds", "5 seconds")) { _, i ->
                    done(JSONObject().put("t", "wait").put("ms", longArrayOf(500, 1000, 2000, 5000)[i]))
                }.show()
            }
        }.show()
    }

    companion object { private const val REQUEST_IMAGE = 2 }
}
