package dev.morse.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private fun c(title: String, keywords: List<String> = emptyList(), boost: Int = 0) =
        SearchEngine.Candidate(title, title, keywords, boost)

    @Test
    fun scoreTiers() {
        assertEquals(1000, SearchEngine.score("camera", "Camera"))
        assertEquals(800, SearchEngine.score("cal", "Calculator"))
        assertEquals(600, SearchEngine.score("maps", "Google Maps"))
        assertEquals(450, SearchEngine.score("gm", "Google Maps"))
        assertEquals(350, SearchEngine.score("lens", "Camera", listOf("Google Lens")))
        assertEquals(200, SearchEngine.score("ndar", "Calendar"))
        assertEquals(60, SearchEngine.score("clc", "Calculator"))
        assertNull(SearchEngine.score("zzz", "Calculator"))
        assertNull(SearchEngine.score("   ", "Calculator"))
    }

    @Test
    fun matchingIgnoresCaseAndAccents() {
        assertEquals(800, SearchEngine.score("CAME", "Câmera"))
        assertEquals(1000, SearchEngine.score("sao paulo", "São  Paulo"))
    }

    @Test
    fun rankOrdersByScoreThenLengthThenName() {
        val hits = SearchEngine.rank("cal", listOf(c("Recall"), c("Calendar"), c("Calculator"), c("Call log")))
        assertEquals(listOf("Calendar", "Call log", "Calculator", "Recall"), hits.map { it.payload })
    }

    @Test
    fun boostReordersWithinATierOnly() {
        val hits = SearchEngine.rank("cal", listOf(c("Calendar"), c("Calculator", boost = 50), c("Recall", boost = 10_000)))
        assertEquals(listOf("Calculator", "Calendar", "Recall"), hits.map { it.payload })
    }

    @Test
    fun rankRespectsLimitAndEmptyQuery() {
        val all = listOf(c("Calendar"), c("Calculator"), c("Camera"))
        assertEquals(2, SearchEngine.rank("ca", all, limit = 2).size)
        assertTrue(SearchEngine.rank("", all).isEmpty())
    }

    @Test
    fun settingsShortcutsAreFindable() {
        val candidates = SettingsShortcuts.all.map { SearchEngine.Candidate(it.title, it, it.keywords) }
        assertEquals("Call settings", SearchEngine.rank("call forwarding", candidates).first().payload.title)
        assertEquals("Wi-Fi", SearchEngine.rank("wifi", candidates).first().payload.title)
        assertEquals("Battery saver", SearchEngine.rank("battery s", candidates).first().payload.title)
    }

    @Test
    fun settingsShortcutActionsLookValid() {
        SettingsShortcuts.all.forEach {
            assertTrue(it.action, it.action.startsWith("android."))
            assertTrue(it.title.isNotBlank())
        }
    }
}
