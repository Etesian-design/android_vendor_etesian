package com.bompotis.etesian.launcher

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.max

/**
 * Pull down to refresh, N9 style: no Android circle. At the top of the list a pull draws the list down (with
 * resistance) and a line of text above it says what letting go does: "Pull down to refresh", then "Release to
 * refresh"; let go past it and [onRefresh] runs. Glance's PullRefresh, on the home screen's page: the label
 * comes in under the search bar ([labelTop], the list's top padding). The list is added through [setContent].
 */
class PullRefresh(ctx: Context, private val onRefresh: () -> Unit) : FrameLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    /** How far (dp, after resistance) the list must come down for a refresh. */
    private val threshold = 72 * d
    private val label = TextView(ctx).apply {
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(N9.TEXT_SECONDARY)
        typeface = N9.FONT
        alpha = 0f
    }
    private lateinit var content: View
    private var downX = 0f
    private var downY = 0f
    private var pulling = false
    private var pull = 0f
    /** Where the list's first row starts (under the floating search bar). */
    var labelTop = 0f

    init { addView(label, LayoutParams(LayoutParams.MATCH_PARENT, threshold.toInt(), Gravity.TOP)) }

    fun setContent(v: View) { content = v; addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; pulling = false }
            // Mostly downwards only: a sideways swipe (the home screen's pages) is never a pull.
            MotionEvent.ACTION_MOVE -> if (!pulling && e.y - downY > slop && e.y - downY > 2 * kotlin.math.abs(e.x - downX) &&
                !content.canScrollVertically(-1)) {
                pulling = true; downY = e.y
            }
        }
        return pulling
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (pulling) {
                // Half the finger's travel: the list comes down heavier than the finger.
                pull = max(0f, (e.y - downY) * 0.5f)
                content.translationY = pull
                label.translationY = labelTop + pull - threshold
                label.alpha = (pull / threshold).coerceAtMost(1f)
                label.text = if (pull >= threshold) "Release to refresh" else "Pull down to refresh"
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pulling) {
                val go = e.actionMasked == MotionEvent.ACTION_UP && pull >= threshold
                pulling = false; pull = 0f
                content.animate().translationY(0f).setDuration(200).start()
                label.animate().translationY(labelTop - threshold).alpha(0f).setDuration(200).start()
                if (go) onRefresh()
            }
        }
        return true
    }
}
