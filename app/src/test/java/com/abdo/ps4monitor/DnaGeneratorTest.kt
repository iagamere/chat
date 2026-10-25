package com.abdo.ps4monitor

import org.junit.Assert.*
import org.junit.Test

class DnaGeneratorTest {
    private fun dna(i: Img, key: String = "CUSA00001") = DnaGenerator.generate(CoverAnalyzer.analyze(i.px, i.w, i.h), key, "Test game")

    @Test fun paperLikeArtworkGetsPaperComposition() {
        val d = dna(TestImages.paperLike())
        assertEquals("paper", d.style.id)
        assertEquals(ShapeFamily.CRISP, d.style.shape.family); assertEquals(BorderStyle.DOUBLE, d.style.ornament.border)
        assertEquals(DividerStyle.BRUSH, d.style.ornament.divider); assertEquals(DisplayFace.SERIF, d.style.type.face)
        assertFalse(d.nativeDark)
        assertTrue(ColorMath.luminance(d.light.bg) > 0.8)                    // ivory page
    }
    @Test fun mistLikeArtworkGetsMistComposition() {
        val d = dna(TestImages.mistLike())
        assertEquals("mist", d.style.id); assertEquals(ShapeFamily.ORGANIC, d.style.shape.family)
        assertEquals(ParticleKind.MOTES, d.style.atmosphere.particles); assertTrue(d.style.atmosphere.glow > 0.1f)
        assertTrue(d.nativeDark)
    }
    @Test fun dramaLikeArtworkGetsAngularComposition() {
        val d = dna(TestImages.dramaLike())
        assertEquals("angular", d.style.id); assertEquals(ShapeFamily.ANGULAR, d.style.shape.family)
        assertEquals(MotifKind.SLASHES, d.style.ornament.motif); assertTrue(d.style.shape.cut > 0f)
    }
    @Test fun everyGeneratedSchemeIsReadableInBothAppearances() {
        for (seed in 1L..250L) {
            val d = dna(TestImages.random(seed), "G$seed")
            assertTrue("seed $seed light: ${DnaRules.check(d.light)}", DnaRules.check(d.light).isEmpty())
            assertTrue("seed $seed dark: ${DnaRules.check(d.dark)}", DnaRules.check(d.dark).isEmpty())
            assertTrue(d.accessibility.ok)
            assertTrue(ColorMath.luminance(d.light.bg) > 0.5); assertTrue(ColorMath.luminance(d.dark.bg) < 0.1)
        }
    }
    @Test fun lightAndDarkVariantsExistWhateverTheArtworkBrightness() {
        val darkArt = dna(TestImages.dramaLike()); val paleArt = dna(TestImages.paperLike())
        for (d in listOf(darkArt, paleArt)) {
            assertFalse(d.scheme(false).dark); assertTrue(d.scheme(true).dark)
            assertTrue(ColorMath.luminance(d.scheme(false).bg) > ColorMath.luminance(d.scheme(true).bg) + 0.5)
            // text is generated per polarity, not derived from one background
            assertTrue(ColorMath.luminance(d.scheme(true).text) > 0.5); assertTrue(ColorMath.luminance(d.scheme(false).text) < 0.1)
        }
    }
    @Test fun brightCoverDoesNotForceUnreadableLightUiNorDarkCoverBlackOnBlack() {
        val bright = dna(TestImages.flat(0xFFFFFF00.toInt()))
        val black = dna(TestImages.flat(0xFF000000.toInt()))
        for (d in listOf(bright, black)) for (dk in listOf(false, true)) assertTrue(DnaRules.check(d.scheme(dk)).isEmpty())
        // surfaces stay distinguishable from the page in the dark variant of a black cover
        val s = black.dark; assertTrue(ColorMath.contrast(s.card, s.bg) > 1.1)
    }
    @Test fun generationIsDeterministic() {
        val a = dna(TestImages.random(42)); val b = dna(TestImages.random(42))
        assertEquals(a.light, b.light); assertEquals(a.dark, b.dark); assertEquals(a.style, b.style); assertEquals(a.seed, b.seed)
        assertEquals(DnaJson.toJson(a), DnaJson.toJson(b))
    }
    @Test fun differentCoversGiveDifferentIdentities() {
        val a = dna(TestImages.paperLike(), "A"); val b = dna(TestImages.dramaLike(), "B"); val c = dna(TestImages.mistLike(), "C")
        assertEquals(3, setOf(a.style.id, b.style.id, c.style.id).size)
        assertEquals(3, setOf(a.dark.primary, b.dark.primary, c.dark.primary).size)
        assertNotEquals(a.seed, b.seed)
    }
    @Test fun unusableArtworkFallsBackToTheAppLook() {
        val d = DnaGenerator.generate(CoverFeatures.UNUSABLE, "CUSA00009", "No cover")
        assertEquals(0f, d.confidence, 0f); assertEquals("none", d.fingerprint); assertTrue(d.style.isPlain)
        assertTrue(DnaRules.check(d.light).isEmpty() && DnaRules.check(d.dark).isEmpty())
        val t = DnaGenerator.generate(CoverAnalyzer.analyze(IntArray(3), 100, 100), "X")
        assertEquals("none", t.fingerprint)
    }
    @Test fun lowResolutionArtworkLowersConfidenceAndEffectStrength() {
        val big = CoverAnalyzer.analyze(TestImages.mistLike(128, 128).px, 128, 128)
        val small = CoverAnalyzer.analyze(TestImages.mistLike(8, 8).px, 8, 8)
        assertTrue(small.usable && small.confidence < big.confidence)
        val sb = DnaGenerator.styleFor(big); val ss = DnaGenerator.styleFor(small)
        assertTrue(ss.atmosphere.glow <= sb.atmosphere.glow); assertTrue(ss.atmosphere.particleCount <= sb.atmosphere.particleCount)
    }
    @Test fun scoresStayInRange() {
        for (seed in 1L..60L) {
            val i = TestImages.random(seed); val f = CoverAnalyzer.analyze(i.px, i.w, i.h)
            for ((k, v) in DnaGenerator.scores(f)) assertTrue("$k=$v", v in 0f..1f)
        }
    }
}
