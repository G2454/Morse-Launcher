package dev.morse.launcher.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.morse.core.apps.AppKey
import dev.morse.core.glyph.Glyph
import dev.morse.core.layout.FolderItem
import dev.morse.launcher.ui.components.AppIcon
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.MorseIconButton
import dev.morse.launcher.ui.components.morseSurface
import dev.morse.launcher.ui.components.tapOrHold
import dev.morse.launcher.ui.home.BoundsHolder
import dev.morse.launcher.ui.home.HomeUiState
import dev.morse.launcher.ui.theme.Morse

/** An open folder: title (renamable) over a card of its apps, on a solid dim. */
@Composable
fun FolderOverlay(
    folder: FolderItem,
    state: HomeUiState,
    onLaunch: (AppKey, Rect?) -> Unit,
    onAppMenu: (AppKey, Rect?) -> Unit,
    onRename: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = Morse.colors
    var renaming by remember { mutableStateOf(false) }
    var draft by remember(folder.id) { mutableStateOf(folder.title) }
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .testTag("folder"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (renaming) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(24) },
                        singleLine = true,
                        textStyle = Morse.type.ui(24.sp, color = c.text).copy(textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onRename(draft)
                            renaming = false
                        }),
                        modifier = Modifier
                            .width(220.dp)
                            .focusRequester(focus),
                    )
                } else {
                    MText(folder.title.uppercase(), Morse.type.dot(44.sp), maxLines = 1)
                }
                Spacer(Modifier.width(6.dp))
                MorseIconButton(
                    if (renaming) Glyph.CHECK else Glyph.PENCIL,
                    if (renaming) "Save name" else "Rename folder",
                    {
                        if (renaming) onRename(draft)
                        renaming = !renaming
                    },
                    background = androidx.compose.ui.graphics.Color.Transparent,
                    tint = c.text3,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .morseSurface(RoundedCornerShape(36.dp), color = c.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp, horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                folder.apps.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            FolderApp(key, state, onLaunch, onAppMenu, Modifier.weight(1f))
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderApp(
    key: AppKey,
    state: HomeUiState,
    onLaunch: (AppKey, Rect?) -> Unit,
    onAppMenu: (AppKey, Rect?) -> Unit,
    modifier: Modifier,
) {
    val info = state.app(key)
    val label = info?.label ?: key.packageName
    val bounds = remember { BoundsHolder() }
    Column(
        modifier
            .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
            .tapOrHold(label, onTap = { onLaunch(key, bounds.rect) }, onHold = { onAppMenu(key, bounds.rect) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(info, original = state.isOriginal(key), badge = state.hasBadge(key), size = 60.dp)
        Spacer(Modifier.height(7.dp))
        MText(label, Morse.type.ui(11.5.sp), color = Morse.colors.text.copy(alpha = 0.86f), maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
    }
}
