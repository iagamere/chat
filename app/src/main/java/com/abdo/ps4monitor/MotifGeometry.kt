package com.abdo.ps4monitor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Deterministic procedural geometry for the decorative motifs. Pure Kotlin: the same seed and size always give the same shapes,
 * so a game's ornaments never change between launches, and the generators can be unit-tested. Units are the caller's (px or dp);
 * every coordinate stays inside the given box.
 */
class Rng(seed: Long) {
    private var s = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed
    fun nextLong(): Long { s = s xor (s shl 13); s = s xor (s ushr 7); s = s xor (s shl 17); return s }   // xorshift64
    /** 0..1 */ fun nextFloat(): Float = ((nextLong() ushr 40) and 0xFFFFFF).toFloat() / 0x1000000
    fun range(lo: Float, hi: Float) = lo + (hi - lo) * nextFloat()
}

object MotifGeometry {
    /** Seigaiha-style scales: rows of overlapping concentric half circles. Returns triples (cx, cy, r), top-half arcs, within a band of height [band]. */
    fun waves(w: Float, h: Float, cell: Float, band: Float): FloatArray {
        if (w <= 0f || h <= 0f || cell < 4f) return FloatArray(0)
        val out = ArrayList<Float>()
        val rows = (min(band, h) / (cell / 2f)).toInt() + 1
        val cols = (w / cell).toInt() + 2
        for (r in 0 until rows) for (c in 0 until cols) {
            val cx = c * cell + (if (r % 2 == 0) 0f else cell / 2f) - cell / 4f
            val cy = r * cell / 2f + cell / 2f
            if (cy - cell / 2f > min(band, h)) continue
            for (k in 0 until 3) { val rad = cell * (0.50f - 0.12f * k); out.add(cx); out.add(cy); out.add(rad) }
        }
        return out.toFloatArray()
    }

    /** Light shafts: quadruples (x0, x1, x2, x3) of a parallelogram spanning y = 0 (x0..x1) to y = h (x2..x3). [angle] degrees from the horizontal. */
    fun rays(w: Float, h: Float, angle: Float, count: Int, seed: Long): FloatArray {
        if (w <= 0f || h <= 0f || count <= 0) return FloatArray(0)
        val rng = Rng(seed); val out = FloatArray(count * 4)
        val t = tan(Math.toRadians(angle.coerceIn(20.0f, 160.0f).toDouble())).toFloat()
        val dx = if (kotlin.math.abs(t) < 0.05f) 0f else -h / t
        for (i in 0 until count) {
            val x0 = rng.range(-0.1f, 1.0f) * w; val rw = rng.range(0.04f, 0.14f) * w
            out[i * 4] = x0; out[i * 4 + 1] = x0 + rw; out[i * 4 + 2] = x0 + rw + dx; out[i * 4 + 3] = x0 + dx
        }
        return out
    }

    /** Diagonal bars for a corner: quadruples (x, y, length, thickness); the caller rotates by 60-65 degrees. Positions are inside [0,w]x[0,h]. */
    fun slashes(w: Float, h: Float, count: Int, seed: Long): FloatArray {
        if (w <= 0f || h <= 0f || count <= 0) return FloatArray(0)
        val rng = Rng(seed); val out = FloatArray(count * 4)
        for (i in 0 until count) {
            val len = rng.range(0.25f, 0.6f) * min(w, h)
            out[i * 4] = rng.range(0f, 1f) * (w - len * 0.5f).coerceAtLeast(1f); out[i * 4 + 1] = rng.range(0f, 1f) * (h - len * 0.5f).coerceAtLeast(1f)
            out[i * 4 + 2] = len; out[i * 4 + 3] = rng.range(2f, 7f)
        }
        return out
    }

    /** A jittered grid of points (x, y, radius). */
    fun dots(w: Float, h: Float, spacing: Float, seed: Long): FloatArray {
        if (w <= 0f || h <= 0f || spacing < 4f) return FloatArray(0)
        val rng = Rng(seed); val out = ArrayList<Float>()
        val cols = (w / spacing).toInt() + 1; val rows = (h / spacing).toInt() + 1
        for (r in 0 until rows) for (c in 0 until cols) {
            out.add((c * spacing + rng.range(-0.15f, 0.15f) * spacing).coerceIn(0f, w)); out.add((r * spacing + rng.range(-0.15f, 0.15f) * spacing).coerceIn(0f, h)); out.add(rng.range(0.8f, 1.8f))
        }
        return out.toFloatArray()
    }

