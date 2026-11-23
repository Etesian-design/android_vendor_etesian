package com.bompotis.etesian.launcher.standby

import android.app.AlarmManager
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import android.view.Display
import com.bompotis.etesian.launcher.EventsStore
import kotlin.random.Random

/**
 * The standby screen: Android's doze screen while the device is locked (the framework hands it this service instead
 * of SystemUI's while Etesian mode and the standby screen are on). The display stays in its low-power state with
 * only the block lit ([StandbyView]); once a minute, on an exact alarm (the CPU sleeps between, the display keeps
 * its last frame), the clock and items update and the block moves to a new place anywhere on the screen, so no
 * pixel stays lit. A tap or the power key wakes the device as usual (the service takes no touches).
 */
class StandbyDream : DreamService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var view: StandbyView
    private val alarms by lazy { getSystemService(AlarmManager::class.java) }
    // Awake until the frame is drawn: the alarm's own wakelock ends when onAlarm returns, before the draw.
    private val wake by lazy {
        getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "etesian:standby")
    }
    // Rescheduled first, so a failing update can never end the minute updates (2026-11-22: the clock stuck).
    private val tick = AlarmManager.OnAlarmListener {
        wake.acquire(3_000)
        schedule()
        runCatching { update(move = true) }.onFailure { Log.w(TAG, "update failed", it) }
        main.postDelayed({ if (wake.isHeld) wake.release() }, 500)
    }
    private val events: () -> Unit = { main.post { if (::view.isInitialized) runCatching { update(move = false) } } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        view = StandbyView(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        setContentView(view)
        // The standby screen doesn't turn with the phone.
        window?.let { w -> w.attributes = w.attributes.apply {
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT } }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        schedule()
        runCatching { update(move = true) }.onFailure { Log.w(TAG, "update failed", it) }
        runCatching {
            setDozeScreenState(Display.STATE_DOZE)
            startDozing()
        }.onFailure { Log.w(TAG, "could not doze", it) }
        EventsStore.addListener(events)
        Log.i(TAG, "standby on")
    }

    override fun onDreamingStopped() {
        alarms?.cancel(tick)
        if (wake.isHeld) wake.release()
        EventsStore.removeListener(events)
        super.onDreamingStopped()
    }

    /** The next whole minute. */
    private fun schedule() {
        val now = System.currentTimeMillis()
        alarms?.setExact(AlarmManager.RTC_WAKEUP, now - now % 60_000 + 60_000, "etesian:standby", tick, main)
    }

    private fun update(move: Boolean) {
        view.config = StandbyConfig.load(this)
        view.values = StandbyItems.values(this)
        view.icons = icons()
        if (move) { view.fx = Random.nextFloat(); view.fy = Random.nextFloat() }
        view.invalidate()
    }

    /** One icon per notifying app, with its count (the N9's missed calls, messages, email...). */
    private fun icons() = EventsStore.items.groupBy { it.pkg }.mapNotNull { (_, list) ->
        list.firstNotNullOfOrNull { it.smallIcon }?.loadDrawable(this)?.mutate()?.let { it to list.size }
    }.take(6)

    companion object { private const val TAG = "EtesianStandby" }
}
