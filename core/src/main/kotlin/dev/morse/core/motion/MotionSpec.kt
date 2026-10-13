package dev.morse.core.motion

import dev.morse.core.power.MotionMode

/** A cubic-bezier easing curve, as in CSS. */
data class Bezier(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    companion object {
        val LINEAR = Bezier(0f, 0f, 1f, 1f)
    }
}

/**
 * One transition: how long, which curve, and whether it may scale.
 * Low-power transitions never scale or slide; they only fade.
 */
data class TransitionSpec(
    val durationMillis: Int,
    val easing: Bezier,
    val scaleFrom: Float? = null,
)

/** The Morse motion tokens. Every value here is shown on the Motion board of the design. */
object MotionSpec {
    val GLIDE = Bezier(0.2f, 0.8f, 0.2f, 1f)
    val DROP = Bezier(0.4f, 0f, 1f, 1f)

    const val LOW_POWER_FADE_MS = 120
    const val MAX_FULL_MS = 300

    private val fade = TransitionSpec(LOW_POWER_FADE_MS, Bezier.LINEAR)

    fun appOpen(mode: MotionMode) = if (mode == MotionMode.LOW_POWER) fade else TransitionSpec(280, GLIDE, scaleFrom = 0.12f)
    fun folderOpen(mode: MotionMode) = if (mode == MotionMode.LOW_POWER) fade else TransitionSpec(240, GLIDE, scaleFrom = 0.2f)
    fun folderClose(mode: MotionMode) = if (mode == MotionMode.LOW_POWER) fade else TransitionSpec(180, DROP, scaleFrom = 0.2f)
    fun sheetEnter(mode: MotionMode) = if (mode == MotionMode.LOW_POWER) fade else TransitionSpec(260, GLIDE)
    fun sheetExit(mode: MotionMode) = if (mode == MotionMode.LOW_POWER) fade else TransitionSpec(200, DROP)
    fun pageSettle(mode: MotionMode) = if (mode == MotionMode.LOW_POWER) TransitionSpec(LOW_POWER_PAGE_MS, Bezier.LINEAR) else TransitionSpec(300, GLIDE)

    /** Press feedback is cheap and stays on in every mode. */
    val pressIn = TransitionSpec(90, GLIDE, scaleFrom = 0.92f)
    val pressOut = TransitionSpec(160, GLIDE)
    const val PRESS_SCALE = 0.92f

    /** The once-a-minute clock change. */
    val minuteTick = TransitionSpec(180, Bezier.LINEAR)

    /** Low-power page swipes still slide (they follow the finger) but never take longer than this. */
    const val LOW_POWER_PAGE_MS = 160

    /** Every overlay transition (everything except the finger-driven page swipe). */
    fun overlays(mode: MotionMode): List<TransitionSpec> = listOf(
        appOpen(mode), folderOpen(mode), folderClose(mode), sheetEnter(mode), sheetExit(mode),
    )
}
