package dev.morse.core.weather

import dev.morse.core.glyph.Glyph
import dev.morse.core.json.Json
import dev.morse.core.json.array
import dev.morse.core.json.asObject
import dev.morse.core.json.double
import dev.morse.core.json.int
import dev.morse.core.json.long
import dev.morse.core.json.obj
import dev.morse.core.json.string
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherCondition(val label: String, val glyph: Glyph) {
    CLEAR("Clear", Glyph.SUN),
    PARTLY_CLOUDY("Partly cloudy", Glyph.PARTLY_CLOUDY),
    CLOUDY("Cloudy", Glyph.WEATHER),
    FOG("Fog", Glyph.FOG),
    DRIZZLE("Drizzle", Glyph.DRIZZLE),
    RAIN("Rain", Glyph.RAIN),
    SNOW("Snow", Glyph.SNOW),
    STORM("Thunderstorm", Glyph.STORM),
}

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Invalid coordinates" }
    }
}

data class HourForecast(val label: String, val temperatureC: Double, val condition: WeatherCondition)

data class WeatherSnapshot(
    val place: String,
    val temperatureC: Double,
    val highC: Double,
    val lowC: Double,
    val condition: WeatherCondition,
    val hourly: List<HourForecast>,
    val fetchedAtMillis: Long,
)

object WeatherCodes {
    /** WMO weather interpretation codes, as used by Open-Meteo. */
    fun condition(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        in 51..57 -> WeatherCondition.DRIZZLE
        in 61..67, in 80..82 -> WeatherCondition.RAIN
        in 71..77, 85, 86 -> WeatherCondition.SNOW
        in 95..99 -> WeatherCondition.STORM
        else -> WeatherCondition.CLOUDY
    }
}

object Temperature {
    fun toUnit(celsius: Double, unit: TemperatureUnit): Int = when (unit) {
        TemperatureUnit.CELSIUS -> celsius.roundToInt()
        TemperatureUnit.FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt()
    }

    /** "24°" — the unit letter is left out on purpose, like the design. */
    fun format(celsius: Double, unit: TemperatureUnit): String = "${toUnit(celsius, unit)}°"
}

/** Open-Meteo forecast API: free, no key, no account. */
object OpenMeteo {
    const val HOURS_SHOWN = 6

    fun forecastUrl(point: GeoPoint): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${"%.4f".format(Locale.US, point.latitude)}" +
            "&longitude=${"%.4f".format(Locale.US, point.longitude)}" +
            "&current=temperature_2m,weather_code" +
            "&hourly=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=2"

    class ForecastException(message: String) : Exception(message)

    /** A place found by name, for setting the weather city by hand. */
    data class Place(val name: String, val region: String, val point: GeoPoint)

    fun geocodeUrl(name: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name=" +
            java.net.URLEncoder.encode(name.trim(), "UTF-8")

    /** The best match, or null when nothing matched. */
    fun parseGeocode(body: String): Place? {
        val root = try {
            Json.parse(body).asObject()
        } catch (e: Json.JsonException) {
            return null
        }
        val first = root.array("results").firstOrNull()?.asObject() ?: return null
        val lat = first.double("latitude") ?: return null
        val lon = first.double("longitude") ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val region = listOfNotNull(first.string("admin1"), first.string("country")).joinToString(", ")
        return Place(first.string("name") ?: "", region, GeoPoint(lat, lon))
    }

    /**
     * Parses a forecast response. Hourly entries start at the current hour; the
     * first one is labelled "Now", the rest by hour ("13", "14"…).
     */
    fun parse(body: String, place: String, fetchedAtMillis: Long): WeatherSnapshot {
        val root = try {
            Json.parse(body).asObject()
        } catch (e: Json.JsonException) {
            throw ForecastException("Malformed forecast: ${e.message}")
        }
        if (root["error"] == true) throw ForecastException(root.string("reason") ?: "Forecast error")
        val current = root.obj("current")
        val temp = current.double("temperature_2m") ?: throw ForecastException("Missing current temperature")
        val code = current.int("weather_code") ?: 3
        val nowTime = current.string("time") ?: ""

        val daily = root.obj("daily")
        val high = daily.array("temperature_2m_max").firstOrNull().let { (it as? Number)?.toDouble() } ?: temp
        val low = daily.array("temperature_2m_min").firstOrNull().let { (it as? Number)?.toDouble() } ?: temp

        val hourly = root.obj("hourly")
        val times = hourly.array("time").map { it as? String ?: "" }
        val temps = hourly.array("temperature_2m").map { (it as? Number)?.toDouble() }
        val codes = hourly.array("weather_code").map { (it as? Number)?.toInt() }
        val nowHour = nowTime.take(13) // "2026-10-13T10"
        val start = times.indexOfFirst { it.take(13) >= nowHour }.takeIf { it >= 0 } ?: 0
        val hours = (start until minOf(start + HOURS_SHOWN, times.size)).mapNotNull { i ->
            val t = temps.getOrNull(i) ?: return@mapNotNull null
            val label = if (i == start) "Now" else times[i].substringAfter('T').take(2)
            HourForecast(label, t, WeatherCodes.condition(codes.getOrNull(i) ?: 3))
        }
        return WeatherSnapshot(place, temp, high, low, WeatherCodes.condition(code), hours, fetchedAtMillis)
    }
}

/** Caches the last forecast on disk so widgets have something to show after a restart. */
object WeatherCodec {
    fun encode(s: WeatherSnapshot): String = Json.stringify(
        mapOf(
            "place" to s.place,
            "t" to s.temperatureC,
            "hi" to s.highC,
            "lo" to s.lowC,
            "cond" to s.condition.name,
            "at" to s.fetchedAtMillis,
            "hourly" to s.hourly.map { mapOf("l" to it.label, "t" to it.temperatureC, "c" to it.condition.name) },
        ),
    )

    fun decode(text: String): WeatherSnapshot? {
        val o = try {
            Json.parse(text).asObject()
        } catch (e: Json.JsonException) {
            return null
        }
        return WeatherSnapshot(
            place = o.string("place") ?: "",
            temperatureC = o.double("t") ?: return null,
            highC = o.double("hi") ?: return null,
            lowC = o.double("lo") ?: return null,
            condition = conditionOf(o.string("cond")),
            hourly = o.array("hourly").map { h ->
                val m = h.asObject()
                HourForecast(m.string("l") ?: "", m.double("t") ?: 0.0, conditionOf(m.string("c")))
            },
            fetchedAtMillis = o.long("at") ?: 0L,
        )
    }

    private fun conditionOf(name: String?) =
        WeatherCondition.entries.firstOrNull { it.name == name } ?: WeatherCondition.CLOUDY
}
