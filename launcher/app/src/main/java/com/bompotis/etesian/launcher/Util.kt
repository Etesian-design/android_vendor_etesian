package com.bompotis.etesian.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

fun Context.dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
fun View.dp(v: Float): Int = context.dp(v)

/** Etesian UI palette. */
object N9 {
    const val BACKGROUND = Color.BLACK
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xFF8C8C8C.toInt()
    /** N9 selection / switch / slider blue. */
    const val ACCENT = 0xFF2A86D8.toInt()
    /** Flat surface for the few remaining raised elements (no rounded cards on home views). */
    const val CARD = 0xFF1C1C1C.toInt()
    /** Icon tile behind generic glyphs. */
    const val TILE = 0xFF2E2E2E.toInt()
    /** Filled grey pill buttons (N9 has no outlined buttons). */
    const val BUTTON = 0xFF3A3A3A.toInt()

    /**
     * A dark N9 button, lit from the top like its icons (measured): a fill from #181818 at the
     * top to #080808 at the bottom, a 1px #393839 rim, squircle-half ends (not a pill).
     */
    fun button(context: Context): android.graphics.drawable.Drawable {
        val d = context.resources.displayMetrics.density
        // its ends: a squircle as tall as the button, cut in half (blanco button background)
        return FieldDrawable(intArrayOf(0xFF1C1C1C.toInt(), 0xFF101010.toInt(), 0xFF080808.toInt()), 0xFF393839.toInt(), d, shade = false)
    }
    /**
     * An edit-mode toolbar button (n9-grid-edit.png): compact, centred in the bar, a dark lit face
     * with a thin light rim and well rounded corners, bold label.
     */
    fun editButton(context: Context, label: String, onClick: () -> Unit) = android.widget.TextView(context).apply {
        val d = context.resources.displayMetrics.density
        text = label
        setTextColor(TEXT); textSize = 16f; typeface = FONT_BOLD
        gravity = android.view.Gravity.CENTER
        background = button(context)  // N9 dark button: squircle-half ends, 1px #393839 rim
        setOnClickListener { onClick() }
    }

    /** N9 edit-bar buttons are 180×41 of a 72 n9px bar, 8 apart; scaled into our 64dp bar. */
    const val EDIT_BUTTON_W = 158f
    const val EDIT_BUTTON_H = 36f

    /** The N9 edit-mode bar: black, a faint top rule, the buttons side by side in the middle. */
    fun editBar(context: Context, vararg buttons: android.view.View) = android.widget.LinearLayout(context).apply {
        val d = context.resources.displayMetrics.density
        gravity = android.view.Gravity.CENTER
        background = android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF141414.toInt(), 0xFF000000.toInt())),
            android.graphics.drawable.GradientDrawable().apply { setColor(0xFF2A2A2A.toInt()) })).apply {
            setLayerHeight(1, Math.max(1, d.toInt()))
        }
        isClickable = true
        setPadding((8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt())
        buttons.forEach { b ->
            addView(b, android.widget.LinearLayout.LayoutParams((EDIT_BUTTON_W * d).toInt(), (EDIT_BUTTON_H * d).toInt()).apply {
                marginStart = (3.5f * d).toInt(); marginEnd = (3.5f * d).toInt()
            })
        }
    }

    /** Thin group-header / group-separator lines. */
    const val DIVIDER = 0xFF4A4A4A.toInt()
    // Nokia Pure is not bundled; sans-serif is the closest stock face.
    /** Nokia Pure Text when the user's N9 font is installed (vendor/etesian/n9-files), else Roboto. */
    private val pure: Typeface? = Typeface.create("etesian-text", Typeface.NORMAL)
        .takeIf { it != Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
    val FONT: Typeface = pure ?: Typeface.create("sans-serif", Typeface.NORMAL)
    val FONT_LIGHT: Typeface = pure?.let { Typeface.create(it, 300, false) } ?: Typeface.create("sans-serif-light", Typeface.NORMAL)
    val FONT_BOLD: Typeface = pure?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.create("sans-serif", Typeface.BOLD)
}

/** Implemented by each of the three home views. */
interface HomePage {
    fun applyInsets(left: Int, top: Int, right: Int, bottom: Int)
    /** Leaves edit / close mode; returns true if a mode was active. */
    fun exitModes(): Boolean
    fun onShown() {}
    /** True while the view needs horizontal swipes for itself (e.g. dismissing entries). */
    fun holdsPaging(): Boolean = false
}

/**
 * The N9 text field's and button's shape (blanco textedit and button backgrounds): a squircle as tall as the field
 * (n = 2.8, the icons' curve), cut in half and pulled apart, joined by straight edges. Taller ones keep corners [corner]
 * tall. A 1px [rim]; [fills] top to bottom (a button's lit face), and for fields a faint [shade] along the top (inset).
 */
class FieldDrawable(private val fills: IntArray, private val rim: Int, private val density: Float, private val corner: Float = 0f,
    private val shade: Boolean = true) : android.graphics.drawable.Drawable() {
    constructor(fill: Int, rim: Int, density: Float, corner: Float = 0f) : this(intArrayOf(fill), rim, density, corner, true)
    private val path = android.graphics.Path()
    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = fills[0] }
    private val rimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeWidth = density; color = rim
    }
    private val shadePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    var rimColor: Int
        get() = rimPaint.color
        set(v) { rimPaint.color = v; invalidateSelf() }

    override fun onBoundsChange(b: android.graphics.Rect) {
        val inset = density / 2
        val l = b.left + inset; val t = b.top + inset; val r = b.right - inset; val bt = b.bottom - inset
        val s = minOf(bt - t, if (corner > 0) corner else bt - t, r - l) / 2   // the squircle's radius
        path.reset()
        // a quarter of the n = 2.8 squircle at each corner, centred (cx, cy), from angle a0 to a0 + 90°
        fun quarter(cx: Float, cy: Float, a0: Int) {
            for (i in 0..24) {
                val a = Math.toRadians(a0 + i * 90.0 / 24)
                val c = Math.cos(a); val sn = Math.sin(a)
                val x = cx + s * Math.signum(c) * Math.pow(Math.abs(c), 2 / 2.8)
                val y = cy + s * Math.signum(sn) * Math.pow(Math.abs(sn), 2 / 2.8)
                if (path.isEmpty) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
            }
        }
        quarter(r - s, bt - s, 0); quarter(l + s, bt - s, 90); quarter(l + s, t + s, 180); quarter(r - s, t + s, 270)
        path.close()
        shadePaint.shader = android.graphics.LinearGradient(0f, t, 0f, t + 4 * density, 0x14000000, 0, android.graphics.Shader.TileMode.CLAMP)
        if (fills.size > 1) fillPaint.shader = android.graphics.LinearGradient(0f, t, 0f, bt, fills, null, android.graphics.Shader.TileMode.CLAMP)
    }

    override fun draw(c: android.graphics.Canvas) { c.drawPath(path, fillPaint); if (shade) c.drawPath(path, shadePaint); c.drawPath(path, rimPaint) }
    override fun setAlpha(alpha: Int) { fillPaint.alpha = alpha; rimPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { fillPaint.colorFilter = cf; rimPaint.colorFilter = cf; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    override fun getOutline(o: android.graphics.Outline) { o.setPath(path) }
}
