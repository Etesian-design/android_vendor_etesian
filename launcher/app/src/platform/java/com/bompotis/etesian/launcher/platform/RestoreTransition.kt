package com.bompotis.etesian.launcher.platform

import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.WindowConfiguration
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.SurfaceControl
import android.view.animation.DecelerateInterpolator
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransition
import android.window.TransitionInfo
import android.window.WindowAnimationState
import com.bompotis.etesian.launcher.platform.WindowDrag.Companion.HOME_LIGHT
import com.bompotis.etesian.launcher.platform.WindowDrag.Companion.HOME_SCALE

/**
 * N9 restore from Open applications: the application slides in from the side the last one
 * was swiped away to ([fromRight]; the left until then) while home
 * behind it sinks back into the dark, the reverse of minimising. Android ignores custom
 * animations for tasks brought back from recents, so we run the transition ourselves.
 */
object RestoreTransition {
    private const val TAG = "EtesianRestore"
    private const val DURATION = 300L
    private val main = Handler(Looper.getMainLooper())

    /** N9: the next application comes in from the side the last one was swiped out to. */
    @Volatile var fromRight = false

    fun options(): Bundle =
        ActivityOptions.makeRemoteTransition(RemoteTransition(Runner(null), "EtesianRestore")).toBundle()

    /**
     * N9 application start: no Android starting window (splash), so the transition only begins
     * once the application has drawn itself; [onReady] runs then (the launcher's loading
     * indicator stops) and the application slides in.
     */
    fun launchOptions(onReady: () -> Unit): Bundle =
        ActivityOptions.makeRemoteTransition(RemoteTransition(Runner(onReady), "EtesianLaunch")).apply {
            setDisableStartingWindow(true)
        }.toBundle()

    private class Runner(private var onReady: (() -> Unit)?) : IRemoteTransition.Stub() {
        private fun ready() {
            val r = onReady ?: return
            onReady = null
            main.post(r)
        }

        override fun startAnimation(token: IBinder, info: TransitionInfo, t: SurfaceControl.Transaction,
                                    finishCallback: IRemoteTransitionFinishedCallback) {
            ready()
            Log.i(TAG, "enter from " + if (fromRight) "right" else "left")
            val apps = mutableListOf<TransitionInfo.Change>()
            val homes = mutableListOf<TransitionInfo.Change>()
            info.changes.forEach { c ->
                val home = c.taskInfo?.topActivityType == WindowConfiguration.ACTIVITY_TYPE_HOME
                when {
                    home -> homes += c
                    TransitionInfo.isIndependent(c, info) && c.taskInfo != null &&
                        (c.mode == android.view.WindowManager.TRANSIT_OPEN ||
                            c.mode == android.view.WindowManager.TRANSIT_TO_FRONT) -> apps += c
                }
                t.show(c.leash)
                t.setAlpha(c.leash, 1f)
            }
            place(t, apps, homes, 0f)
            t.apply()
            main.post {
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = DURATION
                    interpolator = DecelerateInterpolator(1.6f)
                    addUpdateListener { a ->
                        SurfaceControl.Transaction().also { place(it, apps, homes, a.animatedFraction) }.apply()
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            runCatching { finishCallback.onTransitionFinished(null, null) }
                                .onFailure { Log.w(TAG, "finish", it) }
                        }
                    })
                    start()
                }
            }
        }

        override fun mergeAnimation(transition: IBinder, info: TransitionInfo, t: SurfaceControl.Transaction,
                                    mergeTarget: IBinder, finishCallback: IRemoteTransitionFinishedCallback) {}

        override fun takeOverAnimation(transition: IBinder, info: TransitionInfo, t: SurfaceControl.Transaction,
                                       finishCallback: IRemoteTransitionFinishedCallback,
                                       states: Array<WindowAnimationState>) {}

        override fun onTransitionConsumed(transition: IBinder, aborted: Boolean) = ready()
    }

    /** f = 0: application just off the left (or right, [fromRight]) edge, home fully lit; f = 1: the reverse. */
    private fun place(t: SurfaceControl.Transaction, apps: List<TransitionInfo.Change>,
                      homes: List<TransitionInfo.Change>, f: Float) {
        apps.forEach { c ->
            val end = c.endRelOffset
            val w = c.endAbsBounds.width()
            val off = w * (1 - f)
            t.setPosition(c.leash, if (fromRight) end.x + off else end.x - off, end.y.toFloat())
        }
        homes.forEach { c ->
            val b = c.endAbsBounds
            val scale = 1f - (1 - HOME_SCALE) * f
            t.setScale(c.leash, scale, scale)
            t.setPosition(c.leash, c.endRelOffset.x + b.width() * (1 - scale) / 2,
                c.endRelOffset.y + b.height() * (1 - scale) / 2)
            t.setAlpha(c.leash, 1f - (1 - HOME_LIGHT) * f)
        }
    }
}
