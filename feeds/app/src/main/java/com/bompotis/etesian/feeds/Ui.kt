package com.bompotis.etesian.feeds

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bompotis.etesian.toolbar.EtesianToolbar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

fun Context.dp(v: Float): Int = Math.round(v * resources.displayMetrics.density)

/** The N9 Feeds app's light page and orange brand. */
object Ui {
    const val PAGE = 0xFFDEE1E4.toInt()
    const val INK = 0xFF292829.toInt()
    const val SECONDARY = 0xFF525152.toInt()
    const val DATE = 0xFF6B6A6B.toInt()
    const val RULE = 0xFFC3C6CA.toInt()
    const val ORANGE = 0xFFFF8600.toInt()
    const val HEADER = 0xFFFF8A0D.toInt()
    const val RED = 0xFFD42D2A.toInt()
    const val BADGE = 0xFFEDEFF1.toInt()

    private val pure: Typeface? = Typeface.create("etesian-text", Typeface.NORMAL)
        .takeIf { it != Typeface.DEFAULT }
    val FONT: Typeface = pure ?: Typeface.create("sans-serif", Typeface.NORMAL)
    val FONT_LIGHT: Typeface = pure?.let { Typeface.create(it, 300, false) } ?: Typeface.create("sans-serif-light", Typeface.NORMAL)
    val FONT_BOLD: Typeface = pure?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.create("sans-serif", Typeface.BOLD)

    fun text(ctx: Context, size: Float, color: Int = INK, font: Typeface = FONT) = TextView(ctx).apply {
        textSize = size; setTextColor(color); typeface = font
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }

    /** The orange brand header of the main page, or the plain one of a sub page. */
    fun header(ctx: Context, title: String, brand: Boolean) = LinearLayout(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ctx.dp(16f), ctx.dp(14f), ctx.dp(16f), ctx.dp(14f))
        if (brand) setBackgroundColor(HEADER)
        addView(text(ctx, 26f, if (brand) 0xFFFFFFFF.toInt() else INK, FONT_LIGHT).apply { text = title },
            LinearLayout.LayoutParams(0, -2, 1f))
    }

    /** The orange count pill at the right of a sub page's header. */
    fun countBadge(ctx: Context) = text(ctx, 17f, 0xFFFFFFFF.toInt(), FONT).apply {
        gravity = Gravity.CENTER
        minWidth = ctx.dp(40f)
        setPadding(ctx.dp(10f), ctx.dp(2f), ctx.dp(10f), ctx.dp(2f))
        background = GradientDrawable().apply { setColor(ORANGE); cornerRadius = ctx.dp(10f).toFloat() }
    }

    /** The small light count box (or red "!") at a feed row's top right. */
    fun rowBadge(ctx: Context) = text(ctx, 13f, INK, FONT_BOLD).apply {
        gravity = Gravity.CENTER
        minWidth = ctx.dp(26f)
        setPadding(ctx.dp(5f), 0, ctx.dp(5f), 0)
        background = GradientDrawable().apply {
            setColor(BADGE); cornerRadius = ctx.dp(6f).toFloat(); setStroke(1, 0xFFB5B8BC.toInt())
        }
    }

    fun rule(ctx: Context) = View(ctx).apply { setBackgroundColor(RULE) }

    /** The N9 tile silhouette (superellipse). */
    fun squircle(size: Float, n: Double = 5.0): Path {
        val path = Path()
        val r = size / 2f
        for (i in 0..160) {
            val t = 2 * Math.PI * i / 160
            val c = cos(t); val s = sin(t)
            val x = (r + r * sign(c) * abs(c).pow(2.0 / n)).toFloat()
            val y = (r + r * sign(s) * abs(s).pow(2.0 / n)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Thumb-sized icons are squircles: clips [v] to the tile shape. */
    fun clipSquircle(v: View) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val sz = minOf(view.width, view.height).toFloat()
                if (sz > 0f) outline.setPath(squircle(sz))
            }
        }
        v.clipToOutline = true
    }

    /** A feed's icon: the site's own, clipped to a squircle, or the grey N9 feed tile. */
    fun feedIcon(ctx: Context, url: String?): ImageView = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        val b = url?.let { Feeds.icon(ctx, it) }
        if (b != null) { setImageBitmap(b); setBackgroundColor(0xFFFFFFFF.toInt()); clipSquircle(this) }
        else setImageResource(R.drawable.ic_feed_tile)
    }

    /** "23:21" today, "21/05/2021" before (as on the N9). */
    fun time(ctx: Context, t: Long): String =
        if (DateUtils.isToday(t)) DateFormat.getTimeFormat(ctx).format(java.util.Date(t))
        else DateFormat.format("dd/MM/yyyy", t).toString()

    fun date(t: Long): String = DateFormat.format("dd/MM/yyyy", t).toString()

    /** Pads [root] for the system bars: the header gets the top inset, [bottom] the bottom one. */
    fun edgeToEdge(activity: Activity, root: View, header: View, bottom: View) {
        activity.window.setDecorFitsSystemWindows(false)
        val top = header.paddingTop
        val bot = bottom.paddingBottom
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            header.setPadding(header.paddingLeft, top + bars.top, header.paddingRight, header.paddingBottom)
            bottom.setPadding(bottom.paddingLeft, bottom.paddingTop, bottom.paddingRight, bot + bars.bottom)
            root.setPadding(bars.left, 0, bars.right, 0)
            insets
        }
    }

    class Action(val id: String, val icon: Int, val label: String)

    /**
     * The page's toolbar: the system N9 toolbar when it's there, else a plain bar added to the
     * bottom of [root].
     */
    fun toolbar(activity: Activity, root: LinearLayout, back: Boolean, actions: List<Action>,
                onTap: (String) -> Unit): View? {
        if (EtesianToolbar.isAvailable(activity)) {
            EtesianToolbar.of(activity).setBack(back)
                .setItems(actions.map { EtesianToolbar.Item.action(it.id, it.icon, it.label) }) { onTap(it) }
            return null
        }
        val bar = LinearLayout(activity).apply { setBackgroundColor(0xFFC4C4C7.toInt()) }
        val all = (if (back) listOf(Action("back", 0, "Back")) else emptyList()) + actions
        for (a in all) bar.addView(ImageView(activity).apply {
            if (a.icon != 0) setImageResource(a.icon) else setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(0xFF262626.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = a.label
            setPadding(0, activity.dp(14f), 0, activity.dp(14f))
            setOnClickListener { if (a.id == "back") activity.onBackPressed() else onTap(a.id) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        return bar
    }

    fun dialog(ctx: Context) = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)

    fun field(ctx: Context, hint: String, value: String = "", uri: Boolean = true) = EditText(ctx).apply {
        this.hint = hint
        setText(value)
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or (if (uri) InputType.TYPE_TEXT_VARIATION_URI else 0)
    }

    fun wrap(ctx: Context, v: View) = LinearLayout(ctx).apply {
        setPadding(ctx.dp(20f), ctx.dp(8f), ctx.dp(20f), 0)
        addView(v, LinearLayout.LayoutParams(-1, -2))
    }
}
