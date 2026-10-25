package com.abdo.ps4monitor

import org.junit.Assert.*
import org.junit.Test

class DnaCacheTest {
    private fun d(key: String) = DnaGenerator.fallback(key, key)

    @Test fun sameArtworkIsComputedOnce() {
        val c = DnaCache(4); var n = 0
        val a = c.getOrCompute("G1", "fp1") { n++; d("G1") }; val b = c.getOrCompute("G1", "fp1") { n++; d("G1") }
        assertSame(a, b); assertEquals(1, n)
    }
    @Test fun changedCoverNeverReusesTheOldIdentity() {
        val c = DnaCache(4); var n = 0
        val a = c.getOrCompute("G1", "old") { n++; d("G1") }
        assertNull(c.get("G1", "new"))
        val b = c.getOrCompute("G1", "new") { n++; d("G1") }
        assertNotSame(a, b); assertEquals(2, n); assertNull(c.get("G1", "old"))
    }
    @Test fun cacheIsBounded_leastRecentlyUsedGoesFirst() {
        val c = DnaCache(3)
        for (i in 1..3) c.put("G$i", "f", d("G$i"))
        c.get("G1", "f")                       // touch G1
        c.put("G4", "f", d("G4"))
        assertEquals(3, c.size()); assertNotNull(c.get("G1", "f")); assertNull(c.get("G2", "f")); assertNotNull(c.get("G4", "f"))
        for (i in 10..100) c.put("X$i", "f", d("X"))
        assertEquals(3, c.size())
    }
    @Test fun invalidateAndClear() {
        val c = DnaCache(3); c.put("A", "f", d("A")); c.put("B", "f", d("B"))
        c.invalidate("A"); assertNull(c.get("A", "f")); assertNotNull(c.get("B", "f")); c.clear(); assertEquals(0, c.size())
    }
    @Test fun worksWithRealFingerprints() {
        val c = DnaCache(8)
        val i1 = TestImages.paperLike(); val i2 = TestImages.dramaLike()
        val f1 = CoverAnalyzer.analyze(i1.px, i1.w, i1.h); val f2 = CoverAnalyzer.analyze(i2.px, i2.w, i2.h)
        c.put("G", f1.fingerprint, DnaGenerator.generate(f1, "G"))
        assertNotNull(c.get("G", f1.fingerprint)); assertNull(c.get("G", f2.fingerprint))
    }
    @Test(expected = IllegalArgumentException::class) fun zeroCapacityIsRejected() { DnaCache(0) }
}
