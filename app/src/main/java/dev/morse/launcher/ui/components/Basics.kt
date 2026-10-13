package dev.morse.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.morse.core.glyph.Glyph
import dev.morse.core.motion.MotionSpec
import dev.morse.launcher.ui.theme.Morse
import dev.morse.launcher.ui.theme.toTween

/* ---------------- Text ---------------- */

@Composable
fun MText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val resolved = if (color != Color.Unspecified) {
        style.copy(color = color)
    } else if (style.color == Color.Unspecified) {
        style.copy(color = Morse.colors.text)
    } else {
        style
    }
    BasicText(text = text, modifier = modifier, style = resolved, maxLines = maxLines, overflow = overflow, softWrap = maxLines > 1)
}

/* ---------------- Glyphs ---------------- */

private val vectorCache = HashMap<Glyph, ImageVector>()

/** The glyph as a vector, built once per process. */
fun Glyph.vector(): ImageVector = vectorCache.getOrPut(this) {
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = Glyph.VIEWPORT,
        viewportHeight = Glyph.VIEWPORT,
    ).addPath(
        pathData = addPathNodes(path),
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = Glyph.STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = rememberVectorPainter(glyph.vector()),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}

/* ---------------- Surfaces ---------------- */

/** A card on the Morse surface colour with a 1 dp hairline (no shadows anywhere). */
@Composable
fun Modifier.morseSurface(shape: Shape, color: Color = Morse.colors.surface, line: Color = Morse.colors.hairline): Modifier =
    this.clip(shape).background(color, shape).border(1.dp, line, shape)

/**
 * The static dot-grid wallpaper. The point list is built once per size and
 * drawn in a single call; nothing is redrawn unless the size or colour changes.
 */
fun Modifier.dotGrid(ground: Color, dot: Color, spacing: Dp = 18.dp, radius: Dp = 1.dp): Modifier =
    this.drawWithCache {
        val step = spacing.toPx()
        val points = ArrayList<Offset>()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                points += Offset(x, y)
                x += step
            }
            y += step
        }
        val stroke = radius.toPx() * 2
        onDrawBehind {
            drawRect(ground)
            drawPoints(points, PointMode.Points, dot, strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }

/** Scales to 0.92 while pressed (90 ms in, 160 ms out), the only touch feedback Morse uses. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) MotionSpec.PRESS_SCALE else 1f,
        animationSpec = if (pressed) MotionSpec.pressIn.toTween() else MotionSpec.pressOut.toTween(),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/* ---------------- Controls ---------------- */

@Composable
fun MorseIconButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    glyphSize: Dp = 18.dp,
    background: Color = Morse.colors.surface,
    tint: Color = Morse.colors.text2,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(background, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, glyphSize, tint)
    }
}

/** A rounded pill button. [filled] uses the inverse colour (white on black, black on white). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    filled: Boolean = false,
    height: Dp = 40.dp,
) {
    val c = Morse.colors
    val interaction = remember { MutableInteractionSource() }
    val bg = if (filled) c.inverse else c.surface
    val fg = if (filled) c.onInverse else c.text
    Row(
        modifier = modifier
            .height(height)
            .pressScale(interaction)
            .then(if (filled) Modifier.clip(RoundedCornerShape(50)).background(bg) else Modifier.morseSurface(RoundedCornerShape(50)))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (glyph != null) GlyphIcon(glyph, 18.dp, fg)
        MText(text, Morse.type.ui(14.sp, color = fg), maxLines = 1)
    }
}

/** On = filled track with a dark knob; off = dim track. Differs in lightness, not just hue. */
@Composable
fun MorseSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val c = Morse.colors
    val offset by animateFloatAsState(if (checked) 1f else 0f, MotionSpec.pressOut.toTween(), label = "switch")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) c.inverse else c.hairline)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = if (checked) "On" else "Off"
            },
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .graphicsLayer { translationX = offset * 22.dp.toPx() }
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) c.onInverse else c.text3),
        )
    }
}

/** A segmented control (S | M, Dark | Light | Auto…). */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
) {
    val c = Morse.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(c.hairline.copy(alpha = if (c.isDark) 0.9f else 0.6f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) c.inverse else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                val style = if (mono) Morse.type.mono(12.sp, tracking = 0f) else Morse.type.ui(13.sp)
                MText(label(option), style, color = if (on) c.onInverse else c.text2, maxLines = 1)
            }
        }
    }
}

/** A row of dots or bars: battery meters, music progress. */
@Composable
fun DotMeter(
    lit: Int,
    total: Int,
    modifier: Modifier = Modifier,
    dotWidth: Dp = 4.dp,
    dotHeight: Dp = 10.dp,
    gap: Dp = 3.dp,
    on: Color = Morse.colors.text,
    off: Color = Morse.colors.hairline,
    highlight: Int = -1,
    highlightColor: Color = Morse.colors.accent,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(width = dotWidth, height = dotHeight)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == highlight) highlightColor else if (i < lit) on else off),
            )
        }
    }
}

@Composable
fun FlexDotMeter(lit: Int, total: Int, modifier: Modifier = Modifier, height: Dp = 10.dp, on: Color = Morse.colors.text, off: Color = Morse.colors.hairline) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(50))
                    .background(if (i < lit) on else off),
            )
        }
    }
}

@Composable
fun BadgeDot(modifier: Modifier = Modifier, size: Dp = 7.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Morse.colors.accent))
}

/** "SECTION" labels in Geist Mono caps. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    MText(text.uppercase(), Morse.type.mono(11.sp), modifier = modifier, color = Morse.colors.text3, maxLines = 1)
}
