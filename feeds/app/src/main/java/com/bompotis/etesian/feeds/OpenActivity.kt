package com.bompotis.etesian.feeds

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Opens one item for the launcher (Events, the Belle widgets) with a fixed back stack: Feeds ›
 * the item's feed (from a widget, [EXTRA_FEED] set) or All posts (from Events) › the reader.
 *
 * The launcher can't start the reader straight into our task: Android brings a task another app
 * owns to the front without adding the activity (so a tap on an item while Feeds was open showed
 * whatever page was open), and a reader started on its own had nothing under it for back. This
 * runs in a task of its own (taskAffinity), so it starts as ours, rebuilds the Feeds task and goes.
 */
class OpenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(ReaderActivity.EXTRA_ID).orEmpty()
        val link = intent.getStringExtra(ReaderActivity.EXTRA_LINK).orEmpty()
        val feed = intent.getStringExtra(EXTRA_FEED)?.takeIf { it.isNotEmpty() }
        runCatching { startActivities(stack(id, link, feed)) }
        finish()
    }

    private fun stack(id: String, link: String, feed: String?): Array<Intent> = arrayOf(
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        Intent(this, PostsActivity::class.java).putExtra(PostsActivity.EXTRA_FEED, feed),
        Intent(this, ReaderActivity::class.java)
            .putExtra(ReaderActivity.EXTRA_ID, id).putExtra(ReaderActivity.EXTRA_LINK, link),
    )

    companion object {
        /** The feed's URL when opened from a widget; absent (or empty) from Events. */
        const val EXTRA_FEED = "feed"
    }
}
