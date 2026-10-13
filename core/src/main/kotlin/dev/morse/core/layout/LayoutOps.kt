package dev.morse.core.layout

import dev.morse.core.apps.AppKey

/**
 * Pure, immutable operations on [HomeLayout]. Every function returns a new
 * layout; when an operation is not possible it returns the input unchanged,
 * so callers can compare by identity to know whether anything happened.
 */
object LayoutOps {

    data class Placement(val layout: HomeLayout, val pageIndex: Int, val item: HomeItem)

    /* ---------------- Geometry ---------------- */

    fun inBounds(cell: Cell, span: GridSpan, columns: Int, rows: Int): Boolean =
        cell.column >= 0 && cell.row >= 0 &&
            cell.column + span.columns <= columns &&
            cell.row + span.rows <= rows

    /** `grid[row][column]` holds the id of the item covering that cell, if any. */
    fun occupancy(page: HomePage, columns: Int, rows: Int, ignoreId: String? = null): Array<Array<String?>> {
        val grid = Array(rows) { arrayOfNulls<String>(columns) }
        for (item in page.items) {
            if (item.id == ignoreId) continue
            for (r in item.cell.row until item.cell.row + item.span.rows) {
                for (c in item.cell.column until item.cell.column + item.span.columns) {
                    if (r in 0 until rows && c in 0 until columns) grid[r][c] = item.id
                }
            }
        }
        return grid
    }

    fun fits(
        page: HomePage,
        columns: Int,
        rows: Int,
        span: GridSpan,
        cell: Cell,
        ignoreId: String? = null,
    ): Boolean {
        if (!inBounds(cell, span, columns, rows)) return false
        val grid = occupancy(page, columns, rows, ignoreId)
        for (r in cell.row until cell.row + span.rows) {
            for (c in cell.column until cell.column + span.columns) {
                if (grid[r][c] != null) return false
            }
        }
        return true
    }

    /** First free cell for [span], scanning rows top to bottom, left to right. */
    fun findFree(page: HomePage, columns: Int, rows: Int, span: GridSpan, ignoreId: String? = null): Cell? {
        if (span.columns > columns || span.rows > rows) return null
        for (r in 0..rows - span.rows) {
            for (c in 0..columns - span.columns) {
                val cell = Cell(c, r)
                if (fits(page, columns, rows, span, cell, ignoreId)) return cell
            }
        }
        return null
    }

    fun itemAt(page: HomePage, cell: Cell): HomeItem? = page.items.firstOrNull { item ->
        cell.column >= item.cell.column && cell.column < item.cell.column + item.span.columns &&
            cell.row >= item.cell.row && cell.row < item.cell.row + item.span.rows
    }

    /** True when every item is inside the grid and nothing overlaps. */
    fun isValid(page: HomePage, columns: Int, rows: Int): Boolean {
        val seen = Array(rows) { BooleanArray(columns) }
        for (item in page.items) {
            if (!inBounds(item.cell, item.span, columns, rows)) return false
            for (r in item.cell.row until item.cell.row + item.span.rows) {
                for (c in item.cell.column until item.cell.column + item.span.columns) {
                    if (seen[r][c]) return false
                    seen[r][c] = true
                }
            }
        }
        return true
    }

    /* ---------------- Adding ---------------- */

    /** Puts [app] in the first free cell of any page (or a new page). No-op if it is already placed. */
    fun addApp(layout: HomeLayout, app: AppKey, newId: () -> String = Ids::next): HomeLayout {
        if (app in layout.allApps()) return layout
        layout.pages.forEachIndexed { index, page ->
            val cell = findFree(page, layout.columns, layout.rows, GridSpan.ONE)
            if (cell != null) {
                return replacePage(layout, index, page.copy(items = page.items + AppItem(newId(), cell, app)))
            }
        }
        val page = HomePage(newId(), listOf(AppItem(newId(), Cell(0, 0), app)))
        return layout.copy(pages = layout.pages + page)
    }

    /**
     * Places a widget on [preferredPage] if it has room, else on the next page
     * with room, else on a new page. Returns null if the span can never fit.
     */
    fun addWidget(
        layout: HomeLayout,
        preferredPage: Int,
        kind: WidgetKind,
        span: GridSpan,
        newId: () -> String = Ids::next,
        hostedId: Int = -1,
        provider: String? = null,
    ): Placement? {
        if (span.columns > layout.columns || span.rows > layout.rows) return null
        val order = buildList {
            if (preferredPage in layout.pages.indices) add(preferredPage)
            addAll(layout.pages.indices.filter { it != preferredPage })
        }
        for (index in order) {
            val page = layout.pages[index]
            val cell = findFree(page, layout.columns, layout.rows, span) ?: continue
            val item = WidgetItem(newId(), cell, kind, span, hostedId, provider)
            return Placement(replacePage(layout, index, page.copy(items = page.items + item)), index, item)
        }
        val item = WidgetItem(newId(), Cell(0, 0), kind, span, hostedId, provider)
        val newLayout = layout.copy(pages = layout.pages + HomePage(newId(), listOf(item)))
        return Placement(newLayout, newLayout.pages.lastIndex, item)
    }

