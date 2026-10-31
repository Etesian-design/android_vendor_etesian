package com.bompotis.etesian.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.provider.Settings
import android.service.notification.Condition
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Android extras → "Do Not Disturb and Modes as profiles" (mocks 09, 10): Android's Do Not
 * Disturb and its Modes (AutomaticZenRule) as a second flat strip "Mode" under Profile. The
 * launcher is platform-signed, so MANAGE_NOTIFICATIONS lets it see and switch every mode.
 */
object Modes {
    class Mode(val id: String?, val name: String, val rule: AutomaticZenRule?)

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun rules(ctx: Context): Map<String, AutomaticZenRule> =
        runCatching { nm(ctx).automaticZenRules }.getOrNull().orEmpty().filter { (id, r) -> r.isEnabled && !id.startsWith("implicit_") }

    /** Removes the stale "Do Not Disturb (Etesian)" implicit mode an older build made with setInterruptionFilter. */
    fun removeStale(ctx: Context) = runCatching {
        // Implicit rules are not listed by getAutomaticZenRules(); remove ours by its fixed id.
        nm(ctx).removeAutomaticZenRule("implicit_" + ctx.packageName)
    }

    /** Off, Do Not Disturb, then the enabled modes (Sleeping, Driving...). */
    fun all(ctx: Context): List<Mode> = removeStale(ctx).let { listOf(Mode(OFF, "Off", null), Mode(DND, "Do Not Disturb", null)) +
        rules(ctx).entries.sortedBy { it.value.name?.toString() }.map { Mode(it.key, it.value.name?.toString() ?: "Mode", it.value) } }

    private fun ruleOn(ctx: Context, id: String) = runCatching { nm(ctx).getAutomaticZenRuleState(id) == Condition.STATE_TRUE }.getOrDefault(false)

    /** The active entry's id: a mode's rule id, [DND] for manual Do Not Disturb, else [OFF]. */
    fun active(ctx: Context): String {
        rules(ctx).keys.firstOrNull { ruleOn(ctx, it) }?.let { return it }
        return if (Settings.Global.getInt(ctx.contentResolver, "zen_mode", 0) != 0) DND else OFF
    }

    fun select(ctx: Context, id: String) {
        val n = nm(ctx)
        rules(ctx).forEach { (rid, r) ->
            val want = rid == id
            if (want != ruleOn(ctx, rid)) runCatching {
                n.setAutomaticZenRuleState(rid, Condition(r.conditionId, "", if (want) Condition.STATE_TRUE else Condition.STATE_FALSE,
                    Condition.SOURCE_USER_ACTION))
            }
        }
        // The real (manual) Do Not Disturb, as the system's own toggle sets it; setInterruptionFilter
        // would make a separate "implicit" mode owned by the launcher.
        runCatching {
            n.setZenMode(if (id == DND) Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS else Settings.Global.ZEN_MODE_OFF, null, "Etesian")
        }
    }

    const val OFF = "off"
    const val DND = "dnd"
}

/** The flat "Mode" strip, in the Profile strip's language; long-press a mode for its page. */
/** Mock px → dp (mocks are 360 px wide, the phone 491 dp). */
private const val M = 1.36f

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ModeStrip(ctx: Context, private val onLong: (Modes.Mode) -> Unit) : LinearLayout(ctx) {
    private val bar = object : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(11f * M).toFloat(); typeface = N9.FONT_BOLD; color = N9.TEXT }
        var modes: List<Modes.Mode> = emptyList()
        var active = Modes.OFF
        private val gap get() = dp(4f * M).toFloat()
        private var downAt = 0L

        /** Segments fill the width, each at least its label. */
        fun bounds(): List<Pair<Float, Float>> {
            // Mock 09: 3 px in, 8 px tail (18 when selected), the rest shared out.
            val labels = modes.map { text.measureText(it.name) + dp((if (it.id == active) 21f else 11f) * M) }
            val extra = ((width - gap * (modes.size - 1)) - labels.sum()).coerceAtLeast(0f) / modes.size.coerceAtLeast(1)
            var x = 0f
            return labels.map { w -> val s = x; x += w + extra + gap; s to (s + w + extra) }
        }

        fun at(x: Float) = bounds().indexOfFirst { x < it.second + gap }.let { if (it < 0) modes.lastIndex else it }

        private val longPress = Runnable { lastX?.let { x -> modes.getOrNull(at(x))?.let { performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); onLong(it) } }; lastX = null }
        private var lastX: Float? = null

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = e.x; downAt = e.eventTime; postDelayed(longPress, 500) }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPress)
                    if (lastX != null) modes.getOrNull(at(e.x))?.let { m -> m.id?.let { Modes.select(context, it) }; postDelayed({ refresh() }, 300); postDelayed({ refresh() }, 1200) }
                    lastX = null
                }
                MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); lastX = null }
            }
            return true
        }

        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            val ty = h / 2f - (text.descent() + text.ascent()) / 2
            bounds().forEachIndexed { i, (a, b) ->
                val sel = modes[i].id == active
                paint.color = if (sel) 0xFF4A9BFF.toInt() else 0xFF23436E.toInt()
                c.drawRect(a, 0f, b, h, paint)
                // Mock 09: unselected names in the dim blue-grey, as in the Profile strip.
                text.color = if (sel) N9.TEXT else 0xFF8A9BB4.toInt()
                c.drawText(modes[i].name, a + dp(3f * M), ty, text)
            }
        }
    }

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, dp(4f))
        addView(TextView(ctx).apply { text = "Mode"; setTextColor(N9.TEXT); textSize = 15f * M; typeface = N9.FONT; includeFontPadding = false; setPadding(0, dp(10f * M), 0, dp(4f * M)) })
        addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(17f * M)))
        contentDescription = "Mode"
        refresh()
    }

    fun refresh() {
        bar.modes = Modes.all(context)
        bar.active = Modes.active(context)
        bar.contentDescription = "Mode: " + (bar.modes.firstOrNull { it.id == bar.active }?.name ?: "Off")
        bar.invalidate()
    }
}

