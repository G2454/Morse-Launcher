package dev.morse.core

import dev.morse.core.layout.Cell
import dev.morse.core.layout.GridMetrics
import dev.morse.core.layout.GridSpan
import dev.morse.core.reminders.Reminder
import dev.morse.core.reminders.Reminders
import dev.morse.core.time.ClockFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class MiscTest {

    @Test
    fun clockText24Hour() {
        val t = ClockFormat.format(LocalDateTime.of(2026, 10, 13, 10, 42), use24Hour = true, locale = Locale.ENGLISH)
        assertEquals("10:42", t.time)
        assertEquals("TUE 13 OCT", t.dateLine)
        assertEquals("TUE 13", t.shortDate)
        assertEquals("TUESDAY", t.weekday)
        assertEquals("", t.meridiem)
        assertEquals(321f, t.hourAngle, 0.001f)
        assertEquals(252f, t.minuteAngle, 0.001f)
    }

    @Test
    fun clockText12HourAndPadding() {
        val t = ClockFormat.format(LocalDateTime.of(2026, 10, 13, 0, 5), use24Hour = false, locale = Locale.ENGLISH)
        assertEquals("12:05", t.time)
        assertEquals("AM", t.meridiem)
        val t24 = ClockFormat.format(LocalDateTime.of(2026, 10, 13, 7, 5), use24Hour = true, locale = Locale.ENGLISH)
        assertEquals("07:05", t24.time)
    }

    @Test
    fun clockFromMillis() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val millis = LocalDateTime.of(2026, 10, 13, 10, 42).atZone(zone).toInstant().toEpochMilli()
        assertEquals("10:42", ClockFormat.format(millis, zone, true, Locale.ENGLISH).time)
    }

    @Test
    fun nextMinute() {
        assertEquals(60_000L, ClockFormat.millisToNextMinute(120_000L))
        assertEquals(1L, ClockFormat.millisToNextMinute(119_999L))
    }

    @Test
    fun remindersFlow() {
        var n = 0
        val ids = { "r${n++}" }
        var list = Reminders.add(emptyList(), "  Call   the bank ", ids)
        list = Reminders.add(list, "Gym bag", ids)
        list = Reminders.add(list, "Pay rent", ids)
        assertSame(list, Reminders.add(list, "   ", ids))
        assertEquals("Call the bank", list[0].text)
        list = Reminders.toggle(list, "r0")
        assertEquals(1 to 3, Reminders.progress(list))
        assertEquals(listOf("Gym bag", "Pay rent", "Call the bank"), Reminders.forWidget(list, 3).map { it.text })
        assertEquals(list, Reminders.decode(Reminders.encode(list)))
        assertEquals(2, Reminders.clearDone(list).size)
        assertEquals(2, Reminders.remove(list, "r1").size)
        assertTrue(Reminders.decode("[{\"text\":\"no id\"}]").isEmpty())
    }

    @Test
    fun remindersAreCapped() {
        val full = List(Reminders.MAX_ITEMS) { Reminder("$it", "x") }
        assertSame(full, Reminders.add(full, "one more"))
        assertEquals(Reminders.MAX_TEXT, Reminders.add(emptyList(), "y".repeat(500)).single().text.length)
    }

    @Test
    fun hostedWidgetSpans() {
        // Android 12+ target size wins.
        assertEquals(GridSpan(3, 1), GridMetrics.hostedSpan(10f, 10f, 3, 1, 90f, 100f, 4, 6))
        // Older providers: minimum size rounded up to cells, clamped to the grid.
        assertEquals(GridSpan(2, 2), GridMetrics.hostedSpan(110f, 150f, 0, 0, 90f, 100f, 4, 6))
        assertEquals(GridSpan(4, 1), GridMetrics.hostedSpan(900f, 10f, 0, 0, 90f, 100f, 4, 6))
    }

    @Test
    fun snapToCellKeepsItemsInside() {
        assertEquals(Cell(1, 2), GridMetrics.snapToCell(95f, 230f, 91f, 100f, GridSpan.ONE, 4, 6))
        assertEquals(Cell(2, 4), GridMetrics.snapToCell(900f, 900f, 91f, 100f, GridSpan(2, 2), 4, 6))
        assertEquals(Cell(0, 0), GridMetrics.snapToCell(-50f, -50f, 91f, 100f, GridSpan.ONE, 4, 6))
    }

    @Test
    fun dockSlots() {
        assertEquals(0, GridMetrics.dockSlotAt(10f, 0f, 400f))
        assertEquals(3, GridMetrics.dockSlotAt(399f, 0f, 400f))
        assertEquals(-1, GridMetrics.dockSlotAt(401f, 0f, 400f))
    }
}
