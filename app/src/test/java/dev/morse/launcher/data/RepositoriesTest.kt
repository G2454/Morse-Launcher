package dev.morse.launcher.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.morse.core.layout.LayoutCodec
import dev.morse.core.power.PowerState
import dev.morse.core.settings.LauncherSettings
import dev.morse.core.weather.GeoPoint
import dev.morse.launcher.TestData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoriesTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "test-${System.nanoTime()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    /* ---------------- Layout ---------------- */

    @Test
    fun layoutIsEmptyOnFirstRunThenPersists() = runBlocking {
        val file = File(dir, "layout.json")
        val repo = LayoutRepository(file, scope, saveDelayMillis = 0)
        repo.load()
        assertEquals(LayoutRepository.State.Empty, repo.state.value)

        repo.set(TestData.layout)
        repo.flush()
        assertTrue(file.exists())

        val reopened = LayoutRepository(file, scope)
        reopened.load()
        assertEquals(LayoutRepository.State.Ready(LayoutCodec.decode(LayoutCodec.encode(TestData.layout))!!), reopened.state.value)
    }

    @Test
    fun layoutUpdatesAreIgnoredBeforeLoad() = runBlocking {
        val repo = LayoutRepository(File(dir, "l2.json"), scope)
        repo.update { it.copy(columns = 5) }
        assertEquals(LayoutRepository.State.Loading, repo.state.value)
    }

    @Test
    fun damagedLayoutFileIsTreatedAsFirstRun() = runBlocking {
        val file = File(dir, "bad.json").apply { writeText("{{{ not json") }
        val repo = LayoutRepository(file, scope)
        repo.load()
        assertEquals(LayoutRepository.State.Empty, repo.state.value)
    }

    /* ---------------- Settings ---------------- */

    @Test
    fun settingsRoundTripThroughDataStore() = runBlocking {
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
        val repo = SettingsRepository(store, scope)
        repo.update { it.copy(columns = 5, showLabels = false) }
        val loaded = withTimeout(5_000) { repo.settings.first { it.columns == 5 } }
        assertEquals(false, loaded.showLabels)
    }

    /* ---------------- Reminders and usage ---------------- */

    @Test
    fun remindersPersist() = runBlocking {
        val file = File(dir, "reminders.json")
        val repo = RemindersRepository(file, scope)
        repo.add("Pay rent")
        withTimeout(5_000) {
            while (!file.exists() || !file.readText().contains("Pay rent")) kotlinx.coroutines.delay(20)
        }
        val again = RemindersRepository(file, scope)
        val items = withTimeout(5_000) { again.items.first { it.isNotEmpty() } }
        assertEquals("Pay rent", items.single().text)
    }

    @Test
    fun usageCountsLaunches() = runBlocking {
        val repo = UsageRepository(File(dir, "usage.json"), scope)
        repo.recordLaunch(TestData.camera.key)
        repo.recordLaunch(TestData.camera.key)
        assertEquals(2, repo.counts.value[TestData.camera.key])
    }

    /* ---------------- Weather ---------------- */

    private val forecast = """
        {"current":{"time":"2026-10-13T10:45","temperature_2m":24.3,"weather_code":2},
         "hourly":{"time":["2026-10-13T10:00","2026-10-13T11:00"],"temperature_2m":[24.1,25.6],"weather_code":[2,0]},
         "daily":{"temperature_2m_max":[27.4],"temperature_2m_min":[17.6]}}
    """.trimIndent()

    private class Clock(var now: Long)

    private fun weather(
        settings: LauncherSettings = LauncherSettings(),
        located: Located? = Located(GeoPoint(-23.55, -46.63), "São Paulo"),
        clock: Clock = Clock(1_000_000_000L),
        fetch: (String) -> String,
    ) = WeatherRepository(
        settings = MutableStateFlow(settings),
        cacheFile = File(dir, "weather-${System.nanoTime()}.json"),
        fetch = fetch,
        location = LocationProvider { located },
        scope = scope,
        now = { clock.now },
    )

    private val full = PowerState(80, charging = false, systemSaver = false)

    @Test
    fun weatherFetchesOnceThenWaitsForTheInterval() = runBlocking {
        var calls = 0
        val clock = Clock(1_000_000_000L)
        val repo = weather(clock = clock) { calls++; forecast }
        repo.refresh(full)
        val ready = repo.state.value as WeatherState.Ready
        assertEquals("São Paulo", ready.snapshot.place)
        assertEquals(1, calls)

        clock.now += 10 * 60_000L
        repo.refresh(full)
        assertEquals(1, calls)

        clock.now += 25 * 60_000L
        repo.refresh(full)
        assertEquals(2, calls)
    }

    @Test
    fun weatherPausesOnLowBattery() = runBlocking {
        var calls = 0
        val clock = Clock(1_000_000_000L)
        val repo = weather(clock = clock) { calls++; forecast }
        repo.refresh(full)
        clock.now += 5 * 60 * 60_000L
        repo.refresh(PowerState(12, charging = false, systemSaver = false))
        assertEquals(1, calls)
        val state = repo.state.value as WeatherState.Ready
        assertTrue(state.paused)
    }

    @Test
    fun weatherNeedsALocation() = runBlocking {
        val repo = weather(located = null) { forecast }
        repo.refresh(full)
        assertEquals(WeatherState.NeedsLocation, repo.state.value)
    }

    @Test
    fun weatherUsesTheManualCity() = runBlocking {
        var url = ""
        val repo = weather(
            settings = LauncherSettings(manualLocation = GeoPoint(51.5, -0.12), manualPlaceName = "London"),
            located = null,
        ) { url = it; forecast }
        repo.refresh(full)
        assertTrue(url, url.contains("latitude=51.5000"))
        assertEquals("London", (repo.state.value as WeatherState.Ready).snapshot.place)
    }

    @Test
    fun weatherOfflineWithoutCacheFails() = runBlocking {
        val repo = weather { throw IOException("no network") }
        repo.refresh(full)
        assertTrue(repo.state.value is WeatherState.Failed)
    }

    @Test
    fun weatherKeepsTheLastForecastWhenOffline() = runBlocking {
        var online = true
        val clock = Clock(1_000_000_000L)
        val repo = weather(clock = clock) { if (online) forecast else throw IOException("offline") }
        repo.refresh(full)
        online = false
        clock.now += 60 * 60_000L
        repo.refresh(full)
        assertNotNull((repo.state.value as? WeatherState.Ready)?.snapshot)
    }
}
