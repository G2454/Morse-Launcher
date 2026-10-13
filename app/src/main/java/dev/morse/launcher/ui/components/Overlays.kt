package dev.morse.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.morse.core.glyph.Glyph
import dev.morse.core.motion.MotionSpec
import dev.morse.launcher.ui.theme.Morse
import dev.morse.launcher.ui.theme.toTween

/**
 * Tap and long-press on a leaf element (library and folder icons), with the
 * Morse press scale and proper accessibility actions.
 */
@Composable
fun Modifier.tapOrHold(label: String, onTap: () -> Unit, onHold: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val scale by animateFloatAsState(
        if (pressed) MotionSpec.PRESS_SCALE else 1f,
        if (pressed) MotionSpec.pressIn.toTween() else MotionSpec.pressOut.toTween(),
        label = "tapOrHold",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .semantics(mergeDescendants = true) {
            contentDescription = label
            role = Role.Button
            onClick { tap(); true }
            onLongClick { hold(); true }
        }
        .pointerInput(label) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { tap() },
                onLongPress = { hold() },
            )
        }
}

/** A full-screen layer on the ground colour (search, library, settings). */
@Composable
fun FullScreenLayer(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Morse.colors.ground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.systemBars)
            .testTag(tag),
        content = content,
    )
}

/** A bottom sheet over a solid dim (no live blur). Tapping the dim closes it. */
@Composable
fun BottomSheetLayer(
    tag: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Morse.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .testTag(tag),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(if (c.isDark) Color(0xFF141414) else c.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 20.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.hairline),
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** Title row for sheets: a heading and a close button. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MText(title, Morse.type.ui(20.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        MorseIconButton(Glyph.CLOSE, "Close", onClose)
    }
}

/** One row in a list: glyph, title, optional detail and trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    detail: String? = null,
    titleColor: Color = Morse.colors.text,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Morse.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.hairline.copy(alpha = if (c.isDark) 0.8f else 0.5f)),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(glyph, 19.dp, titleColor) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MText(title, Morse.type.ui(15.sp), color = titleColor, maxLines = 2)
            if (detail != null) MText(detail, Morse.type.ui(12.5.sp, weight = FontWeight.Normal), color = c.text3, maxLines = 3)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A grouped card of rows, as in the settings design. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .morseSurface(RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp),
        content = content,
    )
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Morse.colors.hairline))
}

@Composable
fun BoxScope.CenteredMessage(text: String) {
    MText(text, Morse.type.ui(14.sp, weight = FontWeight.Normal), color = Morse.colors.text2, modifier = Modifier.align(Alignment.Center))
}
