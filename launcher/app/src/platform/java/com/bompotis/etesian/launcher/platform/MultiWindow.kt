package com.bompotis.etesian.launcher.platform

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.WindowConfiguration
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.ImageView
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.bompotis.etesian.launcher.N9
import com.bompotis.etesian.launcher.SystemTasks

/** Split screen and floating windows for Open applications, through the system's window shell. */
object MultiWindow {
    private const val TAG = "EtesianMultiWindow"
    private const val SPLIT_POSITION_TOP_OR_LEFT = 0
    private const val SNAP_TO_2_50_50 = 1

    fun split(first: Int, second: Int): Boolean {
        val shell = ProxyService.splitScreen ?: return false
        return runCatching {
            shell.startTasks(first, null, second, null, SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50, null, null)
            true
        }.onFailure { Log.w(TAG, "split $first/$second", it) }.getOrDefault(false)
    }

    /**
     * The split pair, top (or left) first: the two application tasks in the split root's
     * multi-window mode (they stay there while the pair is in the background).
     */
    fun pair(): Pair<Int, Int>? = runCatching {
        val split = ActivityTaskManager.getInstance().getTasks(30).filter {
            it.configuration.windowConfiguration.windowingMode == WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW &&
                it.baseActivity != null
        }.sortedWith(compareBy({ it.configuration.windowConfiguration.bounds.top }, { it.configuration.windowConfiguration.bounds.left }))
        if (split.size == 2) split[0].taskId to split[1].taskId else null
    }.onFailure { Log.w(TAG, "pair", it) }.getOrNull()

    /** Out of split screen: both tasks become full screen again, [top] in front. */
    fun unsplit(top: Int): Boolean {
        val shell = ProxyService.splitScreen ?: return false
        return runCatching { shell.exitSplitScreen(top); true }.onFailure { Log.w(TAG, "unsplit", it) }.getOrDefault(false)
    }

    /** The task in a window over the middle of the screen, the size of a large N9 dialog. */
    fun popup(ctx: Context, taskId: Int): Boolean {
        val inSplit = runCatching { ActivityTaskManager.getInstance().getTasks(20) }.getOrNull()
            ?.firstOrNull { it.taskId == taskId }
            ?.configuration?.windowConfiguration?.windowingMode == WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
        val shell = ProxyService.splitScreen
        if (inSplit && shell != null) {
            // Out of the split first, or the system just brings the pair back.
            runCatching { shell.exitSplitScreen(taskId) }
            Handler(Looper.getMainLooper()).postDelayed({ open(ctx, taskId) }, 450)
            return true
        }
        return open(ctx, taskId)
    }

    private fun open(ctx: Context, taskId: Int): Boolean {
        val dm = ctx.resources.displayMetrics
        val w = (dm.widthPixels * 0.86f).toInt()
        val h = (dm.heightPixels * 0.55f).toInt()
        val left = (dm.widthPixels - w) / 2
        val top = (dm.heightPixels - h) / 2
        val options = ActivityOptions.makeBasic().apply {
            launchWindowingMode = WindowConfiguration.WINDOWING_MODE_FREEFORM
            launchBounds = Rect(left, top, left + w, top + h)
        }
        if (!SystemTasks.start(taskId, options.toBundle())) return false
        Handle.show(ctx.applicationContext, taskId, options.launchBounds!!)
        return true
    }

    /**
     * Android has no title bar for floating windows on phones, so the launcher puts a small bar
     * above the pop-up: drag it to move the window, a button for full screen, one to close.
     */
    @SuppressLint("StaticFieldLeak", "ClickableViewAccessibility")
    private object Handle {
        private val main = Handler(Looper.getMainLooper())
        private var view: View? = null
        private var toolbar: LinearLayout? = null
        private lateinit var toolbarLp: WindowManager.LayoutParams
        private var receiver: android.content.BroadcastReceiver? = null
        private lateinit var appContext: Context
        private var taskId = -1
        private lateinit var bounds: Rect
        private lateinit var lp: WindowManager.LayoutParams
        private lateinit var wm: WindowManager

