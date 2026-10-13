package dev.morse.core.apps

import dev.morse.core.glyph.Glyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsTest {

    @Test
    fun appKeyRoundTrips() {
        val k = AppKey("com.example", "com.example.ui.Main\$Inner", 11)
        assertEquals(k, AppKey.parse(k.flatten()))
    }

    @Test
    fun appKeyWithoutSerialDefaultsToMainProfile() {
        assertEquals(AppKey("a.b", "a.b.C", 0), AppKey.parse("a.b/a.b.C"))
    }

    @Test
    fun appKeyRejectsBadInput() {
        assertNull(AppKey.parse(null))
        assertNull(AppKey.parse(""))
        assertNull(AppKey.parse("noslash"))
        assertNull(AppKey.parse("/cls"))
        assertNull(AppKey.parse("pkg/"))
        assertNull(AppKey.parse("pkg/cls#x"))
        assertNull(AppKey.parse("pkg/#3"))
    }

    @Test
    fun roleGlyphWinsOverPackageTable() {
        assertEquals(Glyph.CAMERA, IconResolver.glyphFor("com.android.settings", Role.CAMERA))
        assertEquals(Glyph.SETTINGS, IconResolver.glyphFor("com.android.settings", null))
        assertNull(IconResolver.glyphFor("com.unknown.app", null))
        assertEquals(Glyph.LAYERS, IconResolver.glyphFor("dev.morse.launcher", Role.CAMERA, ownPackage = "dev.morse.launcher"))
    }

    @Test
    fun everyRoleHasAGlyph() {
        Role.entries.forEach { assertTrue("$it has no glyph", IconResolver.roleGlyphs.containsKey(it)) }
    }

    @Test
    fun initials() {
        assertEquals("C", IconResolver.initialFor("camera"))
        assertEquals("9", IconResolver.initialFor("  9GAG"))
        assertEquals("É", IconResolver.initialFor("échecs"))
        assertEquals("#", IconResolver.initialFor("  ✱ "))
    }

    @Test
    fun categorizeFromAndroidCategoryThenGlyph() {
        assertEquals(AppCategory.GAMES, Library.categorize(Library.AndroidCategory.GAME, null))
        assertEquals(AppCategory.MEDIA, Library.categorize(Library.AndroidCategory.NEWS, Glyph.CAMERA))
        assertEquals(AppCategory.CREATIVITY, Library.categorize(Library.AndroidCategory.UNDEFINED, Glyph.CAMERA))
        assertEquals(AppCategory.OTHER, Library.categorize(Library.AndroidCategory.UNDEFINED, null))
    }

    private fun info(name: String, cat: AppCategory = AppCategory.OTHER, installed: Long = 0, system: Boolean = false) =
        AppInfo(AppKey("p.$name", "p.$name.M"), name, cat, installed, system)

    @Test
    fun libraryGroupsInOrder() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val a = info("Alpha", AppCategory.SOCIAL, installed = now - 2 * day)
        val b = info("Beta", AppCategory.SOCIAL, installed = now - 30 * day)
        val c = info("Charlie", AppCategory.UTILITIES, installed = now - day, system = true)
        val groups = Library.groups(listOf(a, b, c), mapOf(b.key to 5, c.key to 9), now)
        assertEquals(listOf("suggestions", "recent", "social", "utilities"), groups.map { it.id })
        assertEquals(listOf(c, b), groups[0].apps)
        // System apps never count as "recently added".
        assertEquals(listOf(a), groups[1].apps)
        assertEquals(listOf(a, b), groups[2].apps)
    }

    @Test
    fun libraryIsEmptyWithoutApps() {
        assertTrue(Library.groups(emptyList(), emptyMap(), 0).isEmpty())
    }

    @Test
    fun alphabeticalFoldsAccentsAndGroupsSymbols() {
        val apps = listOf(info("Ônibus"), info("camera"), info("Calendar"), info("7-Zip"), info("Árvore"))
        val sections = Library.alphabetical(apps)
        assertEquals(listOf('A', 'C', 'O', '#'), sections.map { it.letter })
        assertEquals(listOf("Calendar", "camera"), sections[1].apps.map { it.label })
    }

    @Test
    fun usageCountsAndCodec() {
        val a = AppKey("a", "a.M")
        var counts = emptyMap<AppKey, Int>()
        repeat(3) { counts = Usage.increment(counts, a) }
        assertEquals(3, counts[a])
        assertEquals(counts, Usage.decode(Usage.encode(counts)))
        assertTrue(Usage.decode("garbage").isEmpty())
        assertTrue(Usage.decode("""{"bad":3,"a/a.M#0":-1}""").isEmpty())
    }

    @Test
    fun usageIsBounded() {
        var counts = (0 until Usage.MAX_TRACKED).associate { AppKey("p$it", "c") to it + 2 }
        counts = Usage.increment(counts, AppKey("new", "c"))
        assertEquals(Usage.MAX_TRACKED, counts.size)
        assertNull(counts[AppKey("new", "c")]) // the newest has the lowest count and is dropped
    }
}
