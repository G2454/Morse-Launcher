package dev.morse.core.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WeatherTest {

    private val sample = """
        {
          "latitude": -23.55, "longitude": -46.63, "timezone": "America/Sao_Paulo",
          "current": {"time": "2026-10-13T10:45", "interval": 900, "temperature_2m": 24.3, "weather_code": 2},
          "hourly": {
            "time": ["2026-10-13T08:00","2026-10-13T09:00","2026-10-13T10:00","2026-10-13T11:00",
                     "2026-10-13T12:00","2026-10-13T13:00","2026-10-13T14:00","2026-10-13T15:00","2026-10-13T16:00"],
            "temperature_2m": [19.0, 21.2, 24.1, 25.6, 26.4, 27.0, 26.8, 25.1, 22.3],
            "weather_code": [0, 1, 2, 0, 0, 2, 3, 61, 95]
          },
          "daily": {"time": ["2026-10-13","2026-10-14"], "temperature_2m_max": [27.4, 29.0], "temperature_2m_min": [17.6, 18.2]}
        }
    """.trimIndent()

    @Test
    fun parsesCurrentDailyAndHourly() {
        val s = OpenMeteo.parse(sample, "São Paulo", 1234L)
        assertEquals("São Paulo", s.place)
        assertEquals(24.3, s.temperatureC, 0.001)
        assertEquals(27.4, s.highC, 0.001)
        assertEquals(17.6, s.lowC, 0.001)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, s.condition)
        assertEquals(1234L, s.fetchedAtMillis)
        assertEquals(listOf("Now", "11", "12", "13", "14", "15"), s.hourly.map { it.label })
        assertEquals(WeatherCondition.RAIN, s.hourly.last().condition)
    }

    @Test
    fun hourlyIsShortNearTheEndOfTheData() {
        val late = sample.replace("2026-10-13T10:45", "2026-10-13T15:10")
        val s = OpenMeteo.parse(late, "x", 0)
        assertEquals(listOf("Now", "16"), s.hourly.map { it.label })
    }

    @Test
    fun apiErrorsAreReported() {
        assertThrows(OpenMeteo.ForecastException::class.java) {
            OpenMeteo.parse("""{"error": true, "reason": "Latitude must be in range"}""", "x", 0)
        }
        assertThrows(OpenMeteo.ForecastException::class.java) { OpenMeteo.parse("<html>", "x", 0) }
        assertThrows(OpenMeteo.ForecastException::class.java) { OpenMeteo.parse("""{"current":{}}""", "x", 0) }
    }

    @Test
    fun wmoCodes() {
        assertEquals(WeatherCondition.CLEAR, WeatherCodes.condition(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCodes.condition(1))
        assertEquals(WeatherCondition.CLOUDY, WeatherCodes.condition(3))
        assertEquals(WeatherCondition.FOG, WeatherCodes.condition(48))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCodes.condition(55))
        assertEquals(WeatherCondition.RAIN, WeatherCodes.condition(81))
        assertEquals(WeatherCondition.SNOW, WeatherCodes.condition(86))
        assertEquals(WeatherCondition.STORM, WeatherCodes.condition(99))
        assertEquals(WeatherCondition.CLOUDY, WeatherCodes.condition(1000))
    }

    @Test
    fun temperatureFormatting() {
        assertEquals("24°", Temperature.format(24.3, TemperatureUnit.CELSIUS))
        assertEquals("76°", Temperature.format(24.3, TemperatureUnit.FAHRENHEIT))
        assertEquals("-3°", Temperature.format(-2.6, TemperatureUnit.CELSIUS))
    }

    @Test
    fun urlUsesDotDecimalsInEveryLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val url = OpenMeteo.forecastUrl(GeoPoint(-23.5505, -46.6333))
            assertTrue(url, url.contains("latitude=-23.5505&longitude=-46.6333"))
            assertTrue(url.startsWith("https://api.open-meteo.com/"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GeoPoint(91.0, 0.0) }
    }

    @Test
    fun snapshotCodecRoundTrips() {
        val s = OpenMeteo.parse(sample, "São Paulo", 99L)
        assertEquals(s, WeatherCodec.decode(WeatherCodec.encode(s)))
        assertNull(WeatherCodec.decode("{}"))
        assertNull(WeatherCodec.decode("nope"))
    }
}

class GeocodeTest {
    @Test
    fun parsesTheFirstResult() {
        val body = """{"results":[{"id":3448439,"name":"São Paulo","latitude":-23.5475,"longitude":-46.63611,"country":"Brazil","admin1":"São Paulo"}],"generationtime_ms":0.5}"""
        val place = OpenMeteo.parseGeocode(body)!!
        assertEquals("São Paulo", place.name)
        assertEquals("São Paulo, Brazil", place.region)
        assertEquals(-23.5475, place.point.latitude, 1e-6)
    }

    @Test
    fun noResultsOrJunkIsNull() {
        assertNull(OpenMeteo.parseGeocode("""{"generationtime_ms":0.2}"""))
        assertNull(OpenMeteo.parseGeocode("oops"))
        assertNull(OpenMeteo.parseGeocode("""{"results":[{"name":"x","latitude":500,"longitude":0}]}"""))
    }

    @Test
    fun urlEncodesTheName() {
        assertTrue(OpenMeteo.geocodeUrl(" São Paulo ").endsWith("name=S%C3%A3o+Paulo"))
    }
}
