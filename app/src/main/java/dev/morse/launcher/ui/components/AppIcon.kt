package dev.morse.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.morse.core.apps.AppInfo
import dev.morse.core.apps.AppKey
import dev.morse.core.apps.IconResolver
import dev.morse.core.glyph.Glyph
import dev.morse.launcher.data.IconImage
import dev.morse.launcher.ui.theme.LocalIconStyle
import dev.morse.launcher.ui.theme.Morse

/** Supplies app icon images; replaced with a no-op in tests. */
fun interface IconLoader {
    suspend fun load(key: AppKey, original: Boolean, sizePx: Int): IconImage
}

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { IconLoader { _, _, _ -> IconImage.None } }

/** Corner radius of a tile, 17/60 of its size (as on the Icon system board). */
fun tileShape(size: Dp) = RoundedCornerShape(size * (17f / 60f))

/**
 * One app tile. In order of preference it shows: the app's original icon (if
 * the person asked for it), the Morse glyph, the app's monochrome layer, or
 * its initial in dot-matrix type.
 */
@Composable
fun AppIcon(
    app: AppInfo?,
    original: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    badge: Boolean = false,
) {
    val c = Morse.colors
    val style = LocalIconStyle.current
    val shape = tileShape(size)
    val tile = c.tile(style)
    val ink = c.glyph(style)
    Box(modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(tile, shape)
                .border(1.dp, c.hairline, shape),
            contentAlignment = Alignment.Center,
        ) {
            val glyph: Glyph? = app?.glyph
            if (app != null && glyph != null && !original) {
                GlyphIcon(glyph, size * 0.5f, ink)
            } else if (app != null) {
                val loader = LocalIconLoader.current
                val px = with(LocalDensity.current) { (size * 1.1f).roundToPx() }
                val image by produceState<IconImage?>(initialValue = null, app.key, original, px) {
                    value = loader.load(app.key, original, px)
                }
                when (val img = image) {
                    is IconImage.Original -> Image(
                        bitmap = img.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is IconImage.Monochrome -> Image(
                        bitmap = img.bitmap,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ink),
                        modifier = Modifier.requiredSize(size * 1.1f),
                    )
                    IconImage.None -> Initial(app.label, size)
                    null -> Unit
                }
            } else {
                Initial("?", size)
            }
        }
        if (badge) BadgeDot(Modifier.align(Alignment.TopEnd).padding(top = size * 0.115f, end = size * 0.115f))
    }
}

@Composable
private fun Initial(label: String, size: Dp) {
    MText(
        IconResolver.initialFor(label),
        Morse.type.dot((size.value * 0.62f).sp),
        color = Morse.colors.glyph(LocalIconStyle.current),
        maxLines = 1,
    )
}
