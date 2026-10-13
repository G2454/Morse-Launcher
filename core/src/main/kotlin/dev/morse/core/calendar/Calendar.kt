package dev.morse.core.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** One calendar instance, as read from the system calendar provider. */
data class CalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

/** A day in the month grid. `null` cells are padding before day 1 / after the last day. */
data class DayCell(
    val day: Int,
    val isToday: Boolean,
    val isPast: Boolean,
    val hasEvent: Boolean,
)

object MonthGrid {

    /** Cells for [month], padded to whole weeks starting on [firstDayOfWeek]. */
    fun build(
        month: YearMonth,
        today: LocalDate,
        eventDays: Set<Int>,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): List<DayCell?> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = ArrayList<DayCell?>(42)
        repeat(lead) { cells += null }
        for (d in 1..month.lengthOfMonth()) {
            val date = month.atDay(d)
            cells += DayCell(d, date == today, date.isBefore(today), d in eventDays)
        }
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** One-letter weekday headers, e.g. M T W T F S S. */
    fun weekdayInitials(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY, locale: Locale = Locale.getDefault()): List<String> =
        (0 until 7).map { i ->
            firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale)
        }
}

object CalendarDays {

    /**
     * Days of [month] that have at least one event. All-day events are stored by
     * Android at UTC midnight, so they are read in UTC; timed events in [zone].
     * Multi-day events mark every day they cover.
     */
    fun eventDaysInMonth(events: List<CalendarEvent>, month: YearMonth, zone: ZoneId): Set<Int> {
        val days = HashSet<Int>()
        for (e in events) {
            val z = if (e.allDay) ZoneOffset.UTC else zone
            val start = Instant.ofEpochMilli(e.startMillis).atZone(z).toLocalDate()
            // End is exclusive; an event ending exactly at midnight does not cover that day.
            val endInstant = maxOf(e.startMillis, e.endMillis - 1)
            val end = Instant.ofEpochMilli(endInstant).atZone(z).toLocalDate()
            var d = start
            var guard = 0
            while (!d.isAfter(end) && guard < 62) {
                if (YearMonth.from(d) == month) days += d.dayOfMonth
                d = d.plusDays(1)
                guard++
            }
        }
        return days
    }

    /** Events that have not ended yet, soonest first. */
    fun upcoming(events: List<CalendarEvent>, nowMillis: Long, limit: Int): List<CalendarEvent> =
        events.filter { it.endMillis > nowMillis }
            .sortedWith(compareBy<CalendarEvent> { it.startMillis }.thenBy { it.title })
            .take(limit)

    /** "15:30" or "3:30 PM". */
    fun formatTime(millis: Long, zone: ZoneId, use24Hour: Boolean): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return if (use24Hour) {
            "%02d:%02d".format(Locale.ROOT, t.hour, t.minute)
        } else {
            val h = if (t.hour % 12 == 0) 12 else t.hour % 12
            "%d:%02d %s".format(Locale.ROOT, h, t.minute, if (t.hour < 12) "AM" else "PM")
        }
    }

    /** "15:30 – 16:00", or "All day". */
    fun formatRange(event: CalendarEvent, zone: ZoneId, use24Hour: Boolean): String =
        if (event.allDay) {
            "All day"
        } else {
            "${formatTime(event.startMillis, zone, use24Hour)} – ${formatTime(event.endMillis, zone, use24Hour)}"
        }
}
