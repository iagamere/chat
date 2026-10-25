package com.abdo.ps4monitor

import org.junit.Assert.*
import org.junit.Test

class CoverAnalyzerTest {
    private fun an(i: Img) = CoverAnalyzer.analyze(i.px, i.w, i.h)

    @Test fun paperLikeCoverIsPaleOpenAndLightNative() {
        val f = an(TestImages.paperLike())
        assertTrue(f.usable)
        assertTrue("light fraction ${f.lightFraction}", f.lightFraction > 0.4f)
        assertTrue("negative space ${f.negativeSpace}", f.negativeSpace > 0.2f)
        assertFalse(f.darkNative)
        assertTrue(f.meanSaturation < 0.2f)
    }
    @Test fun mistLikeCoverIsCoolColourfulAndSoft() {
        val f = an(TestImages.mistLike())
        assertTrue("warmth ${f.warmth}", f.warmth < -0.3f)
        assertTrue("colorfulness ${f.colorfulness}", f.colorfulness > 0.5f)
        assertEquals(220.0, f.dominantHue.toDouble(), 30.0)
        assertTrue("edges ${f.edgeDensity}", f.edgeDensity < 0.1f)
    }
    @Test fun dramaLikeCoverIsDarkContrastyAndBusy() {
        val f = an(TestImages.dramaLike())
        assertTrue(f.darkFraction > 0.5f); assertTrue(f.edgeDensity > 0.1f); assertTrue(f.darkNative)
        assertNotNull(f.secondary)                              // red and violet are both present
    }
    @Test fun spatialStructureIsKept() {
        val top = TestImages.build(96, 96) { _, y -> if (y < 48) 0xFFF0F0F0.toInt() else 0xFF101010.toInt() }
        val f = an(top)
        assertTrue(f.regionLuma[0] > 0.8f && f.regionLuma[8] < 0.2f)
        assertTrue("balance y ${f.balanceY}", f.balanceY.isFinite())
    }
    @Test fun regionsOfLeftRightSplit() {
        val f = an(TestImages.build(96, 96) { x, _ -> if (x < 48) 0xFFFF2020.toInt() else 0xFF2020FF.toInt() })
        assertTrue(f.regionColorfulness[0] > 0.9f && f.regionColorfulness[2] > 0.9f)
        assertTrue(ColorOps.hueDistance(f.dominantHue, 0f) < 20f || ColorOps.hueDistance(f.dominantHue, 240f) < 20f)
    }
    @Test fun transparentWrongSizeAndTinyInputsAreNotUsable() {
        assertFalse(an(TestImages.transparent()).usable)
        assertFalse(CoverAnalyzer.analyze(IntArray(10), 100, 100).usable)       // pixel array too small for the declared size
        assertFalse(CoverAnalyzer.analyze(IntArray(0), 0, 0).usable)
        assertFalse(CoverAnalyzer.analyze(IntArray(4), 2, 2).usable)            // 4 cells
        assertEquals(0f, CoverFeatures.UNUSABLE.confidence, 0f)
    }
    @Test fun flatImageIsUsableButLowConfidence() {
        val f = an(TestImages.flat(0xFF808080.toInt()))
        assertTrue(f.usable); assertTrue("confidence ${f.confidence}", f.confidence < 0.75f)
        assertEquals(0f, f.colorfulness, 0f)
    }
    @Test fun halfTransparentCoverIgnoresTransparentPixels() {
        val f = an(TestImages.build(64, 64) { x, _ -> if (x < 32) 0 else 0xFFFF0000.toInt() })
        assertTrue(f.usable); assertTrue(ColorOps.hueDistance(f.dominantHue, 0f) < 15f)
    }
    @Test fun fingerprintIsStableAndChangesWithTheCover() {
        val a = an(TestImages.paperLike()); val b = an(TestImages.paperLike()); val c = an(TestImages.dramaLike())
        assertEquals(a.fingerprint, b.fingerprint)
        assertNotEquals(a.fingerprint, c.fingerprint)
        assertEquals(16, a.fingerprint.length)
        // a small edit of one corner changes the fingerprint too
        val edited = TestImages.build(128, 128) { x, y -> if (x < 32 && y < 32) 0xFF00AA00.toInt() else TestImages.paperLike().px[y * 128 + x] }
        assertNotEquals(a.fingerprint, an(edited).fingerprint)
    }
    @Test fun nonSquareCoversAreAnalysed() {
        val f = an(TestImages.dramaLike(256, 144))
        assertTrue(f.usable); assertEquals(96, f.gridW); assertEquals(96, f.gridH)
        val g = an(TestImages.dramaLike(60, 40)); assertEquals(60, g.gridW); assertEquals(40, g.gridH)   // never upsampled
    }
    @Test fun analysisIsDeterministic() {
        val a = an(TestImages.random(7)); val b = an(TestImages.random(7))
        assertEquals(a.fingerprint, b.fingerprint); assertEquals(a.dominant, b.dominant); assertEquals(a.edgeDensity, b.edgeDensity, 0f)
    }
}
