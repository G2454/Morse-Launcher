package dev.morse.core.layout

import dev.morse.core.apps.AppKey
import dev.morse.core.apps.Role

/**
 * Builds the first-run home screen from the design:
 *
 * Page 1 — Glance (M), Weather (S) beside a 2×2 block of apps with a "Tools"
 * folder, two rows of apps, and the dock.
 * Page 2 — Music (M), Calendar (S) + Quick toggles (S), Battery (S) + Reminders (S).
 *
 * Roles that have no app are filled with other installed apps so there are no gaps.
 */
object DefaultLayout {

    fun build(
        roles: Map<Role, AppKey>,
        others: List<AppKey>,
        newId: () -> String = Ids::next,
    ): HomeLayout {
        val used = LinkedHashSet<AppKey>()
        val spare = ArrayDeque(others.distinct())

        fun take(role: Role?): AppKey? {
            val byRole = role?.let { roles[it] }?.takeIf { it !in used }
            val app = byRole ?: run {
                while (spare.isNotEmpty()) {
                    val next = spare.removeFirst()
                    if (next !in used && next !in roles.values) return@run next
                }
                null
            }
            if (app != null) used += app
            return app
        }

        // Dock first so its apps never end up on the grid as well.
        val dock = listOf(
            take(Role.DIALER),
            take(Role.SMS),
            take(Role.BROWSER),
            roles[Role.MUSIC]?.takeIf { it !in used }?.also { used += it } ?: take(Role.CAMERA),
        )

        val items = ArrayList<HomeItem>()
        items += WidgetItem(newId(), Cell(0, 0), WidgetKind.GLANCE, WidgetSize.M.span)
        items += WidgetItem(newId(), Cell(0, 2), WidgetKind.WEATHER, WidgetSize.S.span)

        fun app(cell: Cell, role: Role?) {
            take(role)?.let { items += AppItem(newId(), cell, it) }
        }

        app(Cell(2, 2), Role.CALENDAR)
        app(Cell(3, 2), Role.CLOCK)
        app(Cell(2, 3), Role.WEATHER)

        val tools = listOfNotNull(
            take(Role.CALCULATOR),
            take(Role.CONTACTS),
            take(Role.FILES),
        )
        when (tools.size) {
            0 -> Unit
            1 -> items += AppItem(newId(), Cell(3, 3), tools.first())
            else -> items += FolderItem(newId(), Cell(3, 3), "Tools", tools)
        }

        listOf(Role.GALLERY, Role.CAMERA, Role.MAPS, Role.STORE).forEachIndexed { c, role -> app(Cell(c, 4), role) }
        listOf(Role.EMAIL, null, null, Role.SETTINGS).forEachIndexed { c, role -> app(Cell(c, 5), role) }

        val second = listOf<HomeItem>(
            WidgetItem(newId(), Cell(0, 0), WidgetKind.MUSIC, WidgetSize.M.span),
            WidgetItem(newId(), Cell(0, 2), WidgetKind.CALENDAR, WidgetSize.S.span),
            WidgetItem(newId(), Cell(2, 2), WidgetKind.TOGGLES, WidgetSize.S.span),
            WidgetItem(newId(), Cell(0, 4), WidgetKind.BATTERY, WidgetSize.S.span),
            WidgetItem(newId(), Cell(2, 4), WidgetKind.REMINDERS, WidgetSize.S.span),
        )

        return LayoutOps.normalize(
            HomeLayout(
                columns = 4,
                rows = 6,
                pages = listOf(HomePage(newId(), items), HomePage(newId(), second)),
                dock = dock,
            ),
            newId,
        )
    }
}
