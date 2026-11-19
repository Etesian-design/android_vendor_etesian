package com.bompotis.etesian.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.text.TextUtils
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bompotis.etesian.toolbar.EtesianToolbar
import org.json.JSONObject

/**
 * Breeze's buttons, arranged by hand: at the bottom of the page the menu's strip as it will look, with its buttons;
 * above it every button there is to add, grouped as the add menu groups them (system buttons, settings pages,
 * macros, apps, each app's shortcuts). Long-press one and drag it onto the strip to add it where it is let go;
 * drag one along the strip to move it, or off the strip to remove it. A tap adds at the end, or on the strip
 * offers Move and Remove.
 */
class BreezeButtonsActivity : Activity() {
    companion object {
        /** Breeze's menu shows this many buttons (BreezeMenu.MAX_BUTTONS in the platform sources). */
        const val MAX = 5
    }

    /** A button that can be added: its label, how it shows here, and the button it makes. */
    private class Candidate(val label: String, val icon: Drawable?, val tint: Boolean, val make: () -> ToolbarConfig.Button)

    /** What is being dragged: a candidate, or the strip's button at [fromBar]. */
    private class Dragging(val candidate: Candidate?, val fromBar: Int)

    private val dark by lazy { Prefs.breezeDark(this) }
    private val iconColor by lazy { EtesianToolbar.iconColor(dark) }
    private lateinit var groups: LinearLayout
    private lateinit var strip: LinearLayout
    private lateinit var hint: TextView
    private var buttons = mutableListOf<ToolbarConfig.Button>()
    /** While dragging over the strip: where it would go (-1: not over the strip). */
    private var gapAt = -1
    private var dragging: Dragging? = null
    private val folded = mutableSetOf<String>()

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        buttons = ToolbarConfig.buttons(this, ToolbarConfig.BREEZE).toMutableList()
        groups = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(16f)) }
        strip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = EtesianToolbar.barBackground(this@BreezeButtonsActivity, dark)
            contentDescription = "Breeze strip"
            setOnDragListener { _, e -> onStripDrag(e) }
        }
        hint = TextView(this).apply {
            setTextColor(N9.TEXT_SECONDARY); textSize = 14f; typeface = N9.FONT; gravity = Gravity.CENTER
            setPadding(dp(16f), dp(10f), dp(16f), dp(8f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            addView(TextView(context).apply {
                text = "Breeze buttons"
                setTextColor(N9.TEXT); textSize = 35f; typeface = N9.FONT_LIGHT
                setPadding(dp(16f), dp(16f), dp(16f), dp(10f))
            })
            addView(View(context).apply { setBackgroundColor(0xFF5A5A5A.toInt()) },
                LinearLayout.LayoutParams(-1, dp(1f)).apply { marginStart = dp(16f); marginEnd = dp(16f) })
            addView(ScrollView(context).apply { addView(groups) }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(View(context).apply { setBackgroundColor(0xFF5A5A5A.toInt()) }, LinearLayout.LayoutParams(-1, dp(1f)))
            addView(hint)
            addView(FrameLayout(context).apply {
                setPadding(0, 0, 0, dp(12f))
                addView(strip, FrameLayout.LayoutParams(stripWidth(), dp(56f), Gravity.CENTER_HORIZONTAL))
            })
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom); ins
            }
            // Let go anywhere off the strip: a strip button dragged there is removed.
            setOnDragListener { _, e -> onPageDrag(e) }
        }
        setContentView(root)
        drawStrip()
        fill()
    }

    override fun onResume() {
        super.onResume()
        if (SystemToolbar.available(this)) SystemToolbar(this).set(back = true, items = emptyList(), style = "dark") {}
    }

    /** As wide as Breeze's menu. */
    private fun stripWidth() = minOf(dp(300f), (resources.displayMetrics.widthPixels * 0.85f).toInt())

    private fun save() {
        ToolbarConfig.save(this, ToolbarConfig.BREEZE, buttons)
    }

    // --- The strip --------------------------------------------------------------------------

    private fun drawStrip() {
        strip.removeAllViews()
        val size = dp(42f)
        val shown = buttons.withIndex().filter { dragging?.fromBar != it.index }.toMutableList<IndexedValue<ToolbarConfig.Button>?>()
        if (gapAt >= 0) shown.add(gapAt.coerceAtMost(shown.size), null)
        if (shown.isEmpty()) {
            strip.addView(TextView(this).apply {
                text = "Drag buttons here"; gravity = Gravity.CENTER; typeface = N9.FONT; textSize = 15f
                setTextColor(iconColor)
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        for (item in shown) {
            val cell = FrameLayout(this)
            if (item == null) {
                // The gap where the dragged button would go.
                cell.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12f).toFloat(); setStroke(dp(2f), N9.ACCENT, dp(5f).toFloat(), dp(4f).toFloat())
                    }
                }, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            } else {
                val (i, b) = item
                cell.addView(FrameLayout(this).apply {
                    background = EtesianToolbar.buttonBackground(this@BreezeButtonsActivity, size, dark)
                    contentDescription = "On Breeze: ${b.label}"
                    addView(ImageView(context).apply {
                        setImageDrawable(ToolbarConfig.decode(b.icon)?.let { BitmapDrawable(resources, it) })
                        if (b.tint) setColorFilter(iconColor)
                    }, FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.CENTER))
                    setOnClickListener { stripSheet(i) }
                    setOnLongClickListener { v -> startDrag(v, Dragging(null, i), b.label); true }
                }, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            }
            strip.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val full = buttons.size >= MAX
        hint.text = when {
            dragging?.candidate != null && full -> "Breeze holds $MAX buttons: drag one off first"
            dragging?.fromBar != null && dragging!!.fromBar >= 0 -> "Let go off the strip to remove it"
            full -> "Breeze holds $MAX buttons. Drag one off the strip to remove it"
            else -> "Long-press a button and drag it onto Breeze, or tap it"
        }
    }

    private fun stripSheet(i: Int) {
        val b = buttons.getOrNull(i) ?: return
        val items = mutableListOf<N9Sheet.Item>()
        if (i > 0) items += N9Sheet.Item("Move left") { buttons.add(i - 1, buttons.removeAt(i)); save(); drawStrip() }
        if (i < buttons.size - 1) items += N9Sheet.Item("Move right") { buttons.add(i + 1, buttons.removeAt(i)); save(); drawStrip() }
        items += N9Sheet.Item("Remove") { buttons.removeAt(i); save(); drawStrip() }
        N9Sheet.show(this, items, title = b.label, label = "Breeze button")
    }

    private fun startDrag(v: View, d: Dragging, label: String) {
        dragging = d
        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        v.startDragAndDrop(ClipData.newPlainText("breeze", label), View.DragShadowBuilder(v), d, 0)
        drawStrip()
    }

    /** Where on the strip [x] (in the strip's coordinates) would put a button. */
    private fun slotAt(x: Float): Int {
        val n = buttons.size - (if ((dragging?.fromBar ?: -1) >= 0) 1 else 0)
        val cell = strip.width / (n + 1f)
        return (x / cell).toInt().coerceIn(0, n)
    }

    private fun onStripDrag(e: DragEvent): Boolean {
        val d = e.localState as? Dragging ?: return false
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DRAG_LOCATION, DragEvent.ACTION_DRAG_ENTERED -> {
                val full = d.candidate != null && buttons.size >= MAX
                val at = if (full) -1 else slotAt(e.x)
                if (at != gapAt) { gapAt = at; drawStrip() }
            }
            DragEvent.ACTION_DRAG_EXITED -> { gapAt = -1; drawStrip() }
            DragEvent.ACTION_DROP -> {
                val at = slotAt(e.x)
                gapAt = -1
                if (d.candidate != null) {
                    if (buttons.size >= MAX) { full(); dragDone(); return false }
                    buttons.add(at, d.candidate.make())
                } else if (d.fromBar >= 0) {
                    val b = buttons.removeAt(d.fromBar)
                    buttons.add(at.coerceAtMost(buttons.size), b)
                }
                save(); dragDone()
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> dragDone()
        }
        return true
    }

    private fun onPageDrag(e: DragEvent): Boolean {
        val d = e.localState as? Dragging ?: return false
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DROP -> {
                if (d.fromBar in buttons.indices) { buttons.removeAt(d.fromBar); save() }
                dragDone()
            }
            DragEvent.ACTION_DRAG_ENDED -> dragDone()
        }
        return true
    }

    private fun dragDone() {
        if (dragging == null && gapAt < 0) return
        dragging = null; gapAt = -1
        drawStrip()
    }

    private fun full() = Toast.makeText(this, "Breeze holds $MAX buttons: drag one off first", Toast.LENGTH_SHORT).show()

    private fun addAtEnd(c: Candidate) {
        if (buttons.size >= MAX) { full(); return }
        buttons += c.make(); save(); drawStrip()
    }

    // --- What can be added ------------------------------------------------------------------

    private fun fill() {
        val sys = ToolbarConfig.SYSTEM_BUTTONS.map { (label, glyph, key) ->
            val icon = ToolbarConfig.glyph(this, glyph)
            Candidate(label, ToolbarConfig.decode(icon)?.let { BitmapDrawable(resources, it) }, true) {
                ToolbarConfig.Button(label, icon, true, JSONObject().put("t", "key").put("k", key))
            }
        }
        val toggles = ToolbarConfig.SYSTEM_TOGGLES.map { (label, glyph, t) ->
            val icon = ToolbarConfig.glyph(this, glyph)
            Candidate(label, ToolbarConfig.decode(icon)?.let { BitmapDrawable(resources, it) }, true) {
                ToolbarConfig.Button(label, icon, true, JSONObject().put("t", t))
            }
        }
        group("System buttons", sys + toggles)
        val tune = ToolbarConfig.glyph(this, "tune")
        group("Settings pages", ToolbarConfig.SETTINGS_PAGES.map { (label, action) ->
            Candidate(label, ToolbarConfig.decode(tune)?.let { BitmapDrawable(resources, it) }, true) {
                ToolbarConfig.Button(label, tune, true, JSONObject().put("t", "settings").put("a", action))
            }
        })
        val macro = ToolbarConfig.glyph(this, "macro")
        group("Macros", listOf(Candidate("New macro", ToolbarConfig.decode(macro)?.let { BitmapDrawable(resources, it) }, true) {
            // Made on the macro page; this placeholder is replaced there.
            ToolbarConfig.Button("Macro", macro, true, JSONObject().put("t", "macro").put("steps", org.json.JSONArray()))
        }), onTap = { newMacro() })
        // Apps and their shortcuts: read off the main thread, then shown.
        Thread {
            val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) to it.loadLabel(packageManager).toString() }
                .filter { it.first.packageName != packageName || it.second.isNotEmpty() }
                .sortedBy { it.second.lowercase() }
            val appTiles = apps.map { (cn, label) ->
                Candidate(label, runCatching { packageManager.getActivityIcon(cn) }.getOrNull(), false) {
                    val (icon, tint) = ToolbarConfig.appIcon(this, cn.packageName, cn)
                    ToolbarConfig.Button(label, icon, tint, JSONObject().put("t", "app").put("c", cn.flattenToString()))
                }
            }
            val shortcuts = apps.distinctBy { it.first.packageName }.mapNotNull { (cn, label) ->
                val list = shortcuts(cn.packageName)
                if (list.isEmpty()) null else label to list.map { s -> shortcutTile(cn.packageName, s) }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                group("Apps", appTiles)
                if (shortcuts.isNotEmpty()) {
                    header("App shortcuts", shortcuts.sumOf { it.second.size }, "App shortcuts")
                    if ("App shortcuts" !in folded) shortcuts.forEach { (app, tiles) -> group(app, tiles, sub = true) }
                }
            }
        }.start()
    }

    private fun shortcuts(pkg: String): List<ShortcutInfo> = runCatching {
        getSystemService(LauncherApps::class.java).getShortcuts(LauncherApps.ShortcutQuery().setPackage(pkg)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
            Process.myUserHandle()).orEmpty()
    }.getOrDefault(emptyList())

    private fun shortcutTile(pkg: String, s: ShortcutInfo): Candidate {
        val label = (s.shortLabel ?: s.id).toString()
        val d = runCatching {
            getSystemService(LauncherApps::class.java).getShortcutIconDrawable(s, resources.displayMetrics.densityDpi)
        }.getOrNull()
        return Candidate(label, d, false) {
            val mono = (d as? android.graphics.drawable.AdaptiveIconDrawable)?.monochrome
            val (icon, tint) = when {
                mono != null -> ToolbarConfig.colourIcon(mono) to true
                d != null -> ToolbarConfig.colourIcon(d) to false
                else -> ToolbarConfig.appIcon(this, pkg)
            }
            ToolbarConfig.Button(label, icon, tint, JSONObject().put("t", "shortcut").put("p", pkg).put("id", s.id))
        }
    }

    private fun newMacro() {
        if (buttons.size >= MAX) { full(); return }
        startActivity(Intent(this, ToolbarSettingsActivity::class.java)
            .putExtra(ToolbarSettingsActivity.EXTRA_SET, ToolbarConfig.BREEZE)
            .putExtra(ToolbarSettingsActivity.EXTRA_MACRO, true))
    }

    override fun onRestart() {
        super.onRestart()
        // Back from the macro page: it may have added one.
        buttons = ToolbarConfig.buttons(this, ToolbarConfig.BREEZE).toMutableList()
        drawStrip()
    }

    /** A category's header: tap to fold it. */
    private fun header(title: String, n: Int, key: String, sub: Boolean = false) {
        groups.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(if (sub) 8f else 18f), dp(20f), dp(6f))
            addView(TextView(context).apply {
                text = title; setTextColor(if (sub) N9.TEXT_SECONDARY else N9.TEXT)
                textSize = if (sub) 16f else 20f; typeface = if (sub) N9.FONT else N9.FONT_BOLD
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (!sub) {
                addView(TextView(context).apply {
                    text = (if (key in folded) "$n  ▼" else "▲"); setTextColor(N9.TEXT_SECONDARY); textSize = 14f
                })
                setOnClickListener {
                    if (!folded.remove(key)) folded += key
                    groups.removeAllViews(); fill()
                }
            }
        })
    }

    private fun group(title: String, tiles: List<Candidate>, sub: Boolean = false, onTap: ((Candidate) -> Unit)? = null) {
        if (tiles.isEmpty()) return
        header(title, tiles.size, title, sub)
        if (!sub && title in folded) return
        val cols = 4
        val cellW = (resources.displayMetrics.widthPixels - dp(16f)) / cols
        val grid = GridLayout(this).apply { columnCount = cols; setPadding(dp(8f), 0, dp(8f), dp(4f)) }
        tiles.forEach { c -> grid.addView(tile(c, onTap), GridLayout.LayoutParams().apply { width = cellW }) }
        groups.addView(grid)
    }

    /** One button that can be added: its squircle, as on Breeze, and its name. */
    private fun tile(c: Candidate, onTap: ((Candidate) -> Unit)?): View {
        val size = dp(52f)
        val face = FrameLayout(this).apply {
            background = EtesianToolbar.buttonBackground(this@BreezeButtonsActivity, size, dark)
            addView(ImageView(context).apply {
                setImageDrawable(c.icon?.constantState?.newDrawable()?.mutate() ?: c.icon)
                if (c.tint) setColorFilter(iconColor)
            }, FrameLayout.LayoutParams(dp(if (c.tint) 30f else 36f), dp(if (c.tint) 30f else 36f), Gravity.CENTER))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
            contentDescription = "Add ${c.label}"
            addView(face, LinearLayout.LayoutParams(size, size))
            addView(TextView(context).apply {
                text = c.label; setTextColor(N9.TEXT); textSize = 13f; typeface = N9.FONT
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4f), 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))
            setOnClickListener { onTap?.invoke(c) ?: addAtEnd(c) }
            setOnLongClickListener {
                if (onTap != null) { onTap(c); return@setOnLongClickListener true }
                startDrag(face, Dragging(c, -1), c.label); true
            }
        }
    }
}
