package com.bompotis.etesian.store.ui

import android.app.Activity
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bompotis.etesian.store.R
import com.bompotis.etesian.store.StoreApplication
import com.bompotis.etesian.store.data.FdroidApp

sealed class Item {
    class Section(val title: String) : Item()
    class App(val app: FdroidApp) : Item()
    class Category(val id: String, val name: String, val count: Int) : Item()
    class Note(val text: String) : Item()
}

/** Flat, full-width N9 list: section headers, app rows, category rows, notes. */
class AppAdapter(private val activity: Activity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var items: List<Item> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val images = StoreApplication.get(activity).images
    private val iconPx = Ui.dp(activity, 56)

    override fun getItemCount() = items.size

    /** First letter of an app / category row for the fast scroller ("#" for non-letters). */
    fun sectionFor(position: Int): String? {
        val name = when (val item = items.getOrNull(position)) {
            is Item.App -> item.app.name
            is Item.Category -> item.name
            else -> return null
        }
        val c = name.trimStart().firstOrNull()?.uppercaseChar() ?: return null
        return if (c.isLetter()) c.toString() else "#"
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.Section -> 0
        is Item.App -> 1
        is Item.Category -> 2
        is Item.Note -> 3
    }

    private class AppHolder(
        view: View, val icon: ImageView, val name: TextView, val summary: TextView, val button: TextView,
    ) : RecyclerView.ViewHolder(view)

    private class TextHolder(view: View, val title: TextView, val detail: TextView?) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val d = { v: Int -> Ui.dp(ctx, v) }
        return when (viewType) {
            0 -> {
                val t = Ui.text(ctx, 14f, Ui.BLUE).apply {
                    setPadding(d(16), d(18), d(16), d(6))
                    isAllCaps = false
                }
                t.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                TextHolder(t, t, null)
            }
            1 -> {
                val col = column(ctx)
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(d(16), d(10), d(12), d(10))
                    setBackgroundResource(R.drawable.row_bg)
                }
                val icon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                Ui.squircle(icon)
                row.addView(icon, LinearLayout.LayoutParams(iconPx, iconPx))
                val texts = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(d(14), 0, d(10), 0)
                }
                val name = Ui.text(ctx, 18f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
                val summary = Ui.text(ctx, 14f, Ui.SECONDARY).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
                texts.addView(name)
                texts.addView(summary)
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val button = Ui.button(ctx)
                row.addView(button)
                col.addView(row)
                col.addView(Ui.separator(ctx))
                AppHolder(col, icon, name, summary, button)
            }
            else -> {
                val col = column(ctx)
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(d(16), d(16), d(16), d(16))
                    if (viewType == 2) setBackgroundResource(R.drawable.row_bg) else setBackgroundColor(Ui.LIST)
                }
                val title = Ui.text(ctx, if (viewType == 2) 18f else 15f, if (viewType == 2) Ui.TEXT else Ui.SECONDARY)
                val detail = Ui.text(ctx, 14f, Ui.SECONDARY)
                row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(detail)
                col.addView(row)
                col.addView(Ui.separator(ctx))
                TextHolder(col, title, detail)
            }
        }
    }

    private fun column(ctx: android.content.Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Section -> (holder as TextHolder).title.text = item.title
            is Item.Note -> {
                holder as TextHolder
                holder.title.text = item.text
                holder.detail?.text = ""
            }
            is Item.Category -> {
                holder as TextHolder
                holder.title.text = item.name
                holder.detail?.text = item.count.toString()
                holder.itemView.setOnClickListener { CategoryActivity.start(activity, item.id, item.name) }
            }
            is Item.App -> {
                holder as AppHolder
                val app = item.app
                holder.name.text = app.name
                holder.summary.text = app.summary
                images.load(holder.icon, app.icon?.let { RepositoryUrls.file(it) }, iconPx)
                AppActions.bind(holder.button, app)
                holder.button.setOnClickListener { AppActions.perform(activity, app) }
                holder.itemView.setOnClickListener { AppActivity.start(activity, app.packageName) }
            }
        }
    }
}

object RepositoryUrls {
    fun file(path: String) = com.bompotis.etesian.store.data.Repository.BASE + path
}
