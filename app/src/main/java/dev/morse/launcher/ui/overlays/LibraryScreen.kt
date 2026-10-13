package dev.morse.launcher.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.morse.core.apps.AppInfo
import dev.morse.core.apps.LetterSection
import dev.morse.core.apps.LibraryGroup
import dev.morse.core.glyph.Glyph
import dev.morse.launcher.ui.components.AppIcon
import dev.morse.launcher.ui.components.FullScreenLayer
import dev.morse.launcher.ui.components.GlyphIcon
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.MorseIconButton
import dev.morse.launcher.ui.components.Segmented
import dev.morse.launcher.ui.components.morseSurface
import dev.morse.launcher.ui.components.pressScale
import dev.morse.launcher.ui.components.tapOrHold
import dev.morse.launcher.ui.home.BoundsHolder
import dev.morse.launcher.ui.home.HomeUiState
import dev.morse.launcher.ui.theme.Morse
import kotlinx.coroutines.launch

enum class LibraryMode(val title: String) { CATEGORIES("Categories"), AZ("A – Z") }

/** The app library: category cards or an A–Z list with a letter rail. */
@Composable
fun LibraryScreen(
    groups: List<LibraryGroup>,
    sections: List<LetterSection>,
    state: HomeUiState,
    appCount: Int,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppMenu: (AppInfo, Rect?) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Morse.colors
    var mode by rememberSaveable { mutableStateOf(LibraryMode.CATEGORIES) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    FullScreenLayer("library") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 24.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MorseIconButton(Glyph.BACK, "Back to home", onClose, modifier = Modifier.morseSurface(RoundedCornerShape(50)))
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .pressScale(interaction)
                    .morseSurface(RoundedCornerShape(50))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onSearch)
                    .semantics(mergeDescendants = true) { contentDescription = "Search apps" }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlyphIcon(Glyph.SEARCH, 17.dp, c.text3)
                MText("App library", Morse.type.ui(15.sp, weight = FontWeight.Normal), color = c.text3, modifier = Modifier.weight(1f))
                MText("$appCount APPS", Morse.type.mono(11.sp), color = c.text3)
            }
        }
        Spacer(Modifier.height(14.dp))
        Segmented(
            options = LibraryMode.entries,
            selected = mode,
            label = { it.title },
            onSelect = {
                mode = it
                expanded = null
            },
            modifier = Modifier.padding(start = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        val open = groups.firstOrNull { it.id == expanded }
        when {
            open != null -> GroupList(open, state, onLaunch, onAppMenu) { expanded = null }
            mode == LibraryMode.CATEGORIES -> CategoryGrid(groups, state, onLaunch, onAppMenu) { expanded = it }
            else -> AlphabetList(sections, state, onLaunch, onAppMenu)
        }
    }
}

@Composable
private fun CategoryGrid(
    groups: List<LibraryGroup>,
    state: HomeUiState,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppMenu: (AppInfo, Rect?) -> Unit,
    onExpand: (String) -> Unit,
) {
    val c = Morse.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (groups.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                MText("Loading apps…", Morse.type.ui(14.sp), color = c.text2)
            }
        }
        items(groups, key = { it.id }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .morseSurface(RoundedCornerShape(28.dp))
                        .padding(14.dp),
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        val big = group.apps.take(3)
                        val small = group.apps.drop(3).take(4)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            big.take(2).forEach { LibraryIcon(it, state, 62, onLaunch, onAppMenu) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            big.getOrNull(2)?.let { LibraryIcon(it, state, 62, onLaunch, onAppMenu) } ?: Spacer(Modifier.size(62.dp))
                            if (small.isNotEmpty() || group.apps.size > 3) {
                                Column(
                                    Modifier
                                        .size(62.dp)
                                        .clickable(role = Role.Button) { onExpand(group.id) }
                                        .semantics { contentDescription = "All ${group.title}" },
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    small.chunked(2).forEach { pair ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            pair.forEach { AppIcon(it, state.isOriginal(it.key), size = 28.dp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                MText(group.title, Morse.type.ui(12.sp), color = c.text2, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun LibraryIcon(
    app: AppInfo,
    state: HomeUiState,
    sizeDp: Int,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppMenu: (AppInfo, Rect?) -> Unit,
) {
    val bounds = remember { BoundsHolder() }
    AppIcon(
        app = app,
        original = state.isOriginal(app.key),
        size = sizeDp.dp,
        badge = state.hasBadge(app.key),
        modifier = Modifier
            .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
            .tapOrHold(app.label, onTap = { onLaunch(app, bounds.rect) }, onHold = { onAppMenu(app, bounds.rect) }),
    )
}

@Composable
private fun AppListRow(
    app: AppInfo,
    state: HomeUiState,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppMenu: (AppInfo, Rect?) -> Unit,
) {
    val bounds = remember { BoundsHolder() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
            .tapOrHold(app.label, onTap = { onLaunch(app, bounds.rect) }, onHold = { onAppMenu(app, bounds.rect) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, state.isOriginal(app.key), size = 40.dp, badge = state.hasBadge(app.key))
        Spacer(Modifier.width(14.dp))
        MText(app.label, Morse.type.ui(15.sp), maxLines = 1)
    }
}

@Composable
private fun GroupList(
    group: LibraryGroup,
    state: HomeUiState,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppMenu: (AppInfo, Rect?) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                MorseIconButton(Glyph.BACK, "Back to categories", onBack, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                MText(group.title, Morse.type.ui(18.sp, weight = FontWeight.SemiBold))
            }
        }
        items(group.apps, key = { it.key.flatten() }) { AppListRow(it, state, onLaunch, onAppMenu) }
    }
}

@Composable
private fun AlphabetList(
    sections: List<LetterSection>,
    state: HomeUiState,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppMenu: (AppInfo, Rect?) -> Unit,
) {
    val c = Morse.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Index of each section header in the flat list.
    val headerIndex = remember(sections) {
        var i = 0
        sections.associate { s -> s.letter to i.also { i += 1 + s.apps.size } }
    }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 24.dp, end = 8.dp, bottom = 40.dp),
        ) {
            sections.forEach { section ->
                item(key = "h-${section.letter}") {
                    MText(section.letter.toString(), Morse.type.mono(13.sp, weight = FontWeight.SemiBold), color = c.text3, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                }
                items(section.apps, key = { it.key.flatten() }) { AppListRow(it, state, onLaunch, onAppMenu) }
            }
        }
        Column(
            Modifier
                .fillMaxHeight()
                .width(22.dp)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ('A'..'Z').plus('#').forEach { letter ->
                val target = headerIndex[letter]
                MText(
                    letter.toString(),
                    Morse.type.mono(9.5.sp, tracking = 0f),
                    color = if (target != null) c.text else c.text3.copy(alpha = 0.5f),
                    modifier = if (target != null) {
                        Modifier.clickable { scope.launch { listState.scrollToItem(target) } }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
