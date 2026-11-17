package com.bompotis.etesian.launcher.platform

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bompotis.etesian.launcher.BreezeWidgetActivity
import com.bompotis.etesian.launcher.N9
import com.bompotis.etesian.launcher.N9Sounds
import com.bompotis.etesian.launcher.Prefs
import com.bompotis.etesian.launcher.ToolbarActions
import com.bompotis.etesian.launcher.ToolbarConfig
import com.bompotis.etesian.launcher.ToolbarSettingsActivity

/**
 * Breeze's menu, for one hand: a smaller toolbar (Breeze's own buttons, set like the toolbar's) and the user's
 * widgets at reduced size, under the floating button (beside the bar when Breeze is on an edge).
 * A tap on a button runs it and closes the menu; a touch anywhere else closes it too.
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

    fun show(anchor: Rect, free: Boolean, onLeft: Boolean) {
        dismiss()
        val screenW = ctx.resources.displayMetrics.widthPixels
        val screenH = ctx.resources.displayMetrics.heightPixels
        val w = minOf(dp(288f), screenW - dp(16f))
        val icon = com.bompotis.etesian.toolbar.EtesianToolbar.iconColor(ctx)
        // Drawn as the bottom bar is: its gradient and top edge, and its squircle buttons (a little smaller).
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), dp(10f), dp(10f), dp(8f))
            background = com.bompotis.etesian.toolbar.EtesianToolbar.barBackground(ctx)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            contentDescription = "Breeze menu"
        }
        val size = dp(BUTTON_DP)
        fun button(label: String, onClick: () -> Unit, image: (ImageView) -> Unit) = FrameLayout(ctx).apply {
            background = com.bompotis.etesian.toolbar.EtesianToolbar.buttonBackground(ctx, size)
            contentDescription = label
            isClickable = true
            setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
            addView(ImageView(ctx).apply { image(this); setColorFilter(icon) },
                FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.CENTER))
        }
        // The smaller toolbar: Breeze's buttons in a row, then + to change them.
        val bar = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val buttons = ToolbarConfig.buttons(ctx, ToolbarConfig.BREEZE).take(4)
        buttons.forEach { b ->
            val cell = FrameLayout(ctx)
            cell.addView(button(b.label, { dismiss(); ToolbarActions.run(ctx, b.action) }) { iv ->
                iv.setImageDrawable(ToolbarConfig.decode(b.icon)?.let { BitmapDrawable(ctx.resources, it) })
                if (!b.tint) iv.clearColorFilter()
            }, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            bar.addView(cell, LinearLayout.LayoutParams(0, size + dp(8f), 1f))
        }
        val add = FrameLayout(ctx)
        add.addView(button("Edit Breeze shortcuts", {
            dismiss()
            ctx.startActivity(Intent(ctx, ToolbarSettingsActivity::class.java)
                .putExtra(ToolbarSettingsActivity.EXTRA_SET, ToolbarConfig.BREEZE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { it.setImageResource(com.bompotis.etesian.launcher.R.drawable.tb_add) }, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        bar.addView(add, LinearLayout.LayoutParams(0, size + dp(8f), 1f))
        panel.addView(bar)
        // Widgets, at three quarters of their size.
        val ids = Prefs.breezeWidgets(ctx)
        val awm = AppWidgetManager.getInstance(ctx)
        if (ids.isNotEmpty()) {
            val h = BreezeWidgetActivity.host(ctx).also { host = it; it.startListening() }
            ids.forEach { id ->
                val info = awm.getAppWidgetInfo(id) ?: return@forEach
                val view = h.createView(ctx.applicationContext, id, info)
                val inner = w - dp(20f)
                val natural = maxOf(info.minHeight, dp(80f))
                panel.addView(Scaled(ctx, view, SCALE).apply {
                    setOnLongClickListener {
                        N9Sounds.longPress(this)
                        Prefs.setBreezeWidgets(ctx, Prefs.breezeWidgets(ctx) - id)
                        h.deleteAppWidgetId(id)
                        show(anchor, free, onLeft)
                        true
                    }
                }, LinearLayout.LayoutParams(inner, (natural * SCALE).toInt()).apply { topMargin = dp(8f) })
            }
        }
        panel.addView(TextView(ctx).apply {
            text = "Add widget"
            setTextColor(icon); alpha = 0.75f; textSize = 14f; typeface = N9.FONT
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, dp(4f))
            setOnClickListener {
                dismiss()
                ctx.startActivity(Intent(ctx, BreezeWidgetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        panel.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST))
        val ph = panel.measuredHeight
        val gap = dp(8f)
        // Under the button (over it when there's no room below); beside the bar, on the screen side of it.
        val x: Int
        val y: Int
        if (free) {
            x = (anchor.centerX() - w / 2).coerceIn(dp(8f), screenW - w - dp(8f))
            y = if (anchor.bottom + gap + ph <= screenH - dp(8f)) anchor.bottom + gap else (anchor.top - gap - ph).coerceAtLeast(dp(8f))
        } else {
            x = if (onLeft) anchor.right + gap else anchor.left - gap - w
            y = (anchor.centerY() - ph / 2).coerceIn(dp(8f), screenH - ph - dp(8f))
        }
        // A system dialog window: Android never lets a secure overlay take touches (they fell through to the app).
        val lp = WindowManager.LayoutParams(w, ph, WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x; this.y = y
            title = "EtesianBreezeMenu"
        }
        runCatching { wm.addView(panel, lp); root = panel; shown.set(x, y, x + w, y + ph) }
        N9Sounds.play(ctx, N9Sounds.EDIT)
    }

    fun dismiss() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        shown.setEmpty()
        host?.let { runCatching { it.stopListening() } }
        host = null
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

        // Long-press removes the widget: watch for it even when the widget takes the touch.
        private var downX = 0f
        private var downY = 0f

        override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y
                    postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                    if (kotlin.math.abs(ev.x - downX) > slop || kotlin.math.abs(ev.y - downY) > slop) removeCallbacks(hold)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> removeCallbacks(hold)
            }
            super.dispatchTouchEvent(ev)
            return true
        }

        private val hold = Runnable { performLongClick() }

    }

    companion object {
        const val SCALE = 0.75f
        /** Breeze's buttons: the toolbar's (48 dp), a little smaller for the menu. */
        const val BUTTON_DP = 42f
    }
}
