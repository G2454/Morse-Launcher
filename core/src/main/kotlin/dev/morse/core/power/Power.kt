package dev.morse.core.power

import kotlin.math.max

/** How often widgets that fetch data are allowed to refresh. */
enum class RefreshProfile(val title: String, val weatherMinutes: Int) {
    SAVER("Saver", 60),
    BALANCED("Balanced", 30),
    FRESH("Fresh", 15),
}

/** A snapshot of the phone's power situation. */
data class PowerState(
    val batteryPercent: Int,
    val charging: Boolean,
    val systemSaver: Boolean,
) {
    companion object {
        val UNKNOWN = PowerState(batteryPercent = 100, charging = false, systemSaver = false)
    }
}

enum class MotionMode { FULL, LOW_POWER }

object PowerPolicy {
    const val LOW_BATTERY_PERCENT = 20

    /**
     * Widgets freeze (keep their last value, stop fetching) when the setting is on,
     * the phone is not charging, and either the battery is low or Battery Saver is on.
     */
    fun widgetsPaused(state: PowerState, pauseSettingEnabled: Boolean): Boolean =
        pauseSettingEnabled && !state.charging &&
            (state.batteryPercent < LOW_BATTERY_PERCENT || state.systemSaver)

    /** Motion is reduced when the person asked for it, or while widgets are paused. */
    fun motionMode(lowPowerMotionSetting: Boolean, paused: Boolean): MotionMode =
        if (lowPowerMotionSetting || paused) MotionMode.LOW_POWER else MotionMode.FULL

    /**
     * Whether a weather fetch should happen now.
     * A manual refresh ([force]) is always allowed, once per minute at most.
     */
    fun shouldRefreshWeather(
        lastFetchMillis: Long,
        nowMillis: Long,
        profile: RefreshProfile,
        paused: Boolean,
        force: Boolean,
    ): Boolean {
        val age = nowMillis - lastFetchMillis
        if (lastFetchMillis <= 0L) return !paused || force
        if (age < 0) return true // clock moved backwards
        if (force) return age >= 60_000L
        if (paused) return false
        return age >= profile.weatherMinutes * 60_000L
    }
}

object BatteryMeter {
    /** Lit dots out of [total] for a percentage; any charge above zero lights at least one. */
    fun litDots(percent: Int, total: Int = 10): Int {
        val p = percent.coerceIn(0, 100)
        if (p == 0) return 0
        return max(1, (p * total + 50) / 100).coerceAtMost(total)
    }

    fun isLow(percent: Int): Boolean = percent < PowerPolicy.LOW_BATTERY_PERCENT
}
