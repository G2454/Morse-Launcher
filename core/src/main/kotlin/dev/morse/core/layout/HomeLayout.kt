package dev.morse.core.layout

import dev.morse.core.apps.AppKey

/** A cell on the home grid. Column 0 is the left edge, row 0 the top. */
data class Cell(val column: Int, val row: Int)

/** How many grid cells something covers. */
data class GridSpan(val columns: Int, val rows: Int) {
    init {
        require(columns >= 1 && rows >= 1) { "Span must be at least 1×1" }
    }

    companion object {
        val ONE = GridSpan(1, 1)
    }
}

/** The three Morse widget sizes on a 4-column grid. */
enum class WidgetSize(val span: GridSpan, val label: String) {
    S(GridSpan(2, 2), "S"),
    M(GridSpan(4, 2), "M"),
    L(GridSpan(4, 4), "L"),
}

/**
 * Built-in widgets, plus [HOSTED] for widgets provided by other apps.
 * [refreshNote] is shown in the widget picker so people know what each one costs.
 */
enum class WidgetKind(
    val title: String,
    val sizes: List<WidgetSize>,
    val refreshNote: String,
) {
    GLANCE("Glance", listOf(WidgetSize.M), "Updates once a minute"),
    DOT_CLOCK("Dot clock", listOf(WidgetSize.S), "Updates once a minute"),
    DIAL_CLOCK("Dial clock", listOf(WidgetSize.S), "Updates once a minute"),
    WEATHER("Weather", listOf(WidgetSize.S, WidgetSize.M), "Updates every 30 minutes"),
    BATTERY("Battery", listOf(WidgetSize.S), "Updates on system broadcast"),
    CALENDAR("Calendar", listOf(WidgetSize.S, WidgetSize.M, WidgetSize.L), "Updates when your calendar changes"),
    MUSIC("Music", listOf(WidgetSize.M), "Updates on media events"),
    TOGGLES("Quick toggles", listOf(WidgetSize.S), "Updates on tap"),
    REMINDERS("Reminders", listOf(WidgetSize.S, WidgetSize.M), "Updates on tap"),
    HOSTED("App widget", emptyList(), "Managed by the app"),
}

/** Something placed on a home page. */
sealed interface HomeItem {
    val id: String
    val cell: Cell
    val span: GridSpan

    fun movedTo(cell: Cell): HomeItem = when (this) {
        is AppItem -> copy(cell = cell)
        is FolderItem -> copy(cell = cell)
        is WidgetItem -> copy(cell = cell)
    }
}

data class AppItem(
    override val id: String,
    override val cell: Cell,
    val app: AppKey,
) : HomeItem {
    override val span: GridSpan get() = GridSpan.ONE
}

data class FolderItem(
    override val id: String,
    override val cell: Cell,
    val title: String,
    val apps: List<AppKey>,
) : HomeItem {
    override val span: GridSpan get() = GridSpan.ONE
}

data class WidgetItem(
    override val id: String,
    override val cell: Cell,
    val kind: WidgetKind,
    override val span: GridSpan,
    /** AppWidget id for [WidgetKind.HOSTED], otherwise -1. */
    val hostedId: Int = -1,
    /** Flattened provider component for [WidgetKind.HOSTED]. */
    val provider: String? = null,
) : HomeItem {
    val size: WidgetSize? get() = WidgetSize.entries.firstOrNull { it.span == span }
}

data class HomePage(val id: String, val items: List<HomeItem> = emptyList())

/** The whole home screen: pages of items on a grid, plus the dock. */
data class HomeLayout(
    val columns: Int = 4,
    val rows: Int = 6,
    val pages: List<HomePage>,
    val dock: List<AppKey?> = List(DOCK_SIZE) { null },
) {
    fun page(index: Int): HomePage? = pages.getOrNull(index)

    fun findItem(id: String): Pair<Int, HomeItem>? {
        pages.forEachIndexed { index, page ->
            page.items.firstOrNull { it.id == id }?.let { return index to it }
        }
        return null
    }

    /** Every app referenced anywhere: grid, folders and dock. */
    fun allApps(): Set<AppKey> = buildSet {
        pages.forEach { p ->
            p.items.forEach { item ->
                when (item) {
                    is AppItem -> add(item.app)
                    is FolderItem -> addAll(item.apps)
                    is WidgetItem -> Unit
                }
            }
        }
        dock.filterNotNull().forEach { add(it) }
    }

    companion object {
        const val DOCK_SIZE = 4
        const val MIN_COLUMNS = 4
        const val MAX_COLUMNS = 5

        fun empty(newId: () -> String = Ids::next) = HomeLayout(pages = listOf(HomePage(newId())))
    }
}

/** Short random ids for layout entries. */
object Ids {
    private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val random = java.security.SecureRandom()

    fun next(): String = buildString { repeat(10) { append(alphabet[random.nextInt(alphabet.length)]) } }
}
