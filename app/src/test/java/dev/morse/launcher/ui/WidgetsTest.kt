package dev.morse.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.morse.core.layout.Cell
import dev.morse.core.layout.GridSpan
import dev.morse.core.layout.WidgetItem
import dev.morse.core.layout.WidgetKind
import dev.morse.core.layout.WidgetSize
import dev.morse.core.power.PowerState
import dev.morse.core.reminders.Reminder
import dev.morse.core.time.ClockFormat
import dev.morse.core.weather.HourForecast
import dev.morse.core.weather.WeatherCondition
import dev.morse.core.weather.WeatherSnapshot
import dev.morse.launcher.TestTheme
import dev.morse.launcher.data.WeatherState
import dev.morse.launcher.ui.widgets.WidgetActions
import dev.morse.launcher.ui.widgets.WidgetContent
import dev.morse.launcher.ui.widgets.WidgetEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WidgetsTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var previousZone: TimeZone
    private lateinit var previousLocale: Locale

    @Before
    fun fixClock() {
        previousZone = TimeZone.getDefault()
        previousLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restore() {
        TimeZone.setDefault(previousZone)
        Locale.setDefault(previousLocale)
    }

    // 2026-10-13 10:42 in São Paulo.
    private val now = java.time.LocalDateTime.of(2026, 10, 13, 10, 42).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli()

    private fun render(kind: WidgetKind, span: GridSpan, env: WidgetEnvironment) {
        compose.setContent {
            TestTheme(environment = env) {
                val w = if (span.columns >= 3) 364.dp else 174.dp
                val h = if (span.rows >= 3) 364.dp else 174.dp
                Box(Modifier.size(w, h)) {
                    WidgetContent(WidgetItem("w", Cell(0, 0), kind, span), editMode = false)
                }
            }
        }
    }

    @Test
    fun everyBuiltInWidgetRendersInEverySize() {
        val all = WidgetKind.entries.filter { it != WidgetKind.HOSTED }.flatMap { k -> k.sizes.map { k to it } }
        compose.setContent {
            TestTheme(environment = WidgetEnvironment.preview(nowMillis = now)) {
                androidx.compose.foundation.layout.Column {
                    all.forEach { (kind, size) ->
                        val w = if (size.span.columns >= 3) 364.dp else 174.dp
                        val h = if (size.span.rows >= 3) 364.dp else 174.dp
                        Box(Modifier.size(w, h)) {
                            WidgetContent(WidgetItem("w-${kind.name}-${size.name}", Cell(0, 0), kind, size.span), editMode = false)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(13, all.size)
    }

    @Test
    fun glanceShowsTimeDateAndBattery() {
        render(WidgetKind.GLANCE, WidgetSize.M.span, WidgetEnvironment.preview(nowMillis = now))
        val t = ClockFormat.format(now, ZoneId.of("America/Sao_Paulo"), true, Locale.ENGLISH)
        compose.onNodeWithText(t.time).assertExists()
        compose.onNodeWithText("TUE 13 OCT").assertExists()
        compose.onNodeWithText("78%").assertExists()
    }

    @Test
    fun weatherAsksForLocationFirst() {
        var asked = false
        val env = WidgetEnvironment.preview(nowMillis = now, actions = object : WidgetActions {
            override fun requestLocation() {
                asked = true
            }
        })
        render(WidgetKind.WEATHER, WidgetSize.S.span, env)
        compose.onNodeWithText("Allow").performClick()
        assertTrue(asked)
    }

    @Test
    fun weatherShowsPausedStateOnLowBattery() {
        val snapshot = WeatherSnapshot("São Paulo", 24.3, 27.0, 18.0, WeatherCondition.PARTLY_CLOUDY, listOf(HourForecast("Now", 24.0, WeatherCondition.CLEAR)), now - 3_600_000)
        val env = WidgetEnvironment(
            now = MutableStateFlow(now),
            use24Hour = true,
            power = MutableStateFlow(PowerState(12, charging = false, systemSaver = false)),
            paused = MutableStateFlow(true),
            weather = MutableStateFlow(WeatherState.Ready(snapshot, paused = true)),
            temperatureUnit = dev.morse.core.weather.TemperatureUnit.CELSIUS,
            calendarPermission = MutableStateFlow(false),
            events = MutableStateFlow(emptyList()),
            nowPlaying = MutableStateFlow(null),
            mediaAccess = MutableStateFlow(false),
            toggles = kotlinx.coroutines.flow.flowOf(dev.morse.launcher.data.ToggleStates()),
            hasTorch = false,
            reminders = MutableStateFlow(emptyList()),
            actions = object : WidgetActions {},
        )
        render(WidgetKind.WEATHER, WidgetSize.S.span, env)
        compose.onNodeWithText("PAUSED").assertExists()
        compose.onNodeWithText("24°").assertExists()
        compose.onNodeWithText("São Paulo").assertExists()
    }

    @Test
    fun calendarAsksForPermission() {
        render(WidgetKind.CALENDAR, WidgetSize.S.span, WidgetEnvironment.preview(nowMillis = now))
        compose.onNodeWithText("Allow").assertExists()
    }

    @Test
    fun musicAsksForNotificationAccess() {
        render(WidgetKind.MUSIC, WidgetSize.M.span, WidgetEnvironment.preview(nowMillis = now))
        compose.onNodeWithText("Allow notification access").assertExists()
    }

    @Test
    fun remindersToggleOnTap() {
        var toggled: String? = null
        val base = WidgetEnvironment.preview(nowMillis = now, actions = object : WidgetActions {
            override fun toggleReminder(id: String) {
                toggled = id
            }
        })
        val env = WidgetEnvironment(
            base.now, base.use24Hour, base.power, base.paused, base.weather, base.temperatureUnit,
            base.calendarPermission, base.events, base.nowPlaying, base.mediaAccess, base.toggles, base.hasTorch,
            reminders = MutableStateFlow(listOf(Reminder("r1", "Pay rent"), Reminder("r2", "Gym bag", done = true))),
            actions = base.actions,
        )
        render(WidgetKind.REMINDERS, WidgetSize.S.span, env)
        compose.onNodeWithText("1 / 2").assertExists()
        compose.onNodeWithText("Pay rent").performClick()
        assertEquals("r1", toggled)
    }

    @Test
    fun togglesAreLabelled() {
        render(WidgetKind.TOGGLES, WidgetSize.S.span, WidgetEnvironment.preview(nowMillis = now))
        compose.onNodeWithContentDescription("Wi-Fi").assertExists()
        compose.onNodeWithContentDescription("Bluetooth").assertExists()
        compose.onNodeWithContentDescription("Torch").assertExists()
        compose.onNodeWithContentDescription("Do not disturb").assertExists()
    }
}
