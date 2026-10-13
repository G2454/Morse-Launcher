package dev.morse.launcher.ui.widgets

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.morse.core.motion.MotionSpec
import dev.morse.core.power.BatteryMeter
import dev.morse.core.time.ClockFormat
import dev.morse.core.weather.Temperature
import dev.morse.launcher.data.WeatherState
import dev.morse.launcher.ui.components.DotMeter
import dev.morse.launcher.ui.components.GlyphIcon
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.theme.Morse
import dev.morse.launcher.ui.theme.toTween
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun rememberClockText(): ClockFormat.ClockText {
    val env = LocalWidgetEnvironment.current
    val now by env.now.collectAsStateWithLifecycle()
    return ClockFormat.format(now, ZoneId.systemDefault(), env.use24Hour)
}

@Composable
private fun AccentDot(size: Float = 6f) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Morse.colors.accent))
}

/** M · the home-page hero: date, big dot-matrix time, next event and battery. */
@Composable
fun GlanceWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val t = rememberClockText()
    val weather by env.weather.collectAsStateWithLifecycle()
    val power by env.power.collectAsStateWithLifecycle()
    val events by env.events.collectAsStateWithLifecycle()
    val now by env.now.collectAsStateWithLifecycle()
    val next = dev.morse.core.calendar.CalendarDays.upcoming(events, now, 1).firstOrNull()

    WidgetCard(
        description = "Glance: ${t.time}, ${t.dateLine}",
        modifier = modifier,
        padding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        onClick = env.actions::openClock,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val timeSize = min(maxWidth * 0.29f, maxHeight * 0.62f).value.sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AccentDot()
                    Spacer(Modifier.size(8.dp))
                    MText(t.dateLine, Morse.type.mono(12.sp), color = c.text2, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    val w = weather
                    if (w is WeatherState.Ready) {
                        GlyphIcon(w.snapshot.condition.glyph, 16.dp, c.text2)
                        Spacer(Modifier.size(6.dp))
                        MText(Temperature.format(w.snapshot.temperatureC, env.temperatureUnit), Morse.type.mono(12.sp), color = c.text2)
                    }
                }
                Crossfade(targetState = t.time, animationSpec = MotionSpec.minuteTick.toTween(), label = "time") { time ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        MText(time, Morse.type.dot(timeSize), maxLines = 1)
                        if (t.meridiem.isNotEmpty()) {
                            Spacer(Modifier.size(6.dp))
                            MText(t.meridiem, Morse.type.mono(12.sp), color = c.text2)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (next != null) {
                        MText("Next ", Morse.type.ui(13.sp, color = c.text2), maxLines = 1)
                        MText(
                            "${next.title} · ${dev.morse.core.calendar.CalendarDays.formatTime(next.startMillis, ZoneId.systemDefault(), env.use24Hour)}",
                            Morse.type.ui(13.sp),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                    } else {
                        MText(t.weekday.lowercase().replaceFirstChar { it.uppercase() }, Morse.type.ui(13.sp, color = c.text2), modifier = Modifier.weight(1f), maxLines = 1)
                    }
                    val low = BatteryMeter.isLow(power.batteryPercent) && !power.charging
                    DotMeter(
                        lit = BatteryMeter.litDots(power.batteryPercent),
                        total = 10,
                        on = if (low) c.accent else c.text,
                    )
                    Spacer(Modifier.size(8.dp))
                    MText("${power.batteryPercent}%", Morse.type.mono(12.sp, tracking = 0f))
                }
            }
        }
    }
}

/** S · hours over minutes in dot-matrix. */
@Composable
fun DotClockWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val t = rememberClockText()
    WidgetCard(description = "Clock: ${t.time}", modifier = modifier, onClick = env.actions::openClock) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val digits = min(maxWidth * 0.5f, maxHeight * 0.42f).value.sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentDot(5f)
                    Spacer(Modifier.size(6.dp))
                    MText(t.shortDate, Morse.type.mono(11.sp), color = c.text2, maxLines = 1)
                }
                Crossfade(targetState = t.hours to t.minutes, animationSpec = MotionSpec.minuteTick.toTween(), label = "dot-clock") { (h, m) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MText(h, Morse.type.dot(digits), maxLines = 1)
                        MText(m, Morse.type.dot(digits), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** S · an analogue dial with no second hand (a second hand would redraw 60× a minute). */
@Composable
fun DialClockWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val t = rememberClockText()
    WidgetCard(description = "Clock: ${t.time}", modifier = modifier, onClick = env.actions::openClock) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90.0)
                val big = i % 3 == 0
                val p = Offset(center.x + (r * 0.9f) * cos(a).toFloat(), center.y + (r * 0.9f) * sin(a).toFloat())
                drawCircle(if (big) c.text else c.text3, radius = if (big) 3.dp.toPx() else 2.dp.toPx(), center = p)
            }
            fun hand(angleDeg: Float, length: Float, width: Float, color: androidx.compose.ui.graphics.Color) {
                val a = Math.toRadians(angleDeg - 90.0)
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(center.x + length * cos(a).toFloat(), center.y + length * sin(a).toFloat()),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
            hand(t.hourAngle, r * 0.48f, 4.dp.toPx(), c.text)
            hand(t.minuteAngle, r * 0.72f, 2.dp.toPx(), c.text2)
            drawCircle(c.surface, radius = 8.dp.toPx(), center = center)
            drawCircle(c.accent, radius = 5.dp.toPx(), center = center)
        }
    }
}
