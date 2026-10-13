package dev.morse.launcher.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.morse.core.calendar.CalendarEvent
import dev.morse.core.power.PowerState
import dev.morse.core.reminders.Reminder
import dev.morse.core.weather.TemperatureUnit
import dev.morse.launcher.data.NowPlaying
import dev.morse.launcher.data.ToggleStates
import dev.morse.launcher.data.WeatherState
import dev.morse.launcher.ui.components.morseSurface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** Things widgets can ask the launcher to do. Every action defaults to doing nothing. */
interface WidgetActions {
    fun openClock() {}
    fun openCalendar() {}
    fun openBatterySettings() {}
    fun refreshWeather() {}
    fun requestLocation() {}
    fun requestCalendar() {}
    fun grantMediaAccess() {}
    fun openMusic(packageName: String?) {}
    fun playPause() {}
    fun next() {}
    fun previous() {}
    fun toggleWifi() {}
    fun toggleBluetooth() {}
    fun toggleTorch() {}
    fun toggleDoNotDisturb() {}
    fun toggleReminder(id: String) {}
    fun addReminder(text: String) {}
    fun removeReminder(id: String) {}
}

/**
 * Live data for the built-in widgets. Each widget collects only what it shows,
 * and only while the home screen is visible (collection is lifecycle-aware).
 */
class WidgetEnvironment(
    val now: StateFlow<Long>,
    val use24Hour: Boolean,
    val power: StateFlow<PowerState>,
    val paused: StateFlow<Boolean>,
    val weather: StateFlow<WeatherState>,
    val temperatureUnit: TemperatureUnit,
    val calendarPermission: StateFlow<Boolean>,
    val events: StateFlow<List<CalendarEvent>>,
    val nowPlaying: StateFlow<NowPlaying?>,
    val mediaAccess: StateFlow<Boolean>,
    val toggles: Flow<ToggleStates>,
    val hasTorch: Boolean,
    val reminders: StateFlow<List<Reminder>>,
    val actions: WidgetActions,
) {
    companion object {
        /** Fixed data, for previews and tests. */
        fun preview(
            nowMillis: Long = 1_791_891_720_000L,
            actions: WidgetActions = object : WidgetActions {},
        ) = WidgetEnvironment(
            now = MutableStateFlow(nowMillis),
            use24Hour = true,
            power = MutableStateFlow(PowerState(78, charging = false, systemSaver = false)),
            paused = MutableStateFlow(false),
            weather = MutableStateFlow(WeatherState.NeedsLocation),
            temperatureUnit = TemperatureUnit.CELSIUS,
            calendarPermission = MutableStateFlow(false),
            events = MutableStateFlow(emptyList()),
            nowPlaying = MutableStateFlow(null),
            mediaAccess = MutableStateFlow(false),
            toggles = flowOf(ToggleStates()),
            hasTorch = true,
            reminders = MutableStateFlow(emptyList()),
            actions = actions,
        )
    }
}

val LocalWidgetEnvironment = staticCompositionLocalOf<WidgetEnvironment> { WidgetEnvironment.preview() }

val WidgetShape = RoundedCornerShape(28.dp)

/** The rounded card every built-in widget sits on. */
@Composable
fun WidgetCard(
    description: String,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .morseSurface(WidgetShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
            .padding(padding),
        content = content,
    )
}