    /* ---------------- Moving ---------------- */

    /**
     * Moves an item to [target]. If the target area is free the item moves;
     * if it lands exactly on an item of the same size the two swap places.
     */
    fun move(layout: HomeLayout, pageIndex: Int, itemId: String, target: Cell): HomeLayout {
        val page = layout.page(pageIndex) ?: return layout
        val item = page.items.firstOrNull { it.id == itemId } ?: return layout
        if (item.cell == target) return layout
        if (fits(page, layout.columns, layout.rows, item.span, target, ignoreId = item.id)) {
            return replaceItem(layout, pageIndex, item.movedTo(target))
        }
        val occupant = itemAt(page, target) ?: return layout
        if (occupant.id == item.id || occupant.span != item.span || occupant.cell != target) return layout
        val swapped = page.copy(
            items = page.items.map {
                when (it.id) {
                    item.id -> item.movedTo(target)
                    occupant.id -> occupant.movedTo(item.cell)
                    else -> it
                }
            },
        )
        if (!isValid(swapped, layout.columns, layout.rows)) return layout
        return replacePage(layout, pageIndex, swapped)
    }

    /** Moves an item to another page, at [target] if free, else the first free spot. */
    fun moveToPage(layout: HomeLayout, fromPage: Int, itemId: String, toPage: Int, target: Cell? = null): HomeLayout {
        if (fromPage == toPage) return if (target != null) move(layout, fromPage, itemId, target) else layout
        val source = layout.page(fromPage) ?: return layout
        val dest = layout.page(toPage) ?: return layout
        val item = source.items.firstOrNull { it.id == itemId } ?: return layout
        val cell = target?.takeIf { fits(dest, layout.columns, layout.rows, item.span, it) }
            ?: findFree(dest, layout.columns, layout.rows, item.span)
            ?: return layout
        val pages = layout.pages.toMutableList()
        pages[fromPage] = source.copy(items = source.items.filterNot { it.id == itemId })
        pages[toPage] = dest.copy(items = dest.items + item.movedTo(cell))
        return layout.copy(pages = pages)
    }

    /**
     * Drops one app onto another to make a folder, or onto a folder to add it.
     */
    fun dropOnto(
        layout: HomeLayout,
        pageIndex: Int,
        draggedId: String,
        targetId: String,
        newId: () -> String = Ids::next,
        folderTitle: String = "Folder",
    ): HomeLayout {
        if (draggedId == targetId) return layout
        val page = layout.page(pageIndex) ?: return layout
        val dragged = page.items.firstOrNull { it.id == draggedId } as? AppItem ?: return layout
        val target = page.items.firstOrNull { it.id == targetId } ?: return layout
        val replacement: HomeItem = when (target) {
            is AppItem -> FolderItem(newId(), target.cell, folderTitle, listOf(target.app, dragged.app).distinct())
            is FolderItem -> target.copy(apps = (target.apps + dragged.app).distinct())
            is WidgetItem -> return layout
        }
        val items = page.items.filterNot { it.id == draggedId }.map { if (it.id == targetId) replacement else it }
        return replacePage(layout, pageIndex, page.copy(items = items))
    }

    /**
     * Changes a widget's size. It grows in place when there is room, otherwise it
     * moves to the first spot on the same page that fits; if none does, nothing changes.
     */
    fun resizeWidget(layout: HomeLayout, pageIndex: Int, itemId: String, span: GridSpan): HomeLayout {
        val page = layout.page(pageIndex) ?: return layout
        val widget = page.items.firstOrNull { it.id == itemId } as? WidgetItem ?: return layout
        if (widget.span == span) return layout
        if (fits(page, layout.columns, layout.rows, span, widget.cell, ignoreId = itemId)) {
            return replaceItem(layout, pageIndex, widget.copy(span = span))
        }
        val cell = findFree(page, layout.columns, layout.rows, span, ignoreId = itemId) ?: return layout
        return replaceItem(layout, pageIndex, widget.copy(cell = cell, span = span))
    }

    /* ---------------- Removing and folders ---------------- */

    fun remove(layout: HomeLayout, pageIndex: Int, itemId: String): HomeLayout {
        val page = layout.page(pageIndex) ?: return layout
        if (page.items.none { it.id == itemId }) return layout
        return replacePage(layout, pageIndex, page.copy(items = page.items.filterNot { it.id == itemId }))
    }

