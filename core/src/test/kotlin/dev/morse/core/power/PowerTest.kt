package dev.morse.core.power

import dev.morse.core.motion.MotionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTest {

    private val minute = 60_000L

    @Test
    fun widgetsPauseOnlyWhenEnabledAndNotCharging() {
        val low = PowerState(18, charging = false, systemSaver = false)
        assertTrue(PowerPolicy.widgetsPaused(low, true))
        assertFalse(PowerPolicy.widgetsPaused(low, false))
        assertFalse(PowerPolicy.widgetsPaused(low.copy(charging = true), true))
        assertFalse(PowerPolicy.widgetsPaused(PowerState(20, false, false), true))
        assertTrue(PowerPolicy.widgetsPaused(PowerState(80, false, systemSaver = true), true))
    }

    @Test
    fun motionIsReducedWhenAskedOrPaused() {
        assertEquals(MotionMode.LOW_POWER, PowerPolicy.motionMode(true, false))
        assertEquals(MotionMode.LOW_POWER, PowerPolicy.motionMode(false, true))
        assertEquals(MotionMode.FULL, PowerPolicy.motionMode(false, false))
    }

    @Test
    fun weatherRefreshFollowsTheProfile() {
        val last = 1_000_000L
        assertFalse(PowerPolicy.shouldRefreshWeather(last, last + 29 * minute, RefreshProfile.BALANCED, false, false))
        assertTrue(PowerPolicy.shouldRefreshWeather(last, last + 30 * minute, RefreshProfile.BALANCED, false, false))
        assertFalse(PowerPolicy.shouldRefreshWeather(last, last + 59 * minute, RefreshProfile.SAVER, false, false))
        assertTrue(PowerPolicy.shouldRefreshWeather(last, last + 15 * minute, RefreshProfile.FRESH, false, false))
    }

    @Test
    fun pausedWidgetsDoNotFetchUnlessForced() {
        val last = 1_000_000L
        assertFalse(PowerPolicy.shouldRefreshWeather(last, last + 300 * minute, RefreshProfile.BALANCED, paused = true, force = false))
        assertTrue(PowerPolicy.shouldRefreshWeather(last, last + 2 * minute, RefreshProfile.BALANCED, paused = true, force = true))
        // Manual refresh is throttled to once a minute.
        assertFalse(PowerPolicy.shouldRefreshWeather(last, last + 10_000, RefreshProfile.BALANCED, paused = false, force = true))
    }

    @Test
    fun firstFetchAndClockChanges() {
        assertTrue(PowerPolicy.shouldRefreshWeather(0L, 5L, RefreshProfile.SAVER, false, false))
        assertFalse(PowerPolicy.shouldRefreshWeather(0L, 5L, RefreshProfile.SAVER, paused = true, force = false))
        assertTrue(PowerPolicy.shouldRefreshWeather(10 * minute, 5 * minute, RefreshProfile.SAVER, false, false))
    }

    @Test
    fun batteryDots() {
        assertEquals(8, BatteryMeter.litDots(78))
        assertEquals(5, BatteryMeter.litDots(45))
        assertEquals(1, BatteryMeter.litDots(3))
        assertEquals(0, BatteryMeter.litDots(0))
        assertEquals(10, BatteryMeter.litDots(100))
        assertEquals(10, BatteryMeter.litDots(250))
        assertEquals(28, BatteryMeter.litDots(100, total = 28))
        assertTrue(BatteryMeter.isLow(19))
        assertFalse(BatteryMeter.isLow(20))
    }

    @Test
    fun lowPowerMotionOnlyFades() {
        MotionSpec.overlays(MotionMode.LOW_POWER).forEach {
            assertTrue(it.durationMillis <= MotionSpec.LOW_POWER_FADE_MS)
            assertNull(it.scaleFrom)
        }
        val page = MotionSpec.pageSettle(MotionMode.LOW_POWER)
        assertTrue(page.durationMillis <= MotionSpec.LOW_POWER_PAGE_MS)
        assertNull(page.scaleFrom)
    }

    @Test
    fun fullMotionStaysShort() {
        (MotionSpec.overlays(MotionMode.FULL) + MotionSpec.pageSettle(MotionMode.FULL)).forEach {
            assertTrue(it.durationMillis <= MotionSpec.MAX_FULL_MS)
        }
        assertTrue(MotionSpec.folderClose(MotionMode.FULL).durationMillis < MotionSpec.folderOpen(MotionMode.FULL).durationMillis)
        assertTrue(MotionSpec.sheetExit(MotionMode.FULL).durationMillis < MotionSpec.sheetEnter(MotionMode.FULL).durationMillis)
        assertTrue(MotionSpec.pressIn.durationMillis < MotionSpec.pressOut.durationMillis)
    }
}
