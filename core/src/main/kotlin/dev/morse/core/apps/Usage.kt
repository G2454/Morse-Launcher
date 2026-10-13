package dev.morse.core.apps

import dev.morse.core.json.Json
import dev.morse.core.json.asObject

/** Launch counts, used for Suggestions and to rank search results. Kept only on the device. */
object Usage {
    const val MAX_TRACKED = 300

    fun increment(counts: Map<AppKey, Int>, app: AppKey): Map<AppKey, Int> {
        val next = counts.toMutableMap()
        next[app] = (next[app] ?: 0) + 1
        if (next.size > MAX_TRACKED) {
            // Drop the least used so the file never grows without bound.
            next.entries.sortedBy { it.value }.take(next.size - MAX_TRACKED).forEach { next.remove(it.key) }
        }
        return next
    }

    fun encode(counts: Map<AppKey, Int>): String =
        Json.stringify(counts.entries.associate { it.key.flatten() to it.value })

    fun decode(text: String?): Map<AppKey, Int> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            Json.parse(text).asObject().mapNotNull { (k, v) ->
                val key = AppKey.parse(k) ?: return@mapNotNull null
                val n = (v as? Number)?.toInt() ?: return@mapNotNull null
                if (n > 0) key to n else null
            }.toMap()
        } catch (e: Json.JsonException) {
            emptyMap()
        }
    }
}
