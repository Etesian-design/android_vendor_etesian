package com.bompotis.etesian.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.view.View

/**
 * Each open application's N9 toolbar, as SystemUI reports it, so the Open applications cards show
 * the app with its bar (a task snapshot holds only the app's own window, not the bar).
 */
object TaskToolbars {
    class State(val back: Boolean, val items: List<Bundle>, val style: String?, val hidden: Boolean, val height: Int)

    private val states = HashMap<Int, State>()
    private val listeners = mutableSetOf<(Int) -> Unit>()
    private var receiver: BroadcastReceiver? = null

    fun get(task: Int): State? = states[task]

    fun addListener(ctx: Context, l: (Int) -> Unit) {
        listeners += l
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val task = i.getIntExtra("task", -1)
                if (task < 0) return
                states[task] = State(
                    i.getBooleanExtra("back", true),
                    i.getParcelableArrayListExtra("items", Bundle::class.java).orEmpty(),
                    i.getStringExtra("style"),
                    i.getBooleanExtra("hidden", false),
                    i.getIntExtra("height", 0),
                )
                listeners.toList().forEach { it(task) }
            }
        }
        // Only SystemUI holds this permission, so nobody else can describe a card's bar.
        ctx.applicationContext.registerReceiver(r, IntentFilter("com.bompotis.etesian.toolbar.CHANGED"),
            "android.permission.STATUS_BAR_SERVICE", null, Context.RECEIVER_EXPORTED)
        receiver = r
    }

    fun removeListener(l: (Int) -> Unit) { listeners -= l }

    /** Asks SystemUI for [task]'s bar; the answer arrives through the listeners. */
    fun query(ctx: Context, task: Int) {
        if (task < 0 || task in states) return
        ctx.sendBroadcast(Intent("com.bompotis.etesian.toolbar.QUERY").setPackage("com.android.systemui").putExtra("task", task))
    }

    private val lightThemes = HashMap<String, Boolean>()

    /** The bar's look for [pkg], decided as SystemUI does: the app's say, the user's, the theme. */
    fun isDark(ctx: Context, pkg: String, s: State?): Boolean {
        if (s?.style == "dark") return true
        if (s?.style == "light") return false
        if (Settings.Secure.getInt(ctx.contentResolver, "etesian_toolbar_style", 0) == 1) return true
        return !lightThemes.getOrPut(pkg) {
            runCatching {
                val cn = ctx.packageManager.getLaunchIntentForPackage(pkg)!!.component!!
                val theme = ctx.packageManager.getActivityInfo(cn, 0).themeResource
                val res = ctx.createPackageContext(pkg, 0).resources.newTheme().apply { applyStyle(theme, true) }
                res.obtainStyledAttributes(intArrayOf(android.R.attr.isLightTheme)).run { getBoolean(0, true).also { recycle() } }
            }.getOrDefault(true)
        }
    }
}

/**
 * A card's copy of its application's toolbar: the N9 / Belle gradient, Back and the app's items.
 * It covers the whole card and draws the bar in the bottom [ratio] of it (the card has the
 * screen's proportions, so that is where the bar sits on the screen).
 */
class CardBar(ctx: Context) : View(ctx) {
    private val fill = Paint()
    private val line = Paint().apply { strokeWidth = 1f }
    private var dark = false
    private var icons: List<Drawable?> = emptyList()
    private var back = true
    private var ratio = 0f
    private val backIcon = ctx.getDrawable(R.drawable.ic_n9_back)

    fun bind(s: TaskToolbars.State?, dark: Boolean, screenHeight: Int) {
        this.dark = dark
        ratio = if (s == null || s.hidden || s.height <= 0) 0f else s.height / screenHeight.toFloat()
        back = s?.back ?: true
        icons = s?.items.orEmpty().filter { it.getBoolean("enabled", true) }.map { b ->
            b.getParcelable("icon", Icon::class.java)?.loadDrawable(context)?.mutate()?.also { d ->
                if (b.getBoolean("tint", true)) d.colorFilter = PorterDuffColorFilter(iconColor(), PorterDuff.Mode.SRC_IN)
            }
        }
        backIcon?.colorFilter = PorterDuffColorFilter(iconColor(), PorterDuff.Mode.SRC_IN)
        invalidate()
    }

    private fun iconColor() = if (dark) 0xFFFFFFFF.toInt() else 0xFF262626.toInt()

    override fun onDraw(c: Canvas) {
        if (ratio <= 0f) return
        val w = width.toFloat(); val h = height * ratio
        c.translate(0f, height - h)
        fill.shader = if (dark) LinearGradient(0f, 0f, 0f, h, 0xFF3C3D40.toInt(), 0xFF000000.toInt(), Shader.TileMode.CLAMP)
            else LinearGradient(0f, 0f, 0f, h, 0xFFD6D6D8.toInt(), 0xFFADADB0.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        line.color = if (dark) 0xFF4A4A4E.toInt() else 0xFFEDEDEF.toInt()
        c.drawLine(0f, 0.5f, w, 0.5f, line)
        val size = (h * 0.5f).toInt()
        fun put(d: Drawable?, cx: Float) {
            d ?: return
            val l = (cx - size / 2f).toInt(); val t = ((h - size) / 2f).toInt()
            d.setBounds(l, t, l + size, t + size); d.draw(c)
        }
        // As SystemUI lays it out: Back in its own cell on the left, the items as equal cells.
        val cell = h * 1.2f
        if (back) put(backIcon, cell / 2f)
        if (icons.isNotEmpty()) {
            val start = if (back) cell else 0f
            val each = (w - start) / icons.size
            icons.forEachIndexed { i, d -> put(d, start + each * (i + 0.5f)) }
        }
    }
}
