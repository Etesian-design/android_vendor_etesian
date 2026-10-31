package com.bompotis.etesian.launcher.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.bompotis.etesian.launcher.AppInfo
import com.bompotis.etesian.launcher.AppRepository
import com.bompotis.etesian.launcher.Prefs
import com.bompotis.etesian.launcher.RunningApps

/**
 * N9: swipe up from the bottom and hold, and the first row of the Applications view slides up
 * into the gap under the application, each icon zooming out slightly, with a sound. Letting go
 * over an icon opens it.
 */
class AppPeek(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val apps: List<AppInfo> = AppRepository.apps.take(Prefs.gridColumns(ctx))
    private val icons = mutableListOf<ImageView>()
    private var root: FrameLayout? = null
    private var hovered = -1

    val isEmpty get() = apps.isEmpty()

    /** Shows the row in the bottom [height] pixels revealed by the swipe. */
    fun show(height: Int) {
        if (apps.isEmpty()) return
        val size = (Prefs.iconSizeDp(ctx) * density).toInt()
        val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        apps.forEachIndexed { i, app ->
            val icon = ImageView(ctx).apply {
                setImageBitmap(app.icon)
                scaleX = 1.25f; scaleY = 1.25f
                alpha = 0f
                translationY = 40 * density
            }
            icons += icon
            row.addView(icon, LinearLayout.LayoutParams(0, size, 1f))
            icon.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setStartDelay(i * 30L).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        val frame = FrameLayout(ctx).apply {
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, size, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, height,
            WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM
            title = "EtesianPeek"
        }
        runCatching { wm.addView(frame, lp); root = frame }
            .onFailure { android.util.Log.w("EtesianPeek", "addView", it) }
        ctx.getSystemService(AudioManager::class.java).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.6f)
    }

    /** Lifts the icon under the finger. */
    fun hover(x: Float, y: Float) {
        val i = hit(x, y)
        if (i == hovered) return
        icons.getOrNull(hovered)?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
        icons.getOrNull(i)?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.setDuration(120)?.start()
        hovered = i
    }

    private fun hit(x: Float, y: Float): Int {
        val r = Rect()
        icons.forEachIndexed { i, v ->
            val loc = IntArray(2).also { v.getLocationOnScreen(it) }
            r.set(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
            if (r.contains(x.toInt(), y.toInt())) return i
        }
        return -1
    }

    /** Opens the app under the finger, if any. */
    fun launchAt(x: Float, y: Float): Boolean {
        val app = apps.getOrNull(hit(x, y)) ?: return false
        val v = icons[apps.indexOf(app)]
        val loc = IntArray(2).also { v.getLocationOnScreen(it) }
        val bounds = Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        runCatching {
            ctx.getSystemService(LauncherApps::class.java).startMainActivity(app.component, app.user, bounds, null)
        }.onSuccess { RunningApps.touch(ctx, app.component.packageName) }
        return true
    }

    fun hide() {
        val r = root ?: return
        root = null
        r.animate().alpha(0f).setDuration(150).withEndAction { runCatching { wm.removeView(r) } }.start()
    }
}
