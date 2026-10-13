package dev.morse.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.morse.core.apps.AppKey
import dev.morse.core.apps.IconResolver
import dev.morse.core.glyph.Glyph
import dev.morse.core.layout.AppItem
import dev.morse.core.layout.Cell
import dev.morse.core.layout.FolderItem
import dev.morse.core.layout.GridMetrics
import dev.morse.core.layout.HomeItem
import dev.morse.core.layout.HomePage
import dev.morse.core.layout.LayoutOps
import dev.morse.core.layout.WidgetItem
import dev.morse.core.motion.MotionSpec
import dev.morse.core.settings.WallpaperMode
import dev.morse.launcher.ui.components.AppIcon
import dev.morse.launcher.ui.components.GlyphIcon
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.PillButton
import dev.morse.launcher.ui.components.dotGrid
import dev.morse.launcher.ui.components.morseSurface
import dev.morse.launcher.ui.components.pressScale
import dev.morse.launcher.ui.components.tileShape
import dev.morse.launcher.ui.theme.LocalIconStyle
import dev.morse.launcher.ui.theme.Morse
import dev.morse.launcher.ui.theme.toTween
import dev.morse.launcher.ui.widgets.WidgetContent

private val GRID_PADDING = 16.dp
private val WIDGET_INSET = 8.dp
private val ICON_SIZE = 60.dp

/** The home screen: pages of icons and widgets, the search pill and the dock. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    pagerState: PagerState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val c = Morse.colors
    val drag = remember { DragState() }
    val background = when (state.settings.wallpaper) {
        WallpaperMode.DOTS -> Modifier.dotGrid(c.ground, c.dots)
        WallpaperMode.PLAIN -> Modifier.background(c.ground)
        WallpaperMode.SYSTEM -> Modifier
    }
    val settle = MotionSpec.pageSettle(Morse.motion)

    Box(modifier.fillMaxSize().then(background).testTag("home")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            if (state.editMode) {
                EditBar(
                    page = pagerState.currentPage,
                    onAddWidget = { callbacks.onOpenWidgetPicker(pagerState.currentPage) },
                    onSettings = callbacks.onOpenSettings,
                    onDone = callbacks.onExitEdit,
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .homeSwipes(enabled = !state.editMode, onUp = callbacks.onOpenLibrary, onDown = callbacks.onOpenSearch),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().testTag("pager"),
                    userScrollEnabled = !drag.active,
                    beyondViewportPageCount = 0,
                    flingBehavior = PagerDefaults.flingBehavior(pagerState, snapAnimationSpec = settle.toTween()),
                    key = { index -> state.layout.pages.getOrNull(index)?.id ?: index },
                ) { pageIndex ->
                    val page = state.layout.pages.getOrNull(pageIndex)
                    if (page != null) PageGrid(pageIndex, page, state, drag, callbacks)
                }
            }
            PillRow(state, pagerState, callbacks)
            Dock(state, drag, pagerState, callbacks)
        }
    }
}

/* ---------------- Page grid ---------------- */

