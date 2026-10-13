package dev.morse.launcher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.morse.core.calendar.CalendarDays
import dev.morse.core.calendar.MonthGrid
import dev.morse.core.glyph.Glyph
import dev.morse.core.power.BatteryMeter
import dev.morse.core.time.ClockFormat
import dev.morse.core.weather.Temperature
import dev.morse.core.weather.WeatherSnapshot
import dev.morse.launcher.data.WeatherState
import dev.morse.launcher.ui.components.FlexDotMeter
import dev.morse.launcher.ui.components.GlyphIcon
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.PillButton
import dev.morse.launcher.ui.theme.Morse
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/* ---------------- Weather ---------------- */

@Composable
private fun PausedTag() {
    val c = Morse.colors
    Box(
        Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, c.hairline, RoundedCornerShape(50))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        MText("PAUSED", Morse.type.mono(9.5.sp), color = c.text2, maxLines = 1)
    }
}

@Composable
private fun WeatherMessage(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        GlyphIcon(Glyph.WEATHER, 22.dp, Morse.colors.text2)
        MText(text, Morse.type.ui(13.sp, color = Morse.colors.text2), maxLines = 3)
        if (action != null) PillButton(action, onAction, height = 36.dp)
    }
}

@Composable
private fun rememberWeather(): Pair<WeatherState, Boolean> {
    val env = LocalWidgetEnvironment.current
    val w by env.weather.collectAsStateWithLifecycle()
    val paused by env.paused.collectAsStateWithLifecycle()
    return w to paused
}

private fun asOf(snapshot: WeatherSnapshot, use24: Boolean): String =
    "AS OF " + CalendarDays.formatTime(snapshot.fetchedAtMillis, ZoneId.systemDefault(), use24).uppercase()

/** S · place, temperature, condition, high/low. Greys out with PAUSED on low battery. */
@Composable
fun WeatherSmallWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val (state, paused) = rememberWeather()
    WidgetCard("Weather", modifier, PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp), onClick = env.actions::refreshWeather) {
        when (state) {
            is WeatherState.Ready -> {
                val s = state.snapshot
                val dim = paused || state.paused
                val ink = if (dim) c.text3 else c.text
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val big = min(maxWidth * 0.42f, maxHeight * 0.4f).value.sp
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MText(s.place, Morse.type.ui(13.sp), color = ink, modifier = Modifier.weight(1f), maxLines = 1)
                            if (dim) PausedTag()
                        }
                        MText(Temperature.format(s.temperatureC, env.temperatureUnit), Morse.type.dot(big), color = ink, maxLines = 1)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (dim) {
                                MText(asOf(s, env.use24Hour), Morse.type.mono(11.sp), color = c.text3, maxLines = 1)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlyphIcon(s.condition.glyph, 15.dp, ink)
                                    Spacer(Modifier.width(6.dp))
                                    MText(s.condition.label, Morse.type.ui(12.sp, weight = androidx.compose.ui.text.font.FontWeight.Normal), color = ink, maxLines = 1)
                                }
                                MText(
                                    "H ${Temperature.format(s.highC, env.temperatureUnit)}  L ${Temperature.format(s.lowC, env.temperatureUnit)}",
                                    Morse.type.mono(11.sp, tracking = 0f),
                                    color = c.text3,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            WeatherState.NeedsLocation -> WeatherMessage("Allow location, or set a city in Morse settings.", "Allow", env.actions::requestLocation)
            WeatherState.Loading -> WeatherMessage("Loading weather…", null) {}
            is WeatherState.Failed -> WeatherMessage(state.message, "Retry", env.actions::refreshWeather)
        }
    }
}

