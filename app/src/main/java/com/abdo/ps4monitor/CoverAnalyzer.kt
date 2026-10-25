package com.abdo.ps4monitor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What an artwork looks like, as numbers. Measured on a box-filtered grid of up to [CoverAnalyzer.GRID]² cells, so structure
 * (edges, regions, negative space) survives, not just an average colour. Pure Kotlin: no android.* classes.
 *
 * All ratios are 0..1 unless noted. Colours are opaque ARGB ints.
 */
class CoverFeatures(
    val usable: Boolean, val confidence: Float, val gridW: Int, val gridH: Int,
    val dominant: Int, val secondary: Int?, val accent: Int, val lightColor: Int, val darkColor: Int,
    val meanLuma: Float, val lumaP10: Float, val lumaP50: Float, val lumaP90: Float,
    val meanSaturation: Float, val colorfulness: Float,
    /** -1 cool .. +1 warm, over the colourful pixels only */ val warmth: Float,
    /** 0 = one hue family .. 1 = hues all around the wheel */ val hueSpread: Float,
    /** share of cells that sit on a clear edge (visual busyness) */ val edgeDensity: Float,
    /** share of cells with almost no local change */ val flatness: Float,
    /** share of cells that are both flat and nearly colourless (empty paper / sky / black) */ val negativeSpace: Float,
    val darkFraction: Float, val lightFraction: Float,
    /** 3x3 regions, row-major: mean luma and colourful share */ val regionLuma: FloatArray, val regionColorfulness: FloatArray,
    /** visual weight centre, -1..1 (x: left..right, y: top..bottom) */ val balanceX: Float, val balanceY: Float,
    /** hue (degrees) of [dominant] */ val dominantHue: Float,
    val fingerprint: String,
) {
    val spread: Float get() = lumaP90 - lumaP10
    val darkNative: Boolean get() = meanLuma < 0.42f

    companion object {
        /** Neutral, low-confidence result for missing / empty / broken artwork. */
        val UNUSABLE = CoverFeatures(false, 0f, 0, 0, 0xFF3F51B5.toInt(), null, 0xFF3F51B5.toInt(), 0xFFF2F2F2.toInt(), 0xFF101114.toInt(),
            0.3f, 0.1f, 0.3f, 0.6f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 0.5f, 0.2f, FloatArray(9) { 0.3f }, FloatArray(9), 0f, 0f, 230f, "none")
    }
}

object CoverAnalyzer {
    const val GRID = 96
    private const val HUE_BINS = 24
    private const val LUMA_BINS = 64