@Composable
private fun PageGrid(
    pageIndex: Int,
    page: HomePage,
    state: HomeUiState,
    drag: DragState,
    callbacks: HomeCallbacks,
) {
    val columns = state.layout.columns
    val rows = state.layout.rows
    val haptics = LocalHapticFeedback.current
    val currentPage by rememberUpdatedState(page)
    val currentState by rememberUpdatedState(state)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = GRID_PADDING),
    ) {
        val cellW = maxWidth / columns
        val cellH = maxHeight / rows
        val density = LocalDensity.current
        val cellWpx = with(density) { cellW.toPx() }
        val cellHpx = with(density) { cellH.toPx() }
        val currentCallbacks by rememberUpdatedState(callbacks)

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    drag.gridOrigins[pageIndex] = it.positionInWindow()
                    drag.gridSizePx = it.size.width.toFloat() to it.size.height.toFloat()
                }
                .areaPress(
                    key = Triple(state.editMode, cellWpx, cellHpx),
                    onLongPress = { pos ->
                        if (currentState.editMode) return@areaPress false
                        val cell = Cell((pos.x / cellWpx).toInt(), (pos.y / cellHpx).toInt())
                        val item = LayoutOps.itemAt(currentPage, cell)
                        if (currentState.settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (item == null) {
                            currentCallbacks.onEnterEdit()
                        } else {
                            val origin = drag.gridOrigins[pageIndex] ?: Offset.Zero
                            val bounds = Rect(
                                origin.x + item.cell.column * cellWpx,
                                origin.y + item.cell.row * cellHpx,
                                origin.x + (item.cell.column + item.span.columns) * cellWpx,
                                origin.y + (item.cell.row + item.span.rows) * cellHpx,
                            )
                            currentCallbacks.onItemMenu(pageIndex, item, bounds)
                        }
                        true
                    },
                    onTap = { pos ->
                        if (!currentState.editMode) return@areaPress
                        val cell = Cell((pos.x / cellWpx).toInt(), (pos.y / cellHpx).toInt())
                        if (LayoutOps.itemAt(currentPage, cell) == null) currentCallbacks.onExitEdit()
                    },
                ),
        ) {
            page.items.forEach { item ->
                key(item.id) {
                    PlacedItem(
                        pageIndex = pageIndex,
                        item = item,
                        state = state,
                        drag = drag,
                        cellW = cellW,
                        cellH = cellH,
                        onDrop = { dropped, offset ->
                            handleDrop(pageIndex, dropped, offset, currentPage, currentState, drag, cellWpx, cellHpx, callbacks)
                        },
                        callbacks = callbacks,
                    )
                }
            }
        }
    }
}

private fun handleDrop(
    pageIndex: Int,
    item: HomeItem,
    offset: Offset,
    page: HomePage,
    state: HomeUiState,
    drag: DragState,
    cellWpx: Float,
    cellHpx: Float,
    callbacks: HomeCallbacks,
) {
    val columns = state.layout.columns
    val rows = state.layout.rows
    val topLeft = Offset(item.cell.column * cellWpx, item.cell.row * cellHpx) + offset
    val center = topLeft + Offset(item.span.columns * cellWpx / 2f, item.span.rows * cellHpx / 2f)
    val origin = drag.gridOrigins[pageIndex] ?: Offset.Zero
    val centerInWindow = origin + center

    // Into the dock.
    val dock = drag.dockBounds
    if (item is AppItem && dock != null && dock.contains(centerInWindow)) {
        val slot = GridMetrics.dockSlotAt(centerInWindow.x, dock.left, dock.width)
        if (slot >= 0) callbacks.onGridToDock(pageIndex, item.id, slot)
        return
    }
    // Onto another app or folder: make or grow a folder.
    if (item is AppItem) {
        val col = (center.x / cellWpx).toInt()
        val row = (center.y / cellHpx).toInt()
        val target = LayoutOps.itemAt(page, Cell(col, row))
        if (target != null && target.id != item.id && (target is AppItem || target is FolderItem)) {
            val targetCenter = Offset((target.cell.column + 0.5f) * cellWpx, (target.cell.row + 0.5f) * cellHpx)
            if ((center - targetCenter).getDistance() < cellWpx * 0.32f) {
                callbacks.onDropOnto(pageIndex, item.id, target.id)
                return
            }
        }
    }
    val cell = GridMetrics.snapToCell(topLeft.x, topLeft.y, cellWpx, cellHpx, item.span, columns, rows)
    callbacks.onMove(pageIndex, item.id, cell)
}

