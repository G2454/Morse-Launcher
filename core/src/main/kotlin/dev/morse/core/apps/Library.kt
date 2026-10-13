package dev.morse.core.apps

import dev.morse.core.glyph.Glyph
import java.text.Normalizer

/** A titled group of apps in the app library. */
data class LibraryGroup(val id: String, val title: String, val apps: List<AppInfo>)

/** One letter section of the A–Z list. */
data class LetterSection(val letter: Char, val apps: List<AppInfo>)

object Library {

    /** Android's `ApplicationInfo.CATEGORY_*` values (stable since API 26). */
    object AndroidCategory {
        const val UNDEFINED = -1
        const val GAME = 0
        const val AUDIO = 1
        const val VIDEO = 2
        const val IMAGE = 3
        const val SOCIAL = 4
        const val NEWS = 5
        const val MAPS = 6
        const val PRODUCTIVITY = 7
        const val ACCESSIBILITY = 8
    }

    const val SUGGESTIONS_ID = "suggestions"
    const val RECENT_ID = "recent"
    const val SUGGESTION_COUNT = 7
    const val RECENT_COUNT = 7
    const val RECENT_WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000

    /** Maps an Android category (and, when undefined, the app's glyph) to a library group. */
    fun categorize(androidCategory: Int, glyph: Glyph?): AppCategory = when (androidCategory) {
        AndroidCategory.GAME -> AppCategory.GAMES
        AndroidCategory.AUDIO, AndroidCategory.VIDEO, AndroidCategory.NEWS -> AppCategory.MEDIA
        AndroidCategory.IMAGE -> AppCategory.CREATIVITY
        AndroidCategory.SOCIAL -> AppCategory.SOCIAL
        AndroidCategory.MAPS, AndroidCategory.ACCESSIBILITY -> AppCategory.UTILITIES
        AndroidCategory.PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        else -> when (glyph) {
            Glyph.PHONE, Glyph.MESSAGES, Glyph.CONTACTS -> AppCategory.SOCIAL
            Glyph.MAIL, Glyph.CALENDAR, Glyph.NOTES, Glyph.FILES, Glyph.SCANNER, Glyph.TERMINAL -> AppCategory.PRODUCTIVITY
            Glyph.CAMERA, Glyph.PHOTOS, Glyph.RECORDER -> AppCategory.CREATIVITY
            Glyph.MUSIC, Glyph.VIDEO, Glyph.PODCASTS, Glyph.BOOKS, Glyph.NEWS -> AppCategory.MEDIA
            Glyph.SETTINGS, Glyph.CALCULATOR, Glyph.CLOCK, Glyph.COMPASS, Glyph.WALLET, Glyph.STORE,
            Glyph.BROWSER, Glyph.MAPS, Glyph.WEATHER, Glyph.TRANSLATE, Glyph.HEALTH, Glyph.LAYERS,
            -> AppCategory.UTILITIES
            Glyph.GAMES -> AppCategory.GAMES
            else -> AppCategory.OTHER
        }
    }

    /**
     * Library groups: Suggestions (most launched), Recently added (installed in the
     * last two weeks, newest first), then one group per category that has apps.
     */
    fun groups(apps: List<AppInfo>, launchCounts: Map<AppKey, Int>, nowMillis: Long): List<LibraryGroup> {
        if (apps.isEmpty()) return emptyList()
        val byLabel: Comparator<AppInfo> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        val result = ArrayList<LibraryGroup>()

        val suggestions = apps
            .filter { (launchCounts[it.key] ?: 0) > 0 }
            .sortedWith(compareByDescending<AppInfo> { launchCounts[it.key] ?: 0 }.then(byLabel))
            .take(SUGGESTION_COUNT)
        if (suggestions.isNotEmpty()) result += LibraryGroup(SUGGESTIONS_ID, "Suggestions", suggestions)

        val recent = apps
            .filter { !it.isSystem && it.installedAtMillis > 0 && nowMillis - it.installedAtMillis in 0..RECENT_WINDOW_MILLIS }
            .sortedByDescending { it.installedAtMillis }
            .take(RECENT_COUNT)
        if (recent.isNotEmpty()) result += LibraryGroup(RECENT_ID, "Recently added", recent)

        AppCategory.entries.forEach { category ->
            val members = apps.filter { it.category == category }.sortedWith(byLabel)
            if (members.isNotEmpty()) result += LibraryGroup(category.name.lowercase(), category.title, members)
        }
        return result
    }

    /** A–Z sections; digits and symbols go under '#', accents are folded (É → E). */
    fun alphabetical(apps: List<AppInfo>): List<LetterSection> {
        val grouped = apps
            .sortedWith(compareBy<AppInfo> { sortKey(it.label) }.thenBy { it.label })
            .groupBy { letterOf(it.label) }
        val letters = grouped.keys.sortedWith(compareBy<Char> { it == '#' }.thenBy { it })
        return letters.map { LetterSection(it, grouped.getValue(it)) }
    }

    fun letterOf(label: String): Char {
        val c = fold(label).firstOrNull { !it.isWhitespace() }?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    fun sortKey(label: String): String = fold(label).lowercase().trim()

    /** Removes accents: "Câmera" → "Camera". */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

    private val COMBINING_MARKS = Regex("\\p{M}+")
}
