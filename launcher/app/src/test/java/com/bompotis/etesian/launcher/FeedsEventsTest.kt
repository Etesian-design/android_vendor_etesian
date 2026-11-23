package com.bompotis.etesian.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** Read feed items leave the Events view (the widgets keep them, without the orange dot). */
class FeedsEventsTest {
    private fun item(id: String, read: Boolean) =
        Feeds.Item("https://example.org/feed", "Example", "Post $id", "", "https://example.org/$id", 0L, id, read)

    @Test
    fun eventsListsOnlyUnreadItems() {
        val all = listOf(item("1", false), item("2", true), item("3", false))
        assertEquals(listOf("1", "3"), Feeds.forEvents(all).map { it.id })
    }

    @Test
    fun feedsTheUserHidFromEventsStayOut() {
        val other = Feeds.Item("https://other.org/feed", "Other", "Post 4", "", "https://other.org/4", 0L, "4")
        val all = listOf(item("1", false), other)
        assertEquals(listOf("4"), Feeds.forEvents(all, hidden = setOf("https://example.org/feed")).map { it.id })
    }

    @Test
    fun itemsDefaultToUnread() {
        // An older Feeds app without the "read" column: everything still shows.
        val old = Feeds.Item("u", "t", "title", "", "", 0L, "x")
        assertEquals(listOf(old), Feeds.forEvents(listOf(old)))
    }
}