    /**
     * [px]: ARGB pixels, row-major, [w]x[h]. Never throws: wrong sizes, fully transparent or featureless images give [CoverFeatures.UNUSABLE]
     * or a low-confidence result, and the caller falls back to a neutral identity.
     */
    fun analyze(px: IntArray, w: Int, h: Int): CoverFeatures {
        if (w <= 0 || h <= 0 || px.size.toLong() < w.toLong() * h) return CoverFeatures.UNUSABLE
        val gw = min(w, GRID); val gh = min(h, GRID)
        val cells = gw * gh
        val sr = FloatArray(cells); val sg = FloatArray(cells); val sb = FloatArray(cells); val cnt = IntArray(cells)
        for (y in 0 until h) {
            val gy = (y.toLong() * gh / h).toInt(); val row = y * w
            for (x in 0 until w) {
                val p = px[row + x]
                if (ColorMath.alpha(p) < 128) continue
                val i = gy * gw + (x.toLong() * gw / w).toInt()
                sr[i] += ColorMath.red(p).toFloat(); sg[i] += ColorMath.green(p).toFloat(); sb[i] += ColorMath.blue(p).toFloat(); cnt[i]++
            }
        }
        val ok = BooleanArray(cells); val rgb = IntArray(cells); val pl = FloatArray(cells)
        val hue = FloatArray(cells); val sat = FloatArray(cells); val value = FloatArray(cells)
        var n = 0
        for (i in 0 until cells) if (cnt[i] > 0) {
            ok[i] = true; n++
            val r = sr[i] / cnt[i]; val g = sg[i] / cnt[i]; val b = sb[i] / cnt[i]
            rgb[i] = ColorMath.rgb((r + 0.5f).toInt(), (g + 0.5f).toInt(), (b + 0.5f).toInt())
            pl[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val mx = max(r, max(g, b)) / 255f; val mn = min(r, min(g, b)) / 255f; val d = mx - mn
            value[i] = mx; sat[i] = if (mx <= 0f) 0f else d / mx
            hue[i] = if (d <= 0f) 0f else {
                val rr = r / 255f; val gg = g / 255f; val bb = b / 255f
                val hh = (when { mx == rr -> ((gg - bb) / d) % 6f; mx == gg -> (bb - rr) / d + 2f; else -> (rr - gg) / d + 4f }) * 60f
                if (hh < 0f) hh + 360f else hh
            }
        }
        if (n < 16 || n < cells / 25) return CoverFeatures.UNUSABLE

        // ---- luma distribution
        val hist = IntArray(LUMA_BINS); var lumaSum = 0.0; var satSum = 0.0
        for (i in 0 until cells) if (ok[i]) { hist[(pl[i] * (LUMA_BINS - 1)).toInt().coerceIn(0, LUMA_BINS - 1)]++; lumaSum += pl[i]; satSum += sat[i] }
        fun pct(q: Float): Float { var acc = 0; val target = q * n; for (b in 0 until LUMA_BINS) { acc += hist[b]; if (acc >= target) return b / (LUMA_BINS - 1f) }; return 1f }
        val p10 = pct(0.10f); val p50 = pct(0.50f); val p90 = pct(0.90f); val p15 = pct(0.15f); val p85 = pct(0.85f)
        val meanLuma = (lumaSum / n).toFloat()
        var dark = 0; var light = 0
        for (i in 0 until cells) if (ok[i]) { if (pl[i] < 0.25f) dark++; if (pl[i] > 0.75f) light++ }

        // ---- colour: hue histogram weighted by saturation*value, colourful cells only
        val hw = DoubleArray(HUE_BINS); val hc = IntArray(HUE_BINS)
        var colorful = 0; var vx = 0.0; var vy = 0.0; var wSum = 0.0; var warmSum = 0.0
        for (i in 0 until cells) if (ok[i] && sat[i] >= 0.20f && value[i] >= 0.18f) {
            colorful++
            val w8 = (sat[i] * value[i]).toDouble()
            val hb = (hue[i] / (360f / HUE_BINS)).toInt().coerceIn(0, HUE_BINS - 1)
            hw[hb] += w8; hc[hb]++
            val rad = Math.toRadians(hue[i].toDouble())
            vx += cos(rad) * w8; vy += sin(rad) * w8; wSum += w8
            warmSum += cos(Math.toRadians((hue[i] - 30f).toDouble())) * w8
        }
        val colorfulness = colorful.toFloat() / n
        val dominant: Int; var secondary: Int? = null; val accent: Int; val domHue: Float
        if (colorful == 0 || wSum <= 0.0) {
            val m = meanColor(rgb, ok, null)
            dominant = m; accent = m; domHue = ColorMath.toHsl(m)[0]
        } else {
            fun smooth(i: Int) = hw[(i + HUE_BINS - 1) % HUE_BINS] * 0.5 + hw[i] + hw[(i + 1) % HUE_BINS] * 0.5
            var peak = 0; for (i in 1 until HUE_BINS) if (smooth(i) > smooth(peak)) peak = i
            val peakHue = (peak + 0.5f) * (360f / HUE_BINS)
            fun colourNear(centre: Float): Int {
                var r = 0.0; var g = 0.0; var b = 0.0; var ww = 0.0
                for (i in 0 until cells) if (ok[i] && sat[i] >= 0.20f && value[i] >= 0.18f && ColorOps.hueDistance(hue[i], centre) <= 22.5f) {
                    val q = (sat[i] * value[i]).toDouble(); r += ColorMath.red(rgb[i]) * q; g += ColorMath.green(rgb[i]) * q; b += ColorMath.blue(rgb[i]) * q; ww += q
                }
                return if (ww <= 0.0) rgb[0] else ColorMath.rgb((r / ww + 0.5).toInt(), (g / ww + 0.5).toInt(), (b / ww + 0.5).toInt())
            }
            dominant = colourNear(peakHue); domHue = ColorMath.toHsl(dominant)[0]
            var second = -1
            for (i in 0 until HUE_BINS) {
                if (smooth(i) < smooth(peak) * 0.30) continue
                if (ColorOps.hueDistance((i + 0.5f) * (360f / HUE_BINS), peakHue) < 50f) continue
                // among the candidate hues prefer the one that is both large and vivid (a muddy dark slate must not win over a small bright violet)
                fun vivid(b: Int) = hw[b] / max(hc[b], 1)
                if (second < 0 || smooth(i) * vivid(i) > smooth(second) * vivid(second)) second = i
            }
            if (second >= 0) secondary = colourNear((second + 0.5f) * (360f / HUE_BINS))
            // accent: the most vivid small area (top few percent by saturation * (0.3 + value))
            val idx = (0 until cells).filter { ok[it] && sat[it] >= 0.20f && value[it] >= 0.18f }.sortedByDescending { sat[it] * (0.3f + value[it]) }
            val k = max(4, idx.size * 3 / 100).coerceAtMost(idx.size)
            accent = meanColor(rgb, ok, idx.take(k))
        }
        val hueSpread = if (wSum <= 0.0) 0f else (1.0 - sqrt(vx * vx + vy * vy) / wSum).toFloat().coerceIn(0f, 1f)
        val warmth = if (wSum <= 0.0) 0f else (warmSum / wSum).toFloat().coerceIn(-1f, 1f)

        // ---- light / dark anchor colours
        val lc = (0 until cells).filter { ok[it] && pl[it] >= p85 }; val dc = (0 until cells).filter { ok[it] && pl[it] <= p15 }
        val lightColor = if (lc.isEmpty()) 0xFFF2F2F2.toInt() else meanColor(rgb, ok, lc)
        val darkColor = if (dc.isEmpty()) 0xFF101114.toInt() else meanColor(rgb, ok, dc)

        // ---- structure: Sobel edges, flatness, negative space
        var edge = 0; var flat = 0; var neg = 0; var inner = 0
        if (gw >= 3 && gh >= 3) for (y in 1 until gh - 1) for (x in 1 until gw - 1) {
            val c = y * gw + x
            val nb = intArrayOf(c - gw - 1, c - gw, c - gw + 1, c - 1, c + 1, c + gw - 1, c + gw, c + gw + 1)
            if (!ok[c] || nb.any { !ok[it] }) continue
            inner++
            val gx = (pl[nb[2]] + 2 * pl[nb[4]] + pl[nb[7]]) - (pl[nb[0]] + 2 * pl[nb[3]] + pl[nb[5]])
            val gy = (pl[nb[5]] + 2 * pl[nb[6]] + pl[nb[7]]) - (pl[nb[0]] + 2 * pl[nb[1]] + pl[nb[2]])
            val mag = sqrt(gx * gx + gy * gy) / 4f
            if (mag > 0.10f) edge++
            if (mag < 0.03f) { flat++; if (sat[c] < 0.18f) neg++ }
        }
        val edgeDensity = if (inner == 0) 0f else edge.toFloat() / inner
        val flatness = if (inner == 0) 0f else flat.toFloat() / inner
        val negativeSpace = if (inner == 0) 0f else neg.toFloat() / inner

        // ---- 3x3 regions and visual balance
        val rl = FloatArray(9); val rc = FloatArray(9); val rn = IntArray(9)
        var bx = 0.0; var by = 0.0; var bw = 0.0
        for (y in 0 until gh) for (x in 0 until gw) {
            val i = y * gw + x; if (!ok[i]) continue
            val reg = (y * 3 / gh) * 3 + (x * 3 / gw)
            rl[reg] += pl[i]; rn[reg]++; if (sat[i] >= 0.20f && value[i] >= 0.18f) rc[reg] += 1f
            val wgt = (abs(pl[i] - p50) + 0.5f * sat[i]).toDouble()
            bx += wgt * ((x + 0.5) / gw * 2 - 1); by += wgt * ((y + 0.5) / gh * 2 - 1); bw += wgt
        }
        for (r in 0 until 9) if (rn[r] > 0) { rl[r] /= rn[r]; rc[r] /= rn[r] } else { rl[r] = meanLuma; rc[r] = 0f }

        // ---- confidence
        val sizeScore = (min(w, h) / 64f).coerceIn(0f, 1f)
        val opaqueScore = (n.toFloat() / cells / 0.8f).coerceIn(0f, 1f)
        val info = max(((p90 - p10) / 0.35f).coerceIn(0f, 1f), (colorfulness / 0.15f).coerceIn(0f, 1f))
        val confidence = (0.3f * sizeScore + 0.3f * opaqueScore + 0.4f * info).coerceIn(0f, 1f)

        return CoverFeatures(true, confidence, gw, gh, dominant, secondary, accent, lightColor, darkColor, meanLuma, p10, p50, p90,
            (satSum / n).toFloat(), colorfulness, warmth, hueSpread, edgeDensity, flatness, negativeSpace, dark.toFloat() / n, light.toFloat() / n, rl, rc,
            if (bw > 0) (bx / bw).toFloat().coerceIn(-1f, 1f) else 0f, if (bw > 0) (by / bw).toFloat().coerceIn(-1f, 1f) else 0f,
            domHue, CoverFingerprint.of(rgb, ok, gw, gh))
    }

    private fun meanColor(rgb: IntArray, ok: BooleanArray, only: List<Int>?): Int {
        var r = 0L; var g = 0L; var b = 0L; var c = 0
        if (only == null) { for (i in rgb.indices) if (ok[i]) { r += ColorMath.red(rgb[i]); g += ColorMath.green(rgb[i]); b += ColorMath.blue(rgb[i]); c++ } }
        else for (i in only) { r += ColorMath.red(rgb[i]); g += ColorMath.green(rgb[i]); b += ColorMath.blue(rgb[i]); c++ }
        return if (c == 0) 0xFF808080.toInt() else ColorMath.rgb((r / c).toInt(), (g / c).toInt(), (b / c).toInt())
    }
}

/**
 * A stable fingerprint of an artwork: a 16x16 box-filtered, 4-bit-per-channel hash (FNV-1a 64). The same picture gives the same value
 * at any decode size; a changed cover gives a different one, so cached identities are never reused for the wrong picture.
 */
object CoverFingerprint {
    private const val SIDE = 16
    /** From the analyzer's grid (cells with ok=false are skipped). */
    fun of(rgb: IntArray, ok: BooleanArray, gw: Int, gh: Int): String {
        val sw = min(SIDE, gw); val sh = min(SIDE, gh)
        var hash = 0xcbf29ce484222325UL.toLong()   // FNV-1a offset basis
        for (by in 0 until sh) for (bx in 0 until sw) {
            var r = 0; var g = 0; var b = 0; var c = 0
            val y0 = by * gh / sh; val y1 = max(y0 + 1, (by + 1) * gh / sh); val x0 = bx * gw / sw; val x1 = max(x0 + 1, (bx + 1) * gw / sw)
            for (y in y0 until y1) for (x in x0 until x1) { val i = y * gw + x; if (ok[i]) { r += ColorMath.red(rgb[i]); g += ColorMath.green(rgb[i]); b += ColorMath.blue(rgb[i]); c++ } }
            val q = if (c == 0) intArrayOf(0, 0, 0, 0) else intArrayOf(r / c shr 4, g / c shr 4, b / c shr 4, 1)
            for (v in q) { hash = hash xor v.toLong(); hash *= 0x100000001b3L }
        }
        return java.lang.Long.toHexString(hash).padStart(16, '0')
    }
}
