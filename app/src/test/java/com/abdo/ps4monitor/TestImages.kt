package com.abdo.ps4monitor

import java.util.Random

/** Synthetic cover artwork for tests (no real game art is stored in the repository). Pixels are ARGB, row-major. */
class Img(val w: Int, val h: Int, val px: IntArray)

object TestImages {
    private fun rgb(r: Int, g: Int, b: Int) = ColorMath.rgb(r, g, b)
    fun build(w: Int, h: Int, f: (Int, Int) -> Int) = Img(w, h, IntArray(w * h) { f(it % w, it / w) })

    /** Ivory page, black ink figure, one gold band: pale, nearly colourless, lots of empty space. */
    fun paperLike(w: Int = 128, h: Int = 128) = build(w, h) { x, y ->
        when {
            y < h / 10 -> rgb(0xC9, 0xB2, 0x7A)
            x in (w * 0.35).toInt()..(w * 0.62).toInt() && y > h * 0.3 -> rgb(18, 18, 20)
            else -> rgb(0xF5, 0xF1, 0xE2)
        }
    }

    /** Smooth deep-blue gradient with soft cyan glows: cool, colourful, almost no hard edges. */
    fun mistLike(w: Int = 128, h: Int = 128) = build(w, h) { x, y ->
        val t = y / (h - 1f)
        var r = 18 + 40 * t; var g = 40 + 80 * t; var b = 120 + 70 * t
        val d = Math.hypot((x - w * 0.7), (y - h * 0.35)) / (w * 0.35)
        if (d < 1) { val k = (1 - d).toFloat(); r += 40 * k; g += 110 * k; b += 70 * k }
        rgb(r.toInt(), g.toInt(), b.toInt())
    }

    /** Near-black navy with hard red and violet diagonal slashes: dark, high contrast, many edges. */
    fun dramaLike(w: Int = 128, h: Int = 128) = build(w, h) { x, y ->
        val band = ((x + y) / 9) % 6
        when (band) { 0 -> rgb(235, 45, 75); 3 -> rgb(150, 60, 220); else -> rgb(8, 9, 28) }
    }

    fun flat(c: Int, w: Int = 64, h: Int = 64) = build(w, h) { _, _ -> c }
    fun transparent(w: Int = 64, h: Int = 64) = build(w, h) { _, _ -> 0 }

    /** Random blocks of random colours: covers saturated, pastel, grey, dark and bright pictures. */
    fun random(seed: Long): Img {
        val rnd = Random(seed); val w = 48 + rnd.nextInt(80); val h = 48 + rnd.nextInt(80)
        val kind = rnd.nextInt(4)
        val blocks = 3 + rnd.nextInt(10)
        val cols = IntArray(blocks) {
            when (kind) {
                0 -> ColorMath.fromHsl(rnd.nextFloat() * 360f, rnd.nextFloat(), rnd.nextFloat())
                1 -> ColorMath.fromHsl(rnd.nextFloat() * 360f, 0.5f + rnd.nextFloat() * 0.5f, 0.1f + rnd.nextFloat() * 0.8f)
                2 -> ColorMath.fromHsl(rnd.nextFloat() * 360f, rnd.nextFloat() * 0.12f, rnd.nextFloat())
                else -> ColorMath.fromHsl(rnd.nextFloat() * 360f, rnd.nextFloat(), 0.85f + rnd.nextFloat() * 0.14f)
            }
        }
        val bw = 1 + rnd.nextInt(24)
        return build(w, h) { x, y -> cols[((x / bw) * 7 + (y / bw) * 3) % blocks] }
    }
}
