package dev.morse.launcher.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import dev.morse.core.apps.AppInfo
import dev.morse.core.apps.AppKey
import dev.morse.core.layout.Cell
import dev.morse.core.layout.HomeItem
import dev.morse.core.layout.HomeLayout
import dev.morse.core.settings.LauncherSettings

/** Everything the home screen draws. */
@Immutable
data class HomeUiState(
    val layout: HomeLayout,
    val apps: Map<AppKey, AppInfo>,
    val badges: Set<String>,
    val settings: LauncherSettings,
    val editMode: Boolean = false,
) {
    fun app(key: AppKey?): AppInfo? = key?.let { apps[it] }
    fun hasBadge(key: AppKey?): Boolean = settings.notificationDots && key != null && key.packageName in badges
    fun isOriginal(key: AppKey?): Boolean = key != null && key.packageName in settings.originalIconPackages
}

/** What the home screen reports back. Every callback defaults to doing nothing. */
class HomeCallbacks(
    val onLaunch: (app: AppKey, bounds: Rect?) -> Unit = { _, _ -> },
    val onOpenFolder: (page: Int, folderId: String, bounds: Rect?) -> Unit = { _, _, _ -> },
    val onItemMenu: (page: Int, item: HomeItem, bounds: Rect?) -> Unit = { _, _, _ -> },
    val onDockMenu: (slot: Int, bounds: Rect?) -> Unit = { _, _ -> },
    val onEnterEdit: () -> Unit = {},
    val onExitEdit: () -> Unit = {},
    val onMove: (page: Int, itemId: String, cell: Cell) -> Unit = { _, _, _ -> },
    val onDropOnto: (page: Int, draggedId: String, targetId: String) -> Unit = { _, _, _ -> },
    val onGridToDock: (page: Int, itemId: String, slot: Int) -> Unit = { _, _, _ -> },
    val onDockToGrid: (slot: Int, page: Int, cell: Cell) -> Unit = { _, _, _ -> },
    val onSwapDock: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onRemoveItem: (page: Int, itemId: String) -> Unit = { _, _ -> },
    val onOpenSearch: () -> Unit = {},
    val onOpenLibrary: () -> Unit = {},
    val onOpenWidgetPicker: (page: Int) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onEditPages: () -> Unit = {},
)

/** Holds the last on-screen bounds of something without causing recomposition. */
class BoundsHolder {
    var rect: Rect? = null
}