@Composable
private fun PlacedItem(
    pageIndex: Int,
    item: HomeItem,
    state: HomeUiState,
    drag: DragState,
    cellW: Dp,
    cellH: Dp,
    onDrop: (HomeItem, Offset) -> Unit,
    callbacks: HomeCallbacks,
) {
    val dragging = drag.itemId == item.id
    val currentItem by rememberUpdatedState(item)
    Box(
        Modifier
            .offset(x = cellW * item.cell.column, y = cellH * item.cell.row)
            .size(width = cellW * item.span.columns, height = cellH * item.span.rows)
            .zIndex(if (dragging) 2f else 0f)
            .graphicsLayer {
                if (drag.itemId == item.id) {
                    translationX = drag.offset.x
                    translationY = drag.offset.y
                    scaleX = 1.06f
                    scaleY = 1.06f
                    alpha = 0.92f
                }
            }
            .then(
                if (state.editMode) {
                    Modifier.pointerInputDrag(item.id, drag) { offset -> onDrop(currentItem, offset) }
                } else {
                    Modifier
                },
            ),
    ) {
        when (item) {
            is AppItem -> AppCell(
                app = item.app,
                state = state,
                cellWidth = cellW,
                onClick = { bounds -> if (!state.editMode) callbacks.onLaunch(item.app, bounds) },
                onRemove = if (state.editMode) ({ callbacks.onRemoveItem(pageIndex, item.id) }) else null,
            )
            is FolderItem -> FolderCell(
                folder = item,
                state = state,
                cellWidth = cellW,
                onClick = { bounds -> callbacks.onOpenFolder(pageIndex, item.id, bounds) },
                onRemove = if (state.editMode) ({ callbacks.onRemoveItem(pageIndex, item.id) }) else null,
            )
            is WidgetItem -> Box(Modifier.fillMaxSize().padding(WIDGET_INSET)) {
                Box(Modifier.fillMaxSize().then(if (state.editMode) Modifier.editOutline(28.dp) else Modifier)) {
                    WidgetContent(item, state.editMode)
                }
                if (state.editMode) {
                    RemoveBadge(Modifier.align(Alignment.TopStart).offset(x = (-14).dp, y = (-14).dp), "Remove widget") {
                        callbacks.onRemoveItem(pageIndex, item.id)
                    }
                }
            }
        }
    }
}

private fun Modifier.pointerInputDrag(id: String, drag: DragState, onDrop: (Offset) -> Unit): Modifier =
    this.then(
        Modifier.pointerInputCompat(id) {
            detectDragGestures(
                onDragStart = {
                    drag.itemId = id
                    drag.offset = Offset.Zero
                },
                onDragEnd = {
                    val offset = drag.offset
                    drag.reset()
                    onDrop(offset)
                },
                onDragCancel = { drag.reset() },
                onDrag = { change, amount ->
                    change.consume()
                    drag.offset += amount
                },
            )
        },
    )

/* ---------------- Cells ---------------- */

@Composable
private fun AppCell(
    app: AppKey,
    state: HomeUiState,
    cellWidth: Dp,
    onClick: (Rect?) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val info = state.app(app)
    val label = info?.label ?: app.packageName
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val iconSize = min(ICON_SIZE, cellWidth - 20.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onClick(bounds.rect) }
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
                .pressScale(interaction),
        ) {
            AppIcon(
                app = info,
                original = state.isOriginal(app),
                size = iconSize,
                badge = state.hasBadge(app),
                modifier = if (onRemove != null) Modifier.editOutline(iconSize * (17f / 60f)) else Modifier,
            )
            if (onRemove != null) {
                RemoveBadge(Modifier.align(Alignment.TopStart).offset(x = (-12).dp, y = (-12).dp), "Remove $label", onRemove)
            }
        }
        if (state.settings.showLabels) {
            Spacer(Modifier.height(7.dp))
            MText(
                label,
                Morse.type.ui(11.5.sp),
                color = Morse.colors.text.copy(alpha = 0.86f),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun FolderCell(
    folder: FolderItem,
    state: HomeUiState,
    cellWidth: Dp,
    onClick: (Rect?) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val c = Morse.colors
    val style = LocalIconStyle.current
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val iconSize = min(ICON_SIZE, cellWidth - 20.dp)
    val badge = folder.apps.any { state.hasBadge(it) }
    Column(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onClick(bounds.rect) }
            .semantics(mergeDescendants = true) { contentDescription = "Folder ${folder.title}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
                .pressScale(interaction),
        ) {
            Box(
                Modifier
                    .size(iconSize)
                    .then(if (onRemove != null) Modifier.editOutline(iconSize * (17f / 60f)) else Modifier)
                    .morseSurface(tileShape(iconSize), color = c.tile(style))
                    .padding(iconSize * 0.17f),
            ) {
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                    folder.apps.take(4).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { key -> MiniGlyph(state, key, iconSize * 0.28f) }
                        }
                    }
                }
            }
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(iconSize * 0.115f)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(c.accent),
                )
            }
            if (onRemove != null) {
                RemoveBadge(Modifier.align(Alignment.TopStart).offset(x = (-12).dp, y = (-12).dp), "Remove folder ${folder.title}", onRemove)
            }
        }
        if (state.settings.showLabels) {
            Spacer(Modifier.height(7.dp))
            MText(folder.title, Morse.type.ui(11.5.sp), color = c.text.copy(alpha = 0.86f), maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun MiniGlyph(state: HomeUiState, key: AppKey, size: Dp) {
    val c = Morse.colors
    val ink = c.glyph(LocalIconStyle.current)
    val info = state.app(key)
    val glyph: Glyph? = info?.glyph
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (glyph != null) {
            GlyphIcon(glyph, size, ink)
        } else {
            MText(IconResolver.initialFor(info?.label ?: "?"), Morse.type.mono((size.value * 0.8f).sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold, tracking = 0f), color = ink)
        }
    }
}

/* ---------------- Edit mode pieces ---------------- */

/** A dashed outline drawn just outside the item: static, unlike an iOS-style jiggle. */
@Composable
private fun Modifier.editOutline(corner: Dp): Modifier {
    val color = Morse.colors.text3.copy(alpha = 0.7f)
    return this.drawWithContent {
        drawContent()
        val inset = 3.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + inset * 2, size.height + inset * 2),
            cornerRadius = CornerRadius(corner.toPx() + inset),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
        )
    }
}