        fun show(ctx: Context, task: Int, b: Rect) {
            hide()
            wm = ctx.getSystemService(WindowManager::class.java)
            taskId = task
            bounds = Rect(b)
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Float) = (v * density).toInt()
            fun button(text: String, onClick: () -> Unit) = TextView(ctx).apply {
                this.text = text
                setTextColor(N9.TEXT); textSize = 16f; typeface = N9.FONT_BOLD
                gravity = Gravity.CENTER
                setOnClickListener { onClick() }
            }
            val height = dp(36f)
            val bar = LinearLayout(ctx).apply {
                background = GradientDrawable().apply {
                    cornerRadii = floatArrayOf(dp(8f).toFloat(), dp(8f).toFloat(), dp(8f).toFloat(), dp(8f).toFloat(), 0f, 0f, 0f, 0f)
                    setColor(0xF0262626.toInt())
                }
                addView(button("⤢") { fullScreen() }, LinearLayout.LayoutParams(dp(56f), -1))
                // The grip in the middle, like the N9's drag handles.
                addView(View(ctx).apply {
                    background = GradientDrawable().apply { cornerRadius = dp(2f).toFloat(); setColor(0xFF8C8C8C.toInt()) }
                }, LinearLayout.LayoutParams(0, dp(4f), 1f).apply { gravity = Gravity.CENTER_VERTICAL; setMargins(dp(40f), 0, dp(40f), 0) })
                addView(button("✕") { close() }, LinearLayout.LayoutParams(dp(56f), -1))
            }
            // A system dialog type: secure overlays never get touches.
            lp = WindowManager.LayoutParams(bounds.width(), height, @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = bounds.left; y = bounds.top - height
                title = "EtesianPopup"
            }
            var downX = 0f; var downY = 0f; var startLeft = 0; var startTop = 0
            bar.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startLeft = bounds.left; startTop = bounds.top }
                    MotionEvent.ACTION_MOVE -> {
                        val dm = ctx.resources.displayMetrics
                        val left = (startLeft + (e.rawX - downX).toInt()).coerceIn(-bounds.width() / 2, dm.widthPixels - bounds.width() / 2)
                        val top = (startTop + (e.rawY - downY).toInt()).coerceIn(height, dm.heightPixels - bounds.height() - toolbarHeight)
                        bounds.offsetTo(left, top)
                        lp.x = left; lp.y = top - height
                        runCatching { wm.updateViewLayout(bar, lp) }
                        toolbar?.let { tb -> toolbarLp.x = left; toolbarLp.y = bounds.bottom; runCatching { wm.updateViewLayout(tb, toolbarLp) } }
                        runCatching { ActivityTaskManager.getService().resizeTask(taskId, Rect(bounds), ActivityTaskManager.RESIZE_MODE_USER) }
                    }
                }
                true
            }
            runCatching { wm.addView(bar, lp); view = bar }.onFailure { Log.w(TAG, "popup handle", it) }
            appContext = ctx
            listenForToolbar(ctx)
            main.postDelayed(watch, 1000)
        }

        private var toolbarHeight = 0

        /** The app's own N9 toolbar, small, under the pop-up; SystemUI tells us what's on it. */
        private fun listenForToolbar(ctx: Context) {
            val r = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.getIntExtra("task", -1) != taskId) return
                    showToolbar(ctx, intent.getBooleanExtra("back", false),
                        intent.getParcelableArrayListExtra("items", Bundle::class.java).orEmpty(),
                        intent.getParcelableExtra("callback", PendingIntent::class.java))
                }
            }
            // Only SystemUI holds this permission, so nobody else can put buttons here.
            ctx.registerReceiver(r, IntentFilter("com.bompotis.etesian.toolbar.CHANGED"),
                "android.permission.STATUS_BAR_SERVICE", null, Context.RECEIVER_EXPORTED)
            receiver = r
            ctx.sendBroadcast(Intent("com.bompotis.etesian.toolbar.QUERY").setPackage("com.android.systemui").putExtra("task", taskId))
        }

        private fun showToolbar(ctx: Context, back: Boolean, items: List<Bundle>, callback: PendingIntent?) {
            toolbar?.let { runCatching { wm.removeView(it) } }
            toolbar = null
            if (!back && items.isEmpty()) return
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Float) = (v * density).toInt()
            toolbarHeight = dp(40f)
            val tb = LinearLayout(ctx).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xFFD6D6D8.toInt(), 0xFFADADB0.toInt())).apply {
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(8f).toFloat(), dp(8f).toFloat(), dp(8f).toFloat(), dp(8f).toFloat())
                }
            }
            fun add(drawable: android.graphics.drawable.Drawable?, label: String, tint: Boolean, selected: Boolean, onClick: () -> Unit) {
                tb.addView(ImageView(ctx).apply {
                    setImageDrawable(drawable)
                    if (tint) setColorFilter(0xFF262626.toInt())
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(0, dp(9f), 0, dp(9f))
                    contentDescription = label
                    if (selected) setBackgroundColor(0x66FFFFFF)
                    setOnClickListener { onClick() }
                }, LinearLayout.LayoutParams(0, -1, 1f))
            }
            if (back) add(ctx.getDrawable(com.bompotis.etesian.launcher.R.drawable.ic_n9_back), "Back", true, false) { sendBack() }
            for (item in items) {
                if (!item.getBoolean("enabled", true)) continue
                val id = item.getString("id") ?: continue
                val icon = item.getParcelable("icon", android.graphics.drawable.Icon::class.java)
                add(icon?.loadDrawable(ctx), item.getString("label").orEmpty(), item.getBoolean("tint", true),
                    item.getBoolean("selected")) {
                    runCatching { callback?.send(ctx, 0, Intent().putExtra("id", id)) }
                }
            }
            toolbarLp = WindowManager.LayoutParams(bounds.width(), toolbarHeight, @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = bounds.left; y = bounds.bottom
                title = "EtesianPopupToolbar"
            }
            tb.visibility = view?.visibility ?: View.VISIBLE
            runCatching { wm.addView(tb, toolbarLp); toolbar = tb }.onFailure { Log.w(TAG, "popup toolbar", it) }
        }

        /** Back for the pop-up: focus its task first, so the key goes to it and not to what's behind. */
        private fun sendBack() {
            runCatching { ActivityTaskManager.getService().setFocusedTask(taskId) }
            val now = android.os.SystemClock.uptimeMillis()
            val input = appContext.getSystemService(android.hardware.input.InputManager::class.java)
            for (action in intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
                val ev = android.view.KeyEvent(now, now, action, android.view.KeyEvent.KEYCODE_BACK, 0, 0,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    android.view.KeyEvent.FLAG_FROM_SYSTEM or android.view.KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    android.view.InputDevice.SOURCE_KEYBOARD)
                runCatching { input.injectInputEvent(ev, android.hardware.input.InputManager.INJECT_INPUT_EVENT_MODE_ASYNC) }
            }
        }

        /** The bar goes with the window: hidden while it isn't showing, gone once it's closed. */
        private val watch = object : Runnable {
            override fun run() {
                val v = view ?: return
                val task = runCatching { ActivityTaskManager.getInstance().getTasks(20) }.getOrNull()
                    ?.firstOrNull { it.taskId == taskId }
                val floating = task != null &&
                    task.configuration.windowConfiguration.windowingMode == WindowConfiguration.WINDOWING_MODE_FREEFORM
                if (!floating) { hide(); return }
                v.visibility = if (task!!.isVisible) View.VISIBLE else View.GONE
                toolbar?.visibility = v.visibility
                main.postDelayed(this, 500)
            }
        }

        private fun fullScreen() {
            val id = taskId
            hide()
            val options = ActivityOptions.makeBasic().apply {
                launchWindowingMode = WindowConfiguration.WINDOWING_MODE_FULLSCREEN
            }
            SystemTasks.start(id, options.toBundle())
        }

        private fun close() {
            val id = taskId
            hide()
            SystemTasks.remove(id)
        }

        fun hide() {
            main.removeCallbacks(watch)
            view?.let { runCatching { wm.removeView(it) } }
            view = null
            toolbar?.let { runCatching { wm.removeView(it) } }
            toolbar = null
            toolbarHeight = 0
            receiver?.let { runCatching { appContext.unregisterReceiver(it) } }
            receiver = null
        }
    }
}
