package dev.morse.core.layout

import dev.morse.core.apps.AppKey
import dev.morse.core.json.Json
import dev.morse.core.json.array
import dev.morse.core.json.asArray
import dev.morse.core.json.asObject
import dev.morse.core.json.int
import dev.morse.core.json.string

/**
 * Stores a [HomeLayout] as JSON. Decoding is forgiving: unknown entries are
 * skipped and the result is always passed through [LayoutOps.normalize], so a
 * damaged file can never crash the launcher.
 */
object LayoutCodec {
    const val VERSION = 1

    fun encode(layout: HomeLayout): String = Json.stringify(
        mapOf(
            "version" to VERSION,
            "columns" to layout.columns,
            "rows" to layout.rows,
            "dock" to layout.dock.map { it?.flatten() },
            "pages" to layout.pages.map { page ->
                mapOf("id" to page.id, "items" to page.items.map(::encodeItem))
            },
        ),
    )

    private fun encodeItem(item: HomeItem): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>("id" to item.id, "c" to item.cell.column, "r" to item.cell.row)
        when (item) {
            is AppItem -> {
                base["type"] = "app"
                base["app"] = item.app.flatten()
            }
            is FolderItem -> {
                base["type"] = "folder"
                base["title"] = item.title
                base["apps"] = item.apps.map { it.flatten() }
            }
            is WidgetItem -> {
                base["type"] = "widget"
                base["kind"] = item.kind.name
                base["w"] = item.span.columns
                base["h"] = item.span.rows
                if (item.hostedId >= 0) base["hostedId"] = item.hostedId
                if (item.provider != null) base["provider"] = item.provider
            }
        }
        return base
    }

    /** Returns null only when the text is not a layout at all. */
    fun decode(text: String, newId: () -> String = Ids::next): HomeLayout? {
        val root = try {
            Json.parse(text).asObject()
        } catch (e: Json.JsonException) {
            return null
        }
        if (root.isEmpty() || root["pages"] !is List<*>) return null
        val columns = (root.int("columns") ?: 4).coerceIn(HomeLayout.MIN_COLUMNS, HomeLayout.MAX_COLUMNS)
        val rows = (root.int("rows") ?: 6).coerceIn(4, 8)
        val dock = root.array("dock").map { AppKey.parse(it as? String) }
        val pages = root.array("pages").map { p ->
            val page = p.asObject()
            HomePage(
                id = page.string("id") ?: newId(),
                items = page.array("items").mapNotNull { decodeItem(it.asObject(), newId) },
            )
        }
        return LayoutOps.normalize(HomeLayout(columns, rows, pages, dock), newId)
    }

    private fun decodeItem(o: Map<String, Any?>, newId: () -> String): HomeItem? {
        val id = o.string("id") ?: newId()
        val cell = Cell(o.int("c") ?: 0, o.int("r") ?: 0)
        return when (o.string("type")) {
            "app" -> AppKey.parse(o.string("app"))?.let { AppItem(id, cell, it) }
            "folder" -> {
                val apps = o["apps"].asArray().mapNotNull { AppKey.parse(it as? String) }.distinct()
                when (apps.size) {
                    0 -> null
                    1 -> AppItem(id, cell, apps.first())
                    else -> FolderItem(id, cell, o.string("title") ?: "Folder", apps)
                }
            }
            "widget" -> {
                val kind = WidgetKind.entries.firstOrNull { it.name == o.string("kind") } ?: return null
                val w = o.int("w") ?: return null
                val h = o.int("h") ?: return null
                if (w < 1 || h < 1) return null
                val hostedId = o.int("hostedId") ?: -1
                if (kind == WidgetKind.HOSTED && hostedId < 0) return null
                WidgetItem(id, cell, kind, GridSpan(w, h), hostedId, o.string("provider"))
            }
            else -> null
        }
    }
}