@Composable
private fun RemoveBadge(modifier: Modifier, description: String, onClick: () -> Unit) {
    val c = Morse.colors
    Box(
        modifier
            .size(36.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (c.isDark) Color(0xFF2E2E2E) else Color(0xFFD9D9D4)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.MINUS, 13.dp, c.text)
        }
    }
}

@Composable
private fun EditBar(page: Int, onAddWidget: () -> Unit, onSettings: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton("Widget", onAddWidget, glyph = Glyph.PLUS)
        Spacer(Modifier.weight(1f))
        MText("EDITING · PAGE ${page + 1}", Morse.type.mono(11.sp), color = Morse.colors.text3, maxLines = 1)
        Spacer(Modifier.weight(1f))
        dev.morse.launcher.ui.components.MorseIconButton(Glyph.SETTINGS, "Launcher settings", onSettings, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        PillButton("Done", onDone, filled = true)
    }
}

/* ---------------- Search pill and dock ---------------- */

@Composable
private fun PillRow(state: HomeUiState, pagerState: PagerState, callbacks: HomeCallbacks) {
    val c = Morse.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.editMode) {
            PillButton("Edit pages", callbacks.onEditPages, glyph = Glyph.LAYERS)
            return@Row
        }
        if (state.settings.showSearchPill) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .height(40.dp)
                    .pressScale(interaction)
                    .morseSurface(RoundedCornerShape(50))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = callbacks.onOpenSearch)
                    .semantics(mergeDescendants = true) { contentDescription = "Search" }
                    .padding(start = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(Glyph.SEARCH, 15.dp, c.text2)
                MText("Search", Morse.type.ui(13.sp), color = c.text2)
                PageDots(pagerState.currentPage, state.layout.pages.size)
            }
        } else {
            PageDots(pagerState.currentPage, state.layout.pages.size)
        }
        dev.morse.launcher.ui.components.MorseIconButton(
            Glyph.GRID,
            "App library",
            callbacks.onOpenLibrary,
            size = 40.dp,
            glyphSize = 16.dp,
            modifier = Modifier.morseSurface(CircleShape),
        )
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    if (count <= 1) return
    val c = Morse.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 12.dp else 4.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == current) c.text else c.text3.copy(alpha = 0.6f)),
            )
        }
    }
}

