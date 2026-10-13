package dev.morse.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dev.morse.core.power.PowerPolicy
import dev.morse.core.power.PowerState
import dev.morse.core.settings.LauncherSettings
import dev.morse.core.weather.GeoPoint
import dev.morse.core.weather.OpenMeteo
import dev.morse.core.weather.WeatherCodec
import dev.morse.core.weather.WeatherSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface WeatherState {
    /** No location permission and no city set in settings. */
    data object NeedsLocation : WeatherState
    data object Loading : WeatherState
    data class Ready(val snapshot: WeatherSnapshot, val paused: Boolean) : WeatherState
    data class Failed(val message: String, val last: WeatherSnapshot?) : WeatherState
}

/** Where the phone is, with a readable place name. */
data class Located(val point: GeoPoint, val place: String)

fun interface LocationProvider {
    suspend fun current(): Located?
}

/**
 * Weather from Open-Meteo. It never polls: [refresh] is called when the home
 * screen becomes visible and only fetches when [PowerPolicy] says the last
 * forecast is old enough. The last forecast is cached on disk.
 */
class WeatherRepository(
    private val settings: StateFlow<LauncherSettings>,
    cacheFile: File,
    private val fetch: (String) -> String,
    private val location: LocationProvider,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val cache = JsonFile(cacheFile)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private var last: WeatherSnapshot? = null
    private var cacheLoaded = false

    private suspend fun loadCacheOnce() {
        if (cacheLoaded) return
        cacheLoaded = true
        last = withContext(Dispatchers.IO) { cache.read()?.let(WeatherCodec::decode) }
        last?.let { _state.value = WeatherState.Ready(it, paused = false) }
    }

    fun refreshAsync(power: PowerState, force: Boolean = false) {
        scope.launch { refresh(power, force) }
    }

    suspend fun refresh(power: PowerState, force: Boolean = false) = mutex.withLock {
        loadCacheOnce()
        val s = settings.value
        val paused = PowerPolicy.widgetsPaused(power, s.pauseWidgetsOnLowBattery)
        val previous = last
        if (!PowerPolicy.shouldRefreshWeather(previous?.fetchedAtMillis ?: 0L, now(), s.refresh, paused, force)) {
            _state.value = previous?.let { WeatherState.Ready(it, paused) }
                ?: if (paused) WeatherState.Failed("Paused to save battery", null) else _state.value
            return@withLock
        }
        val where = location.current()
            ?: s.manualLocation?.let { Located(it, s.manualPlaceName.ifBlank { "Weather" }) }
        if (where == null) {
            _state.value = previous?.let { WeatherState.Ready(it, paused) } ?: WeatherState.NeedsLocation
            return@withLock
        }
        if (previous == null) _state.value = WeatherState.Loading
        try {
            val body = withContext(Dispatchers.IO) { fetch(OpenMeteo.forecastUrl(where.point)) }
            val snapshot = OpenMeteo.parse(body, where.place, now())
            last = snapshot
            withContext(Dispatchers.IO) { cache.write(WeatherCodec.encode(snapshot)) }
            _state.value = WeatherState.Ready(snapshot, paused)
        } catch (e: IOException) {
            _state.value = previous?.let { WeatherState.Ready(it, paused) } ?: WeatherState.Failed("Offline", null)
        } catch (e: OpenMeteo.ForecastException) {
            _state.value = WeatherState.Failed(e.message ?: "Forecast unavailable", previous)
        }
    }
}

/** A plain HTTPS GET with short timeouts. */
object HttpFetcher {
    fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "MorseLauncher/1.0")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299 && !body.contains("\"error\"")) throw IOException("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Uses the most recent location Android already knows (no GPS fix is started,
 * so this costs no battery) and turns it into a city name.
 */
class AndroidLocationProvider(private val context: Context) : LocationProvider {

    @SuppressLint("MissingPermission")
    override suspend fun current(): Located? = withContext(Dispatchers.IO) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext null
        val lm = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val best: Location = lm.getProviders(true)
            .mapNotNull { provider -> try { lm.getLastKnownLocation(provider) } catch (e: SecurityException) { null } }
            .maxByOrNull { it.time }
            ?: return@withContext null
        val point = GeoPoint(best.latitude, best.longitude)
        Located(point, placeName(best) ?: "Current location")
    }

    private fun placeName(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            @Suppress("DEPRECATION")
            val results = Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)
            results?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
