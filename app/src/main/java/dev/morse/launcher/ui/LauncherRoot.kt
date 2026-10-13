package dev.morse.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.morse.core.glyph.Glyph
import dev.morse.core.layout.FolderItem
import dev.morse.core.motion.MotionSpec
import dev.morse.core.power.MotionMode
import dev.morse.core.settings.WallpaperMode
import dev.morse.launcher.LauncherViewModel
import dev.morse.launcher.Overlay
import dev.morse.launcher.data.ProviderEntry
import dev.morse.launcher.ui.components.FullScreenLayer
import dev.morse.launcher.ui.components.IconLoader
import dev.morse.launcher.ui.components.ListRow
import dev.morse.launcher.ui.components.LocalIconLoader
import dev.morse.launcher.ui.components.MorseIconButton
import dev.morse.launcher.ui.components.PillButton
import dev.morse.launcher.ui.home.HomeScreen
import dev.morse.launcher.ui.home.HomeUiState
import dev.morse.launcher.ui.overlays.ActionSheet
import dev.morse.launcher.ui.overlays.FolderOverlay
import dev.morse.launcher.ui.overlays.LibraryScreen
import dev.morse.launcher.ui.overlays.PageSummary
import dev.morse.launcher.ui.overlays.PagesSheet
import dev.morse.launcher.ui.overlays.SearchScreen
import dev.morse.launcher.ui.overlays.SettingsScreen
import dev.morse.launcher.ui.overlays.WidgetPickerSheet
import dev.morse.launcher.ui.theme.Morse
import dev.morse.launcher.ui.theme.MorseFonts
import dev.morse.launcher.ui.theme.MorseTheme
import dev.morse.launcher.ui.theme.toTween
import dev.morse.launcher.ui.widgets.HostedWidgetViews
import dev.morse.launcher.ui.widgets.LocalHostedWidgetViews
import dev.morse.launcher.ui.widgets.LocalWidgetEnvironment
import dev.morse.launcher.ui.widgets.WidgetEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The whole launcher UI: theme, home screen and whatever layer is on top. */
@Composable
fun LauncherRoot(
    vm: LauncherViewModel,
    environment: WidgetEnvironment,
    iconLoader: IconLoader,
    hostedViews: HostedWidgetViews?,
    fonts: MorseFonts = MorseFonts.Bundled,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val motion by vm.motion.collectAsStateWithLifecycle()
    val home by vm.homeState.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()

    MorseTheme(settings.theme, settings.accent, settings.iconStyle, motion, fonts) {
        CompositionLocalProvider(
            LocalIconLoader provides iconLoader,
            LocalWidgetEnvironment provides environment,
            LocalHostedWidgetViews provides hostedViews,
        ) {
            val ground = if (settings.wallpaper == WallpaperMode.SYSTEM) Color.Transparent else Morse.colors.ground
            Box(Modifier.fillMaxSize().background(ground)) {
                val state = home
                if (state != null) {
                    val pagerState = rememberPagerState { state.layout.pages.size.coerceAtLeast(1) }
                    LaunchedEffect(pagerState) {
                        vm.pageRequests.collect { page ->
                            if (vm.motion.value == MotionMode.LOW_POWER) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page)
                        }
                    }
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { vm.currentPage = it }
                    }
                    HomeScreen(state, pagerState, vm.homeCallbacks)
                    OverlayHost(overlay, state, vm, motion, pagerState.currentPage)
                }
                BackHandler(enabled = true) { vm.onBack() }
            }
        }
    }
}

