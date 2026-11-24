package com.bompotis.etesian.feeds

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * Find feeds: search the feed lists (Directory) by name, site or topic, and subscribe with a tap. The lists are
 * downloaded on the first search only, and searched on the phone after that.
 */
class FindActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var field: android.widget.EditText
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var downloading = false
    private val search = Runnable { run() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.PAGE) }
        val header = Ui.header(this, "Find feeds", brand = false)
        root.addView(header)
        field = Ui.field(this, "Name, site or topic", uri = false).apply { contentDescription = "Find feeds" }
        root.addView(Ui.wrap(this, field))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list); clipToPadding = false }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = Ui.toolbar(this, root, back = true, actions = emptyList()) {}
        setContentView(root)
        Ui.edgeToEdge(this, root, header, bar ?: scroll)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { main.removeCallbacks(search); main.postDelayed(search, 300) }
        })
        intro()
    }

    private fun message(text: String) {
        list.removeAllViews()
        list.addView(Ui.text(this, 17f, Ui.SECONDARY).apply {
            maxLines = 8; this.text = text; setPadding(dp(16f), dp(20f), dp(16f), dp(12f)); contentDescription = "find:status"
        })
    }

    private fun intro() {
        message(if (Directory.ready(this)) "Type to search about 60,000 feeds."
        else "The feed lists are downloaded the first time you search (about 13 MB), then searched on this phone.")
        credits()
    }

    private fun credits() {
        list.addView(Ui.text(this, 13f, Ui.SECONDARY).apply {
            maxLines = 6
            text = "Lists: " + Directory.SOURCES.joinToString("; ") { "${it.name} (${it.licence})" }
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        })
    }

    private fun run() {
        val q = field.text.toString().trim()
        if (q.isEmpty()) { intro(); return }
        if (!Directory.ready(this) || (Directory.stale(this) && !downloading)) {
            if (!Directory.ready(this)) { fetchLists(); return }
            // A month old: searched as they are, fetched again behind.
            downloading = true
            Thread { runCatching { Directory.download(applicationContext) {} }; downloading = false }.start()
        }
        val app = applicationContext
        Thread {
            val results = Directory.search(app, q)
            runOnUiThread { if (q == field.text.toString().trim()) show(results) }
        }.start()
    }

    private fun fetchLists() {
        if (downloading) return
        downloading = true
        message("Downloading the feed lists…")
        val app = applicationContext
        Thread {
            val error = runCatching { Directory.download(app) { name -> runOnUiThread { message("Downloading $name…") } } }.exceptionOrNull()
            runOnUiThread {
                downloading = false
                if (error != null) { message("Couldn't download the feed lists. Check the connection and try again."); return@runOnUiThread }
                run()
            }
        }.start()
    }

    private fun show(results: List<Directory.Result>) {
        list.removeAllViews()
        if (results.isEmpty()) { message("No feeds found."); credits(); return }
        val mine = Feeds.feeds(this).map { it.url }.toSet()
        for (r in results) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(80f)
                setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
                background = getDrawable(android.R.drawable.list_selector_background)
                addView(Ui.text(this@FindActivity, 19f, Ui.INK, Ui.FONT_BOLD).apply { text = r.title; maxLines = 2 })
                if (r.about.isNotEmpty()) addView(Ui.text(this@FindActivity, 15f, Ui.SECONDARY).apply { text = r.about; maxLines = 2 })
                val status = Ui.text(this@FindActivity, 14f, Ui.DATE).apply {
                    text = if (r.url in mine) "Subscribed" else r.url; maxLines = 1
                }
                addView(status)
                setOnClickListener {
                    if (r.url in Feeds.feeds(this@FindActivity).map { it.url }) return@setOnClickListener
                    status.text = "Adding…"
                    Feeds.add(this@FindActivity, r.url) { error ->
                        status.text = if (error == null) "Subscribed" else r.url
                        if (error != null) Toast.makeText(this@FindActivity, error, Toast.LENGTH_LONG).show()
                        else FeedsJob.schedule(this@FindActivity)
                    }
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
            list.addView(View(this).apply { setBackgroundColor(Ui.RULE) }, LinearLayout.LayoutParams(-1, 1).apply {
                marginStart = dp(16f); marginEnd = dp(16f) })
        }
        credits()
    }
}
