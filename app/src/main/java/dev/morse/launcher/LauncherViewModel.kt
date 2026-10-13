package dev.morse.launcher

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.morse.core.apps.AppInfo
import dev.morse.core.apps.AppKey
import dev.morse.core.apps.Library
import dev.morse.core.apps.LetterSection
import dev.morse.core.apps.LibraryGroup
import dev.morse.core.glyph.Glyph
import dev.morse.core.layout.AppItem
import dev.morse.core.layout.Cell
import dev.morse.core.layout.DefaultLayout
import dev.morse.core.layout.FolderItem
import dev.morse.core.layout.GridMetrics
import dev.morse.core.layout.GridSpan
import dev.morse.core.layout.HomeItem
import dev.morse.core.layout.HomeLayout
import dev.morse.core.layout.LayoutOps
import dev.morse.core.layout.WidgetItem
import dev.morse.core.layout.WidgetKind
import dev.morse.core.power.MotionMode
import dev.morse.core.power.PowerPolicy
import dev.morse.core.search.SearchEngine
import dev.morse.core.search.SettingShortcut
import dev.morse.core.search.SettingsShortcuts
import dev.morse.core.settings.LauncherSettings
import dev.morse.core.weather.OpenMeteo
import dev.morse.launcher.data.AppsRepository
import dev.morse.launcher.data.ContactHit
import dev.morse.launcher.data.HttpFetcher
import dev.morse.launcher.data.LayoutRepository
import dev.morse.launcher.data.ProviderEntry
import dev.morse.launcher.service.NotificationBadges
import dev.morse.launcher.system.SystemActions
import dev.morse.launcher.ui.home.HomeCallbacks
import dev.morse.launcher.ui.home.HomeUiState
import dev.morse.launcher.ui.overlays.MenuAction
import dev.morse.launcher.ui.overlays.SearchActions
import dev.morse.launcher.ui.overlays.SearchResults
import dev.morse.launcher.ui.overlays.SettingsAction
import dev.morse.launcher.ui.overlays.SettingsInfo
import dev.morse.launcher.ui.widgets.WidgetActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which layer is on top of the home screen. */
sealed interface Overlay {
    data object None : Overlay
    data class Folder(val page: Int, val folderId: String, val origin: Rect?) : Overlay
    data object Library : Overlay
    data object Search : Overlay
    data object Settings : Overlay
    data object HiddenApps : Overlay
    data class WidgetPicker(val page: Int) : Overlay
    data object Pages : Overlay
    data class Menu(val title: String, val subtitle: String?, val actions: List<MenuAction>) : Overlay
}

/** Work only the activity can do (it owns the window, results and permissions). */
sealed interface UiEvent {
    data class Launch(val app: AppKey, val bounds: Rect?) : UiEvent
    data class StartIntent(val intent: Intent) : UiEvent
    data class RequestPermissions(val permissions: List<String>) : UiEvent
    data class BindWidget(val intent: Intent) : UiEvent
    data class ConfigureWidget(val appWidgetId: Int) : UiEvent
    data class OpenAppInfo(val app: AppKey, val bounds: Rect?) : UiEvent
    data class Message(val text: String) : UiEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LauncherViewModel(private val c: AppContainer) : ViewModel() {

    /* ---------------- State ---------------- */

    val settings: StateFlow<LauncherSettings> = c.settings.settings

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private val _overlay = MutableStateFlow<Overlay>(Overlay.None)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val _pageRequests = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val pageRequests: SharedFlow<Int> = _pageRequests.asSharedFlow()

    private val _settingsInfo = MutableStateFlow(SettingsInfo(versionName = BuildConfig.VERSION_NAME))
    val settingsInfo: StateFlow<SettingsInfo> = _settingsInfo.asStateFlow()

    var currentPage: Int = 0