/** M · current conditions plus the next five hours. */
@Composable
fun WeatherHourlyWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val (state, paused) = rememberWeather()
    WidgetCard("Hourly weather", modifier, PaddingValues(horizontal = 18.dp, vertical = 16.dp), onClick = env.actions::refreshWeather) {
        if (state !is WeatherState.Ready) {
            when (state) {
                WeatherState.NeedsLocation -> WeatherMessage("Allow location, or set a city in Morse settings.", "Allow", env.actions::requestLocation)
                is WeatherState.Failed -> WeatherMessage(state.message, "Retry", env.actions::refreshWeather)
                else -> WeatherMessage("Loading weather…", null) {}
            }
            return@WidgetCard
        }
        val s = state.snapshot
        val dim = paused || state.paused
        val ink = if (dim) c.text3 else c.text
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MText(s.place, Morse.type.ui(13.sp), color = ink, maxLines = 1)
                    MText(
                        if (dim) asOf(s, env.use24Hour) else "${s.condition.label} · H ${Temperature.format(s.highC, env.temperatureUnit)} L ${Temperature.format(s.lowC, env.temperatureUnit)}",
                        if (dim) Morse.type.mono(11.sp) else Morse.type.ui(12.sp),
                        color = c.text2,
                        maxLines = 1,
                    )
                }
                if (dim) PausedTag() else MText(Temperature.format(s.temperatureC, env.temperatureUnit), Morse.type.dot(46.sp), maxLines = 1)
            }
            Row(Modifier.fillMaxWidth()) {
                s.hourly.take(6).forEach { h ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MText(h.label.uppercase(), Morse.type.mono(11.sp), color = c.text3, maxLines = 1)
                        GlyphIcon(h.condition.glyph, 20.dp, ink)
                        MText(Temperature.format(h.temperatureC, env.temperatureUnit), Morse.type.ui(13.sp), color = ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

/* ---------------- Battery ---------------- */

/** S · battery percentage and a dot meter; turns to the accent colour below 20%. */
@Composable
fun BatteryWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val power by env.power.collectAsStateWithLifecycle()
    val low = BatteryMeter.isLow(power.batteryPercent) && !power.charging
    WidgetCard("Battery ${power.batteryPercent} percent", modifier, onClick = env.actions::openBatterySettings) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = min(maxWidth * 0.36f, maxHeight * 0.3f).value.sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth()) {
                    MText("BATTERY", Morse.type.mono(11.sp), color = c.text2, modifier = Modifier.weight(1f))
                    MText(
                        when {
                            power.charging -> "CHARGING"
                            power.systemSaver -> "SAVER"
                            else -> ""
                        },
                        Morse.type.mono(11.sp),
                        color = c.text2,
                    )
                }
                MText("${power.batteryPercent}%", Morse.type.dot(big), color = if (low) c.accent else c.text, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.DEVICE, 16.dp, c.text)
                    Spacer(Modifier.width(8.dp))
                    FlexDotMeter(
                        lit = BatteryMeter.litDots(power.batteryPercent),
                        total = 10,
                        modifier = Modifier.weight(1f),
                        on = if (low) c.accent else c.text,
                    )
                }
            }
        }
    }
}

/* ---------------- Calendar ---------------- */

@Composable
private fun CalendarPermissionPrompt() {
    val env = LocalWidgetEnvironment.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        GlyphIcon(Glyph.CALENDAR, 22.dp, Morse.colors.text2)
        MText("Show your next events here.", Morse.type.ui(13.sp, color = Morse.colors.text2), maxLines = 3)
        PillButton("Allow", env.actions::requestCalendar, height = 36.dp)
    }
}

private fun zone() = ZoneId.systemDefault()

