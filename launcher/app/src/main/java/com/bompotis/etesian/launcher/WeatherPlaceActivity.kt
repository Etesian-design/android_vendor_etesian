package com.bompotis.etesian.launcher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Belle Weather's place picker: tapping the place on a weather widget. Type a town, pick it from the matches, or go back to
 * the phone's own location. The place is shared by every weather surface (both widgets, the Events header, the lock screen).
 */
class WeatherPlaceActivity : Activity() {
    private lateinit var list: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var asked = 0
    private val search = Runnable { find() }
    private lateinit var field: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(N9.BACKGROUND)
            setPadding(dp(16f), dp(40f), dp(16f), dp(16f))
        }
        root.addView(TextView(this).apply {
            text = "Place"; setTextColor(N9.TEXT); textSize = 26f; typeface = N9.FONT_LIGHT; setPadding(0, 0, 0, dp(12f))
        })
        field = EditText(this).apply {
            hint = "Town or city"; setSingleLine(true); textSize = 19f; typeface = N9.FONT
            setTextColor(N9.TEXT); setHintTextColor(N9.TEXT_SECONDARY)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setText(Prefs.weatherCity(this@WeatherPlaceActivity))
            setOnEditorActionListener { _, _, _ -> main.removeCallbacks(search); find(); true }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { main.removeCallbacks(search); main.postDelayed(search, 400) }
            })
        }
        root.addView(field)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        fill(null, null)
    }

    override fun onDestroy() { main.removeCallbacks(search); io.shutdownNow(); super.onDestroy() }

    private fun find() {
        val q = field.text.toString().trim()
        val n = ++asked
        if (q.length < 2) { fill(null, null); return }
        fill(null, "Searching…")
        io.execute {
            val found = runCatching { Weather.search(q) }
            main.post {
                if (n != asked || isDestroyed) return@post
                found.fold({ fill(it, if (it.isEmpty()) "No places called “$q”" else null) }, { fill(null, "Can't search now. Check the connection.") })
            }
        }
    }

    private fun fill(places: List<Weather.Place>?, note: String?) {
        list.removeAllViews()
        row("Current location", "Where the phone is", Prefs.weatherCity(this).isBlank()) { Weather.choose(this, null); finish() }
        note?.let {
            list.addView(TextView(this).apply {
                text = it; setTextColor(N9.TEXT_SECONDARY); textSize = 17f; typeface = N9.FONT
                minHeight = dp(56f); gravity = Gravity.CENTER_VERTICAL
            })
        }
        places?.forEach { p -> row(p.name, p.detail, false) { Weather.choose(this, p); finish() } }
    }

    private fun row(title: String, sub: String, chosen: Boolean, tap: () -> Unit) {
        list.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(64f)
            setOnClickListener { tap() }
            addView(TextView(context).apply {
                text = title; textSize = 19f; typeface = N9.FONT; setTextColor(if (chosen) N9.ACCENT else N9.TEXT)
            })
            if (sub.isNotBlank()) addView(TextView(context).apply {
                text = sub; textSize = 15f; typeface = N9.FONT; setTextColor(N9.TEXT_SECONDARY)
            })
        })
    }
}
