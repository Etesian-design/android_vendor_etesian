package com.bompotis.etesian.launcher.platform

import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController
import com.bompotis.etesian.launcher.Platform

/**
 * N9 unlock, home's half: SystemUI (KeyguardUnlockAnimationController) calls this when the
 * keyguard goes away with home underneath. Home starts dimmed (25%) and scaled to 0.9, then
 * zooms to full size and brightness. The lock screen itself has already been swiped away by
 * SystemUI's EtesianKeyguardSwipe; for a fingerprint unlock this is the whole animation.
 */
class UnlockAnimation(private val ctx: android.content.Context) : ILauncherUnlockAnimationController.Stub() {

    private val main = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null
    private var amount = 1f
    /** SystemUI is moving home with the lock screen slide (vs a fingerprint unlock). */
    private var driven = false

    private fun n9Mode() = Settings.Secure.getInt(ctx.contentResolver, "etesian_gestures", 1) == 1

    private fun apply(a: Float) {
        amount = a
        val view = Platform.homeContent ?: return
        val scale = START_SCALE + (1f - START_SCALE) * a
        view.scaleX = scale
        view.scaleY = scale
        view.alpha = START_ALPHA + (1f - START_ALPHA) * a
    }

    override fun prepareForUnlock(animateSmartspace: Boolean, lockscreenSmartspaceBounds: Rect?, selectedPage: Int) {
        main.post {
            if (!n9Mode()) return@post
            animator?.cancel()
            // Unlocking returns to whichever home pane was showing when the phone locked.
            driven = false
            apply(0f)
        }
    }

    override fun setUnlockAmount(amount: Float, forceIfAnimating: Boolean) {
        main.post {
            if (animator != null && !forceIfAnimating) return@post
            animator?.cancel()
            driven = amount < 1f || driven
            apply(amount.coerceIn(0f, 1f))
        }
    }

    override fun playUnlockAnimation(unlocked: Boolean, duration: Long, startDelay: Long) {
        main.post {
            animator?.cancel()
            if (!unlocked || !n9Mode()) { apply(1f); return@post }
            // Followed the slide: finish from where it is (nothing left to do at 1). Otherwise
            // (fingerprint; prepareForUnlock isn't always called first) zoom from the far end.
            if (!driven && amount >= 1f) apply(0f)
            driven = false
            animator = ValueAnimator.ofFloat(amount, 1f).apply {
                // Short and snappy (fingerprint ~200ms), whatever SystemUI's canned timing is.
                this.duration = ZOOM_MS
                this.startDelay = startDelay.coerceAtMost(MAX_DELAY_MS)
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { apply(it.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (animator === animation) animator = null
                        apply(1f)
                    }
                })
                start()
            }
        }
    }

    override fun setSmartspaceSelectedPage(selectedPage: Int) {}
    override fun setSmartspaceVisibility(visibility: Int) {}
    override fun dispatchSmartspaceStateToSysui() {}

    private companion object {
        const val START_SCALE = 0.9f
        const val START_ALPHA = 0.25f
        const val ZOOM_MS = 220L
        const val MAX_DELAY_MS = 60L
    }
}
