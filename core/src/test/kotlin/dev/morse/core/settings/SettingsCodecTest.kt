package dev.morse.core.settings

import dev.morse.core.power.RefreshProfile
import dev.morse.core.weather.GeoPoint
import dev.morse.core.weather.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun defaultsMatchTheDesign() {
        val d = LauncherSettings()
        assertEquals(4, d.columns)
        assertEquals(ThemeMode.DARK, d.theme)
        assertEquals(Accent.SIGNAL_RED, d.accent)
        assertEquals(WallpaperMode.DOTS, d.wallpaper)
        assertEquals(true, d.lowPowerMotion)
        assertEquals(true, d.pauseWidgetsOnLowBattery)
        assertEquals(RefreshProfile.BALANCED, d.refresh)
    }

    @Test
    fun roundTrips() {
        val s = LauncherSettings(
            columns = 5,
            showLabels = false,
            showSearchPill = false,
            theme = ThemeMode.AUTO,
            accent = Accent.COBALT,
            iconStyle = IconStyle.SIGNAL,
            wallpaper = WallpaperMode.SYSTEM,
            lowPowerMotion = false,
            pauseWidgetsOnLowBattery = false,
            refresh = RefreshProfile.SAVER,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            haptics = false,
            notificationDots = false,
            originalIconPackages = setOf("com.b", "com.a"),
            hiddenApps = setOf("a/b#0"),
            manualLocation = GeoPoint(-23.55, -46.63),
            manualPlaceName = "São Paulo",
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun badDataFallsBackToDefaults() {
        assertEquals(LauncherSettings(), SettingsCodec.decode(null))
        assertEquals(LauncherSettings(), SettingsCodec.decode(""))
        assertEquals(LauncherSettings(), SettingsCodec.decode("{broken"))
        val odd = SettingsCodec.decode("""{"columns":9,"theme":"NEON","accent":3,"manualLocation":[200,0],"haptics":"yes"}""")
        assertEquals(4, odd.columns)
        assertEquals(ThemeMode.DARK, odd.theme)
        assertEquals(Accent.SIGNAL_RED, odd.accent)
        assertNull(odd.manualLocation)
        assertEquals(true, odd.haptics)
    }
}
