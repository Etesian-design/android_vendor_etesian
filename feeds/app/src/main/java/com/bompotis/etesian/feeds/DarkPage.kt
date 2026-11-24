package com.bompotis.etesian.feeds

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bompotis.etesian.toolbar.EtesianToolbar

/**
 * The pages Feeds shows over Etesian settings (its connections), in the settings' own dark N9 look: a page follows the
 * look of the page it came from (2026-11-24, owner), so these are black like Settings, not light like the Feeds app.
 * As in the N9 Accounts app: a full page per step, never a dialog: the services as rows (a squircle, the name, what it
 * does, ›), then the form.
 */
object DarkPage {
    const val BACKGROUND = 0xFF000000.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val SECONDARY = 0xFF8C8C8C.toInt()
    const val RULE = 0xFF4A4A4A.toInt()
    const val ERROR = 0xFFFF6B5E.toInt()

    /** A page: the settings header (light 35sp title over a hairline), [body] scrolling under it, the dark toolbar. */
    fun page(a: Activity, title: String, body: LinearLayout): LinearLayout {
        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BACKGROUND) }
        val header = Ui.text(a, 35f, TEXT, Ui.FONT_LIGHT).apply {
            text = title; gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(16f), a.dp(16f), a.dp(16f), a.dp(10f))
        }
        root.addView(header)
        root.addView(View(a).apply { setBackgroundColor(0xFF5A5A5A.toInt()) },
            LinearLayout.LayoutParams(-1, a.dp(1f)).apply { marginStart = a.dp(16f); marginEnd = a.dp(16f) })
        val scroll = ScrollView(a).apply { addView(body); isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (EtesianToolbar.isAvailable(a)) {
            EtesianToolbar.of(a).setStyle(EtesianToolbar.STYLE_DARK).setBack(true).setItems(emptyList()) {}
        }
        a.setContentView(root)
        Ui.edgeToEdge(a, root, header, scroll)
        return root
    }

    fun body(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, ctx.dp(8f), 0, ctx.dp(24f)) }

    /** A line of explanation under the header, grey as the settings' summaries. */
    fun note(ctx: Context, s: String) = Ui.text(ctx, 17f, SECONDARY).apply {
        text = s; maxLines = 8; setPadding(ctx.dp(16f), ctx.dp(12f), ctx.dp(16f), ctx.dp(12f))
    }

    /** A Accounts-style row: a lit squircle with [mark], the name bold, what it does under it, and › (it leads on). */
    fun serviceRow(ctx: Context, name: String, what: String, color: Int, mark: String, onTap: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(88f)
        setPadding(ctx.dp(16f), ctx.dp(12f), ctx.dp(16f), ctx.dp(12f))
        background = ctx.getDrawable(android.R.drawable.list_selector_background)
        contentDescription = name
        addView(TileView(ctx, color, mark), LinearLayout.LayoutParams(ctx.dp(52f), ctx.dp(52f)).apply { marginEnd = ctx.dp(16f) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 20f, TEXT, Ui.FONT_BOLD).apply { text = name })
            addView(Ui.text(ctx, 15f, SECONDARY).apply { text = what; maxLines = 3 })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_chevron) }, LinearLayout.LayoutParams(ctx.dp(22f), ctx.dp(22f)))
        setOnClickListener { onTap() }
    }

    /** A detail line: the label grey, the value white under it. */
    fun detail(ctx: Context, label: String, value: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(16f), ctx.dp(10f), ctx.dp(16f), ctx.dp(10f))
        addView(Ui.text(ctx, 15f, SECONDARY).apply { text = label })
        addView(Ui.text(ctx, 20f, TEXT).apply { text = value; maxLines = 2 })
    }

    fun rule(ctx: Context) = View(ctx).apply { setBackgroundColor(RULE) }.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, 1).apply { marginStart = ctx.dp(16f); marginEnd = ctx.dp(16f) }
    }

    /** A labelled N9 field: the label over a dark squircle-ended field. */
    fun field(ctx: Context, label: String, hint: String, secret: Boolean = false, uri: Boolean = false): Pair<View, EditText> {
        val edit = EditText(ctx).apply {
            this.hint = hint; setSingleLine(true); contentDescription = label
            setTextColor(TEXT); setHintTextColor(0xFF6E6E6E.toInt()); textSize = 18f; typeface = Ui.FONT
            inputType = InputType.TYPE_CLASS_TEXT or when {
                secret -> InputType.TYPE_TEXT_VARIATION_PASSWORD
                uri -> InputType.TYPE_TEXT_VARIATION_URI
                else -> 0
            }
            background = Ends(0xFF1C1C1C.toInt(), 0xFF1C1C1C.toInt(), 0xFF5A5A5A.toInt())
            setPadding(ctx.dp(18f), 0, ctx.dp(18f), 0)
            minHeight = ctx.dp(50f)
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(16f), ctx.dp(10f), ctx.dp(16f), ctx.dp(4f))
            // Tapping the label goes to its field, as tapping the field does.
            addView(Ui.text(ctx, 15f, SECONDARY).apply {
                text = label; setPadding(ctx.dp(4f), 0, 0, ctx.dp(6f))
                setOnClickListener {
                    edit.requestFocus()
                    ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showSoftInput(edit, 0)
                }
            })
            addView(edit, LinearLayout.LayoutParams(-1, ctx.dp(50f)))
        }
        return box to edit
    }

    /** The N9 button: 52dp, squircle-half ends; blue for the action, dark for leaving (Sign out, Disconnect). */
    fun button(ctx: Context, label: String, blue: Boolean, onTap: () -> Unit) = TextView(ctx).apply {
        text = label; gravity = Gravity.CENTER; textSize = 18f; typeface = Ui.FONT_BOLD; setTextColor(TEXT)
        contentDescription = label
        background = if (blue) Ends(0xFF5AA0FF.toInt(), 0xFF3A7BE0.toInt(), 0) else Ends(0xFF3A3A3C.toInt(), 0xFF1A1A1C.toInt(), 0)
        isClickable = true; isFocusable = true
        setOnClickListener { onTap() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(ctx.dp(280f), ctx.dp(52f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = ctx.dp(20f)
        }
    }

    /** A shape with squircle-half ends (n = 2.8), filled top to bottom, with an optional 1px rim. */
    class Ends(private val top: Int, private val bottom: Int, private val rim: Int) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        private var path = Path()
        override fun onBoundsChange(b: android.graphics.Rect) {
            val h = b.height().toFloat(); val w = b.width().toFloat()
            path = Ui.squircle(h, 2.8).apply { offset(b.left.toFloat(), b.top.toFloat()) }
            path.op(Ui.squircle(h, 2.8).apply { offset(b.left + w - h, b.top.toFloat()) }, Path.Op.UNION)
            path.op(Path().apply { addRect(RectF(b.left + h / 2, b.top.toFloat(), b.left + w - h / 2, b.bottom.toFloat()), Path.Direction.CW) }, Path.Op.UNION)
            fill.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        override fun draw(c: Canvas) {
            val pressed = state.contains(android.R.attr.state_pressed)
            fill.alpha = if (pressed) 170 else 255
            c.drawPath(path, fill)
            if (rim != 0) { stroke.color = rim; c.drawPath(path, stroke) }
        }
        override fun isStateful() = true
        override fun onStateChange(state: IntArray): Boolean { invalidateSelf(); return true }
        override fun setAlpha(alpha: Int) { fill.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { fill.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** A lit N9 tile: the service's colour, light from the top, its mark in white. */
    class TileView(ctx: Context, private val color: Int, private val mark: String) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val s = minOf(width, height).toFloat()
            val path = Ui.squircle(s, 2.8)
            p.shader = null; p.color = color; c.drawPath(path, p)
            p.shader = LinearGradient(0f, 0f, 0f, s, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawPath(path, p)
            p.shader = null; p.color = TEXT; p.textAlign = Paint.Align.CENTER; p.typeface = Ui.FONT_BOLD; p.textSize = s * 0.46f
            c.drawText(mark, s / 2, s / 2 - (p.descent() + p.ascent()) / 2, p)
        }
    }
}
