package dev.morse.core.search

import dev.morse.core.apps.Library

/**
 * Ranks short queries against titles. Matching is case- and accent-insensitive.
 *
 * Score tiers (higher is better):
 * exact title 1000 · title prefix 800 · word prefix 600 · initials 450 ·
 * keyword prefix 350 · substring 200 · in-order letters 60.
 */
object SearchEngine {

    data class Candidate<T>(
        val title: String,
        val payload: T,
        val keywords: List<String> = emptyList(),
        /** Added to the score; e.g. launch count, so frequent apps rank first among equals. */
        val boost: Int = 0,
    )

    data class Hit<T>(val payload: T, val title: String, val score: Int)

    fun normalize(text: String): String = Library.fold(text).lowercase().trim().replace(SPACES, " ")

    /** The match score, or null when [query] does not match. */
    fun score(query: String, title: String, keywords: List<String> = emptyList()): Int? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val t = normalize(title)
        if (t.isEmpty()) return null
        val words = t.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        return when {
            t == q -> 1000
            t.startsWith(q) -> 800
            words.any { it.startsWith(q) } -> 600
            q.length >= 2 && !q.contains(' ') && initials(words).startsWith(q) -> 450
            keywords.any { k -> normalize(k).let { it.startsWith(q) || it.split(' ').any { w -> w.startsWith(q) } } } -> 350
            q.length >= 2 && t.contains(q) -> 200
            q.length >= 3 && isSubsequence(q.replace(" ", ""), t.replace(" ", "")) -> 60
            else -> null
        }
    }

    fun <T> rank(query: String, candidates: List<Candidate<T>>, limit: Int = Int.MAX_VALUE): List<Hit<T>> {
        if (normalize(query).isEmpty()) return emptyList()
        return candidates
            .mapNotNull { c -> score(query, c.title, c.keywords)?.let { Hit(c.payload, c.title, it + c.boost.coerceIn(0, MAX_BOOST)) } }
            .sortedWith(
                compareByDescending<Hit<T>> { it.score }
                    .thenBy { it.title.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
            )
            .take(limit)
    }

    /** Boost is capped so usage can only reorder results inside a tier. */
    const val MAX_BOOST = 99

    private fun initials(words: List<String>): String = words.joinToString("") { it.take(1) }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var i = 0
        for (c in haystack) {
            if (i < needle.length && needle[i] == c) i++
        }
        return i == needle.length
    }

    private val SPACES = Regex("\\s+")
}
