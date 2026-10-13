package dev.morse.core.glyph

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Axis-aligned bounds of a path, in path units. */
data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

/** One absolute drawing segment of a parsed path. */
sealed interface Segment {
    data class MoveTo(val x: Float, val y: Float) : Segment
    data class LineTo(val x: Float, val y: Float) : Segment
    data class CubicTo(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float,
    ) : Segment
    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : Segment
    data class ArcTo(
        val fromX: Float, val fromY: Float,
        val rx: Float, val ry: Float, val rotation: Float,
        val largeArc: Boolean, val sweep: Boolean,
        val x: Float, val y: Float,
    ) : Segment
    data object Close : Segment
}

/**
 * A small, strict SVG path-data parser. It is used to validate the glyph set
 * (every glyph must parse and stay inside its 24 × 24 viewport) and by the
 * app to measure glyphs. It supports the full path grammar: M L H V C S Q T A Z,
 * absolute and relative, implicit repeats and compact number/flag syntax.
 */
object SvgPath {

    class ParseException(message: String) : IllegalArgumentException(message)

    fun parse(data: String): List<Segment> = Parser(data).run()

    fun bounds(data: String): Bounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        fun add(x: Float, y: Float) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        var cx = 0f
        var cy = 0f
        for (s in parse(data)) {
            when (s) {
                is Segment.MoveTo -> { add(s.x, s.y); cx = s.x; cy = s.y }
                is Segment.LineTo -> { add(s.x, s.y); cx = s.x; cy = s.y }
                is Segment.CubicTo -> {
                    for (i in 1..SAMPLES) {
                        val t = i / SAMPLES.toFloat()
                        val mt = 1 - t
                        val x = mt * mt * mt * cx + 3 * mt * mt * t * s.x1 + 3 * mt * t * t * s.x2 + t * t * t * s.x
                        val y = mt * mt * mt * cy + 3 * mt * mt * t * s.y1 + 3 * mt * t * t * s.y2 + t * t * t * s.y
                        add(x, y)
                    }
                    cx = s.x; cy = s.y
                }
                is Segment.QuadTo -> {
                    for (i in 1..SAMPLES) {
                        val t = i / SAMPLES.toFloat()
                        val mt = 1 - t
                        add(
                            mt * mt * cx + 2 * mt * t * s.x1 + t * t * s.x,
                            mt * mt * cy + 2 * mt * t * s.y1 + t * t * s.y,
                        )
                    }
                    cx = s.x; cy = s.y
                }
                is Segment.ArcTo -> {
                    arcPoints(s).forEach { (x, y) -> add(x, y) }
                    cx = s.x; cy = s.y
                }
                Segment.Close -> Unit
            }
        }
        if (minX == Float.POSITIVE_INFINITY) throw ParseException("Path has no points")
        return Bounds(minX, minY, maxX, maxY)
    }

    /** Samples points along an elliptical arc (SVG spec, appendix F.6.5). */
    internal fun arcPoints(a: Segment.ArcTo): List<Pair<Float, Float>> {
        val x1 = a.fromX.toDouble()
        val y1 = a.fromY.toDouble()
        val x2 = a.x.toDouble()
        val y2 = a.y.toDouble()
        var rx = abs(a.rx.toDouble())
        var ry = abs(a.ry.toDouble())
        if (rx == 0.0 || ry == 0.0 || (x1 == x2 && y1 == y2)) return listOf(a.x to a.y)

        val phi = a.rotation * PI / 180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (x1 - x2) / 2
        val dy = (y1 - y2) / 2
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy

        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) {
            val s = sqrt(lambda)
            rx *= s
            ry *= s
        }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var coef = if (den == 0.0) 0.0 else sqrt(maxOf(0.0, num / den))
        if (a.largeArc == a.sweep) coef = -coef
        val cxp = coef * (rx * y1p / ry)
        val cyp = coef * (-ry * x1p / rx)
        val centerX = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2
        val centerY = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2

        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var ang = acos((dot / len).coerceIn(-1.0, 1.0))
            if (ux * vy - uy * vx < 0) ang = -ang
            return ang
        }

        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!a.sweep && delta > 0) delta -= 2 * PI
        if (a.sweep && delta < 0) delta += 2 * PI

        return (1..SAMPLES).map { i ->
            val t = theta1 + delta * i / SAMPLES
            val ex = rx * cos(t)
            val ey = ry * sin(t)
            (cosPhi * ex - sinPhi * ey + centerX).toFloat() to (sinPhi * ex + cosPhi * ey + centerY).toFloat()
        }
    }

    private const val SAMPLES = 24

    private class Parser(private val s: String) {
        private var i = 0
        private val out = ArrayList<Segment>()
        private var cx = 0f
        private var cy = 0f
        private var startX = 0f
        private var startY = 0f
        private var lastCtrlX = 0f
        private var lastCtrlY = 0f
        private var lastCmd = ' '

        fun run(): List<Segment> {
            var cmd = ' '
            skipSeparators()
            if (i < s.length && s[i] != 'M' && s[i] != 'm') throw ParseException("Path must start with M")
            while (true) {
                skipSeparators()
                if (i >= s.length) break
                val c = s[i]
                if (c.isLetter()) {
                    if (c !in COMMANDS) throw ParseException("Unknown command '$c' at $i")
                    cmd = c
                    i++
                } else if (cmd == ' ' || cmd == 'Z' || cmd == 'z') {
                    throw ParseException("Unexpected '$c' at $i")
                }
                execute(cmd)
                // A move-to followed by bare coordinates continues as line-to.
                if (cmd == 'M') cmd = 'L'
                if (cmd == 'm') cmd = 'l'
            }
            if (out.isEmpty()) throw ParseException("Empty path")
            return out
        }

        private fun execute(cmd: Char) {
            val rel = cmd.isLowerCase()
            val ox = if (rel) cx else 0f
            val oy = if (rel) cy else 0f
            when (cmd.uppercaseChar()) {
                'M' -> {
                    val x = num() + ox
                    val y = num() + oy
                    out += Segment.MoveTo(x, y)
                    cx = x; cy = y; startX = x; startY = y
                }
                'L' -> {
                    val x = num() + ox
                    val y = num() + oy
                    out += Segment.LineTo(x, y)
                    cx = x; cy = y
                }
                'H' -> {
                    val x = num() + ox
                    out += Segment.LineTo(x, cy)
                    cx = x
                }
                'V' -> {
                    val y = num() + oy
                    out += Segment.LineTo(cx, y)
                    cy = y
                }
                'C' -> {
                    val x1 = num() + ox; val y1 = num() + oy
                    val x2 = num() + ox; val y2 = num() + oy
                    val x = num() + ox; val y = num() + oy
                    out += Segment.CubicTo(x1, y1, x2, y2, x, y)
                    lastCtrlX = x2; lastCtrlY = y2
                    cx = x; cy = y
                }
                'S' -> {
                    val reflect = lastCmd.uppercaseChar() == 'C' || lastCmd.uppercaseChar() == 'S'
                    val x1 = if (reflect) 2 * cx - lastCtrlX else cx
                    val y1 = if (reflect) 2 * cy - lastCtrlY else cy
                    val x2 = num() + ox; val y2 = num() + oy
                    val x = num() + ox; val y = num() + oy
                    out += Segment.CubicTo(x1, y1, x2, y2, x, y)
                    lastCtrlX = x2; lastCtrlY = y2
                    cx = x; cy = y
                }
                'Q' -> {
                    val x1 = num() + ox; val y1 = num() + oy
                    val x = num() + ox; val y = num() + oy
                    out += Segment.QuadTo(x1, y1, x, y)
                    lastCtrlX = x1; lastCtrlY = y1
                    cx = x; cy = y
                }
                'T' -> {
                    val reflect = lastCmd.uppercaseChar() == 'Q' || lastCmd.uppercaseChar() == 'T'
                    val x1 = if (reflect) 2 * cx - lastCtrlX else cx
                    val y1 = if (reflect) 2 * cy - lastCtrlY else cy
                    val x = num() + ox; val y = num() + oy
                    out += Segment.QuadTo(x1, y1, x, y)
                    lastCtrlX = x1; lastCtrlY = y1
                    cx = x; cy = y
                }
                'A' -> {
                    val rx = num(); val ry = num(); val rot = num()
                    val large = flag(); val sweep = flag()
                    val x = num() + ox; val y = num() + oy
                    out += Segment.ArcTo(cx, cy, rx, ry, rot, large, sweep, x, y)
                    cx = x; cy = y
                }
                'Z' -> {
                    out += Segment.Close
                    cx = startX; cy = startY
                }
            }
            lastCmd = cmd
        }

        private fun skipSeparators() {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
        }

        private fun num(): Float {
            skipSeparators()
            val start = i
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            var digits = 0
            while (i < s.length && s[i].isDigit()) { i++; digits++ }
            if (i < s.length && s[i] == '.') {
                i++
                while (i < s.length && s[i].isDigit()) { i++; digits++ }
            }
            if (digits == 0) throw ParseException("Expected a number at $start in \"$s\"")
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                val save = i
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i < s.length && s[i].isDigit()) {
                    while (i < s.length && s[i].isDigit()) i++
                } else {
                    i = save
                }
            }
            return s.substring(start, i).toFloat()
        }

        private fun flag(): Boolean {
            skipSeparators()
            if (i >= s.length) throw ParseException("Expected an arc flag at end of path")
            return when (s[i++]) {
                '0' -> false
                '1' -> true
                else -> throw ParseException("Arc flag must be 0 or 1 at ${i - 1}")
            }
        }

        companion object {
            const val COMMANDS = "MmLlHhVvCcSsQqTtAaZz"
        }
    }
}
