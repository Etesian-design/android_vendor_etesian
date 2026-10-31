package com.bompotis.etesian.store.ui

import android.app.Activity
import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import com.bompotis.etesian.store.R

/** N9 "blanco" palette and small view helpers (see docs/n9-app-style.md). */
object Ui {
    const val WINDOW = 0xFFE9E9E9.toInt()
    const val LIST = 0xFFF4F4F4.toInt()
    const val TEXT = 0xFF191919.toInt()
    const val SECONDARY = 0xFF8C8C8C.toInt()
    const val SEPARATOR = 0xFFD6D6D6.toInt()
    const val BLUE = 0xFF00A5E5.toInt()

    val regular: Typeface by lazy { Typeface.create("etesian-text", Typeface.NORMAL) }
    val light: Typeface by lazy { Typeface.create(regular, 300, false) }

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun text(ctx: Context, sp: Float, color: Int = TEXT, face: Typeface = regular): TextView =
        TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = face
        }

    fun separator(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(SEPARATOR)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1))
    }

    /** AOSP squircle on a 100x100 box: the N9 tile shape for every icon/thumbnail. */
    private const val SQUIRCLE = "M100.00 50.00L99.92 57.13L99.69 61.68L99.31 65.56L98.78 69.04L98.09 72.23L97.25 75.18L96.26 77.92L95.12 80.48L93.82 82.86L92.38 85.08L90.78 87.13L89.04 89.04L87.13 90.78L85.08 92.38L82.86 93.82L80.48 95.12L77.92 96.26L75.18 97.25L72.23 98.09L69.04 98.78L65.56 99.31L61.68 99.69L57.13 99.92L50.00 100.00L42.87 99.92L38.32 99.69L34.44 99.31L30.96 98.78L27.77 98.09L24.82 97.25L22.08 96.26L19.52 95.12L17.14 93.82L14.92 92.38L12.87 90.78L10.96 89.04L9.22 87.13L7.62 85.08L6.18 82.86L4.88 80.48L3.74 77.92L2.75 75.18L1.91 72.23L1.22 69.04L0.69 65.56L0.31 61.68L0.08 57.13L0.00 50.00L0.08 42.87L0.31 38.32L0.69 34.44L1.22 30.96L1.91 27.77L2.75 24.82L3.74 22.08L4.88 19.52L6.18 17.14L7.62 14.92L9.22 12.87L10.96 10.96L12.87 9.22L14.92 7.62L17.14 6.18L19.52 4.88L22.08 3.74L24.82 2.75L27.77 1.91L30.96 1.22L34.44 0.69L38.32 0.31L42.87 0.08L50.00 0.00L57.13 0.08L61.68 0.31L65.56 0.69L69.04 1.22L72.23 1.91L75.18 2.75L77.92 3.74L80.48 4.88L82.86 6.18L85.08 7.62L87.13 9.22L89.04 10.96L90.78 12.87L92.38 14.92L93.82 17.14L95.12 19.52L96.26 22.08L97.25 24.82L98.09 27.77L98.78 30.96L99.31 34.44L99.69 38.32L99.92 42.87Z"

    /** Clips a view to the N9 squircle (largest centred square). */
    fun squircle(view: View) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                val sz = minOf(v.width, v.height).toFloat()
                if (sz <= 0f) return
                val p = androidx.core.graphics.PathParser.createPathFromPathData(SQUIRCLE)
                p.transform(android.graphics.Matrix().apply {
                    setScale(sz / 100f, sz / 100f)
                    postTranslate((v.width - sz) / 2f, (v.height - sz) / 2f)
                })
                outline.setPath(p)
            }
        }
        view.clipToOutline = true
    }

    /** Slightly rounded corners for large surfaces (screenshots). */
    fun rounded(view: View, fraction: Float) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, minOf(v.width, v.height) * fraction)
            }
        }
        view.clipToOutline = true
    }

    /** Rounded action button (green/blue/grey/red backgrounds). */
    fun button(ctx: Context): TextView = text(ctx, 15f, 0xFFFFFFFF.toInt()).apply {
        gravity = Gravity.CENTER
        minWidth = dp(ctx, 88)
        minHeight = dp(ctx, 36)
        setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0)
        setShadowLayer(1f, 0f, -1f, 0x40000000)
        setBackgroundResource(R.drawable.btn_green)
    }

    class Header(ctx: Context, titleText: String) {
        val view = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.header_bg)
            minimumHeight = Ui.dp(ctx, 72)
            setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 12), 0)
        }
        val title: TextView = Ui.text(ctx, 28f, Ui.TEXT, Ui.light).apply {
            text = titleText
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(1f, 0f, 1f, 0x80FFFFFF.toInt())
        }

        init {
            view.addView(this.title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        fun action(label: String, onClick: () -> Unit): TextView {
            val ctx = view.context
            val b = Ui.button(ctx).apply {
                text = label
                setBackgroundResource(R.drawable.btn_grey)
                minWidth = Ui.dp(ctx, 72)
                setOnClickListener { onClick() }
            }
            view.addView(b)
            return b
        }
    }

    /** Edge-to-edge: header grows under the status bar, [bottom] gets the nav bar inset. */
    fun setupWindow(activity: Activity, root: View, header: View, bottom: View) {
        activity.window.setDecorFitsSystemWindows(false)
        activity.window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
        val hTop = header.paddingTop
        val bBottom = bottom.paddingBottom
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            header.setPadding(header.paddingLeft, hTop + bars.top, header.paddingRight, header.paddingBottom)
            bottom.setPadding(bottom.paddingLeft, bottom.paddingTop, bottom.paddingRight, bBottom + bars.bottom)
            root.setPadding(bars.left, 0, bars.right, 0)
            insets
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "–"
        bytes < 1024 * 1024 -> "%.0f kB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