    /** Vine along the bottom edge: cubic segments (x0,y0, c1x,c1y, c2x,c2y, x1,y1) then leaves as (x, y, r). Returns (curves, leaves). */
    fun vines(w: Float, h: Float, seed: Long): Pair<FloatArray, FloatArray> {
        if (w <= 0f || h <= 0f) return FloatArray(0) to FloatArray(0)
        val rng = Rng(seed); val segs = max(2, (w / 70f).toInt())
        val curves = FloatArray(segs * 8); val leaves = ArrayList<Float>()
        var x = 0f; var y = h * rng.range(0.82f, 0.95f)
        for (i in 0 until segs) {
            val nx = if (i == segs - 1) w else (i + 1) * w / segs; val ny = (h * rng.range(0.80f, 0.97f)).coerceIn(0f, h)
            val b = i * 8
            curves[b] = x; curves[b + 1] = y; curves[b + 2] = x + (nx - x) * 0.35f; curves[b + 3] = (y - h * rng.range(0.05f, 0.14f)).coerceIn(0f, h)
            curves[b + 4] = x + (nx - x) * 0.7f; curves[b + 5] = (ny + h * rng.range(0.03f, 0.1f)).coerceIn(0f, h); curves[b + 6] = nx; curves[b + 7] = ny
            leaves.add(curves[b + 2]); leaves.add(curves[b + 3]); leaves.add(rng.range(1.5f, 3.5f))
            x = nx; y = ny
        }
        return curves to leaves.toFloatArray()
    }

    /** Short strokes for paper fibres / grain: (x, y, length, angle radians). */
    fun specks(w: Float, h: Float, count: Int, maxLen: Float, seed: Long): FloatArray {
        if (w <= 0f || h <= 0f || count <= 0) return FloatArray(0)
        val rng = Rng(seed); val out = FloatArray(count * 4)
        for (i in 0 until count) { out[i * 4] = rng.range(0f, w); out[i * 4 + 1] = rng.range(0f, h); out[i * 4 + 2] = rng.range(0.3f, 1f) * maxLen; out[i * 4 + 3] = rng.range(0f, PI.toFloat()) }
        return out
    }

    /** Particles in unit space: (x, y, size 0..1, speed 0.3..1, phase 0..2pi). */
    fun particles(count: Int, seed: Long): FloatArray {
        if (count <= 0) return FloatArray(0)
        val rng = Rng(seed); val out = FloatArray(count * 5)
        for (i in 0 until count) { out[i * 5] = rng.nextFloat(); out[i * 5 + 1] = rng.nextFloat(); out[i * 5 + 2] = rng.nextFloat(); out[i * 5 + 3] = rng.range(0.3f, 1f); out[i * 5 + 4] = rng.range(0f, 2f * PI.toFloat()) }
        return out
    }

    /** Position of particle [i] at animation time [t] (0..1, looping): rises and sways; wraps without a jump. Returns (x, y, alpha) in unit space. */
    fun particleAt(p: FloatArray, i: Int, t: Float, rise: Float): Triple<Float, Float, Float> {
        val x0 = p[i * 5]; val y0 = p[i * 5 + 1]; val sp = p[i * 5 + 3]; val ph = p[i * 5 + 4]
        val lap = (y0 - t * sp * rise).let { it - kotlin.math.floor(it) }
        val x = (x0 + 0.02f * sin(ph + t * 2f * PI.toFloat() * sp)).coerceIn(0f, 1f)
        val fade = sin(lap * PI.toFloat())                       // 0 at both ends of the lap: invisible when it wraps
        val twinkle = 0.65f + 0.35f * cos(ph + t * 4f * PI.toFloat())
        return Triple(x, lap, (fade * twinkle).coerceIn(0f, 1f))
    }
}
