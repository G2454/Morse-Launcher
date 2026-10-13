package dev.morse.core.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun parsesNestedStructures() {
        val v = Json.parse("""{"a": [1, 2.5, -3e2, true, false, null], "b": {"c": "d"}}""").asObject()
        val a = v.array("a")
        assertEquals(1L, a[0])
        assertEquals(2.5, a[1])
        assertEquals(-300.0, a[2])
        assertEquals(true, a[3])
        assertEquals(false, a[4])
        assertNull(a[5])
        assertEquals("d", v.obj("b").string("c"))
    }

    @Test
    fun parsesEscapes() {
        val s = Json.parse("\"line\\nbreak \\\"quoted\\\" \\u00e3 tab\\t slash\\/\"")
        assertEquals("line\nbreak \"quoted\" ã tab\t slash/", s)
    }

    @Test
    fun keepsKeyOrder() {
        val v = Json.parse("""{"z":1,"a":2,"m":3}""").asObject()
        assertEquals(listOf("z", "a", "m"), v.keys.toList())
    }

    @Test
    fun roundTrips() {
        val value = linkedMapOf(
            "text" to "São Paulo \"24°\"\n",
            "int" to 42L,
            "double" to 18.5,
            "list" to listOf(1L, "two", null, false),
            "nested" to linkedMapOf("k" to emptyList<Any>()),
        )
        assertEquals(value, Json.parse(Json.stringify(value)))
    }

    @Test
    fun writesWholeDoublesAsIntegers() {
        assertEquals("[24,24.5]", Json.stringify(listOf(24.0, 24.5)))
    }

    @Test
    fun writesControlCharactersEscaped() {
        assertEquals("\"\\u0001\"", Json.stringify("\u0001"))
    }

    @Test
    fun typedAccessorsTolerateWrongTypes() {
        val o = Json.parse("""{"n": "not a number", "s": 3}""").asObject()
        assertNull(o.int("n"))
        assertNull(o.string("s"))
        assertTrue(o.array("missing").isEmpty())
        assertTrue(o.obj("missing").isEmpty())
    }

    @Test
    fun rejectsMalformedInput() {
        assertThrows(Json.JsonException::class.java) { Json.parse("{") }
        assertThrows(Json.JsonException::class.java) { Json.parse("[1,]") }
        assertThrows(Json.JsonException::class.java) { Json.parse("\"open") }
        assertThrows(Json.JsonException::class.java) { Json.parse("{} extra") }
        assertThrows(Json.JsonException::class.java) { Json.parse("tru") }
        assertThrows(Json.JsonException::class.java) { Json.parse("-") }
    }
}
