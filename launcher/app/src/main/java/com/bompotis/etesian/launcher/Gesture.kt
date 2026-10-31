package com.bompotis.etesian.launcher

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

/** Tests only: injects one- or two-finger touch paths through the system's input (INJECT_EVENTS). */
object Gesture {
    private fun inject(ev: MotionEvent) {
        val im = Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null)!!
        im.javaClass.getMethod("injectInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType).invoke(im, ev, 2)
        ev.recycle()
    }

    private fun event(down: Long, action: Int, pts: List<Pair<Float, Float>>): MotionEvent {
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pts.size) { i -> MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    fun play(frames: List<List<Float>>, steps: Int, ms: Long, hold: Long) {
        if (frames.isEmpty()) return
        val two = frames[0].size >= 4 && frames[0][2] >= 0
        fun pts(f: List<Float>) = if (two) listOf(f[0] to f[1], f[2] to f[3]) else listOf(f[0] to f[1])
        val down = SystemClock.uptimeMillis()
        inject(event(down, MotionEvent.ACTION_DOWN, pts(frames[0]).take(1)))
        if (two) { SystemClock.sleep(30); inject(event(down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pts(frames[0]))) }
        var last = frames[0]
        for (f in frames.drop(1)) {
            for (s in 1..steps) {
                val k = s / steps.toFloat()
                val cur = last.indices.map { last[it] + (f[it] - last[it]) * k }
                SystemClock.sleep(ms); inject(event(down, MotionEvent.ACTION_MOVE, pts(cur)))
            }
            last = f
        }
        if (hold > 0) SystemClock.sleep(hold)
        if (two) inject(event(down, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pts(last)))
        inject(event(down, MotionEvent.ACTION_UP, pts(last).take(1)))
    }
}
