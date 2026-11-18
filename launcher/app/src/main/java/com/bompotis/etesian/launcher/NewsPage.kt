package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * Glance's news as a home screen (Settings → Views → News), left of the others: its search bar on top (opens Glance
 * with the keyboard up, searching everything), then Top stories and For you as Glance ranked them. Tapping a story
 * opens it (Glance learns from it); a long press offers "Fewer like this".
 */
class NewsPage(context: Context) : FrameLayout(context), HomePage {

    private val list = RecyclerView(context)
    private val adapter = Adapter()
    private var stories: List<Glance.Story> = emptyList()
    private var observer: ContentObserver? = null
    private val io = Executors.newSingleThreadExecutor()

    private val searchBar = TextView(context).apply {
        text = "Search your phone and the web"
        setTextColor(N9.TEXT_SECONDARY)
        textSize = 17f
        typeface = N9.FONT
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
        background = FieldDrawable(intArrayOf(0xFF262626.toInt()), 0xFF393839.toInt(), resources.displayMetrics.density)
        contentDescription = "news:search"
        setOnClickListener { Glance.launch(context, search = true) }
    }

    init {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.clipToPadding = false
        list.overScrollMode = OVER_SCROLL_NEVER
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(48f)))
        contentDescription = "news"
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observer = Glance.observe(context) { load() }
        load()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    override fun onShown() {
        // Fresh news when the view comes up (Glance skips it if it fetched in the last few minutes).
        io.execute { Glance.refresh(context, force = false) }
    }

    private fun load() {
        val ctx = context.applicationContext
        io.execute {
            val s = Glance.stories(ctx)
            post {
                stories = s
                @SuppressLint("NotifyDataSetChanged")
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun applyInsets(left: Int, top: Int, right: Int, bottom: Int) {
        (searchBar.layoutParams as LayoutParams).apply {
            topMargin = top
            leftMargin = left + dp(16f)
            rightMargin = right + dp(16f)
        }
        searchBar.requestLayout()
        list.setPadding(left, top + dp(60f), right, bottom + dp(16f))
    }

    override fun exitModes() = false

    /** Rows: a heading where the section changes, then the stories. */
    private val rows: List<Any> get() = buildList {
        if (stories.isEmpty()) add("Getting the news…")
        var section = ""
        stories.forEach { s ->
            if (s.section != section) {
                section = s.section
                add(if (section == "TOP") "TOP STORIES" else "FOR YOU")
            }
            add(s)
        }
    }

    private class Holder(val root: LinearLayout, val title: TextView, val meta: TextView) : RecyclerView.ViewHolder(root)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val title = TextView(context).apply { setTextColor(N9.TEXT); typeface = N9.FONT_BOLD; maxLines = 4; ellipsize = TextUtils.TruncateAt.END }
            val meta = TextView(context).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 13f; typeface = N9.FONT }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title); addView(meta)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val h = Holder(root, title, meta)
            root.setOnClickListener { (rows.getOrNull(h.bindingAdapterPosition) as? Glance.Story)?.let { Glance.open(context, it.open) } }
            root.setOnLongClickListener { v ->
                val s = rows.getOrNull(h.bindingAdapterPosition) as? Glance.Story ?: return@setOnLongClickListener false
                N9Sounds.longPress(v)
                PopupMenu(context, v).apply {
                    menu.add("Fewer like this")
                    setOnMenuItemClickListener { io.execute { Glance.fewerLike(context, s.link) }; true }
                    show()
                }
                true
            }
            return h
        }

        override fun onBindViewHolder(h: Holder, position: Int) {
            when (val r = rows[position]) {
                is String -> {
                    h.title.text = r
                    h.title.setTextColor(N9.ACCENT)
                    h.title.textSize = 13f
                    h.meta.visibility = View.GONE
                    h.root.setPadding(dp(18f), dp(18f), dp(18f), dp(6f))
                    h.root.isClickable = false
                    h.root.contentDescription = null
                }
                is Glance.Story -> {
                    val first = position == 1
                    h.title.text = r.title
                    h.title.setTextColor(N9.TEXT)
                    h.title.textSize = if (first) 21f else 16.5f
                    h.meta.text = listOfNotNull(r.source.takeIf { it.isNotEmpty() }, ago(r.time),
                        if (r.outlets > 1) "${r.outlets} outlets" else null).joinToString(" · ")
                    h.meta.visibility = View.VISIBLE
                    h.root.setPadding(dp(18f), dp(10f), dp(18f), dp(12f))
                    h.root.isClickable = true
                    h.root.contentDescription = "story:${r.title}"
                }
            }
        }
    }

    private fun ago(t: Long): String? {
        if (t <= 0) return null
        val m = (System.currentTimeMillis() - t) / 60_000
        return when { m < 1 -> "Now"; m < 60 -> "$m min"; m < 1440 -> "${m / 60} h"; else -> "${m / 1440} d" }
    }
}
