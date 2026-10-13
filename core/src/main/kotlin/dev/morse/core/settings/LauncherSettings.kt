package dev.morse.core.settings

import dev.morse.core.json.Json
import dev.morse.core.json.asArray
import dev.morse.core.json.asObject
import dev.morse.core.power.RefreshProfile
import dev.morse.core.weather.GeoPoint
import dev.morse.core.weather.TemperatureUnit

enum class ThemeMode(val title: String) { DARK("Dark"), LIGHT("Light"), AUTO("Auto") }

/** Accent colours as ARGB. Light theme uses [lightArgb] so contrast holds on white. */
enum class Accent(val title: String, val darkArgb: Long, val lightArgb: Long) {
    SIGNAL_RED("Signal red", 0xFFE5322D, 0xFFD42A24),
    PAPER_WHITE("Paper white", 0xFFF2F2F0, 0xFF141414),
    AMBER("Amber", 0xFFF28C28, 0xFFB45F06),
    COBALT("Cobalt", 0xFF4C8DF6, 0xFF2F6FDB),
}

enum class IconStyle(val title: String) { MONO("Mono"), SIGNAL("Signal") }

enum class WallpaperMode(val title: String) { DOTS("Dot grid"), PLAIN("Plain"), SYSTEM("System") }

data class LauncherSettings(
    val columns: Int = 4,
    val showLabels: Boolean = true,
    val showSearchPill: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Accent = Accent.SIGNAL_RED,
    val iconStyle: IconStyle = IconStyle.MONO,
    val wallpaper: WallpaperMode = WallpaperMode.DOTS,
    val lowPowerMotion: Boolean = true,
    val pauseWidgetsOnLowBattery: Boolean = true,
    val refresh: RefreshProfile = RefreshProfile.BALANCED,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val haptics: Boolean = true,
    val notificationDots: Boolean = true,
    /** Packages drawn with their original icon instead of a Morse tile. */
    val originalIconPackages: Set<String> = emptySet(),
    /** Flattened [dev.morse.core.apps.AppKey]s hidden from the library and search. */
    val hiddenApps: Set<String> = emptySet(),
    /** Used for weather when location permission is not granted. */
    val manualLocation: GeoPoint? = null,
    val manualPlaceName: String = "",
)

/**
 * Settings are stored as one JSON document. Unknown keys are ignored and bad
 * values fall back to defaults, so old or damaged data never breaks startup.
 */
object SettingsCodec {

    fun encode(s: LauncherSettings): String = Json.stringify(
        linkedMapOf(
            "columns" to s.columns,
            "showLabels" to s.showLabels,
            "showSearchPill" to s.showSearchPill,
            "theme" to s.theme.name,
            "accent" to s.accent.name,
            "iconStyle" to s.iconStyle.name,
            "wallpaper" to s.wallpaper.name,
            "lowPowerMotion" to s.lowPowerMotion,
            "pauseWidgetsOnLowBattery" to s.pauseWidgetsOnLowBattery,
            "refresh" to s.refresh.name,
            "temperatureUnit" to s.temperatureUnit.name,
            "haptics" to s.haptics,
            "notificationDots" to s.notificationDots,
            "originalIconPackages" to s.originalIconPackages.sorted(),
            "hiddenApps" to s.hiddenApps.sorted(),
            "manualLocation" to s.manualLocation?.let { listOf(it.latitude, it.longitude) },
            "manualPlaceName" to s.manualPlaceName,
        ),
    )

    fun decode(text: String?): LauncherSettings {
        if (text.isNullOrBlank()) return LauncherSettings()
        val o = try {
            Json.parse(text).asObject()
        } catch (e: Json.JsonException) {
            return LauncherSettings()
        }
        val d = LauncherSettings()
        return LauncherSettings(
            columns = (o["columns"] as? Number)?.toInt()?.takeIf { it in 4..5 } ?: d.columns,
            showLabels = o["showLabels"] as? Boolean ?: d.showLabels,
            showSearchPill = o["showSearchPill"] as? Boolean ?: d.showSearchPill,
            theme = enumOf(o["theme"], d.theme),
            accent = enumOf(o["accent"], d.accent),
            iconStyle = enumOf(o["iconStyle"], d.iconStyle),
            wallpaper = enumOf(o["wallpaper"], d.wallpaper),
            lowPowerMotion = o["lowPowerMotion"] as? Boolean ?: d.lowPowerMotion,
            pauseWidgetsOnLowBattery = o["pauseWidgetsOnLowBattery"] as? Boolean ?: d.pauseWidgetsOnLowBattery,
            refresh = enumOf(o["refresh"], d.refresh),
            temperatureUnit = enumOf(o["temperatureUnit"], d.temperatureUnit),
            haptics = o["haptics"] as? Boolean ?: d.haptics,
            notificationDots = o["notificationDots"] as? Boolean ?: d.notificationDots,
            originalIconPackages = o["originalIconPackages"].asArray().filterIsInstance<String>().toSet(),
            hiddenApps = o["hiddenApps"].asArray().filterIsInstance<String>().toSet(),
            manualLocation = o["manualLocation"].asArray().let { ll ->
                val lat = (ll.getOrNull(0) as? Number)?.toDouble()
                val lon = (ll.getOrNull(1) as? Number)?.toDouble()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) GeoPoint(lat, lon) else null
            },
            manualPlaceName = o["manualPlaceName"] as? String ?: d.manualPlaceName,
        )
    }

    private inline fun <reified E : Enum<E>> enumOf(value: Any?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == value } ?: fallback
}
