package com.bompotis.etesian.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.preference.PreferenceManager

/**
 * Android extras → "Share filter and pinned targets" (mocks 11, 11b, 11c): the share chooser as
 * the N9 menu sheet. A white Filter field (word-start matching), Pinned targets (long-press a
 * target to pin or unpin), then every app as plain bold rows with squircle icons. Optional
 * (Android extras → "Suggested people in share"): Android's direct-share people, hideable.
 * Enabled as the system chooser (ACTION_CHOOSER, priority above IntentResolver) only while the
 * switch is on: [sync] enables or disables the ShareChooser alias, so off = stock IntentResolver.
 */
class ShareActivity : Activity() {
    private class Target(val ri: ResolveInfo, val label: String, val cn: ComponentName)
    private class Person(val label: String, val icon: Bitmap?, val shortcutId: String, val cn: ComponentName)

    private lateinit var target: Intent
    private lateinit var list: LinearLayout
    private var targets: List<Target> = emptyList()
    private var people: List<Person> = emptyList()
    private var query = ""
    private val iconCache = HashMap<ComponentName, Bitmap?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val t = when (val x = runCatching { intent.extras?.get(Intent.EXTRA_INTENT) }.getOrNull()) {
            is Intent -> x
            // From a shell (am start --eu): an intent: URI.
            is android.net.Uri, is String -> runCatching { Intent.parseUri(x.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
            else -> null
        } ?: run { finish(); return }
        target = t
        if (!Extras.on(this, Extras.SHARE)) { forwardToStock(); return }
        val excluded = runCatching {
            @Suppress("DEPRECATION") intent.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)?.map { it as ComponentName }
        }.getOrNull().orEmpty().toSet()
        targets = packageManager.queryIntentActivities(target, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName != packageName }
            .map { Target(it, it.loadLabel(packageManager).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
            .filter { it.cn !in excluded }
            .distinctBy { it.cn }
            .sortedBy { it.label.lowercase() }
        if (Extras.on(this, Extras.SHARE_PEOPLE) && !prefs().getBoolean(PEOPLE_HIDDEN, false)) people = loadPeople()
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        setContentView(build())
        fill()
    }

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(this)
    private fun pins(): List<String> = prefs().getString(PINS, "").orEmpty().split('\n').filter { it.isNotEmpty() }
    private fun setPins(p: List<String>) = prefs().edit().putString(PINS, p.distinct().joinToString("\n")).apply()

    private fun build(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(PANEL); cornerRadius = dp(8f).toFloat(); setStroke(dp(1f), LINE) }
            clipToOutline = true
            isClickable = true
            contentDescription = "Share"
        }
        // The N9 Filter field: white, magnifier, clear ✕.
        val field = EditText(this).apply {
            hint = "Filter"; setHintTextColor(0xFF8C8C8C.toInt()); setTextColor(TEXT); textSize = 18f; typeface = N9.FONT_LIGHT
            background = null; isSingleLine = true; contentDescription = "Filter"
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        val clear = ImageView(this).apply {
            setImageDrawable(Glyphs.drawable("close", 0xFF6E6E6E.toInt())); contentDescription = "Clear filter"; visibility = View.GONE
            setOnClickListener { field.setText("") }
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty(); clear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE; fill()
            }
        })
        panel.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(0xFFFFFFFF.toInt()); cornerRadius = dp(6f).toFloat(); setStroke(dp(1.5f), 0xFFCFCFD3.toInt()) }
            setPadding(dp(12f), dp(4f), dp(8f), dp(4f))
            addView(ImageView(context).apply { setImageDrawable(Glyphs.drawable("search", 0xFF4A4A4A.toInt())) }, LinearLayout.LayoutParams(dp(24f), dp(24f)))
            addView(field, LinearLayout.LayoutParams(0, dp(44f), 1f))
            addView(clear, LinearLayout.LayoutParams(dp(22f), dp(22f)))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16f), dp(14f), dp(16f), dp(8f))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(this).apply { addView(list); isVerticalScrollBarEnabled = true },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return FrameLayout(this).apply {
            // Mock 11: the app's header stays clear (52 px), the rest dims 45%; the card starts
            // 64 px down with 11 px sides (mock px x1.36 = dp).
            val dim = android.graphics.drawable.InsetDrawable(android.graphics.drawable.ColorDrawable(0x73000000), 0)
            background = dim
            setOnClickListener { finish() }
            addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(15f), 0, dp(15f), dp(15f))
            })
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                v.background = android.graphics.drawable.InsetDrawable(android.graphics.drawable.ColorDrawable(0x73000000), 0, b.top + dp(71f), 0, 0)
                v.setPadding(b.left, b.top + dp(87f), b.right, b.bottom); ins
            }
        }
    }

    /** Word-start matching: "n" finds Nearby and Google Notes, not Gallery. */
    private fun matches(label: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return label.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(q) } || label.lowercase().startsWith(q)
    }

    private fun fill() {
        list.removeAllViews()
        val pinned = pins()
        if (query.isNotEmpty()) {
            val hits = targets.filter { matches(it.label) }
            if (hits.isEmpty()) list.addView(header("No matches"))
            hits.forEachIndexed { i, t -> list.addView(row(t, t.cn.flattenToString() in pinned, i > 0)) }
            return
        }
        if (people.isNotEmpty()) {
            list.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(header("Suggested people"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = "Hide"; setTextColor(0xFF0B6BC8.toInt()); textSize = 16f; typeface = N9.FONT
                    setPadding(dp(16f), dp(12f), dp(16f), dp(4f))
                    setOnClickListener { prefs().edit().putBoolean(PEOPLE_HIDDEN, true).apply(); people = emptyList(); fill() }
                })
            })
            list.addView(LinearLayout(this).apply {
                setPadding(dp(16f), dp(6f), dp(16f), dp(12f))
                people.take(4).forEach { p ->
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                        contentDescription = p.label
                        addView(ImageView(context).apply { setImageBitmap(p.icon?.let { IconFactory.squircled(it, dp(56f)) }) }, LinearLayout.LayoutParams(dp(56f), dp(56f)))
                        addView(TextView(context).apply { text = p.label; setTextColor(0xFF2A2A2A.toInt()); textSize = 13f; typeface = N9.FONT_LIGHT; maxLines = 1 })
                        setOnClickListener { launchPerson(p) }
                    }, LinearLayout.LayoutParams(dp(72f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8f) })
                }
            })
            list.addView(line())
        }
        val pinnedTargets = pinned.mapNotNull { f -> targets.firstOrNull { it.cn.flattenToString() == f } }
        if (pinnedTargets.isNotEmpty()) {
            list.addView(header("Pinned"))
            pinnedTargets.forEachIndexed { i, t -> list.addView(row(t, true, i > 0)) }
        }
        list.addView(header("All apps"))
        targets.filter { it !in pinnedTargets }.forEachIndexed { i, t -> list.addView(row(t, false, i > 0)) }
    }

    private fun header(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF5A5A5E.toInt()); textSize = 18f; typeface = N9.FONT_BOLD
        setPadding(dp(22f), dp(11f), dp(22f), dp(3f))
    }

    private fun line() = View(this).apply {
        setBackgroundColor(LINE); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
    }

    private fun icon(t: Target): Bitmap? = iconCache.getOrPut(t.cn) {
        runCatching { IconFactory.render(t.ri.loadIcon(packageManager), dp(48f)) }.getOrNull()
    }

    private fun row(t: Target, pinned: Boolean, divider: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (divider) addView(line())
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(65f)
            setPadding(dp(22f), dp(4f), dp(22f), dp(4f))
            contentDescription = t.label
            addView(ImageView(context).apply { setImageBitmap(icon(t)) }, LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginEnd = dp(16f) })
            addView(TextView(context).apply { text = t.label; setTextColor(TEXT); textSize = 23f; typeface = N9.FONT_BOLD; maxLines = 1 },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (pinned) addView(TextView(context).apply { text = "☆"; setTextColor(0xFF4A4A4A.toInt()); textSize = 20f; contentDescription = "Pinned" })
            setOnClickListener { launch(t) }
            setOnLongClickListener {
                val f = t.cn.flattenToString()
                setPins(if (f in pins()) pins() - f else pins() + f)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                android.widget.Toast.makeText(context, if (f in pins()) "Pinned" else "Unpinned", android.widget.Toast.LENGTH_SHORT).show()
                fill(); true
            }
        })
    }

    private fun launch(t: Target) = start(Intent(target).setComponent(t.cn))

    private fun launchPerson(p: Person) = start(Intent(target).setComponent(p.cn).putExtra(Intent.EXTRA_SHORTCUT_ID, p.shortcutId))

    /** Starts [i] as the app that asked to share, so its URI grants go with it. */
    private fun start(i: Intent) {
        i.flags = i.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).inv()
        i.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT or Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
        val ok = runCatching {
            Activity::class.java.getMethod("startActivityAsCaller", Intent::class.java, Bundle::class.java,
                Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(this, i, null, false, android.os.Process.myUid() / 100000)
        }.isSuccess
        if (!ok) runCatching { startActivity(i) }
        finish()
    }

    /** Extra off (or not the chooser): hand the request to Android's own chooser unchanged. */
    private fun forwardToStock() {
        runCatching {
            startActivity(Intent(intent).setComponent(ComponentName("com.android.intentresolver",
                "com.android.intentresolver.ChooserActivity")).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT))
        }
        finish()
    }

    /** Android's direct-share people (sharing shortcuts) for this content. */
    private fun loadPeople(): List<Person> = runCatching {
        val sm = getSystemService(android.content.pm.ShortcutManager::class.java)
        val filter = android.content.IntentFilter(target.action).apply { target.type?.let { runCatching { addDataType(it) } } }
        @Suppress("UNCHECKED_CAST")
        val list = sm.javaClass.getMethod("getShareTargets", android.content.IntentFilter::class.java).invoke(sm, filter) as List<Any>
        list.mapNotNull { st ->
            val info = st.javaClass.getMethod("getShortcutInfo").invoke(st) as android.content.pm.ShortcutInfo
            val cn = st.javaClass.getMethod("getTargetComponent").invoke(st) as ComponentName
            val la = getSystemService(android.content.pm.LauncherApps::class.java)
            val d = runCatching { la.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi) }.getOrNull()
            Person(info.shortLabel?.toString() ?: return@mapNotNull null, d?.let { IconFactory.render(it, dp(56f)) }, info.id, cn)
        }.take(4)
    }.getOrDefault(emptyList())

    companion object {
        private const val PINS = "share_pins"
        private const val PEOPLE_HIDDEN = "share_people_hidden"
        private const val PANEL = 0xFFF4F3F6.toInt()
        private const val TEXT = 0xFF1A1A1A.toInt()
        private const val LINE = 0xFFD6D5D9.toInt()
        const val ALIAS = "com.bompotis.etesian.launcher.ShareChooser"

        /** Makes the N9 sheet the system chooser while the extra is on. */
        fun sync(ctx: Context) {
            val want = if (Extras.on(ctx, Extras.SHARE)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val cn = ComponentName(ctx.packageName, ALIAS)
            runCatching {
                if (ctx.packageManager.getComponentEnabledSetting(cn) != want)
                    ctx.packageManager.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
