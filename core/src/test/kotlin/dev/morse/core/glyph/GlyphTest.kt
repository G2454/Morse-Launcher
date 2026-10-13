package dev.morse.core.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphTest {

    @Test
    fun everyGlyphParses() {
        for (glyph in Glyph.entries) {
            val segments = SvgPath.parse(glyph.path)
            assertTrue("${glyph.name} has no segments", segments.isNotEmpty())
            assertTrue("${glyph.name} must start with a move", segments.first() is Segment.MoveTo)
        }
    }

    @Test
    fun everyGlyphStaysInsideTheViewportWithStrokeRoom() {
        // A 1.6 stroke needs 0.8 units of room; keep at least 1 unit from each edge.
        for (glyph in Glyph.entries) {
            val b = SvgPath.bounds(glyph.path)
            assertTrue("${glyph.name} left edge ${b.minX}", b.minX >= 1f)
            assertTrue("${glyph.name} top edge ${b.minY}", b.minY >= 1f)
            assertTrue("${glyph.name} right edge ${b.maxX}", b.maxX <= Glyph.VIEWPORT - 1f)
            assertTrue("${glyph.name} bottom edge ${b.maxY}", b.maxY <= Glyph.VIEWPORT - 1f)
        }
    }

    @Test
    fun glyphsAreVisuallySubstantial() {
        // Catches typos that collapse a glyph to a dot (e.g. a lost command letter).
        for (glyph in Glyph.entries) {
            val b = SvgPath.bounds(glyph.path)
            assertTrue("${glyph.name} is too small: $b", b.width >= 6f || b.height >= 6f)
        }
    }

    @Test
    fun glyphPathsAreUnique() {
        val duplicates = Glyph.entries.groupBy { it.path }.filterValues { it.size > 1 }
        assertTrue("Duplicate paths: ${duplicates.values}", duplicates.isEmpty())
    }

    @Test
    fun lookupByName() {
        assertSame(Glyph.CAMERA, Glyph.byName("CAMERA"))
        assertNull(Glyph.byName("camera"))
    }

    @Test
    fun circleArcBoundsAreExact() {
        val b = SvgPath.bounds("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z")
        assertEquals(3f, b.minX, 0.05f)
        assertEquals(21f, b.maxX, 0.05f)
        assertEquals(3f, b.minY, 0.05f)
        assertEquals(21f, b.maxY, 0.05f)
    }

    @Test
    fun relativeCommandsAndImplicitLineTo() {
        val s = SvgPath.parse("m2 2 3 0 0 3h-3z")
        assertEquals(Segment.MoveTo(2f, 2f), s[0])
        assertEquals(Segment.LineTo(5f, 2f), s[1])
        assertEquals(Segment.LineTo(5f, 5f), s[2])
        assertEquals(Segment.LineTo(2f, 5f), s[3])
        assertEquals(Segment.Close, s[4])
    }

    @Test
    fun compactNumbersAndFlags() {
        val s = SvgPath.parse("M1.5.5L.25-1a1 1 0 011 1")
        assertEquals(Segment.MoveTo(1.5f, 0.5f), s[0])
        assertEquals(Segment.LineTo(0.25f, -1f), s[1])
        val arc = s[2] as Segment.ArcTo
        assertEquals(false, arc.largeArc)
        assertEquals(true, arc.sweep)
        assertEquals(1.25f, arc.x, 0.0001f)
        assertEquals(0f, arc.y, 0.0001f)
    }

    @Test
    fun smoothCurvesReflectTheLastControlPoint() {
        val s = SvgPath.parse("M0 0C1 1 2 1 3 0S5 -1 6 0")
        val smooth = s[2] as Segment.CubicTo
        assertEquals(4f, smooth.x1, 0.0001f)
        assertEquals(-1f, smooth.y1, 0.0001f)
    }

    @Test
    fun scientificNotation() {
        val s = SvgPath.parse("M1e1 2E-1")
        assertEquals(Segment.MoveTo(10f, 0.2f), s[0])
    }

    @Test
    fun invalidPathsAreRejected() {
        assertThrows(SvgPath.ParseException::class.java) { SvgPath.parse("L1 1") }
        assertThrows(SvgPath.ParseException::class.java) { SvgPath.parse("M1") }
        assertThrows(SvgPath.ParseException::class.java) { SvgPath.parse("M0 0X1 1") }
        assertThrows(SvgPath.ParseException::class.java) { SvgPath.parse("M0 0a1 1 0 2 0 1 1") }
        assertThrows(SvgPath.ParseException::class.java) { SvgPath.parse("") }
    }
}