    fun addToFolder(layout: HomeLayout, pageIndex: Int, folderId: String, app: AppKey): HomeLayout {
        val folder = layout.page(pageIndex)?.items?.firstOrNull { it.id == folderId } as? FolderItem ?: return layout
        if (app in folder.apps) return layout
        return replaceItem(layout, pageIndex, folder.copy(apps = folder.apps + app))
    }

    /** Removes [app] from a folder. A folder left with one app turns back into that app. */
    fun removeFromFolder(layout: HomeLayout, pageIndex: Int, folderId: String, app: AppKey): HomeLayout {
        val page = layout.page(pageIndex) ?: return layout
        val folder = page.items.firstOrNull { it.id == folderId } as? FolderItem ?: return layout
        if (app !in folder.apps) return layout
        val remaining = folder.apps - app
        val items = page.items.mapNotNull {
            if (it.id != folderId) it else collapseFolder(folder.copy(apps = remaining))
        }
        return replacePage(layout, pageIndex, page.copy(items = items))
    }

    fun renameFolder(layout: HomeLayout, pageIndex: Int, folderId: String, title: String): HomeLayout {
        val folder = layout.page(pageIndex)?.items?.firstOrNull { it.id == folderId } as? FolderItem ?: return layout
        val clean = title.trim().ifEmpty { folder.title }.take(MAX_FOLDER_TITLE)
        if (clean == folder.title) return layout
        return replaceItem(layout, pageIndex, folder.copy(title = clean))
    }

    /** Removes every reference to apps that are no longer installed. */
    fun purgeApps(layout: HomeLayout, isInstalled: (AppKey) -> Boolean): HomeLayout {
        var changed = false
        val pages = layout.pages.map { page ->
            val items = page.items.mapNotNull { item ->
                when (item) {
                    is AppItem -> if (isInstalled(item.app)) item else { changed = true; null }
                    is FolderItem -> {
                        val kept = item.apps.filter(isInstalled)
                        if (kept.size != item.apps.size) changed = true
                        collapseFolder(item.copy(apps = kept))
                    }
                    is WidgetItem -> item
                }
            }
            page.copy(items = items)
        }
        val dock = layout.dock.map { app -> if (app != null && !isInstalled(app)) { changed = true; null } else app }
        return if (changed) layout.copy(pages = pages, dock = dock) else layout
    }

    /** Removes every placement of [app] (grid, folders and dock). */
    fun removeAppEverywhere(layout: HomeLayout, app: AppKey): HomeLayout = purgeApps(layout) { it != app }

    /* ---------------- Dock ---------------- */

    fun setDock(layout: HomeLayout, slot: Int, app: AppKey?): HomeLayout {
        if (slot !in 0 until HomeLayout.DOCK_SIZE) return layout
        val dock = layout.dock.map { if (app != null && it == app) null else it }.toMutableList()
        dock[slot] = app
        return layout.copy(dock = dock)
    }

    fun swapDock(layout: HomeLayout, from: Int, to: Int): HomeLayout {
        if (from == to || from !in layout.dock.indices || to !in layout.dock.indices) return layout
        val dock = layout.dock.toMutableList()
        val tmp = dock[from]
        dock[from] = dock[to]
        dock[to] = tmp
        return layout.copy(dock = dock)
    }

    /** Moves a grid app into a dock slot; whatever was in the slot takes the app's cell. */
    fun gridToDock(layout: HomeLayout, pageIndex: Int, itemId: String, slot: Int): HomeLayout {
        if (slot !in layout.dock.indices) return layout
        val page = layout.page(pageIndex) ?: return layout
        val item = page.items.firstOrNull { it.id == itemId } as? AppItem ?: return layout
        val previous = layout.dock[slot]
        val items = page.items.mapNotNull {
            if (it.id != itemId) it else previous?.let { prev -> item.copy(app = prev) }
        }
        val dock = layout.dock.toMutableList().also { it[slot] = item.app }
        return replacePage(layout, pageIndex, page.copy(items = items)).copy(dock = dock)
    }

    /** Moves a dock app onto the grid: into a free cell, into a folder, or swapping with an app. */
    fun dockToGrid(
        layout: HomeLayout,
        slot: Int,
        pageIndex: Int,
        target: Cell,
        newId: () -> String = Ids::next,
    ): HomeLayout {
        val app = layout.dock.getOrNull(slot) ?: return layout
        val page = layout.page(pageIndex) ?: return layout
        val dock = layout.dock.toMutableList()
        if (fits(page, layout.columns, layout.rows, GridSpan.ONE, target)) {
            dock[slot] = null
            return replacePage(layout, pageIndex, page.copy(items = page.items + AppItem(newId(), target, app)))
                .copy(dock = dock)
        }
        return when (val occupant = itemAt(page, target)) {
            is AppItem -> {
                dock[slot] = occupant.app
                replaceItem(layout, pageIndex, occupant.copy(app = app)).copy(dock = dock)
            }
            is FolderItem -> {
                dock[slot] = null
                addToFolder(layout, pageIndex, occupant.id, app).copy(dock = dock)
            }
            else -> layout
        }
    }