@Composable
private fun OverlayHost(
    overlay: Overlay,
    state: HomeUiState,
    vm: LauncherViewModel,
    motion: MotionMode,
    currentPage: Int,
) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }

    fun originOf(rect: Rect?): TransformOrigin {
        if (rect == null || screenW <= 0f || screenH <= 0f) return TransformOrigin.Center
        return TransformOrigin((rect.center.x / screenW).coerceIn(0f, 1f), (rect.center.y / screenH).coerceIn(0f, 1f))
    }

    AnimatedContent(
        targetState = overlay,
        contentKey = { it::class },
        transitionSpec = {
            val target = targetState
            val initial = initialState
            val enter = if (target is Overlay.Folder && motion == MotionMode.FULL) {
                val spec = MotionSpec.folderOpen(motion)
                fadeIn(spec.toTween()) + scaleIn(spec.toTween(), initialScale = spec.scaleFrom ?: 1f, transformOrigin = originOf(target.origin))
            } else {
                fadeIn(MotionSpec.sheetEnter(motion).toTween())
            }
            val exit = if (initial is Overlay.Folder && motion == MotionMode.FULL) {
                val spec = MotionSpec.folderClose(motion)
                fadeOut(spec.toTween()) + scaleOut(spec.toTween(), targetScale = spec.scaleFrom ?: 1f, transformOrigin = originOf(initial.origin))
            } else {
                fadeOut(MotionSpec.sheetExit(motion).toTween())
            }
            ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
        },
        label = "overlay",
    ) { o ->
        when (o) {
            Overlay.None -> Box(Modifier)
            is Overlay.Folder -> {
                val folder = state.layout.page(o.page)?.items?.firstOrNull { it.id == o.folderId } as? FolderItem
                if (folder == null) {
                    LaunchedEffect(Unit) { vm.closeOverlay() }
                } else {
                    FolderOverlay(
                        folder = folder,
                        state = state,
                        onLaunch = vm::launch,
                        onAppMenu = { app, bounds -> vm.folderAppMenu(o.page, o.folderId, app, bounds) },
                        onRename = { vm.renameFolder(o.page, o.folderId, it) },
                        onClose = vm::closeOverlay,
                    )
                }
            }
            Overlay.Library -> {
                val groups by vm.libraryGroups.collectAsStateWithLifecycle()
                val sections by vm.letterSections.collectAsStateWithLifecycle()
                val apps by vm.visibleApps.collectAsStateWithLifecycle()
                LibraryScreen(
                    groups = groups,
                    sections = sections,
                    state = state,
                    appCount = apps.size,
                    onLaunch = { app, bounds -> vm.launch(app.key, bounds) },
                    onAppMenu = vm::libraryAppMenu,
                    onSearch = vm::openSearch,
                    onClose = vm::closeOverlay,
                )
            }
            Overlay.Search -> {
                val query by vm.query.collectAsStateWithLifecycle()
                val results by vm.searchResults.collectAsStateWithLifecycle()
                val suggestions by vm.suggestions.collectAsStateWithLifecycle()
                val info by vm.settingsInfo.collectAsStateWithLifecycle()
                SearchScreen(query, results, suggestions, info.contactsGranted, state, vm.searchActions)
            }
            Overlay.Settings -> {
                val settings by vm.settings.collectAsStateWithLifecycle()
                val info by vm.settingsInfo.collectAsStateWithLifecycle()
                SettingsScreen(settings, info, vm::updateSettings, vm::settingsAction, vm::setCity, vm::closeOverlay)
            }
            Overlay.HiddenApps -> {
                val hidden by vm.hiddenApps.collectAsStateWithLifecycle()
                FullScreenLayer("hidden-apps") {
                    Box(Modifier) {
                        MorseIconButton(Glyph.BACK, "Back", vm::openSettings, background = Color.Transparent)
                    }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                        if (hidden.isEmpty()) {
                            item { ListRow("No hidden apps", glyph = Glyph.HIDE, detail = "Hide apps from the library with a long-press.") }
                        }
                        items(hidden, key = { it.key.flatten() }) { app ->
                            ListRow(app.label, glyph = app.glyph ?: Glyph.GRID, trailing = { PillButton("Show", { vm.unhide(app) }, height = 36.dp) })
                        }
                    }
                }
            }
            is Overlay.WidgetPicker -> {
                val providers by produceState(initialValue = emptyList<ProviderEntry>()) {
                    value = withContext(Dispatchers.IO) { vm.widgetProviders() }
                }
                WidgetPickerSheet(providers, vm::addWidget, vm::addHostedWidget, vm::closeOverlay)
            }
            Overlay.Pages -> PagesSheet(
                pages = state.layout.pages.map { PageSummary(it.id, it.items.size) },
                current = currentPage,
                onAdd = vm::addPage,
                onRemove = vm::removePage,
                onMove = vm::movePage,
                onClose = vm::closeOverlay,
            )
            is Overlay.Menu -> ActionSheet(o.title, o.subtitle, o.actions, vm::closeOverlay)
        }
    }
}
