package dev.morse.core.layout

import dev.morse.core.apps.AppKey
import dev.morse.core.apps.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLayoutTest {

    private var n = 0
    private val ids: () -> String = { "d${n++}" }

    private fun app(name: String) = AppKey("pkg.$name", "pkg.$name.Main")

    private val allRoles = Role.entries.associateWith { app(it.name.lowercase()) }

    @Test
    fun matchesTheDesign() {
        val layout = DefaultLayout.build(allRoles, emptyList(), ids)
        assertEquals(2, layout.pages.size)
        val first = layout.pages[0].items
        val glance = first.first { it is WidgetItem && it.kind == WidgetKind.GLANCE }
        assertEquals(Cell(0, 0), glance.cell)
        assertEquals(WidgetSize.M.span, glance.span)
        val weather = first.first { it is WidgetItem && it.kind == WidgetKind.WEATHER }
        assertEquals(Cell(0, 2), weather.cell)
        val tools = first.single { it is FolderItem } as FolderItem
        assertEquals("Tools", tools.title)
        assertEquals(Cell(3, 3), tools.cell)
        assertEquals(listOf(allRoles[Role.DIALER], allRoles[Role.SMS], allRoles[Role.BROWSER], allRoles[Role.MUSIC]), layout.dock)

        val second = layout.pages[1].items.filterIsInstance<WidgetItem>().map { it.kind }.toSet()
        assertEquals(setOf(WidgetKind.MUSIC, WidgetKind.CALENDAR, WidgetKind.TOGGLES, WidgetKind.BATTERY, WidgetKind.REMINDERS), second)
    }

    @Test
    fun pagesAreValidAndAppsAreNeverDuplicated() {
        val layout = DefaultLayout.build(allRoles, List(30) { app("extra$it") }, ids)
        layout.pages.forEach { assertTrue(LayoutOps.isValid(it, layout.columns, layout.rows)) }
        val placed = buildList {
            layout.pages.forEach { p ->
                p.items.forEach { i ->
                    when (i) {
                        is AppItem -> add(i.app)
                        is FolderItem -> addAll(i.apps)
                        else -> Unit
                    }
                }
            }
            addAll(layout.dock.filterNotNull())
        }
        assertEquals(placed.size, placed.toSet().size)
    }

    @Test
    fun missingRolesAreFilledWithOtherApps() {
        val roles = mapOf(Role.DIALER to app("dialer"))
        val others = listOf(app("o1"), app("o2"), app("o3"), app("o4"), app("o5"))
        val layout = DefaultLayout.build(roles, others, ids)
        assertEquals(app("dialer"), layout.dock[0])
        assertEquals(app("o1"), layout.dock[1])
        assertTrue(layout.allApps().containsAll(others))
    }

    @Test
    fun worksWithNoAppsAtAll() {
        val layout = DefaultLayout.build(emptyMap(), emptyList(), ids)
        assertEquals(listOf<AppKey?>(null, null, null, null), layout.dock)
        assertTrue(layout.pages[0].items.all { it is WidgetItem })
    }
}