/** S · weekday, date in dot-matrix and the next event. */
@Composable
fun CalendarSmallWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val granted by env.calendarPermission.collectAsStateWithLifecycle()
    val events by env.events.collectAsStateWithLifecycle()
    val now by env.now.collectAsStateWithLifecycle()
    val t = ClockFormat.format(now, zone(), env.use24Hour)
    WidgetCard("Calendar, ${t.weekday} ${t.dayOfMonth}", modifier, onClick = env.actions::openCalendar) {
        if (!granted) {
            CalendarPermissionPrompt()
            return@WidgetCard
        }
        val next = CalendarDays.upcoming(events, now, 1).firstOrNull()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = min(maxWidth * 0.45f, maxHeight * 0.4f).value.sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MText(t.weekday, Morse.type.ui(11.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold).copy(letterSpacing = 0.12.em), color = c.accent, maxLines = 1)
                    MText(t.dayOfMonth.toString(), Morse.type.dot(big), maxLines = 1)
                }
                if (next == null) {
                    MText("No more events", Morse.type.ui(13.sp, color = c.text2), maxLines = 1)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(c.text))
                            Spacer(Modifier.width(6.dp))
                            MText(next.title, Morse.type.ui(13.sp), maxLines = 1)
                        }
                        MText(
                            CalendarDays.formatRange(next, zone(), env.use24Hour),
                            Morse.type.mono(11.sp, tracking = 0f),
                            color = c.text2,
                            modifier = Modifier.padding(start = 12.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** M · the next three events. */
@Composable
fun AgendaWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val granted by env.calendarPermission.collectAsStateWithLifecycle()
    val events by env.events.collectAsStateWithLifecycle()
    val now by env.now.collectAsStateWithLifecycle()
    val t = ClockFormat.format(now, zone(), env.use24Hour)
    WidgetCard("Agenda", modifier, PaddingValues(horizontal = 18.dp, vertical = 16.dp), onClick = env.actions::openCalendar) {
        if (!granted) {
            CalendarPermissionPrompt()
            return@WidgetCard
        }
        val upcoming = CalendarDays.upcoming(events, now, 3)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                MText(t.weekday.take(3), Morse.type.ui(11.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.accent)
                MText(t.dayOfMonth.toString(), Morse.type.dot(56.sp), maxLines = 1)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                if (upcoming.isEmpty()) {
                    MText("Nothing scheduled", Morse.type.ui(13.sp, color = c.text2))
                }
                upcoming.forEach { e ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        MText(e.title, Morse.type.ui(13.sp), maxLines = 1)
                        MText(dayAndRange(e.startMillis, e, now, env.use24Hour), Morse.type.mono(11.sp, tracking = 0f), color = c.text2, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun dayAndRange(start: Long, e: dev.morse.core.calendar.CalendarEvent, now: Long, use24: Boolean): String {
    val z = zone()
    val day = Instant.ofEpochMilli(start).atZone(if (e.allDay) java.time.ZoneOffset.UTC else z).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(z).toLocalDate()
    val prefix = when (day) {
        today -> ""
        today.plusDays(1) -> "TOMORROW · "
        else -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase() + " ${day.dayOfMonth} · "
    }
    return prefix + CalendarDays.formatRange(e, z, use24)
}

/** L · the month, with dots under days that have events, and the next two events. */
@Composable
fun MonthWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val granted by env.calendarPermission.collectAsStateWithLifecycle()
    val events by env.events.collectAsStateWithLifecycle()
    val now by env.now.collectAsStateWithLifecycle()
    val today = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
    val month = YearMonth.from(today)
    WidgetCard("Month", modifier, PaddingValues(20.dp), onClick = env.actions::openCalendar) {
        if (!granted) {
            CalendarPermissionPrompt()
            return@WidgetCard
        }
        val eventDays = CalendarDays.eventDaysInMonth(events, month, zone())
        val firstDay = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val cells = MonthGrid.build(month, today, eventDays, firstDay)
        val heads = MonthGrid.weekdayInitials(firstDay)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                MText(
                    month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    Morse.type.ui(15.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                MText(month.year.toString(), Morse.type.mono(11.sp), color = c.text3)
            }
            Row(Modifier.fillMaxWidth()) {
                heads.forEach { MText(it, Morse.type.mono(10.sp, tracking = 0f).copy(textAlign = TextAlign.Center), color = c.text3, modifier = Modifier.weight(1f)) }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (cell != null) {
                                Box(
                                    Modifier
                                        .size(width = 26.dp, height = 22.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (cell.isToday) c.inverse else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MText(
                                        cell.day.toString(),
                                        Morse.type.ui(12.sp),
                                        color = when {
                                            cell.isToday -> c.onInverse
                                            cell.isPast -> c.text3
                                            else -> c.text
                                        },
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Box(Modifier.size(3.dp).clip(CircleShape).background(if (cell.hasEvent) c.accent else Color.Transparent))
                            } else {
                                Spacer(Modifier.height(27.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            CalendarDays.upcoming(events, now, 2).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MText(
                        if (e.allDay) "ALL DAY" else CalendarDays.formatTime(e.startMillis, zone(), env.use24Hour),
                        Morse.type.mono(12.sp, tracking = 0f),
                        color = c.text2,
                        modifier = Modifier.width(64.dp),
                        maxLines = 1,
                    )
                    MText(e.title, Morse.type.ui(13.sp, weight = androidx.compose.ui.text.font.FontWeight.Normal), maxLines = 1)
                }
            }
        }
    }
}
