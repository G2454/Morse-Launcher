package dev.morse.core.json

/**
 * A tiny, dependency-free JSON reader/writer.
 *
 * Values map to Kotlin types as follows:
 * object → [Map] (insertion ordered), array → [List], string → [String],
 * number → [Long] when integral, otherwise [Double], true/false → [Boolean],
 * null → `null`.
 *
 * It is deliberately small: the launcher only stores its own files and reads
 * one weather API, so a full serialization framework would be dead weight.
 */
object Json {

    class JsonException(message: String) : IllegalArgumentException(message)

    fun parse(text: String): Any? {
        val r = Reader(text)
        r.ws()
        val v = r.value()
        r.ws()
        if (!r.atEnd()) throw JsonException("Unexpected trailing content at ${r.pos}")
        return v
    }

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Float -> writeDouble(sb, v.toDouble())
            is Double -> writeDouble(sb, v)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    write(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, item)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, v.asList())
            is Enum<*> -> quote(sb, v.name)
            else -> throw JsonException("Cannot serialize ${v::class.simpleName}")
        }
    }

    private fun writeDouble(sb: StringBuilder, d: Double) {
        if (d.isNaN() || d.isInfinite()) {
            sb.append("null")
        } else if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) {
            sb.append(d.toLong())
        } else {
            sb.append(d.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }

    private class Reader(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun ws() {
            while (pos < s.length && s[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        fun value(): Any? {
            if (atEnd()) throw JsonException("Unexpected end of input")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw JsonException("Unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Expected $word at $pos")
            pos += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            pos++
            ws()
            if (peek() == '}') { pos++; return m }
            while (true) {
                ws()
                if (peek() != '"') throw JsonException("Expected a key at $pos")
                val k = str()
                ws()
                expect(':')
                ws()
                m[k] = value()
                ws()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return m }
                    else -> throw JsonException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            pos++
            ws()
            if (peek() == ']') { pos++; return l }
            while (true) {
                ws()
                l += value()
                ws()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return l }
                    else -> throw JsonException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && s[pos].isDigit()) pos++
            var integral = true
            if (!atEnd() && s[pos] == '.') {
                integral = false
                pos++
                while (!atEnd() && s[pos].isDigit()) pos++
            }
            if (!atEnd() && (s[pos] == 'e' || s[pos] == 'E')) {
                integral = false
                pos++
                if (!atEnd() && (s[pos] == '+' || s[pos] == '-')) pos++
                while (!atEnd() && s[pos].isDigit()) pos++
            }
            val text = s.substring(start, pos)
            if (text == "-" || text.isEmpty()) throw JsonException("Bad number at $start")
            return if (integral) text.toLongOrNull() ?: text.toDouble() else text.toDouble()
        }

        private fun peek(): Char = if (atEnd()) '\u0000' else s[pos]

        private fun expect(c: Char) {
            if (peek() != c) throw JsonException("Expected '$c' at $pos")
            pos++
        }
    }
}

/* ---- Typed accessors used by the codecs ------------------------------------------------- */

@Suppress("UNCHECKED_CAST")
fun Any?.asObject(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

fun Any?.asArray(): List<Any?> = this as? List<Any?> ?: emptyList()

fun Map<String, Any?>.string(key: String): String? = this[key] as? String

fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean

fun Map<String, Any?>.obj(key: String): Map<String, Any?> = this[key].asObject()

fun Map<String, Any?>.array(key: String): List<Any?> = this[key].asArray()
