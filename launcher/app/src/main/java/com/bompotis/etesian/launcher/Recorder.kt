package com.bompotis.etesian.launcher

import android.content.Context
import android.provider.Settings

/**
 * The strip's Screen recorder tile (mock 1a): SystemUI (ScreenRecordingCoreStartable) watches
 * Settings.Secure etesian_screen_record for "start"/"stop" and publishes the recording's start
 * time in etesian_screen_recording (0 = not recording). Whole screen, no audio.
 */
object Recorder {
    private const val CMD = "etesian_screen_record"
    private const val STATE = "etesian_screen_recording"

    fun since(ctx: Context): Long = runCatching { Settings.Secure.getLong(ctx.contentResolver, STATE, 0L) }.getOrDefault(0L)
    fun on(ctx: Context) = since(ctx) > 0L
    fun start(ctx: Context) = send(ctx, "start")
    fun stop(ctx: Context) = send(ctx, "stop")
    /** SystemUI shows its "Stop recording?" dialog (the status-bar dot's tap). */
    fun askToStop(ctx: Context) = send(ctx, "ask")
    fun toggle(ctx: Context) = if (on(ctx)) stop(ctx) else start(ctx)

    // A timestamp makes every command a change, so the observer always fires.
    private fun send(ctx: Context, what: String) {
        runCatching { Settings.Secure.putString(ctx.contentResolver, CMD, "$what:${System.currentTimeMillis()}") }
    }
}
