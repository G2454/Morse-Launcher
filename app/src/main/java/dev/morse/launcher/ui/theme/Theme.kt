package dev.morse.launcher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.morse.core.motion.Bezier
import dev.morse.core.motion.TransitionSpec
import dev.morse.core.power.MotionMode
import dev.morse.core.settings.Accent
import dev.morse.core.settings.IconStyle
import dev.morse.core.settings.ThemeMode
import dev.morse.launcher.R

/** The Morse colour tokens (see the Visual language board). */
@Immutable
data class MorseColors(
    val isDark: Boolean,
    /** Wallpaper base. True black in dark mode: OLED pixels off. */
    val ground: Color,
    /** Wallpaper dot grid. */
    val dots: Color,
    /** Widgets, dock, pills. */
    val surface: Color,
    /** Icon tiles. */
    val raised: Color,
    /** 1 px inner borders. */
    val hairline: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    /** The single accent: badges, live dot, caret. */
    val accent: Color,
    /** Solid dim behind folders and sheets (instead of a live blur). */
    val scrim: Color,
    /** Filled controls (switch on, primary button). */
    val inverse: Color,
    val onInverse: Color,
) {
    fun tile(style: IconStyle): Color = if (style == IconStyle.SIGNAL) accent.copy(alpha = 0.14f).compositeOver(ground) else raised
    fun glyph(style: IconStyle): Color = if (style == IconStyle.SIGNAL) accent else text

    companion object {
        fun dark(accent: Accent) = MorseColors(
            isDark = true,
            ground = Color(0xFF000000),
            dots = Color(0xFF1E1E1E),
            surface = Color(0xFF111111),
            raised = Color(0xFF161616),
            hairline = Color(0xFF232323),
            text = Color(0xFFF2F2F0),
            text2 = Color(0xFFA3A3A0),
            text3 = Color(0xFF8C8C88),
            accent = Color(accent.darkArgb),
            scrim = Color(0xD9000000),
            inverse = Color(0xFFF2F2F0),
            onInverse = Color(0xFF000000),
        )

        fun light(accent: Accent) = MorseColors(
            isDark = false,
            ground = Color(0xFFECECE8),
            dots = Color(0xFFD3D3CD),
            surface = Color(0xFFFFFFFF),
            raised = Color(0xFFFFFFFF),
            hairline = Color(0xFFDEDED9),
            text = Color(0xFF141414),
            text2 = Color(0xFF5E5E5A),
            text3 = Color(0xFF77776F),
            accent = Color(accent.lightArgb),
            scrim = Color(0xD9ECECE8),
            inverse = Color(0xFF141414),
            onInverse = Color(0xFFFFFFFF),
        )
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

/** The three families: Doto (dot-matrix display), Geist (UI), Geist Mono (labels). */
@Immutable
data class MorseFonts(val dot: FontFamily, val ui: FontFamily, val mono: FontFamily) {
    companion object {
        @OptIn(ExperimentalTextApi::class)
        val Bundled = MorseFonts(
            dot = FontFamily(
                Font(
                    R.font.doto,
                    FontWeight.Black,
                    variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.Setting("ROND", 100f)),
                ),
            ),
            ui = FontFamily(
                Font(R.font.geist, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
                Font(R.font.geist, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
                Font(R.font.geist, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
            ),
            mono = FontFamily(
                Font(R.font.geist_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
                Font(R.font.geist_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
                Font(R.font.geist_mono, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
            ),
        )

        /** Platform fonts; used by unit tests where bundled variable fonts are not available. */
        val System = MorseFonts(FontFamily.Monospace, FontFamily.SansSerif, FontFamily.Monospace)
    }
}

/** Text styles. Doto is only used at 40 sp and up, as on the design boards. */
@Immutable
class MorseType(private val fonts: MorseFonts) {
    fun dot(size: TextUnit, color: Color = Color.Unspecified) = TextStyle(
        fontFamily = fonts.dot,
        fontWeight = FontWeight.Black,
        fontSize = size,
        lineHeight = size * 0.86f,
        letterSpacing = (-0.02).em,
        color = color,
    )

    fun ui(size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Medium, color: Color = Color.Unspecified) = TextStyle(
        fontFamily = fonts.ui,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size * 1.25f,
        color = color,
    )

    fun mono(size: TextUnit = 11.sp, weight: FontWeight = FontWeight.Medium, color: Color = Color.Unspecified, tracking: Float = 0.08f) = TextStyle(
        fontFamily = fonts.mono,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size * 1.2f,
        letterSpacing = tracking.em,
        color = color,
    )
}

val LocalMorseColors = staticCompositionLocalOf { MorseColors.dark(Accent.SIGNAL_RED) }
val LocalMorseType = staticCompositionLocalOf { MorseType(MorseFonts.System) }
val LocalMotionMode = staticCompositionLocalOf { MotionMode.FULL }
val LocalIconStyle = staticCompositionLocalOf { IconStyle.MONO }

object Morse {
    val colors: MorseColors
        @Composable @ReadOnlyComposable get() = LocalMorseColors.current
    val type: MorseType
        @Composable @ReadOnlyComposable get() = LocalMorseType.current
    val motion: MotionMode
        @Composable @ReadOnlyComposable get() = LocalMotionMode.current
}

@Composable
fun MorseTheme(
    theme: ThemeMode,
    accent: Accent,
    iconStyle: IconStyle = IconStyle.MONO,
    motion: MotionMode = MotionMode.FULL,
    fonts: MorseFonts = MorseFonts.Bundled,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val colors = remember(dark, accent) { if (dark) MorseColors.dark(accent) else MorseColors.light(accent) }
    val type = remember(fonts) { MorseType(fonts) }
    CompositionLocalProvider(
        LocalMorseColors provides colors,
        LocalMorseType provides type,
        LocalMotionMode provides motion,
        LocalIconStyle provides iconStyle,
        content = content,
    )
}

/* ---- Motion tokens → Compose animation specs ---- */

fun Bezier.toEasing(): Easing = if (this == Bezier.LINEAR) LinearEasing else CubicBezierEasing(x1, y1, x2, y2)

fun <T> TransitionSpec.toTween(): TweenSpec<T> = tween(durationMillis = durationMillis, easing = easing.toEasing())