@Composable
private fun Dock(state: HomeUiState, drag: DragState, pagerState: PagerState, callbacks: HomeCallbacks) {
    val haptics = LocalHapticFeedback.current
    val currentState by rememberUpdatedState(state)
    val currentCallbacks by rememberUpdatedState(callbacks)
    Box(
        Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 16.dp)
            .fillMaxWidth()
            .height(92.dp)
            .morseSurface(RoundedCornerShape(34.dp))
            .onGloballyPositioned { drag.dockBounds = it.boundsInWindow() }
            .areaPress(
                key = state.editMode,
                onLongPress = { pos ->
                    if (currentState.editMode) return@areaPress false
                    val dock = drag.dockBounds ?: return@areaPress false
                    val slotWidth = dock.width / 4f
                    val slot = (pos.x / slotWidth).toInt().coerceIn(0, 3)
                    if (currentState.layout.dock[slot] == null) {
                        currentCallbacks.onEnterEdit()
                    } else {
                        if (currentState.settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentCallbacks.onDockMenu(slot, Rect(dock.left + slot * slotWidth, dock.top, dock.left + (slot + 1) * slotWidth, dock.bottom))
                    }
                    true
                },
            )
            .testTag("dock"),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            state.layout.dock.forEachIndexed { slot, app ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (app != null) {
                        DockIcon(slot, app, state, drag, pagerState, callbacks)
                    } else if (state.editMode) {
                        Box(
                            Modifier
                                .size(ICON_SIZE)
                                .editOutline(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockIcon(
    slot: Int,
    app: AppKey,
    state: HomeUiState,
    drag: DragState,
    pagerState: PagerState,
    callbacks: HomeCallbacks,
) {
    val info = state.app(app)
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val dragging = drag.dockSlot == slot
    val label = info?.label ?: app.packageName
    Box(
        Modifier
            .size(ICON_SIZE)
            .zIndex(if (dragging) 2f else 0f)
            .graphicsLayer {
                if (drag.dockSlot == slot) {
                    translationX = drag.offset.x
                    translationY = drag.offset.y
                    scaleX = 1.06f
                    scaleY = 1.06f
                }
            }
            .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                if (!state.editMode) callbacks.onLaunch(app, bounds.rect)
            }
            .semantics { contentDescription = label }
            .then(
                if (state.editMode) {
                    Modifier.pointerInputCompat("dock$slot") {
                        detectDragGestures(
                            onDragStart = {
                                drag.dockSlot = slot
                                drag.offset = Offset.Zero
                            },
                            onDragEnd = {
                                val start = bounds.rect
                                val offset = drag.offset
                                drag.reset()
                                if (start == null) return@detectDragGestures
                                val center = start.center + offset
                                val dock = drag.dockBounds
                                if (dock != null && dock.contains(center)) {
                                    val target = GridMetrics.dockSlotAt(center.x, dock.left, dock.width)
                                    if (target >= 0 && target != slot) callbacks.onSwapDock(slot, target)
                                } else {
                                    val page = pagerState.currentPage
                                    val origin = drag.gridOrigins[page] ?: return@detectDragGestures
                                    val (w, h) = drag.gridSizePx
                                    if (w <= 0f || h <= 0f) return@detectDragGestures
                                    val cols = state.layout.columns
                                    val rows = state.layout.rows
                                    val local = center - origin
                                    val cell = Cell(
                                        (local.x / (w / cols)).toInt().coerceIn(0, cols - 1),
                                        (local.y / (h / rows)).toInt().coerceIn(0, rows - 1),
                                    )
                                    callbacks.onDockToGrid(slot, page, cell)
                                }
                            },
                            onDragCancel = { drag.reset() },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.offset += amount
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        AppIcon(
            app = info,
            original = state.isOriginal(app),
            badge = state.hasBadge(app),
            modifier = if (state.editMode) Modifier.editOutline(17.dp) else Modifier,
        )
    }
}

/** `pointerInput` with a single key, kept separate so drag modifiers read clearly. */
private fun Modifier.pointerInputCompat(
    key: Any?,
    block: suspend PointerInputScope.() -> Unit,
): Modifier = this.pointerInput(key, block)
