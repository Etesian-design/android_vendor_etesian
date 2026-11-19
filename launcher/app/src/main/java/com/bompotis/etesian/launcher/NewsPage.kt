package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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

    private class Holder(val root: LinearLayout, val picture: Picture, val title: TextView, val meta: TextView) : RecyclerView.ViewHolder(root)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val title = TextView(context).apply { setTextColor(N9.TEXT); typeface = N9.FONT_BOLD; maxLines = 4; ellipsize = TextUtils.TruncateAt.END }
            val meta = TextView(context).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 13f; typeface = N9.FONT }
            val picture = Picture(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(picture, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                addView(title); addView(meta)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val h = Holder(root, picture, title, meta)
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
                    h.picture.visibility = View.GONE
                    h.title.setPadding(0, 0, 0, 0)
                    h.root.setPadding(dp(18f), dp(18f), dp(18f), dp(6f))
                    h.root.isClickable = false
                    h.root.contentDescription = null
                }
                is Glance.Story -> {
                    val first = position == 1
                    // As in Google News: the picture edge to edge, the headline and where it's from under it.
                    h.picture.visibility = View.VISIBLE
                    (h.picture.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(12f)
                    val tile = Tile(r.source.ifEmpty { r.title }, resources.displayMetrics.density)
                    if (r.image.isNotEmpty()) Pictures.load(r.image, h.picture, tile) else { h.picture.tag = null; h.picture.setImageDrawable(tile) }
                    h.title.text = r.title
                    h.title.setTextColor(N9.TEXT)
                    h.title.textSize = if (first) 21f else 18f
                    h.title.setPadding(dp(18f), 0, dp(18f), 0)
                    h.meta.setPadding(dp(18f), dp(6f), dp(18f), 0)
                    h.meta.text = listOfNotNull(r.source.takeIf { it.isNotEmpty() }, ago(r.time),
                        if (r.outlets > 1) "${r.outlets} outlets" else null).joinToString(" · ")
                    h.meta.visibility = View.VISIBLE
                    h.root.setPadding(0, dp(8f), 0, dp(16f))
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

/** A story's picture: the width of the view, 16:9, cropped to fill. */
private class Picture(ctx: Context) : ImageView(ctx) {
    init { scaleType = ScaleType.CENTER_CROP }
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, width * 9 / 16)
    }
}

/** No picture: a colour of the outlet's own (the same each time, as in Glance) with its name. */
private class Tile(private val name: String, private val density: Float) : android.graphics.drawable.Drawable() {
    private val hue = ((name.hashCode() and 0x7fffffff) % 360).toFloat()
    private val bg = android.graphics.Paint()
    private val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt(); typeface = N9.FONT_BOLD; textAlign = android.graphics.Paint.Align.CENTER
    }
    override fun draw(c: android.graphics.Canvas) {
        val b = bounds
        bg.shader = android.graphics.LinearGradient(0f, b.top.toFloat(), b.width().toFloat(), b.bottom.toFloat(),
            android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.55f)),
            android.graphics.Color.HSVToColor(floatArrayOf((hue + 40) % 360, 0.6f, 0.3f)), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(b, bg)
        ink.textSize = 26 * density
        val label = TextUtils.ellipsize(name, android.text.TextPaint(ink), b.width() - 48 * density, TextUtils.TruncateAt.END).toString()
        c.drawText(label, b.exactCenterX(), b.exactCenterY() - (ink.descent() + ink.ascent()) / 2, ink)
    }
    override fun setAlpha(a: Int) { ink.alpha = a }
    override fun setColorFilter(f: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}

/** Story pictures from the web, kept in memory while the launcher runs. */
private object Pictures {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun load(url: String, into: ImageView, meanwhile: android.graphics.drawable.Drawable) {
        into.tag = url
        cache.get(url)?.let { into.setImageBitmap(it); return }
        into.setImageDrawable(meanwhile)
        pool.execute {
            val bmp = runCatching {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                try {
                    val bytes = c.inputStream.readBytes()
                    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    var sample = 1
                    while (o.outWidth / (sample * 2) >= 1080) sample *= 2
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 })
                } finally { c.disconnect() }
            }.getOrNull() ?: return@execute
            cache.put(url, bmp)
            main.post { if (into.tag == url) into.setImageBitmap(bmp) }
        }
    }
}
