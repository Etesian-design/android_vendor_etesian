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

    // Searching from this page happens here (2026-11-20, user: "when tapping the search bar, the actual glance app
    // opens instead of getting the keyboard"): the stories make way for Glance's results, each source as it answers.
    private val searchBar = android.widget.EditText(context).apply {
        hint = "Search your phone and the web"
        setHintTextColor(N9.TEXT_SECONDARY)
        setTextColor(N9.TEXT)
        textSize = 17f
        typeface = N9.FONT
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
        background = FieldDrawable(intArrayOf(0xFF262626.toInt()), 0xFF393839.toInt(), resources.displayMetrics.density)
        contentDescription = "news:search"
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = search(s?.toString().orEmpty().trim())
        })
        setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
    }
    private var query = ""
    private var results: List<Glance.Result> = emptyList()
    private var searchSeq = 0
    private val searchIo = Executors.newCachedThreadPool()

    /** Each of Glance's sources on its own, all at once; sections keep a fixed order, so nothing jumps. */
    private fun search(q: String) {
        val seq = ++searchSeq
        query = q
        results = emptyList()
        refreshRows()
        if (q.isEmpty()) return
        val ctx = context.applicationContext
        val kinds = listOf("APP") + Glance.KINDS
        val got = HashMap<String, List<Glance.Result>>()
        postDelayed({
            if (seq != searchSeq) return@postDelayed
            kinds.forEach { kind ->
                searchIo.execute {
                    if (seq != searchSeq) return@execute
                    val answer = Glance.search(ctx, q, kinds = listOf(kind), apps = true)
                    post {
                        if (seq != searchSeq) return@post
                        if (answer.all) { if (got.isNotEmpty()) return@post; kinds.forEach { got[it] = emptyList() } }
                        got[kind] = answer.results
                        results = kinds.flatMap { got[it].orEmpty() }
                        refreshRows()
                    }
                }
            }
        }, 120)
    }

    private fun refreshRows() {
        @SuppressLint("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    private fun hideKeyboard() {
        context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(searchBar.windowToken, 0)
    }

    /** Leaves the search: the stories again, the keyboard down. */
    private fun closeSearch() {
        hideKeyboard()
        searchBar.setText("")
        searchBar.clearFocus()
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

    override fun exitModes(): Boolean {
        if (query.isEmpty() && !searchBar.hasFocus()) return false
        closeSearch()
        return true
    }

    /** Rows: a heading where the section changes, then the stories. */
    private val rows: List<Any> get() = if (query.isNotEmpty()) buildList {
        var section = ""
        results.forEach { r ->
            if (r.section != section) { section = r.section; add(section.uppercase()) }
            add(r)
        }
    } else buildList {
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

    private class Holder(val root: LinearLayout, val picture: Picture, val icon: ImageView, val title: TextView, val meta: TextView) :
        RecyclerView.ViewHolder(root)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val title = TextView(context).apply { setTextColor(N9.TEXT); typeface = N9.FONT_BOLD; maxLines = 4; ellipsize = TextUtils.TruncateAt.END }
            val meta = TextView(context).apply { setTextColor(N9.TEXT_SECONDARY); textSize = 13f; typeface = N9.FONT }
            val picture = Picture(context)
            val icon = ImageView(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(picture, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                addView(icon, LinearLayout.LayoutParams(dp(40f), dp(40f)))
                addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(title); addView(meta) },
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val h = Holder(root, picture, icon, title, meta)
            root.setOnClickListener {
                when (val r = rows.getOrNull(h.bindingAdapterPosition)) {
                    is Glance.Story -> Glance.open(context, r.open)
                    is Glance.Result -> if (Glance.open(context, r.open)) closeSearch()
                }
            }
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
            val r = rows[position]
            // A search result is one row: the icon beside its title and subtitle; stories and headings stack.
            h.root.orientation = if (r is Glance.Result) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            h.icon.visibility = if (r is Glance.Result) View.VISIBLE else View.GONE
            when (r) {
                is Glance.Result -> {
                    h.picture.visibility = View.GONE
                    h.icon.setImageDrawable(r.pkg.takeIf { it.isNotEmpty() }
                        ?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }
                        ?: context.getDrawable(android.R.drawable.ic_menu_search))
                    (h.icon.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(14f)
                    h.title.text = r.title
                    h.title.setTextColor(N9.TEXT)
                    h.title.textSize = 16f
                    h.title.setPadding(0, 0, 0, 0)
                    h.meta.text = r.subtitle
                    h.meta.visibility = if (r.subtitle.isEmpty()) View.GONE else View.VISIBLE
                    h.meta.setPadding(0, dp(2f), 0, 0)
                    h.root.gravity = Gravity.CENTER_VERTICAL
                    h.root.setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
                    h.root.isClickable = true
                    h.root.contentDescription = "glance:${r.kind.lowercase()}:${r.title}"
                }
                is String -> {
                    h.root.gravity = Gravity.NO_GRAVITY
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
                    h.root.gravity = Gravity.NO_GRAVITY
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
