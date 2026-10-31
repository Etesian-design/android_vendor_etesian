package com.bompotis.etesian.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Picks the four applications of a Shortcuts (Belle) widget: tap a slot, then an application. */
class BelleShortcutsConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val slots = MutableList<ComponentName?>(BelleShortcutsWidget.SLOTS) { null }
    private var selected = 0
    private var apps: List<AppInfo> = emptyList()
    private val slotViews = mutableListOf<Pair<ImageView, TextView>>()
    private val slotRows = mutableListOf<LinearLayout>()
    private val adapter = Adapter()
    /** Configuring a one-app [BelleAppWidget] rather than the 4-slot bar. */
    private val single by lazy {
        AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className == BelleAppWidget::class.java.name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        BelleShortcutsWidget.slots(this, widgetId).forEachIndexed { i, c -> slots[i] = c }
        selected = slots.indexOfFirst { it == null }.coerceAtLeast(0)

        val title = TextView(this).apply {
            text = "Shortcuts"
            setTextColor(N9.TEXT)
            textSize = 26f
            typeface = N9.FONT_LIGHT
            setPadding(dp(16f), dp(16f), dp(16f), dp(4f))
        }
        val hint = TextView(this).apply {
            text = "Tap a slot, then an application. Long-press a slot to empty it."
            setTextColor(N9.TEXT_SECONDARY)
            textSize = 13f
            typeface = N9.FONT
            setPadding(dp(16f), 0, dp(16f), dp(10f))
        }
        val slotBar = LinearLayout(this).apply { setPadding(dp(10f), 0, dp(10f), dp(10f)) }
        for (i in 0 until BelleShortcutsWidget.SLOTS) {
            val icon = ImageView(this)
            val label = TextView(this).apply {
                setTextColor(N9.TEXT); textSize = 11f; typeface = N9.FONT
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
                addView(icon, LinearLayout.LayoutParams(dp(44f), dp(44f)))
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                setOnClickListener { selected = i; bindSlots() }
                setOnLongClickListener { slots[i] = null; selected = i; bindSlots(); true }
            }
            slotViews += icon to label
            slotRows += cell
            slotBar.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4f), 0, dp(4f), 0)
            })
        }
        val divider = android.view.View(this).apply { setBackgroundColor(N9.DIVIDER) }
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@BelleShortcutsConfigActivity)
            adapter = this@BelleShortcutsConfigActivity.adapter
        }
        val done = TextView(this).apply {
            text = "Done"
            setTextColor(N9.TEXT)
            textSize = 17f
            typeface = N9.FONT
            gravity = Gravity.CENTER
            background = N9.button(context)
            setOnClickListener { save() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            fitsSystemWindows = true
            addView(title)
            addView(hint)
            addView(slotBar)
            if (single) { slotBar.visibility = View.GONE; hint.text = "Pick an application."; title.text = "Shortcut" }
            addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(done, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)).apply {
                setMargins(dp(16f), dp(8f), dp(16f), dp(16f))
            })
        }
        setContentView(root)
        bindSlots()
        AppRepository.loadAsync(this) { list ->
            apps = list.sortedBy { it.label.lowercase() }
            @Suppress("NotifyDataSetChanged")
            adapter.notifyDataSetChanged()
            bindSlots()
        }
    }

    private fun bindSlots() {
        slotViews.forEachIndexed { i, (icon, label) ->
            val cn = slots[i]
            val app = cn?.let { c -> apps.firstOrNull { it.component == c } }
            if (app != null) icon.setImageBitmap(app.icon) else icon.setImageResource(R.drawable.belle_add)
            label.text = app?.label ?: if (cn != null) cn.packageName else "Empty"
            slotRows[i].background = GradientDrawable().apply {
                cornerRadius = dp(8f).toFloat()
                setColor(if (i == selected) N9.ACCENT else N9.CARD)
            }
        }
    }

    private fun save() {
        BelleShortcutsWidget.save(this, widgetId, slots)
        if (single) BelleAppWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
        else BelleShortcutsWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            val row = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
                addView(ImageView(ctx), LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginEnd = dp(14f) })
                addView(TextView(ctx).apply {
                    setTextColor(N9.TEXT); textSize = 17f; typeface = N9.FONT
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val h = object : RecyclerView.ViewHolder(row) {}
            row.setOnClickListener {
                val app = apps.getOrNull(h.bindingAdapterPosition) ?: return@setOnClickListener
                slots[selected] = app.component
                // A single shortcut (Belle classic): the pick is the whole setup.
                if (single) { save(); return@setOnClickListener }
                selected = (selected + 1) % BelleShortcutsWidget.SLOTS
                bindSlots()
            }
            return h
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            val row = h.itemView as LinearLayout
            val app = apps[position]
            (row.getChildAt(0) as ImageView).setImageBitmap(app.icon)
            (row.getChildAt(1) as TextView).text = app.label
        }
    }
}
