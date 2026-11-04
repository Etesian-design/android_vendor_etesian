package com.bompotis.etesian.launcher.platform

import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.ActivityThread
import android.app.PendingIntent
import android.app.WindowConfiguration
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.animation.DecelerateInterpolator
import android.window.TaskSnapshot
import android.window.TransitionInfo
import com.android.wm.shell.recents.IRecentsAnimationController
import com.android.wm.shell.recents.IRecentsAnimationRunner
import com.bompotis.etesian.launcher.HomeActivity
import com.bompotis.etesian.launcher.N9Sounds
import com.bompotis.etesian.launcher.SystemTasks
import com.bompotis.etesian.launcher.platform.EdgeGestures.Edge
import kotlin.math.abs

/**
 * Holds the foreground app's window during an edge swipe, the way Launcher3 does for its
 * swipe-up: a recents transition gives us the window's surface, which follows the finger
 * sideways (or down for close), with Open applications coming out of the dark behind it.
 */
class WindowDrag(
    private val ctx: Context,
    private val edge: Edge,
    private val taskId: Int,
    private val closing: Boolean,
) {
    companion object {
        private const val TAG = "EtesianDrag"
        /** Fraction of the screen past which letting go minimises/closes. */
        const val COMMIT = 0.5f
        /** How home looks behind the application before the swipe starts. */
        const val HOME_SCALE = 0.9f
        const val HOME_LIGHT = 0.25f
        private const val FLING_DP_PER_S = 900f
        private val main = Handler(Looper.getMainLooper())
    }


    private var controller: IRecentsAnimationController? = null
    private var apps: List<RemoteAnimationTarget> = emptyList()
    private var homes: List<RemoteAnimationTarget> = emptyList()
    private var progress = 0f
    private var across = 0f
    private var ended = false
    private var finishWhenReady: Boolean? = null
    private var animator: ValueAnimator? = null
    /** False when SystemUI isn't connected: then letting go just switches views, unanimated. */
    private var running = false
    /** The display's corner radius in px (0 on square screens). */
    private val cornerRadius: Float = ctx.getSystemService(android.hardware.display.DisplayManager::class.java)
        ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        ?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f

    fun begin() {
        val tasks = ProxyService.recentTasks ?: return
        val intent = HomeActivity.switcherIntent(ctx)
        val pending = PendingIntent.getActivity(ctx, 0, Intent().setPackage(ctx.packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT or
                Intent.FILL_IN_COMPONENT or PendingIntent.FLAG_CANCEL_CURRENT,
            ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle())
        val options = ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            setTransientLaunch()
        }
        running = runCatching {
            tasks.startRecentsTransition(pending, intent, options.toBundle(), null,
                ActivityThread.currentActivityThread().applicationThread, runner)
        }.onFailure { Log.w(TAG, "startRecentsTransition", it) }.isSuccess
    }

    private val runner = object : IRecentsAnimationRunner.Stub() {
        override fun onAnimationStart(
            controller: IRecentsAnimationController, apps: Array<RemoteAnimationTarget>,
            wallpapers: Array<RemoteAnimationTarget>?, homeContentInsets: Rect?, extras: Bundle?,
            info: TransitionInfo?,
        ) {
            Log.d(TAG, "start apps=${apps.map { "${it.taskId}/${it.mode}/${it.windowConfiguration.activityType}" }} " +
                "changes=${info?.changes?.map { "${it.taskInfo?.taskId}/${it.mode}/${it.taskInfo?.topActivityType}" }}")
            main.post {
                this@WindowDrag.controller = controller
                this@WindowDrag.apps = apps.filter { it.mode == RemoteAnimationTarget.MODE_CLOSING }
                homes = apps.filter { it.mode == RemoteAnimationTarget.MODE_OPENING }
                SurfaceControl.Transaction().apply {
                    apps.forEach { show(it.leash) }
                    apply()
                }
                apply()
                finishWhenReady?.let { if (it) commit() else cancel() }
            }
        }

        override fun onAnimationCanceled(taskIds: IntArray?, taskSnapshots: Array<TaskSnapshot>?) {
            main.post { ended = true; controller = null }
        }

        override fun onTasksAppeared(app: Array<RemoteAnimationTarget>?, transitionInfo: TransitionInfo?) {}
    }

    fun update(progress: Float, across: Float) {
        if (ended) return
        this.progress = progress
        this.across = across
        apply()
    }

    /**
     * As on the N9: past half the screen, or a quick flick inward. Moving back towards the edge
     * when letting go (the user changed their mind) always slides the application back.
     */
    fun shouldCommit(inwardVelocityDp: Float, distance: Float = COMMIT) = when {
        inwardVelocityDp < -FLING_DP_PER_S / 3 -> false
        inwardVelocityDp > FLING_DP_PER_S -> progress > 0.08f
        else -> progress > distance
    }

    /**
     * Places everything for the current progress, as on the N9: the application only slides
     * along with the finger, while home behind it comes out of the dark, brightening from a
     * quarter and zooming in slightly to full size.
     */
    private fun apply(p: Float = progress) {
        val t = SurfaceControl.Transaction()
        apps.forEach { target ->
            val b = target.screenSpaceBounds
            // The screen's own rounded corners, which the window keeps while it moves. The radius
            // only applies within a crop (Android 17 no longer falls back to the window's bounds).
            t.setWindowCrop(target.leash, b.width(), b.height())
            t.setCornerRadius(target.leash, cornerRadius)
            val distance = p * (if (edge == Edge.LEFT || edge == Edge.RIGHT) b.width() else b.height())
            when (edge) {
                Edge.LEFT -> t.setPosition(target.leash, b.left + distance, b.top.toFloat())
                Edge.RIGHT -> t.setPosition(target.leash, b.left - distance, b.top.toFloat())
                Edge.TOP -> t.setPosition(target.leash, b.left.toFloat(), b.top + distance)
                Edge.BOTTOM -> t.setPosition(target.leash, b.left.toFloat(), b.top - distance)
            }
        }
        homes.forEach { target ->
            val b = target.screenSpaceBounds
            val scale = HOME_SCALE + (1 - HOME_SCALE) * p
            t.setScale(target.leash, scale, scale)
            t.setPosition(target.leash, b.left + b.width() * (1 - scale) / 2, b.top + b.height() * (1 - scale) / 2)
            t.setWindowCrop(target.leash, b.width(), b.height())
            t.setCornerRadius(target.leash, cornerRadius / scale)
            t.setAlpha(target.leash, HOME_LIGHT + (1 - HOME_LIGHT) * p)
        }
        t.apply()
    }

    /** Lets go past the threshold: the window slides on off screen, home comes fully into the light. */
    fun commit() {
        if (ended) return
        // Swiped in from the left edge, the window leaves to the right: the next one comes from there.
        if (!closing && (edge == Edge.LEFT || edge == Edge.RIGHT)) RestoreTransition.fromRight = edge == Edge.LEFT
        // Close first, then show Open applications: 2026-11-04 it was the other way round, so the
        // closed application's card stayed there, full size, as if nothing had closed.
        if (!running) {
            ended = true
            if (closing) closeTask()
            HomeActivity.showSwitcherStill(ctx)
            return
        }
        if (controller == null) { finishWhenReady = true; return }
        ended = true
        animateTo(from = progress, to = 1f) {
            finish(toHome = true)
            if (closing) closeTask()
            // The transient launch doesn't deliver our intent; make sure home shows Open applications.
            HomeActivity.showSwitcherStill(ctx)
        }
    }

    private fun closeTask() {
        N9Sounds.play(ctx, N9Sounds.CLOSE)
        SystemTasks.remove(taskId)
    }

    /** Another application is opening on top: just end the transition with home underneath. */
    fun commitQuiet() {
        if (ended) return
        ended = true
        if (controller == null) { finishWhenReady = true; ended = false; return }
        finish(toHome = true)
    }

    /**
     * Rests the window at [to] (the app peek: just above the row of applications) and keeps the
     * transition open until [commitQuiet] (an icon opened) or [cancel] (back to the application).
     */
    fun settle(to: Float) {
        if (ended) return
        if (controller == null) { progress = to; return }
        animateTo(from = progress, to = to) { progress = to }
    }

    /** Lets go early: the window springs back and the app carries on. */
    fun cancel() {
        if (ended || !running) { ended = true; return }
        if (controller == null) { finishWhenReady = false; return }
        ended = true
        animateTo(from = progress, to = 0f) { finish(toHome = false) }
    }

    private fun animateTo(from: Float, to: Float, end: () -> Unit) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (220 + 180 * abs(to - from)).toLong()
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val f = it.animatedFraction
                apply(from + (to - from) * f)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = end()
            })
            start()
        }
    }

    private fun finish(toHome: Boolean) {
        val c = controller ?: return
        controller = null
        runCatching { c.finish(toHome, toHome, null) }.onFailure { Log.w(TAG, "finish", it) }
    }
}
