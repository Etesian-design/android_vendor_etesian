package com.bompotis.etesian.launcher

import android.os.Bundle

/**
 * Hooks filled in by the ROM-only code in app/src/platform, so the rest of the launcher
 * can use it without depending on internal APIs. Null in the plain Gradle build.
 */
object Platform {
    /**
     * Launch options for starting an application the N9 way: no splash, and [onReady] is called
     * when the application has drawn and starts sliding in.
     */
    @Volatile var launchOptions: ((onReady: () -> Unit) -> Bundle)? = null

    /** Launch options that restore a task with the N9 slide-in. */
    @Volatile var restoreOptions: (() -> Bundle)? = null

    /**
     * True while an edge swipe is in progress. Android hands the rest of that touch to home
     * once the app starts moving away; home must not treat it as its own swipe.
     */
    @Volatile var edgeGestureActive = false

    /** Home's content view, scaled/dimmed by the N9 unlock animation. Main thread only. */
    var homeContent: android.view.View? = null

    /** Puts two tasks side by side in split screen, the first on the left/top. */
    @Volatile var splitTasks: ((first: Int, second: Int) -> Boolean)? = null

    /** The split-screen pair (top/left task, bottom/right task), if there is one. */
    @Volatile var splitPair: (() -> Pair<Int, Int>?)? = null

    /** Takes a split pair apart; [top] stays in front. */
    @Volatile var unsplit: ((top: Int) -> Boolean)? = null

    /** Brings a task back as a floating window over whatever is showing. */
    @Volatile var popupTask: ((taskId: Int) -> Boolean)? = null
}