    /* ---------------- Pages ---------------- */

    fun addPage(layout: HomeLayout, newId: () -> String = Ids::next): HomeLayout =
        layout.copy(pages = layout.pages + HomePage(newId()))

    /** Removes a page and everything on it. The layout always keeps at least one page. */
    fun removePage(layout: HomeLayout, index: Int, newId: () -> String = Ids::next): HomeLayout {
        if (index !in layout.pages.indices) return layout
        val pages = layout.pages.filterIndexed { i, _ -> i != index }
        return layout.copy(pages = pages.ifEmpty { listOf(HomePage(newId())) })
    }

    fun movePage(layout: HomeLayout, from: Int, to: Int): HomeLayout {
        if (from == to || from !in layout.pages.indices || to !in layout.pages.indices) return layout
        val pages = layout.pages.toMutableList()
        val page = pages.removeAt(from)
        pages.add(to, page)
        return layout.copy(pages = pages)
    }

    /** Drops empty pages, keeping the first one so the home screen never disappears. */
    fun dropEmptyPages(layout: HomeLayout): HomeLayout {
        val kept = layout.pages.filterIndexed { i, p -> i == 0 || p.items.isNotEmpty() }
        return if (kept.size == layout.pages.size) layout else layout.copy(pages = kept)
    }

    /* ---------------- Grid size and repair ---------------- */

    /** Changes the column count, re-placing anything that no longer fits. */
    fun withColumns(layout: HomeLayout, columns: Int, newId: () -> String = Ids::next): HomeLayout {
        val target = columns.coerceIn(HomeLayout.MIN_COLUMNS, HomeLayout.MAX_COLUMNS)
        if (target == layout.columns) return layout
        return normalize(layout.copy(columns = target), newId)
    }

    /**
     * Repairs a layout: fixes the dock size, guarantees a page, removes duplicate
     * ids and re-places items that are out of bounds or overlapping.
     */
    fun normalize(layout: HomeLayout, newId: () -> String = Ids::next): HomeLayout {
        val dock = (layout.dock + List(HomeLayout.DOCK_SIZE) { null }).take(HomeLayout.DOCK_SIZE)
        val seenIds = HashSet<String>()
        val overflow = ArrayList<HomeItem>()
        val pages = layout.pages.map { page ->
            var current = page.copy(items = emptyList())
            val pending = ArrayList<HomeItem>()
            for (item in page.items.sortedWith(compareBy({ it.cell.row }, { it.cell.column }))) {
                val unique = if (seenIds.add(item.id)) item else withId(item, newId().also { seenIds.add(it) })
                if (unique.span.columns > layout.columns || unique.span.rows > layout.rows) continue
                if (fits(current, layout.columns, layout.rows, unique.span, unique.cell)) {
                    current = current.copy(items = current.items + unique)
                } else {
                    pending += unique
                }
            }
            for (item in pending) {
                val cell = findFree(current, layout.columns, layout.rows, item.span)
                if (cell != null) current = current.copy(items = current.items + item.movedTo(cell)) else overflow += item
            }
            current
        }.toMutableList()
        if (pages.isEmpty()) pages += HomePage(newId())
        for (item in overflow) {
            var placed = false
            for (i in pages.indices) {
                val cell = findFree(pages[i], layout.columns, layout.rows, item.span) ?: continue
                pages[i] = pages[i].copy(items = pages[i].items + item.movedTo(cell))
                placed = true
                break
            }
            if (!placed) pages += HomePage(newId(), listOf(item.movedTo(Cell(0, 0))))
        }
        return layout.copy(pages = pages, dock = dock)
    }

    /* ---------------- Helpers ---------------- */

    private const val MAX_FOLDER_TITLE = 24

    private fun collapseFolder(folder: FolderItem): HomeItem? = when (folder.apps.size) {
        0 -> null
        1 -> AppItem(folder.id, folder.cell, folder.apps.first())
        else -> folder
    }

    private fun withId(item: HomeItem, id: String): HomeItem = when (item) {
        is AppItem -> item.copy(id = id)
        is FolderItem -> item.copy(id = id)
        is WidgetItem -> item.copy(id = id)
    }

    private fun replacePage(layout: HomeLayout, index: Int, page: HomePage): HomeLayout =
        layout.copy(pages = layout.pages.toMutableList().also { it[index] = page })

    private fun replaceItem(layout: HomeLayout, pageIndex: Int, item: HomeItem): HomeLayout {
        val page = layout.pages[pageIndex]
        return replacePage(layout, pageIndex, page.copy(items = page.items.map { if (it.id == item.id) item else it }))
    }
}
