package dev.morse.core.reminders

import dev.morse.core.json.Json
import dev.morse.core.json.asArray
import dev.morse.core.json.asObject
import dev.morse.core.json.bool
import dev.morse.core.json.string
import dev.morse.core.layout.Ids

data class Reminder(val id: String, val text: String, val done: Boolean = false)

/** The small on-device checklist behind the Reminders widget. */
object Reminders {
    const val MAX_TEXT = 80
    const val MAX_ITEMS = 50

    fun add(list: List<Reminder>, text: String, newId: () -> String = Ids::next): List<Reminder> {
        val clean = text.trim().replace(Regex("\\s+"), " ").take(MAX_TEXT)
        if (clean.isEmpty() || list.size >= MAX_ITEMS) return list
        return list + Reminder(newId(), clean)
    }

    fun toggle(list: List<Reminder>, id: String): List<Reminder> =
        list.map { if (it.id == id) it.copy(done = !it.done) else it }

    fun remove(list: List<Reminder>, id: String): List<Reminder> = list.filterNot { it.id == id }

    fun clearDone(list: List<Reminder>): List<Reminder> = list.filterNot { it.done }

    /** What the widget shows: open items first (in order added), then done ones. */
    fun forWidget(list: List<Reminder>, limit: Int): List<Reminder> =
        (list.filter { !it.done } + list.filter { it.done }).take(limit)

    fun progress(list: List<Reminder>): Pair<Int, Int> = list.count { it.done } to list.size

    fun encode(list: List<Reminder>): String =
        Json.stringify(list.map { mapOf("id" to it.id, "text" to it.text, "done" to it.done) })

    fun decode(text: String?): List<Reminder> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            Json.parse(text).asArray().mapNotNull { e ->
                val o = e.asObject()
                val id = o.string("id") ?: return@mapNotNull null
                val t = o.string("text") ?: return@mapNotNull null
                Reminder(id, t, o.bool("done") ?: false)
            }
        } catch (e: Json.JsonException) {
            emptyList()
        }
    }
}
