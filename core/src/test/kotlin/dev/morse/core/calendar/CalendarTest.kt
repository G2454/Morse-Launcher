package dev.morse.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CalendarTest {

    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 13)
    private val saoPaulo: ZoneId = ZoneId.of("America/Sao_Paulo")

    @Test
    fun october2026StartsOnThursday() {
        val cells = MonthGrid.build(october, today, emptySet())
        assertNull(cells[0]); assertNull(cells[1]); assertNull(cells[2])
        assertEquals(1, cells[3]!!.day)
        assertEquals(0, cells.size % 7)
        assertEquals(35, cells.size)
    }

    @Test
    fun sundayFirstGridHasFourLeadingBlanks() {
        val cells = MonthGrid.build(october, today, emptySet(), DayOfWeek.SUNDAY)
        assertEquals(4, cells.takeWhile { it == null }.size)
    }

    @Test
    fun todayPastAndEventFlags() {
        val cells = MonthGrid.build(october, today, setOf(13, 16)).filterNotNull()
        val d13 = cells.first { it.day == 13 }
        assertTrue(d13.isToday)
        assertFalse(d13.isPast)
        assertTrue(d13.hasEvent)
        assertTrue(cells.first { it.day == 12 }.isPast)
        assertFalse(cells.first { it.day == 14 }.hasEvent)
    }

    @Test
    fun weekdayInitials() {
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), MonthGrid.weekdayInitials(DayOfWeek.MONDAY, Locale.ENGLISH))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = saoPaulo) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun allDayEventsAreReadInUtc() {
        // Android stores an all-day event on Oct 20 as 2026-10-20T00:00Z .. 2026-10-21T00:00Z.
        val e = CalendarEvent("Holiday", at(2026, 10, 20, 0, 0, ZoneOffset.UTC), at(2026, 10, 21, 0, 0, ZoneOffset.UTC), allDay = true)
        // In São Paulo (UTC-3) a naive read would land on the 19th.
        assertEquals(setOf(20), CalendarDays.eventDaysInMonth(listOf(e), october, saoPaulo))
    }

    @Test
    fun multiDayEventsMarkEveryDayAndClipToTheMonth() {
        val trip = CalendarEvent("Trip", at(2026, 10, 30, 9, 0), at(2026, 11, 2, 18, 0), allDay = false)
        assertEquals(setOf(30, 31), CalendarDays.eventDaysInMonth(listOf(trip), october, saoPaulo))
    }

    @Test
    fun eventEndingAtMidnightDoesNotSpillOver() {
        val e = CalendarEvent("Late", at(2026, 10, 13, 22, 0), at(2026, 10, 14, 0, 0), allDay = false)
        assertEquals(setOf(13), CalendarDays.eventDaysInMonth(listOf(e), october, saoPaulo))
    }

    @Test
    fun upcomingSkipsFinishedEventsAndSorts() {
        val now = at(2026, 10, 13, 10, 42)
        val done = CalendarEvent("Standup", at(2026, 10, 13, 9, 0), at(2026, 10, 13, 9, 15), false)
        val ongoing = CalendarEvent("Focus", at(2026, 10, 13, 10, 0), at(2026, 10, 13, 11, 0), false)
        val later = CalendarEvent("Design review", at(2026, 10, 13, 15, 30), at(2026, 10, 13, 16, 0), false)
        assertEquals(listOf(ongoing, later), CalendarDays.upcoming(listOf(later, done, ongoing), now, 5))
        assertEquals(listOf(ongoing), CalendarDays.upcoming(listOf(later, done, ongoing), now, 1))
    }

    @Test
    fun timeFormatting() {
        val start = at(2026, 10, 13, 15, 30)
        val end = at(2026, 10, 13, 16, 0)
        assertEquals("15:30", CalendarDays.formatTime(start, saoPaulo, true))
        assertEquals("3:30 PM", CalendarDays.formatTime(start, saoPaulo, false))
        assertEquals("12:05 AM", CalendarDays.formatTime(at(2026, 10, 13, 0, 5), saoPaulo, false))
        val e = CalendarEvent("Design review", start, end, false)
        assertEquals("15:30 – 16:00", CalendarDays.formatRange(e, saoPaulo, true))
        assertEquals("All day", CalendarDays.formatRange(e.copy(allDay = true), saoPaulo, true))
    }
}