    private val allApps: StateFlow<List<AppInfo>> = c.apps.state
        .map { (it as? AppsRepository.State.Ready)?.apps ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appsByKey: StateFlow<Map<AppKey, AppInfo>> = allApps
        .map { list -> list.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Apps shown in the library and search (hidden ones and Morse's own settings entry excluded from neither: settings stays findable). */
    val visibleApps: StateFlow<List<AppInfo>> = combine(allApps, settings) { apps, s ->
        apps.filter { it.key.flatten() !in s.hiddenApps }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<AppInfo>> = combine(allApps, settings) { apps, s ->
        apps.filter { it.key.flatten() in s.hiddenApps }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val libraryGroups: StateFlow<List<LibraryGroup>> = combine(visibleApps, c.usage.counts) { apps, counts ->
        Library.groups(apps, counts, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val letterSections: StateFlow<List<LetterSection>> = visibleApps
        .map { Library.alphabetical(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val paused: StateFlow<Boolean> = combine(c.battery.state, settings) { power, s ->
        PowerPolicy.widgetsPaused(power, s.pauseWidgetsOnLowBattery)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val motion: StateFlow<MotionMode> = combine(settings, paused) { s, p ->
        PowerPolicy.motionMode(s.lowPowerMotion, p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MotionMode.LOW_POWER)

    val homeState: StateFlow<HomeUiState?> = combine(
        c.layout.state,
        appsByKey,
        NotificationBadges.packages,
        settings,
        _editMode,
    ) { layout, apps, badges, s, edit ->
        (layout as? LayoutRepository.State.Ready)?.let { HomeUiState(it.layout, apps, badges, s, edit) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /* ---------------- Search ---------------- */

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<SearchResults> = _query
        .debounce(80)
        .distinctUntilChanged()
        .mapLatest { q -> search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    val suggestions: StateFlow<List<AppInfo>> = combine(visibleApps, c.usage.counts) { apps, counts ->
        apps.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] ?: 0 }.take(4)
            .ifEmpty { apps.filter { it.glyph != null }.take(4) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun search(q: String): SearchResults {
        if (q.isBlank()) return SearchResults()
        val counts = c.usage.counts.value
        val apps = SearchEngine.rank(
            q,
            visibleApps.value.map { SearchEngine.Candidate(it.label, it, boost = counts[it.key] ?: 0) },
            limit = 8,
        ).map { it.payload }
        val settingsHits = SearchEngine.rank(
            q,
            SettingsShortcuts.all.map { SearchEngine.Candidate(it.title, it, it.keywords) },
            limit = 3,
        ).map { it.payload }
        val contacts = if (q.trim().length >= 2) c.contacts.search(q, 3) else emptyList()
        return SearchResults(apps, contacts, settingsHits)
    }

    /* ---------------- Lifecycle ---------------- */

    private var screenWidthDp = 412f
    private var screenHeightDp = 915f

    init {
        c.apps.start()
        viewModelScope.launch { c.layout.load() }

        // First run: build the default home once apps are known.
        viewModelScope.launch {
            combine(c.layout.state, c.apps.state, c.apps.roles) { l, a, r -> Triple(l, a, r) }
                .collect { (layout, apps, roles) ->
                    if (layout == LayoutRepository.State.Empty && apps is AppsRepository.State.Ready) {
                        val others = apps.apps.filter { !it.isSystem && it.key.packageName != BuildConfig.APPLICATION_ID }
                            .sortedBy { it.label.lowercase() }
                            .map { it.key }
                        val built = DefaultLayout.build(roles, others)
                        c.layout.set(LayoutOps.withColumns(built, settings.value.columns))
                    } else if (layout is LayoutRepository.State.Ready && apps is AppsRepository.State.Ready && apps.apps.isNotEmpty()) {
                        val installed = apps.apps.mapTo(HashSet()) { it.key.packageName }
                        c.layout.update { l -> LayoutOps.purgeApps(l) { it.packageName in installed } }
                    }
                }
        }
        // Uninstalls while running.
        viewModelScope.launch {
            c.apps.removedPackages.collect { pkg ->
                delay(1_500)
                c.layout.update { l -> LayoutOps.purgeApps(l) { it.packageName != pkg || c.apps.isInstalled(it) } }
            }
        }
        // Grid size follows the setting.
        viewModelScope.launch {
            settings.map { it.columns }.distinctUntilChanged().collect { cols ->
                c.layout.update { LayoutOps.withColumns(it, cols) }
            }
        }
    }

    fun setScreenSize(widthDp: Float, heightDp: Float) {
        screenWidthDp = widthDp
        screenHeightDp = heightDp
    }

    fun onResume() {
        c.calendar.recheckPermission()
        c.media.start()
        refreshSettingsInfo()
        c.weather.refreshAsync(c.battery.currentState())
    }

    fun onStop() {
        viewModelScope.launch { c.layout.flush() }
    }

    /** Home pressed while already home: close what is open, else go to page 1. */
    fun onHomePressed() {
        when {
            _overlay.value != Overlay.None -> closeOverlay()
            _editMode.value -> exitEdit()
            else -> _pageRequests.tryEmit(0)
        }
    }

    /** Back: same as home, but never leaves the launcher. */
    fun onBack() = onHomePressed()

    fun refreshSettingsInfo() {
        val ctx = c.appContext
        _settingsInfo.value = _settingsInfo.value.copy(
            isDefaultLauncher = SystemActions.isDefaultLauncher(ctx),
            locationGranted = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            calendarGranted = granted(Manifest.permission.READ_CALENDAR),
            contactsGranted = granted(Manifest.permission.READ_CONTACTS),
            notificationAccess = c.media.hasAccess(),
        )
    }

    private fun granted(permission: String) =
        androidx.core.content.ContextCompat.checkSelfPermission(c.appContext, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun onPermissionsResult() {
        c.calendar.recheckPermission()
        refreshSettingsInfo()
        c.weather.refreshAsync(c.battery.currentState(), force = true)
    }

    /* ---------------- Overlays ---------------- */

    fun closeOverlay() {
        _overlay.value = Overlay.None
        _query.value = ""
    }

    fun openSearch() {
        _query.value = ""
        _overlay.value = Overlay.Search
    }

    fun openLibrary() {
        _overlay.value = Overlay.Library
    }

    fun openSettings() {
        refreshSettingsInfo()
        _overlay.value = Overlay.Settings
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    /* ---------------- Launching ---------------- */

    fun launch(app: AppKey, bounds: Rect?) {
        c.usage.recordLaunch(app)
        _events.trySend(UiEvent.Launch(app, bounds))
        if (_overlay.value is Overlay.Search || _overlay.value is Overlay.Library || _overlay.value is Overlay.Folder) {
            viewModelScope.launch {
                delay(350) // let the app's opening animation start first
                closeOverlay()
            }
        }
    }

    private fun startIntent(intent: Intent) {
        _events.trySend(UiEvent.StartIntent(intent))
    }

    /* ---------------- Edit mode and layout ---------------- */

    fun enterEdit() {
        _overlay.value = Overlay.None
        _editMode.value = true
    }

    fun exitEdit() {
        _editMode.value = false
        c.layout.update { LayoutOps.dropEmptyPages(it) }
    }

    fun move(page: Int, itemId: String, cell: Cell) = c.layout.update { LayoutOps.move(it, page, itemId, cell) }

    fun dropOnto(page: Int, draggedId: String, targetId: String) =
        c.layout.update { LayoutOps.dropOnto(it, page, draggedId, targetId, folderTitle = "Folder") }

    fun gridToDock(page: Int, itemId: String, slot: Int) = c.layout.update { LayoutOps.gridToDock(it, page, itemId, slot) }

    fun dockToGrid(slot: Int, page: Int, cell: Cell) = c.layout.update { LayoutOps.dockToGrid(it, slot, page, cell) }

    fun swapDock(from: Int, to: Int) = c.layout.update { LayoutOps.swapDock(it, from, to) }

    fun removeItem(page: Int, itemId: String) {
        val layout = currentLayout() ?: return
        val item = layout.page(page)?.items?.firstOrNull { it.id == itemId }
        if (item is WidgetItem && item.kind == WidgetKind.HOSTED) c.widgets.deleteId(item.hostedId)
        c.layout.update { LayoutOps.remove(it, page, itemId) }
    }

    fun addToHome(app: AppKey) {
        c.layout.update { LayoutOps.addApp(it, app) }
        _events.trySend(UiEvent.Message("Added to home"))
    }

    private fun currentLayout(): HomeLayout? = (c.layout.state.value as? LayoutRepository.State.Ready)?.layout

    fun addPage() {
        c.layout.update { LayoutOps.addPage(it) }
    }

    fun removePage(index: Int) {
        val layout = currentLayout() ?: return
        layout.page(index)?.items?.filterIsInstance<WidgetItem>()?.filter { it.kind == WidgetKind.HOSTED }?.forEach { c.widgets.deleteId(it.hostedId) }
        c.layout.update { LayoutOps.removePage(it, index) }
    }

    fun movePage(from: Int, to: Int) = c.layout.update { LayoutOps.movePage(it, from, to) }

    fun renameFolder(page: Int, folderId: String, title: String) = c.layout.update { LayoutOps.renameFolder(it, page, folderId, title) }

    fun resetLayout() {
        val layout = currentLayout()
        layout?.pages?.flatMap { it.items }?.filterIsInstance<WidgetItem>()?.filter { it.kind == WidgetKind.HOSTED }?.forEach {
            c.widgets.deleteId(it.hostedId)
        }
        val apps = (c.apps.state.value as? AppsRepository.State.Ready)?.apps.orEmpty()
        val others = apps.filter { !it.isSystem && it.key.packageName != BuildConfig.APPLICATION_ID }.map { it.key }
        c.layout.set(LayoutOps.withColumns(DefaultLayout.build(c.apps.roles.value, others), settings.value.columns))
        closeOverlay()
        _pageRequests.tryEmit(0)
    }

    /* ---------------- Widgets ---------------- */

    fun openWidgetPicker(page: Int) {
        _overlay.value = Overlay.WidgetPicker(page)
    }

    fun widgetProviders(): List<ProviderEntry> = c.widgets.providers()

    fun addWidget(kind: WidgetKind, span: GridSpan) {
        val page = (_overlay.value as? Overlay.WidgetPicker)?.page ?: currentPage
        var placedPage = -1
        c.layout.update { layout ->
            val placement = LayoutOps.addWidget(layout, page, kind, span)
            placedPage = placement?.pageIndex ?: -1
            placement?.layout ?: layout
        }
        closeOverlay()
        if (placedPage >= 0) _pageRequests.tryEmit(placedPage)
    }

    private data class PendingHosted(val id: Int, val entry: ProviderEntry, val page: Int)

    private var pendingHosted: PendingHosted? = null

    fun addHostedWidget(entry: ProviderEntry) {
        val page = (_overlay.value as? Overlay.WidgetPicker)?.page ?: currentPage
        val id = c.widgets.allocateId()
        pendingHosted = PendingHosted(id, entry, page)
        closeOverlay()
        if (c.widgets.bindIfAllowed(id, entry)) {
            afterBind()
        } else {
            _events.trySend(UiEvent.BindWidget(c.widgets.bindIntent(id, entry)))
        }
    }

    fun onBindResult(ok: Boolean) {
        if (ok) afterBind() else cancelPending()
    }

    private fun afterBind() {
        val p = pendingHosted ?: return
        if (c.widgets.needsConfigure(p.id)) {
            _events.trySend(UiEvent.ConfigureWidget(p.id))
        } else {
            placePending()
        }
    }

    fun onConfigureResult(ok: Boolean) {
        if (ok) placePending() else cancelPending()
    }

    private fun cancelPending() {
        pendingHosted?.let { c.widgets.deleteId(it.id) }
        pendingHosted = null
    }

    private fun placePending() {
        val p = pendingHosted ?: return
        pendingHosted = null
        val layout = currentLayout() ?: return
        val cellW = (screenWidthDp - 32f) / layout.columns
        val cellH = (screenHeightDp - 260f) / layout.rows
        val span = GridMetrics.hostedSpan(
            p.entry.minWidthDp, p.entry.minHeightDp, p.entry.targetColumns, p.entry.targetRows,
            cellW, cellH, layout.columns, layout.rows,
        )
        var placedPage = -1
        c.layout.update { l ->
            val placement = LayoutOps.addWidget(
                l, p.page, WidgetKind.HOSTED, span,
                hostedId = p.id, provider = p.entry.provider.flattenToString(),
            )
            placedPage = placement?.pageIndex ?: -1
            placement?.layout ?: l
        }
        if (placedPage >= 0) _pageRequests.tryEmit(placedPage) else c.widgets.deleteId(p.id)
    }

    /* ---------------- Menus ---------------- */

    private fun appLabel(key: AppKey) = appsByKey.value[key]?.label ?: key.packageName

    private fun iconToggle(key: AppKey): MenuAction {
        val original = key.packageName in settings.value.originalIconPackages
        return MenuAction(if (original) "Use Morse icon" else "Use original icon", Glyph.LAYERS) {
            updateSettings { s ->
                s.copy(originalIconPackages = if (original) s.originalIconPackages - key.packageName else s.originalIconPackages + key.packageName)
            }
            closeOverlay()
        }
    }

    private fun appInfoAction(key: AppKey, bounds: Rect?) = MenuAction("App info", Glyph.INFO) {
        closeOverlay()
        _events.trySend(UiEvent.OpenAppInfo(key, bounds))
    }

    private fun uninstallAction(key: AppKey): MenuAction? {
        val info = appsByKey.value[key]
        if (info?.isSystem == true || key.packageName == BuildConfig.APPLICATION_ID) return null
        return MenuAction("Uninstall", Glyph.TRASH, destructive = true) {
            closeOverlay()
            startIntent(c.apps.uninstallIntent(key))
        }
    }

    fun itemMenu(page: Int, item: HomeItem, bounds: Rect?) {
        val actions = mutableListOf<MenuAction>()
        val title: String
        var subtitle: String? = null
        when (item) {
            is AppItem -> {
                title = appLabel(item.app)
                actions += MenuAction("Edit home", Glyph.PENCIL) { enterEdit() }
                actions += appInfoAction(item.app, bounds)
                actions += iconToggle(item.app)
                actions += MenuAction("Move to next page", Glyph.CHEVRON_RIGHT) {
                    c.layout.update { l ->
                        val withPage = if (page + 1 >= l.pages.size) LayoutOps.addPage(l) else l
                        LayoutOps.moveToPage(withPage, page, item.id, page + 1)
                    }
                    closeOverlay()
                    _pageRequests.tryEmit(page + 1)
                }
                actions += MenuAction("Remove from home", Glyph.MINUS) {
                    removeItem(page, item.id)
                    closeOverlay()
                }
                uninstallAction(item.app)?.let { actions += it }
            }
            is FolderItem -> {
                title = item.title
                subtitle = "${item.apps.size} APPS"
                actions += MenuAction("Open", Glyph.GRID) { _overlay.value = Overlay.Folder(page, item.id, bounds) }
                actions += MenuAction("Edit home", Glyph.PENCIL) { enterEdit() }
                actions += MenuAction("Remove folder", Glyph.MINUS, destructive = true) {
                    removeItem(page, item.id)
                    closeOverlay()
                }
            }
            is WidgetItem -> {
                title = if (item.kind == WidgetKind.HOSTED) "App widget" else item.kind.title
                subtitle = item.size?.let { "SIZE ${it.label}" }
                actions += MenuAction("Edit home", Glyph.PENCIL) { enterEdit() }
                val sizes = item.kind.sizes
                if (sizes.size > 1) {
                    val currentIndex = item.size?.let { sizes.indexOf(it) } ?: -1
                    val next = sizes[(currentIndex.coerceAtLeast(0) + 1) % sizes.size]
                    actions += MenuAction("Make it ${next.label}", Glyph.WIDGET) {
                        c.layout.update { LayoutOps.resizeWidget(it, page, item.id, next.span) }
                        closeOverlay()
                    }
                }
                actions += MenuAction("Remove widget", Glyph.MINUS, destructive = true) {
                    removeItem(page, item.id)
                    closeOverlay()
                }
            }
        }
        _overlay.value = Overlay.Menu(title, subtitle, actions)
    }

    fun dockMenu(slot: Int, bounds: Rect?) {
        val key = currentLayout()?.dock?.getOrNull(slot) ?: return
        val actions = mutableListOf(
            MenuAction("Edit home", Glyph.PENCIL) { enterEdit() },
            appInfoAction(key, bounds),
            iconToggle(key),
            MenuAction("Remove from dock", Glyph.MINUS) {
                c.layout.update { LayoutOps.setDock(it, slot, null) }
                closeOverlay()
            },
        )
        uninstallAction(key)?.let { actions += it }
        _overlay.value = Overlay.Menu(appLabel(key), "DOCK", actions)
    }

    fun libraryAppMenu(app: AppInfo, bounds: Rect?) {
        val onHome = currentLayout()?.allApps()?.contains(app.key) == true
        val hidden = app.key.flatten() in settings.value.hiddenApps
        val actions = mutableListOf<MenuAction>()
        if (!onHome) actions += MenuAction("Add to home", Glyph.PLUS) {
            addToHome(app.key)
            closeOverlay()
        }
        actions += appInfoAction(app.key, bounds)
        actions += iconToggle(app.key)
        actions += MenuAction(if (hidden) "Show in library" else "Hide from library", Glyph.HIDE) {
            updateSettings { s ->
                val k = app.key.flatten()
                s.copy(hiddenApps = if (hidden) s.hiddenApps - k else s.hiddenApps + k)
            }
            closeOverlay()
        }
        uninstallAction(app.key)?.let { actions += it }
        _overlay.value = Overlay.Menu(app.label, null, actions)
    }

    fun folderAppMenu(page: Int, folderId: String, app: AppKey, bounds: Rect?) {
        val actions = mutableListOf(
            MenuAction("Remove from folder", Glyph.MINUS) {
                c.layout.update { l ->
                    val without = LayoutOps.removeFromFolder(l, page, folderId, app)
                    LayoutOps.addApp(without, app)
                }
                closeOverlay()
            },
            appInfoAction(app, bounds),
            iconToggle(app),
        )
        uninstallAction(app)?.let { actions += it }
        _overlay.value = Overlay.Menu(appLabel(app), null, actions)
    }

    /* ---------------- Settings ---------------- */

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { c.settings.update(transform) }
    }

    fun settingsAction(action: SettingsAction) {
        when (action) {
            SettingsAction.SET_DEFAULT_LAUNCHER -> startIntent(SystemActions.defaultLauncherIntent(c.appContext))
            SettingsAction.GRANT_LOCATION -> _events.trySend(UiEvent.RequestPermissions(listOf(Manifest.permission.ACCESS_COARSE_LOCATION)))
            SettingsAction.GRANT_CALENDAR -> _events.trySend(UiEvent.RequestPermissions(listOf(Manifest.permission.READ_CALENDAR)))
            SettingsAction.GRANT_CONTACTS -> _events.trySend(UiEvent.RequestPermissions(listOf(Manifest.permission.READ_CONTACTS)))
            SettingsAction.GRANT_NOTIFICATIONS -> startIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            SettingsAction.PICK_SYSTEM_WALLPAPER -> startIntent(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
            SettingsAction.APP_PERMISSIONS -> startIntent(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.appContext.packageName, null)),
            )
            SettingsAction.SHOW_HIDDEN_APPS -> _overlay.value = Overlay.HiddenApps
            SettingsAction.RESET_LAYOUT -> _overlay.value = Overlay.Menu(
                "Reset home screen?",
                "PAGES, FOLDERS AND WIDGETS GO BACK TO DEFAULT",
                listOf(
                    MenuAction("Reset", Glyph.TRASH, destructive = true) { resetLayout() },
                    MenuAction("Cancel", Glyph.CLOSE) { _overlay.value = Overlay.Settings },
                ),
            )
        }
    }

    fun unhide(app: AppInfo) = updateSettings { it.copy(hiddenApps = it.hiddenApps - app.key.flatten()) }

    fun setCity(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            updateSettings { it.copy(manualLocation = null, manualPlaceName = "") }
            _settingsInfo.value = _settingsInfo.value.copy(placeStatus = "City cleared.")
            return
        }
        _settingsInfo.value = _settingsInfo.value.copy(placeStatus = "Looking up $clean…")
        viewModelScope.launch {
            val place = try {
                withContext(Dispatchers.IO) { OpenMeteo.parseGeocode(HttpFetcher.get(OpenMeteo.geocodeUrl(clean))) }
            } catch (e: java.io.IOException) {
                null
            }
            if (place == null) {
                _settingsInfo.value = _settingsInfo.value.copy(placeStatus = "Couldn't find “$clean”. Check the spelling or your connection.")
            } else {
                c.settings.update { it.copy(manualLocation = place.point, manualPlaceName = place.name) }
                _settingsInfo.value = _settingsInfo.value.copy(placeStatus = "Using ${place.name}${if (place.region.isNotEmpty()) ", ${place.region}" else ""}.")
                c.weather.refresh(c.battery.currentState(), force = true)
            }
        }
    }

    /* ---------------- Callback bundles for the UI ---------------- */

    val homeCallbacks = HomeCallbacks(
        onLaunch = ::launch,
        onOpenFolder = { page, id, bounds -> _overlay.value = Overlay.Folder(page, id, bounds) },
        onItemMenu = ::itemMenu,
        onDockMenu = ::dockMenu,
        onEnterEdit = ::enterEdit,
        onExitEdit = ::exitEdit,
        onMove = ::move,
        onDropOnto = ::dropOnto,
        onGridToDock = ::gridToDock,
        onDockToGrid = ::dockToGrid,
        onSwapDock = ::swapDock,
        onRemoveItem = ::removeItem,
        onOpenSearch = ::openSearch,
        onOpenLibrary = ::openLibrary,
        onOpenWidgetPicker = ::openWidgetPicker,
        onOpenSettings = ::openSettings,
        onEditPages = { _overlay.value = Overlay.Pages },
    )

    val searchActions = object : SearchActions {
        override fun onQueryChange(query: String) = this@LauncherViewModel.onQueryChange(query)
        override fun onLaunch(app: AppInfo, bounds: Rect?) = launch(app.key, bounds)
        override fun onAppMenu(app: AppInfo, bounds: Rect?) = libraryAppMenu(app, bounds)
        override fun onContact(contact: ContactHit) = startIntent(Intent(Intent.ACTION_VIEW, contact.lookupUri))
        override fun onCall(contact: ContactHit) {
            contact.phone?.let { startIntent(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null))) }
        }
        override fun onMessage(contact: ContactHit) {
            contact.phone?.let { startIntent(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", it, null))) }
        }
        override fun onSetting(setting: SettingShortcut) {
            startIntent(Intent(setting.action))
            closeOverlay()
        }
        override fun onWebSearch(query: String) {
            _events.trySend(UiEvent.StartIntent(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)))
            closeOverlay()
        }
        override fun onAllowContacts() {
            _events.trySend(UiEvent.RequestPermissions(listOf(Manifest.permission.READ_CONTACTS)))
        }
        override fun onClose() = closeOverlay()
    }

    val widgetActions = object : WidgetActions {
        override fun openClock() = startIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        override fun openCalendar() = startIntent(
            Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()),
        )
        override fun openBatterySettings() = startIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        override fun refreshWeather() = c.weather.refreshAsync(c.battery.currentState(), force = true)
        override fun requestLocation() {
            _events.trySend(UiEvent.RequestPermissions(listOf(Manifest.permission.ACCESS_COARSE_LOCATION)))
        }
        override fun requestCalendar() {
            _events.trySend(UiEvent.RequestPermissions(listOf(Manifest.permission.READ_CALENDAR)))
        }
        override fun grantMediaAccess() = startIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        override fun openMusic(packageName: String?) {
            val intent = packageName?.let { c.appContext.packageManager.getLaunchIntentForPackage(it) }
                ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            startIntent(intent)
        }
        override fun playPause() = c.media.playPause()
        override fun next() = c.media.next()
        override fun previous() = c.media.previous()
        override fun toggleWifi() = startIntent(c.toggles.toggleWifi())
        override fun toggleBluetooth() = startIntent(c.toggles.toggleBluetooth())
        override fun toggleTorch() = c.toggles.toggleTorch()
        override fun toggleDoNotDisturb() {
            c.toggles.toggleDoNotDisturb()?.let { startIntent(it) }
        }
        override fun toggleReminder(id: String) = c.reminders.toggle(id)
        override fun addReminder(text: String) = c.reminders.add(text)
        override fun removeReminder(id: String) = c.reminders.remove(id)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LauncherViewModel(container) as T
    }
}
