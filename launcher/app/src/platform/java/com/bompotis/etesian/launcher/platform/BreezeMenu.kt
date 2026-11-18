package com.bompotis.etesian.launcher.platform

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.bompotis.etesian.launcher.BreezeWidgetActivity
import com.bompotis.etesian.launcher.Prefs
import com.bompotis.etesian.launcher.ToolbarActions
import com.bompotis.etesian.launcher.ToolbarConfig
import com.bompotis.etesian.toolbar.EtesianToolbar

/**
 * Breeze's menu, for one hand. It slides out of the side Breeze is on and stays against it, its top level with
 * Breeze: a strip of Breeze's buttons drawn as the bottom toolbar is (in Breeze's theme), and under it the user's
 * widgets on nothing at all, scrolling when there are more than fit. Nothing is set up here: buttons and widgets
 * are chosen in Etesian settings → Breeze. A tap on a button runs it and closes the menu; a touch anywhere
 * outside closes it too.
 */
class BreezeMenu(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private var root: View? = null
    private val shown = Rect()
    private var host: AppWidgetHost? = null

    val showing get() = root != null

    fun contains(x: Float, y: Float) = shown.contains(x.toInt(), y.toInt())

    private fun dp(v: Float) = (v * density).toInt()

    /** Opens with its top at [top], out of the left side of the screen when [fromLeft], else the right. */
    fun show(top: Int, fromLeft: Boolean) {
        dismiss(animate = false)
        val screenW = ctx.resources.displayMetrics.widthPixels
        val screenH = ctx.resources.displayMetrics.heightPixels
        val w = minOf(dp(300f), (screenW * 0.85f).toInt())
        val dark = Prefs.breezeDark(ctx)
        val icon = EtesianToolbar.iconColor(dark)
        val size = dp(BUTTON_DP)
        val stripH = size + dp(14f)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = "Breeze menu"
        }
        // The strip: Breeze's buttons, spread evenly, as the bottom toolbar draws them.
        val strip = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = EtesianToolbar.barBackground(ctx, dark)
        }
        val buttons = ToolbarConfig.buttons(ctx, ToolbarConfig.BREEZE).take(MAX_BUTTONS)
        buttons.forEach { b ->
            val cell = FrameLayout(ctx)
            cell.addView(FrameLayout(ctx).apply {
                background = EtesianToolbar.buttonBackground(ctx, size, dark)
                contentDescription = b.label
                isClickable = true
                setOnClickListener {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    dismiss()
                    ToolbarActions.run(ctx, b.action)
                }
                addView(ImageView(ctx).apply {
                    setImageDrawable(ToolbarConfig.decode(b.icon)?.let { BitmapDrawable(ctx.resources, it) })
                    if (b.tint) setColorFilter(icon)
                }, FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.CENTER))
            }, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            strip.addView(cell, LinearLayout.LayoutParams(0, stripH, 1f))
        }
        content.addView(strip, LinearLayout.LayoutParams(w, stripH))

        // Widgets: under the strip, on nothing, at three quarters of their size.
        val ids = Prefs.breezeWidgets(ctx)
        val awm = AppWidgetManager.getInstance(ctx)
        val widgets = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        if (ids.isNotEmpty()) {
            val h = BreezeWidgetActivity.host(ctx).also { host = it; it.startListening() }
            ids.forEach { id ->
                val info = awm.getAppWidgetInfo(id) ?: return@forEach
                val view = h.createView(ctx.applicationContext, id, info)
                val natural = maxOf(info.minHeight, dp(80f))
                widgets.addView(Scaled(ctx, view, SCALE),
                    LinearLayout.LayoutParams(w, (natural * SCALE).toInt()).apply { topMargin = dp(8f) })
            }
        }
        // As high as it can go below its top; the widgets scroll past that.
        val bottomLimit = screenH - dp(8f)
        val y = top.coerceIn(0, maxOf(0, screenH - stripH - dp(8f)))
        widgets.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val widgetsH = minOf(widgets.measuredHeight, maxOf(0, bottomLimit - y - stripH))
        if (widgets.childCount > 0) content.addView(ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(widgets)
        }, LinearLayout.LayoutParams(w, widgetsH))
        val ph = stripH + if (widgets.childCount > 0) widgetsH else 0

        val x = if (fromLeft) 0 else screenW - w
        // A system dialog window: Android never lets a secure overlay take touches (they fell through to the app).
        val lp = WindowManager.LayoutParams(w, ph, WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x; this.y = y
            title = "EtesianBreezeMenu"
            windowAnimations = 0
        }
        content.translationX = if (fromLeft) -w.toFloat() else w.toFloat()
        runCatching { wm.addView(content, lp); root = content; shown.set(x, y, x + w, y + ph); side = fromLeft }
        content.animate().translationX(0f).setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    private var side = false

    fun dismiss(animate: Boolean = true) {
        val v = root ?: return
        root = null
        shown.setEmpty()
        val h = host
        host = null
        val gone = {
            runCatching { wm.removeView(v) }
            h?.let { runCatching { it.stopListening() } }
            Unit
        }
        if (!animate || !v.isAttachedToWindow) { gone(); return }
        // Back into the side it came from.
        v.animate().translationX(if (side) -v.width.toFloat() else v.width.toFloat())
            .setDuration(180).setInterpolator(AccelerateInterpolator()).withEndAction { gone() }.start()
    }

    /** Lays [child] out at 1/[scale] of the space it's given and draws it at [scale]: the widget at a smaller size. */
    @SuppressLint("ViewConstructor")
    private class Scaled(ctx: Context, child: View, private val scale: Float) : FrameLayout(ctx) {
        init {
            addView(child)
            child.pivotX = 0f; child.pivotY = 0f
            child.scaleX = scale; child.scaleY = scale
        }

        override fun onMeasure(ws: Int, hs: Int) {
            val w = MeasureSpec.getSize(ws); val h = MeasureSpec.getSize(hs)
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec((w / scale).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((h / scale).toInt(), MeasureSpec.EXACTLY))
            setMeasuredDimension(w, h)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val c = getChildAt(0); c.layout(0, 0, c.measuredWidth, c.measuredHeight)
        }
    }

    companion object {
        const val SCALE = 0.75f
        /** Breeze's buttons: the toolbar's (48 dp), a little smaller for the menu. */
        const val BUTTON_DP = 42f
        const val MAX_BUTTONS = 5
    }
}
