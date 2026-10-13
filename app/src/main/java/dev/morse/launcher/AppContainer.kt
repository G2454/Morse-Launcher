package dev.morse.launcher

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.morse.launcher.data.AndroidLocationProvider
import dev.morse.launcher.data.AppsRepository
import dev.morse.launcher.data.BatteryMonitor
import dev.morse.launcher.data.CalendarRepository
import dev.morse.launcher.data.ClockTicker
import dev.morse.launcher.data.ContactsRepository
import dev.morse.launcher.data.HttpFetcher
import dev.morse.launcher.data.IconRepository
import dev.morse.launcher.data.LayoutRepository
import dev.morse.launcher.data.MediaRepository
import dev.morse.launcher.data.RemindersRepository
import dev.morse.launcher.data.SettingsRepository
import dev.morse.launcher.data.TogglesController
import dev.morse.launcher.data.UsageRepository
import dev.morse.launcher.data.WeatherRepository
import dev.morse.launcher.data.WidgetHostManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Hand-written dependency container. Everything is lazy: a repository only
 * starts (and only registers receivers or callbacks) when something uses it.
 */
class AppContainer(private val context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val appContext: Context get() = context.applicationContext ?: context

    val settings by lazy { SettingsRepository(context.settingsDataStore, scope) }
    val apps by lazy { AppsRepository(context, scope) }
    val icons by lazy { IconRepository(apps) }
    val layout by lazy { LayoutRepository(File(context.filesDir, "home_layout.json"), scope) }
    val usage by lazy { UsageRepository(File(context.filesDir, "usage.json"), scope) }
    val reminders by lazy { RemindersRepository(File(context.filesDir, "reminders.json"), scope) }
    val clock by lazy { ClockTicker(context, scope) }
    val battery by lazy { BatteryMonitor(context, scope) }
    val weather by lazy {
        WeatherRepository(
            settings = settings.settings,
            cacheFile = File(context.cacheDir, "weather.json"),
            fetch = HttpFetcher::get,
            location = AndroidLocationProvider(context),
            scope = scope,
        )
    }
    val calendar by lazy { CalendarRepository(context, scope) }
    val contacts by lazy { ContactsRepository(context) }
    val media by lazy { MediaRepository(context) }
    val toggles by lazy { TogglesController(context) }
    val widgets by lazy { WidgetHostManager(context) }
}
