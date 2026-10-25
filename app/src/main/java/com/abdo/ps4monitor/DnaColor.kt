package com.abdo.ps4monitor

import kotlin.math.max
import kotlin.math.min

/**
 * Colour helpers used by the design system. Pure Kotlin (no android.* / compose classes) so every rule can be unit-tested on a plain JVM.
 * All colours are opaque ARGB ints. [ColorMath] (CoverPalette.kt) provides the base conversions.
 */
object ColorOps {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    fun hsl(h: Float, s: Float, l: Float) = ColorMath.fromHsl(h, s, l)
    fun opaque(c: Int) = c or (0xFF shl 24)

    /** Worst-case contrast of [fg] over several backgrounds. */
    fun worst(fg: Int, bgs: List<Int>): Double {
        var w = Double.MAX_VALUE
        for (b in bgs) w = min(w, ColorMath.contrast(opaque(fg), opaque(b)))
        return w
    }

    /**
     * Returns [fg] unchanged if it already has [min] contrast against every background, otherwise moves its lightness (hue and
     * saturation kept) towards whichever extreme can reach the target, and falls back to pure black / white as the last resort.
     */
    fun ensure(fg: Int, bgs: List<Int>, min: Double): Int {
        val f = opaque(fg)
        if (bgs.isEmpty() || worst(f, bgs) >= min) return f
        val up = worst(WHITE, bgs) >= worst(BLACK, bgs)
        val hsl = ColorMath.toHsl(f)
        var l = hsl[2]
        var c = f
        var n = 0
        while (worst(c, bgs) < min && n++ < 60) {
            l += if (up) 0.02f else -0.02f
            if (l > 1f || l < 0f) break
            c = ColorMath.fromHsl(hsl[0], hsl[1], l)
        }
        if (worst(c, bgs) >= min) return c
        val ext = if (up) WHITE else BLACK
        if (worst(ext, bgs) >= min) return ext
        return if (worst(WHITE, bgs) >= worst(BLACK, bgs)) WHITE else BLACK
    }

    /** A near-black or near-white tinted with [hue] that reads on [bg] (text on a coloured fill). */
    fun readableOn(bg: Int, hue: Float, min: Double = 4.5): Int {
        val dark = ColorMath.fromHsl(hue, 0.25f, 0.07f)
        val light = ColorMath.fromHsl(hue, 0.20f, 0.97f)
        val cd = ColorMath.contrast(dark, opaque(bg)); val cl = ColorMath.contrast(light, opaque(bg))
        val pick = if (cd >= cl) dark else light
        return if (max(cd, cl) >= min) pick else ensure(pick, listOf(bg), min)
    }

    /** Colour [t] of the way from [a] to [b] (0 = a, 1 = b). */
    fun mix(a: Int, b: Int, t: Float) = ColorMath.blend(opaque(a), opaque(b), t.coerceIn(0f, 1f))

    /** Perceptual brightness proxy 0..1 (Rec.601), used for classification, not for WCAG contrast. */
    fun luma(c: Int) = (0.299f * ColorMath.red(c) + 0.587f * ColorMath.green(c) + 0.114f * ColorMath.blue(c)) / 255f

    /** "#RRGGBB" */
    fun toHex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    /** Accepts #RGB, #RRGGBB, RRGGBB (case-insensitive). Anything else is rejected (null). */
    fun parseHex(s: String): Int? {
        var h = s.trim().removePrefix("#")
        if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
        if (h.length != 6 || !h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return (0xFF000000L or h.toLong(16)).toInt()
    }

    /** Circular distance between two hues in degrees (0..180). */
    fun hueDistance(a: Float, b: Float): Float { val d = kotlin.math.abs(a - b) % 360f; return min(d, 360f - d) }
}
