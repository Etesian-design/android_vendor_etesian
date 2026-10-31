package com.bompotis.etesian.launcher

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

/**
 * Full-screen widget picker: widgets grouped per app (all expanded; tap an app to fold it),
 * each shown with its preview, name, grid size and description.
 */
class WidgetPicker(context: Context, private val onPicked: (AppWidgetProviderInfo) -> Unit) :
    Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar) {

    private class Group(val pkg: String, val label: String, val widgets: List<AppWidgetProviderInfo>)

    private sealed class Row {
        class App(val group: Group, val open: Boolean) : Row()
        class Widget(val info: AppWidgetProviderInfo) : Row()
    }

    private val pm = context.packageManager
    private val groups: List<Group>
    private val expanded = mutableSetOf<String>()
    private var rows: List<Row> = emptyList()
    private val adapter = Adapter()

    init {
        groups = AppWidgetManager.getInstance(context).installedProviders
            .groupBy { it.provider.packageName }
            .map { (pkg, list) -> Group(pkg, appLabel(pkg), list.sortedBy { it.loadLabel(pm).lowercase() }) }
            .sortedBy { it.label.lowercase() }
        // Opens with every app expanded; tapping an app folds it.
        expanded += groups.map { it.pkg }
        rebuild()

        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WidgetPicker.adapter
            clipToPadding = false
            setPadding(0, 0, 0, context.dp(24f))
        }
        val title = TextView(context).apply {
            text = "Add widget"
            setTextColor(N9.TEXT)
            textSize = 26f
            typeface = N9.FONT_LIGHT
        }
        val close = TextView(context).apply {
            text = "✕"
            setTextColor(N9.TEXT)
            textSize = 20f
            gravity = Gravity.CENTER
            setOnClickListener { dismiss() }
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(16f), context.dp(16f), context.dp(8f), context.dp(12f))
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(close, LinearLayout.LayoutParams(context.dp(48f), context.dp(48f)))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            addView(header)
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    /**
     * The picker is a window of its own over home: left open through a lock it would cover home
     * (black) during the N9 unlock, which zooms home's content only. Close it when the screen goes off.
     */
    private val screenOff = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: android.content.Intent) { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        context.registerReceiver(screenOff, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
            Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        runCatching { context.unregisterReceiver(screenOff) }
        super.onStop()
    }

    private fun appLabel(pkg: String) =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun rebuild() {
        rows = groups.flatMap { g ->
            val open = g.pkg in expanded
            listOf<Row>(Row.App(g, open)) + if (open) g.widgets.map { Row.Widget(it) } else emptyList()
        }
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    /** Grid size in launcher cells, the way Android's own picker labels it. */
    private fun cells(info: AppWidgetProviderInfo): String {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return "${info.targetCellWidth} × ${info.targetCellHeight}"
        }
        val density = context.resources.displayMetrics.density
        fun n(px: Int) = max(1, ((px / density) + 30).toInt() / 70)
        return "${n(info.minWidth)} × ${n(info.minHeight)}"
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.App) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = if (viewType == 0) AppRowView(parent.context) else WidgetCard(parent.context)
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.App -> (h.itemView as AppRowView).bind(row)
                is Row.Widget -> (h.itemView as WidgetCard).bind(row.info)
            }
        }
    }

    private inner class AppRowView(ctx: Context) : LinearLayout(ctx) {
        private val icon = ImageView(ctx)
        private val name = TextView(ctx).apply {
            setTextColor(N9.TEXT); textSize = 17f; typeface = N9.FONT
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val count = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 13f; typeface = N9.FONT }
        private val chevron = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 16f }
        private var pkg = ""

        init {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(20f), dp(10f))
            addView(icon, LayoutParams(dp(44f), dp(44f)).apply { marginEnd = dp(14f) })
            addView(LinearLayout(ctx).apply {
                orientation = VERTICAL
                addView(name)
                addView(count)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(chevron)
            setOnClickListener {
                if (!expanded.remove(pkg)) expanded += pkg
                rebuild()
            }
        }

        fun bind(row: Row.App) {
            pkg = row.group.pkg
            icon.setImageBitmap(AppRepository.iconFor(context, pkg))
            name.text = row.group.label
            val n = row.group.widgets.size
            count.text = if (n == 1) "1 widget" else "$n widgets"
            chevron.text = if (row.open) "▲" else "▼"
        }
    }

    /** One widget: live preview (or preview image), then name, size and description. */
    private inner class WidgetCard(ctx: Context) : LinearLayout(ctx) {
        private val preview = FrameLayout(ctx).apply {
            minimumHeight = dp(72f)
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(0xFF2A2A2A.toInt()) }
        }
        private val name = TextView(ctx).apply { setTextColor(N9.TEXT); textSize = 15f; typeface = N9.FONT_BOLD }
        private val size = TextView(ctx).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 13f; typeface = N9.FONT }
        private val description = TextView(ctx).apply {
            setTextColor(N9.TEXT_SECONDARY); textSize = 13f; typeface = N9.FONT
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        private var info: AppWidgetProviderInfo? = null

        init {
            orientation = VERTICAL
            setPadding(dp(74f), dp(4f), dp(16f), dp(16f))
            addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2f), dp(8f), dp(2f), 0)
                addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(size)
            })
            addView(description.apply { setPadding(dp(2f), dp(2f), dp(2f), 0) })
            setOnClickListener { info?.let { dismiss(); onPicked(it) } }
        }

        fun bind(i: AppWidgetProviderInfo) {
            info = i
            name.text = i.loadLabel(pm)
            size.text = cells(i)
            val desc = if (Build.VERSION.SDK_INT >= 31) i.loadDescription(context) else null
            description.text = desc
            description.visibility = if (desc.isNullOrEmpty()) GONE else VISIBLE
            preview.removeAllViews()
            preview.addView(previewView(i), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

        private fun previewView(i: AppWidgetProviderInfo): View {
            // Android 12+: the app's own scalable preview layout, rendered like the real widget.
            if (Build.VERSION.SDK_INT >= 31 && i.previewLayout != 0) {
                runCatching {
                    return RemoteViews(i.provider.packageName, i.previewLayout).apply(context, preview)
                }
            }
            val drawable = runCatching { i.loadPreviewImage(context, 0) }.getOrNull()
                ?: runCatching { i.loadIcon(context, 0) }.getOrNull()
            return ImageView(context).apply {
                setImageDrawable(drawable)
                adjustViewBounds = true
                maxHeight = dp(180f)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
    }
}
