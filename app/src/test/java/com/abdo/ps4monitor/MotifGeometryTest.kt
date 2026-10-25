package com.abdo.ps4monitor

import org.junit.Assert.*
import org.junit.Test

class MotifGeometryTest {
    @Test fun sameSeedSameShapes() {
        assertArrayEquals(MotifGeometry.rays(300f, 200f, 100f, 5, 9L), MotifGeometry.rays(300f, 200f, 100f, 5, 9L), 0f)
        assertArrayEquals(MotifGeometry.slashes(300f, 200f, 4, 9L), MotifGeometry.slashes(300f, 200f, 4, 9L), 0f)
        assertFalse(MotifGeometry.slashes(300f, 200f, 4, 9L).contentEquals(MotifGeometry.slashes(300f, 200f, 4, 10L)))
    }
    @Test fun wavesStayInTheBand() {
        val a = MotifGeometry.waves(320f, 400f, 22f, 56f)
        assertTrue(a.isNotEmpty()); assertEquals(0, a.size % 3)
        var i = 0; while (i < a.size) { assertTrue(a[i + 1] <= 56f + 11f); assertTrue(a[i + 2] in 1f..11f); i += 3 }
    }
    @Test fun slashesAndDotsAreInsideTheirBox() {
        val s = MotifGeometry.slashes(150f, 90f, 6, 3L); var i = 0
        while (i < s.size) { assertTrue(s[i] in 0f..150f); assertTrue(s[i + 1] in 0f..90f); i += 4 }
        val d = MotifGeometry.dots(120f, 80f, 14f, 3L); i = 0
        while (i < d.size) { assertTrue(d[i] in 0f..120f); assertTrue(d[i + 1] in 0f..80f); i += 3 }
    }
    @Test fun vinesAreContinuous() {
        val (c, l) = MotifGeometry.vines(300f, 100f, 5L)
        assertTrue(c.size % 8 == 0 && c.size >= 16); assertTrue(l.size % 3 == 0)
        var i = 8; while (i < c.size) { assertEquals(c[i - 2], c[i], 1e-3f); assertEquals(c[i - 1], c[i + 1], 1e-3f); i += 8 }   // segment starts where the last ended
        assertEquals(300f, c[c.size - 2], 1e-3f)
        for (v in c) assertTrue(v.isFinite())
    }
    @Test fun degenerateSizesGiveNothing() {
        assertEquals(0, MotifGeometry.waves(0f, 10f, 22f, 56f).size); assertEquals(0, MotifGeometry.rays(100f, 100f, 90f, 0, 1L).size)
        assertEquals(0, MotifGeometry.dots(10f, 10f, 1f, 1L).size); assertEquals(0, MotifGeometry.particles(0, 1L).size)
        assertEquals(0, MotifGeometry.vines(-1f, 10f, 1L).first.size); assertEquals(0, MotifGeometry.specks(10f, 10f, 0, 3f, 1L).size)
    }
    @Test fun particlesWrapInvisibly() {
        val p = MotifGeometry.particles(20, 77L); assertEquals(100, p.size)
        for (i in 0 until 20) for (t in listOf(0f, 0.25f, 0.5f, 0.999f)) {
            val (x, y, a) = MotifGeometry.particleAt(p, i, t, 1f)
            assertTrue(x in 0f..1f && y in 0f..1f && a in 0f..1f)
        }
        // a particle that has just wrapped (y near 0 or 1) is almost invisible
        var minA = 1f; for (i in 0 until 20) for (k in 0..400) minA = minOf(minA, MotifGeometry.particleAt(p, i, k / 400f, 1f).third)
        assertTrue(minA < 0.05f)
    }
    @Test fun rngIsDeterministicAndInRange() {
        val a = Rng(5L); val b = Rng(5L); for (i in 0 until 50) { val x = a.nextFloat(); assertEquals(x, b.nextFloat(), 0f); assertTrue(x in 0f..1f) }
        assertTrue(Rng(0L).nextFloat().isFinite())
    }
    @Test fun styleScalingKeepsDiscreteChoices() {
        val s = DnaStyles.scaled(DnaStyles.ANGULAR, 0.3f)
        assertEquals(DnaStyles.ANGULAR.shape.family, s.shape.family); assertEquals(DnaStyles.ANGULAR.ornament.divider, s.ornament.divider)
        assertTrue(s.ornament.motifAlpha < DnaStyles.ANGULAR.ornament.motifAlpha); assertTrue(s.atmosphere.particleCount < DnaStyles.ANGULAR.atmosphere.particleCount)
        assertTrue(DnaStyles.PLAIN.isPlain); assertFalse(DnaStyles.MIST.isPlain)
    }
}
