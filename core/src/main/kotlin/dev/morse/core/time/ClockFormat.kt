package dev.morse.core.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Text for the clock widgets. */
object ClockFormat {

    data class ClockText(
        /** "10:42" */
        val time: String,
        /** "10" */
        val hours: String,
        /** "42" */
        val minutes: String,
        /** "AM"/"PM" in 12-hour mode, else empty. */
        val meridiem: String,
        /** "TUE 13 OCT" */
        val dateLine: String,
        /** "TUE 13" */
        val shortDate: String,
        /** "TUESDAY" */
        val weekday: String,
        val dayOfMonth: Int,
        /** Hand angles in degrees, clockwise from 12. */
        val hourAngle: Float,
        val minuteAngle: Float,
    )

    fun format(millis: Long, zone: ZoneId, use24Hour: Boolean, locale: Locale = Locale.getDefault()): ClockText =
        format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone), use24Hour, locale)

    fun format(t: LocalDateTime, use24Hour: Boolean, locale: Locale = Locale.getDefault()): ClockText {
        val h = if (use24Hour) t.hour else (if (t.hour % 12 == 0) 12 else t.hour % 12)
        val hours = if (use24Hour) "%02d".format(Locale.ROOT, h) else h.toString()
        val minutes = "%02d".format(Locale.ROOT, t.minute)
        val dow = t.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).trimEnd('.')
        val month = t.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).trimEnd('.')
        return ClockText(
            time = "$hours:$minutes",
            hours = hours,
            minutes = minutes,
            meridiem = if (use24Hour) "" else if (t.hour < 12) "AM" else "PM",
            dateLine = "$dow ${t.dayOfMonth} $month",
            shortDate = "$dow ${t.dayOfMonth}",
            weekday = t.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(locale),
            dayOfMonth = t.dayOfMonth,
            hourAngle = ((t.hour % 12) * 30f + t.minute * 0.5f),
            minuteAngle = t.minute * 6f,
        )
    }

    /** Milliseconds until the next whole minute, for scheduling the next tick. */
    fun millisToNextMinute(nowMillis: Long): Long = 60_000L - (nowMillis % 60_000L)
}
