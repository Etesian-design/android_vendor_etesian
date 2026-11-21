package com.bompotis.etesian.launcher.standby

import com.bompotis.etesian.launcher.standby.StandbyConfig.Line
import com.bompotis.etesian.launcher.standby.StandbyConfig.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyTest {
    private val battery = Line(listOf(Part.Text("Battery: "), Part.Item("battery"), Part.Text("%")))
    private val song = Line(listOf(Part.Item("song")), empty = "No media")

    @Test fun `words and items make the line`() =
        assertEquals("Battery: 98%", StandbyItems.text(battery, mapOf("battery" to "98")))

    @Test fun `a line whose items are all empty gives way to its empty text`() =
        assertNull(StandbyItems.text(song, mapOf("song" to "")))

    @Test fun `a line of words only always shows`() =
        assertEquals("Hello", StandbyItems.text(Line(listOf(Part.Text("Hello"))), emptyMap()))

    @Test fun `settings survive a save`() {
        val c = StandbyConfig(lines = listOf(battery, song), color = StandbyConfig.COLOURS[1], size = 18,
            align = StandbyConfig.Align.RIGHT, clock = false, battery = true, icons = false)
        assertEquals(c, StandbyConfig.fromJson(c.toJson()))
    }

    @Test fun `nothing saved is the N9's own`() {
        val c = StandbyConfig.fromJson(null)
        assertTrue(c.lines.isEmpty()); assertTrue(c.clock); assertTrue(c.icons)
    }

    @Test fun `broken settings fall back instead of crashing the standby screen`() =
        assertEquals(StandbyConfig(), StandbyConfig.fromJson("{not json"))

    @Test fun `every preset uses real items`() {
        val ids = StandbyItems.ALL.map { it.id }.toSet()
        StandbyConfig.PRESETS.forEach { (name, c) ->
            c.lines.flatMap { it.parts }.filterIsInstance<Part.Item>().forEach { assertTrue("$name: ${it.id}", it.id in ids) }
        }
    }

    @Test fun uptime() {
        assertEquals("12 min", StandbyItems.uptime(12 * 60_000L))
        assertEquals("4 h 12 min", StandbyItems.uptime((4 * 60 + 12) * 60_000L))
        assertEquals("3 d 4 h", StandbyItems.uptime(((3 * 24 + 4) * 60 + 5) * 60_000L))
    }
}