/**
 * A mode's page (mock 10), a plain N9 sub page: Schedule, People and Apps open Android's own mode
 * editor (an in-place editor would be too big); Dim the screen switches in place.
 */
class ModeSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID) ?: Modes.DND
        val nm = getSystemService(NotificationManager::class.java)
        val rule = if (id == Modes.DND || id == Modes.OFF) null else runCatching { nm.getAutomaticZenRule(id) }.getOrNull()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(20f), dp(16f), dp(16f)) }
        col.addView(TextView(this).apply {
            text = rule?.name ?: "Do Not Disturb"; setTextColor(N9.TEXT); textSize = 30f * M; typeface = N9.FONT_LIGHT
            setPadding(0, 0, 0, dp(8f * M))
        })
        // Mock 09b: a hairline under the page title.
        col.addView(View(this).apply { setBackgroundColor(0xFF5A5A5A.toInt()) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply { bottomMargin = dp(6f * M) })
        fun open() {
            val i = if (rule != null) Intent(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS).putExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID, id)
                else Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
            runCatching { startActivity(i) }
        }
        fun row(title: String, sub: String) = col.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(64f * M)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { text = title; setTextColor(N9.TEXT); textSize = 18f * M; typeface = N9.FONT_BOLD; includeFontPadding = false })
                addView(TextView(context).apply { text = sub; setTextColor(0xFFC8C8C8.toInt()); textSize = 15f * M; typeface = N9.FONT; setPadding(0, dp(3f * M), 0, 0) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            // Mock 09b: each row drills in, with a chevron.
            addView(android.widget.ImageView(context).apply {
                setImageDrawable(Glyphs.drawable("chevron", N9.TEXT)); rotation = -90f; contentDescription = "Open"
            }, LinearLayout.LayoutParams(dp(20f * M), dp(20f * M)).apply { marginEnd = dp(4f * M) })
            setOnClickListener { open() }
        })
        row("Schedule", schedule(rule))
        row("People", people(rule?.zenPolicy))
        row("Apps", "Which apps may notify")
        if (rule != null) {
            col.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(52f * M)
                addView(TextView(context).apply { text = "Dim the screen"; setTextColor(N9.TEXT); textSize = 18f * M; typeface = N9.FONT_BOLD },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Switch(context).apply {
                    contentDescription = "Dim the screen"
                    isChecked = rule.deviceEffects?.shouldDimWallpaper() == true
                    setOnCheckedChangeListener { _, on ->
                        runCatching {
                            val fx = android.service.notification.ZenDeviceEffects.Builder(rule.deviceEffects
                                ?: android.service.notification.ZenDeviceEffects.Builder().build()).setShouldDimWallpaper(on).build()
                            rule.deviceEffects = fx
                            nm.updateAutomaticZenRule(id, rule)
                        }
                    }
                })
            })
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(N9.BACKGROUND); addView(col) })
        window.decorView.setOnApplyWindowInsetsListener { v, ins ->
            val b = ins.getInsets(android.view.WindowInsets.Type.systemBars()); col.setPadding(dp(16f * M), b.top + dp(14f * M), dp(16f * M), b.bottom + dp(16f)); ins
        }
    }

    override fun onResume() {
        super.onResume()
        if (SystemToolbar.available(this)) SystemToolbar(this).set(back = true, items = emptyList(), style = "dark", hidden = false) {}
    }

    private fun schedule(rule: AutomaticZenRule?): String {
        rule ?: return "Until you turn it off"
        val s = runCatching { android.service.notification.ZenModeConfig.tryParseScheduleConditionId(rule.conditionId) }.getOrNull()
            ?: return "Turns on automatically"
        fun t(h: Int, m: Int) = String.format("%02d:%02d", h, m)
        return "${t(s.startHour, s.startMinute)}–${t(s.endHour, s.endMinute)}"
    }

    private fun people(p: android.service.notification.ZenPolicy?): String = when (p?.priorityCallSenders) {
        android.service.notification.ZenPolicy.PEOPLE_TYPE_ANYONE -> "Anyone may call"
        android.service.notification.ZenPolicy.PEOPLE_TYPE_CONTACTS -> "Contacts may call"
        android.service.notification.ZenPolicy.PEOPLE_TYPE_STARRED -> "Favourites may call"
        android.service.notification.ZenPolicy.PEOPLE_TYPE_NONE -> "Nobody may call"
        else -> "Who may reach you"
    }

    companion object { const val EXTRA_ID = "mode" }
}
