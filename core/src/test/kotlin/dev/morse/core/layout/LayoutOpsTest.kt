package dev.morse.core.layout

import dev.morse.core.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {

    private var counter = 0
    private val ids: () -> String = { "id${counter++}" }

    private fun app(name: String) = AppKey("pkg.$name", "pkg.$name.Main")

    private fun layoutOf(vararg items: HomeItem, dock: List<AppKey?> = List(4) { null }) =
        HomeLayout(columns = 4, rows = 6, pages = listOf(HomePage("p0", items.toList())), dock = dock)

    @Test
    fun occupancyMarksEveryCoveredCell() {
        val w = WidgetItem("w", Cell(0, 0), WidgetKind.GLANCE, GridSpan(4, 2))
        val grid = LayoutOps.occupancy(HomePage("p", listOf(w)), 4, 6)
        for (r in 0..1) for (c in 0..3) assertEquals("w", grid[r][c])
        assertNull(grid[2][0])
    }

    @Test
    fun findFreeScansRowMajor() {
        val page = HomePage("p", listOf(AppItem("a", Cell(0, 0), app("a"))))
        assertEquals(Cell(1, 0), LayoutOps.findFree(page, 4, 6, GridSpan.ONE))
        assertEquals(Cell(1, 0), LayoutOps.findFree(page, 4, 6, GridSpan(2, 2)))
        assertEquals(Cell(0, 1), LayoutOps.findFree(page, 4, 6, GridSpan(4, 2)))
        assertNull(LayoutOps.findFree(page, 4, 6, GridSpan(5, 1)))
    }

    @Test
    fun addAppUsesFirstFreeCellAndNeverDuplicates() {
        val start = layoutOf(AppItem("a", Cell(0, 0), app("a")))
        val added = LayoutOps.addApp(start, app("b"), ids)
        val item = added.pages[0].items.last() as AppItem
        assertEquals(Cell(1, 0), item.cell)
        assertSame(added, LayoutOps.addApp(added, app("b"), ids))
    }

    @Test
    fun addAppOpensANewPageWhenFull() {
        val full = layoutOf(*Array(24) { i -> AppItem("a$i", Cell(i % 4, i / 4), app("a$i")) })
        val added = LayoutOps.addApp(full, app("new"), ids)
        assertEquals(2, added.pages.size)
        assertEquals(Cell(0, 0), added.pages[1].items.single().cell)
    }

    @Test
    fun addWidgetPrefersRequestedPage() {
        val two = HomeLayout(pages = listOf(HomePage("p0"), HomePage("p1")))
        val placed = LayoutOps.addWidget(two, 1, WidgetKind.WEATHER, WidgetSize.S.span, ids)
        assertNotNull(placed)
        assertEquals(1, placed!!.pageIndex)
        assertEquals(Cell(0, 0), placed.item.cell)
    }

    @Test
    fun addWidgetFallsThroughToANewPage() {
        val busy = layoutOf(WidgetItem("big", Cell(0, 0), WidgetKind.CALENDAR, GridSpan(4, 4)), WidgetItem("m", Cell(0, 4), WidgetKind.MUSIC, GridSpan(4, 2)))
        val placed = LayoutOps.addWidget(busy, 0, WidgetKind.CALENDAR, WidgetSize.L.span, ids)!!
        assertEquals(1, placed.pageIndex)
        assertEquals(2, placed.layout.pages.size)
    }

    @Test
    fun addWidgetRejectsSpansLargerThanTheGrid() {
        assertNull(LayoutOps.addWidget(layoutOf(), 0, WidgetKind.HOSTED, GridSpan(6, 1), ids))
    }

    @Test
    fun moveToFreeCell() {
        val l = layoutOf(AppItem("a", Cell(0, 0), app("a")))
        val moved = LayoutOps.move(l, 0, "a", Cell(3, 5))
        assertEquals(Cell(3, 5), moved.pages[0].items.single().cell)
    }

    @Test
    fun moveOntoSameSizeItemSwaps() {
        val l = layoutOf(AppItem("a", Cell(0, 0), app("a")), AppItem("b", Cell(1, 0), app("b")))
        val moved = LayoutOps.move(l, 0, "a", Cell(1, 0))
        val byId = moved.pages[0].items.associateBy { it.id }
        assertEquals(Cell(1, 0), byId.getValue("a").cell)
        assertEquals(Cell(0, 0), byId.getValue("b").cell)
    }

    @Test
    fun moveIsRefusedWhenBlocked() {
        val l = layoutOf(
            WidgetItem("w", Cell(0, 0), WidgetKind.WEATHER, GridSpan(2, 2)),
            AppItem("a", Cell(2, 0), app("a")),
        )
        assertSame(l, LayoutOps.move(l, 0, "w", Cell(1, 0)))
        assertSame(l, LayoutOps.move(l, 0, "w", Cell(3, 0)))
        assertSame(l, LayoutOps.move(l, 0, "missing", Cell(3, 3)))
    }

    @Test
    fun moveToAnotherPage() {
        val l = HomeLayout(pages = listOf(HomePage("p0", listOf(AppItem("a", Cell(0, 0), app("a")))), HomePage("p1")))
        val moved = LayoutOps.moveToPage(l, 0, "a", 1, Cell(2, 3))
        assertTrue(moved.pages[0].items.isEmpty())
        assertEquals(Cell(2, 3), moved.pages[1].items.single().cell)
    }

    @Test
    fun droppingAppOnAppMakesAFolder() {
        val l = layoutOf(AppItem("a", Cell(0, 0), app("a")), AppItem("b", Cell(1, 0), app("b")))
        val result = LayoutOps.dropOnto(l, 0, "a", "b", ids, "Tools")
        val folder = result.pages[0].items.single() as FolderItem
        assertEquals(Cell(1, 0), folder.cell)
        assertEquals("Tools", folder.title)
        assertEquals(listOf(app("b"), app("a")), folder.apps)
    }

    @Test
    fun droppingAppOnFolderAddsIt() {
        val l = layoutOf(AppItem("a", Cell(0, 0), app("a")), FolderItem("f", Cell(1, 0), "F", listOf(app("b"), app("c"))))
        val folder = LayoutOps.dropOnto(l, 0, "a", "f", ids).pages[0].items.single() as FolderItem
        assertEquals(listOf(app("b"), app("c"), app("a")), folder.apps)
    }

    @Test
    fun droppingOnAWidgetDoesNothing() {
        val l = layoutOf(AppItem("a", Cell(0, 0), app("a")), WidgetItem("w", Cell(0, 1), WidgetKind.BATTERY, GridSpan(2, 2)))
        assertSame(l, LayoutOps.dropOnto(l, 0, "a", "w", ids))
    }

    @Test
    fun removingTheSecondLastAppCollapsesTheFolder() {
        val l = layoutOf(FolderItem("f", Cell(2, 2), "F", listOf(app("a"), app("b"))))
        val item = LayoutOps.removeFromFolder(l, 0, "f", app("a")).pages[0].items.single()
        assertTrue(item is AppItem)
        assertEquals(app("b"), (item as AppItem).app)
        assertEquals(Cell(2, 2), item.cell)
    }

    @Test
    fun renameFolderTrimsAndIgnoresBlank() {
        val l = layoutOf(FolderItem("f", Cell(0, 0), "F", listOf(app("a"), app("b"))))
        val renamed = LayoutOps.renameFolder(l, 0, "f", "  Work  ")
        assertEquals("Work", (renamed.pages[0].items.single() as FolderItem).title)
        assertSame(l, LayoutOps.renameFolder(l, 0, "f", "   "))
    }

    @Test
    fun purgeRemovesUninstalledAppsEverywhere() {
        val l = layoutOf(
            AppItem("a", Cell(0, 0), app("gone")),
            FolderItem("f", Cell(1, 0), "F", listOf(app("gone"), app("keep"))),
            AppItem("k", Cell(2, 0), app("keep2")),
            dock = listOf(app("gone"), app("keep3"), null, null),
        )
        val purged = LayoutOps.purgeApps(l) { it != app("gone") }
        assertEquals(2, purged.pages[0].items.size)
        assertTrue(purged.pages[0].items.none { it is FolderItem })
        assertNull(purged.dock[0])
        assertEquals(app("keep3"), purged.dock[1])
        assertFalse(app("gone") in purged.allApps())
    }

    @Test
    fun purgeReturnsSameInstanceWhenNothingChanges() {
        val l = layoutOf(AppItem("a", Cell(0, 0), app("a")))
        assertSame(l, LayoutOps.purgeApps(l) { true })
    }

    @Test
    fun gridToDockSwapsWithTheSlot() {
        val l = layoutOf(AppItem("a", Cell(1, 1), app("a")), dock = listOf(app("d"), null, null, null))
        val result = LayoutOps.gridToDock(l, 0, "a", 0)
        assertEquals(app("a"), result.dock[0])
        val back = result.pages[0].items.single() as AppItem
        assertEquals(app("d"), back.app)
        assertEquals(Cell(1, 1), back.cell)
    }

    @Test
    fun gridToEmptyDockSlotRemovesTheGridItem() {
        val l = layoutOf(AppItem("a", Cell(1, 1), app("a")))
        val result = LayoutOps.gridToDock(l, 0, "a", 2)
        assertTrue(result.pages[0].items.isEmpty())
        assertEquals(app("a"), result.dock[2])
    }

    @Test
    fun dockToGridIntoFreeCellOrFolder() {
        val l = layoutOf(FolderItem("f", Cell(0, 0), "F", listOf(app("a"), app("b"))), dock = listOf(app("d"), app("e"), null, null))
        val free = LayoutOps.dockToGrid(l, 0, 0, Cell(3, 3), ids)
        assertNull(free.dock[0])
        assertEquals(Cell(3, 3), free.pages[0].items.last().cell)
        val folder = LayoutOps.dockToGrid(l, 1, 0, Cell(0, 0), ids)
        assertNull(folder.dock[1])
        assertEquals(3, (folder.pages[0].items.single() as FolderItem).apps.size)
    }

    @Test
    fun setDockNeverDuplicates() {
        val l = layoutOf(dock = listOf(app("a"), null, null, null))
        val result = LayoutOps.setDock(l, 3, app("a"))
        assertEquals(listOf(null, null, null, app("a")), result.dock)
    }

    @Test
    fun removePageKeepsAtLeastOne() {
        val l = layoutOf(AppItem("a", Cell(0, 0), app("a")))
        val result = LayoutOps.removePage(l, 0, ids)
        assertEquals(1, result.pages.size)
        assertTrue(result.pages[0].items.isEmpty())
    }

    @Test
    fun dropEmptyPagesKeepsTheFirst() {
        val l = HomeLayout(pages = listOf(HomePage("a"), HomePage("b"), HomePage("c", listOf(AppItem("x", Cell(0, 0), app("x"))))))
        assertEquals(listOf("a", "c"), LayoutOps.dropEmptyPages(l).pages.map { it.id })
    }

    @Test
    fun shrinkingColumnsReflowsItems() {
        val five = HomeLayout(columns = 5, pages = listOf(HomePage("p", listOf(AppItem("edge", Cell(4, 0), app("edge"))))))
        val four = LayoutOps.withColumns(five, 4, ids)
        assertEquals(4, four.columns)
        val cell = four.pages[0].items.single().cell
        assertTrue(cell.column in 0..3)
        assertTrue(LayoutOps.isValid(four.pages[0], 4, 6))
    }

    @Test
    fun normalizeRepairsOverlapsDuplicatesAndDock() {
        val broken = HomeLayout(
            pages = listOf(
                HomePage(
                    "p",
                    listOf(
                        AppItem("x", Cell(0, 0), app("a")),
                        AppItem("x", Cell(0, 0), app("b")),
                        AppItem("out", Cell(9, 9), app("c")),
                    ),
                ),
            ),
            dock = listOf(app("d")),
        )
        val fixed = LayoutOps.normalize(broken, ids)
        val page = fixed.pages[0]
        assertEquals(3, page.items.size)
        assertEquals(3, page.items.map { it.id }.toSet().size)
        assertTrue(LayoutOps.isValid(page, 4, 6))
        assertEquals(HomeLayout.DOCK_SIZE, fixed.dock.size)
    }

    @Test
    fun normalizeSpillsToNewPagesWhenFull() {
        val crowded = HomeLayout(
            pages = listOf(
                HomePage("p", List(25) { i -> AppItem("a$i", Cell(0, 0), app("a$i")) }),
            ),
        )
        val fixed = LayoutOps.normalize(crowded, ids)
        assertEquals(2, fixed.pages.size)
        assertEquals(25, fixed.pages.sumOf { it.items.size })
        fixed.pages.forEach { assertTrue(LayoutOps.isValid(it, 4, 6)) }
    }

    @Test
    fun resizeGrowsInPlaceOrMoves() {
        val l = layoutOf(WidgetItem("w", Cell(0, 0), WidgetKind.WEATHER, GridSpan(2, 2)))
        val grown = LayoutOps.resizeWidget(l, 0, "w", WidgetSize.M.span)
        assertEquals(WidgetItem("w", Cell(0, 0), WidgetKind.WEATHER, GridSpan(4, 2)), grown.pages[0].items.single())

        val blocked = layoutOf(
            WidgetItem("w", Cell(0, 0), WidgetKind.WEATHER, GridSpan(2, 2)),
            AppItem("a", Cell(3, 0), app("a")),
        )
        val moved = LayoutOps.resizeWidget(blocked, 0, "w", WidgetSize.M.span).pages[0].items.first { it.id == "w" }
        assertEquals(Cell(0, 1), moved.cell)
        assertEquals(GridSpan(4, 2), moved.span)
    }

    @Test
    fun resizeWithNoRoomChangesNothing() {
        val full = layoutOf(*Array(24) { i -> if (i == 0) WidgetItem("w", Cell(0, 0), WidgetKind.WEATHER, GridSpan(1, 1)) else AppItem("a$i", Cell(i % 4, i / 4), app("a$i")) })
        assertSame(full, LayoutOps.resizeWidget(full, 0, "w", GridSpan(2, 2)))
    }
}
