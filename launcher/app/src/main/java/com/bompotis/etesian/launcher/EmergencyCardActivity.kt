package com.bompotis.etesian.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Android extras → Emergency information: the full-screen card over the lock screen (story
 * Phone v2, state medical-card), opened from the block left of the lock-screen clock without
 * unlocking. Red header, owner, facts, emergency contacts (tap = call) and the only Emergency
 * call button, plus Close. Data: Settings.Secure etesian_emergency_* ([Emergency]).
 */
class EmergencyCardActivity : Activity() {
    /** The card has its own Close (mock: no toolbar under it), so the system toolbar is hidden. */
    override fun onResume() {
        super.onResume()
        if (SystemToolbar.available(this)) SystemToolbar(this).set(back = false, items = emptyList(), style = "dark", hidden = true) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        window.decorView.setBackgroundColor(BG)
        setContentView(build())
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom); ins
            }
        }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFC21A17.toInt())
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            addView(TextView(context).apply { text = "✚"; setTextColor(N9.TEXT); textSize = 20f },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10f) })
            addView(TextView(context).apply { text = "Emergency information"; setTextColor(N9.TEXT); textSize = 17f; typeface = N9.FONT_BOLD })
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(TextView(this).apply {
            text = Emergency.get(context, "name").ifEmpty { "Owner" }
            setTextColor(N9.TEXT); textSize = 30f; typeface = N9.FONT_LIGHT
            setPadding(dp(16f), dp(14f), dp(16f), dp(6f))
        })
        Emergency.facts(this).forEach { (k, v) ->
            body.addView(LinearLayout(this).apply {
                setPadding(dp(16f), dp(3f), dp(16f), dp(3f))
                addView(TextView(context).apply { text = k; setTextColor(0xFFA0A0A0.toInt()); textSize = 15f; typeface = N9.FONT; minWidth = dp(110f) })
                addView(TextView(context).apply { text = v; setTextColor(N9.TEXT); textSize = 15f; typeface = N9.FONT_BOLD },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        val contacts = Emergency.contacts(this)
        if (contacts.isNotEmpty()) {
            body.addView(View(this).apply { setBackgroundColor(0xFF333333.toInt()) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply { topMargin = dp(10f) })
            body.addView(TextView(this).apply {
                text = "Emergency contacts"; setTextColor(0xFFA0A0A0.toInt()); textSize = 14f; typeface = N9.FONT
                setPadding(dp(16f), dp(10f), dp(16f), dp(4f))
            })
            contacts.forEach { c -> body.addView(contactRow(c)) }
        }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            addView(button("Emergency call", negative = true) { emergencyCall() },
                LinearLayout.LayoutParams(dp(158f), dp(44f)).apply { marginEnd = dp(10f) })
            addView(button("Close", negative = false) { finish() }, LinearLayout.LayoutParams(dp(158f), dp(44f)))
        })
        return root
    }

    private fun contactRow(c: Emergency.Contact) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        contentDescription = "Call ${c.name}"
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = c.name; setTextColor(N9.TEXT); textSize = 17f; typeface = N9.FONT })
            if (c.relation.isNotEmpty()) addView(TextView(context).apply { text = c.relation; setTextColor(0xFFA0A0A0.toInt()); textSize = 13f; typeface = N9.FONT })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(context).apply {
            // Mock: the white outline phone, 20px of the 360px mock (×1.36 → 27dp).
            setImageDrawable(Glyphs.drawable("phone", N9.TEXT, outline = true))
            contentDescription = "Call icon"
        }, LinearLayout.LayoutParams(dp(27f), dp(27f)))
        setOnClickListener { call(c.number) }
    }

    /** N9 buttons: negative = red, the other the dark N9 button. */
    private fun button(label: String, negative: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(N9.TEXT); textSize = 16f; typeface = N9.FONT_BOLD; gravity = Gravity.CENTER
        contentDescription = label
        background = if (negative) FieldDrawable(intArrayOf(0xFFE2332E.toInt(), 0xFFB01814.toInt()),
            0xFF8E1210.toInt(), resources.displayMetrics.density, shade = false)
            else N9.button(context)
        setOnClickListener { onClick() }
    }

    private fun call(number: String) {
        if (number.isEmpty()) return
        val granted = checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val i = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun emergencyCall() {
        val tm = getSystemService(android.telecom.TelecomManager::class.java) ?: return
        runCatching { startActivity(tm.createLaunchEmergencyDialerIntent(null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object { const val BG = 0xFF0E0E0E.toInt() }
}

/** The owner's emergency information (our own fields: Android's Emergency Information app keeps its data private). */
object Emergency {
    class Contact(val name: String, val relation: String, val number: String)
    val FIELDS = listOf("blood" to "Blood type", "allergies" to "Allergies", "medication" to "Medication", "notes" to "Notes", "donor" to "Organ donor")

    fun get(ctx: Context, k: String) = Settings.Secure.getString(ctx.contentResolver, "etesian_emergency_$k")?.trim().orEmpty()
    fun put(ctx: Context, k: String, v: String) = Settings.Secure.putString(ctx.contentResolver, "etesian_emergency_$k", v)
    fun facts(ctx: Context) = FIELDS.mapNotNull { (k, t) -> get(ctx, k).takeIf { it.isNotEmpty() }?.let { t to it } }
    /** One per line: "Name | relation | number". */
    fun contacts(ctx: Context) = get(ctx, "contacts").lines().mapNotNull { l ->
        val p = l.split('|').map { it.trim() }
        when {
            p.size >= 3 && p[0].isNotEmpty() -> Contact(p[0], p[1], p[2])
            p.size == 2 && p[0].isNotEmpty() -> Contact(p[0], "", p[1])
            else -> null
        }
    }
}
