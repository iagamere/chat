package com.abdo.ps4monitor

import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    private val modes = ThemeMode.values()
    private val game = DnaGenerator.generate(CoverAnalyzer.analyze(TestImages.dramaLike().px, 128, 128), "CUSA00001", "Game")
    private val custom = AppTheme("u1", "Mine", false, 0xFF101820.toInt(), 0xFF16222E.toInt(), 0xFF1D2D3C.toInt(), 0xFF4FC3F7.toInt(), 0xFFF59E0B.toInt(),
        0xFFF0F5FA.toInt(), 0xFFA8B8C8.toInt(), 0xFF2B3E52.toInt(), 0xFF4FC3F7.toInt())
    private val lightCustom = custom.copy(id = "u2", bg = 0xFFFAF7F0.toInt(), bg2 = 0xFFF1ECDF.toInt(), surface = 0xFFE6DFCC.toInt(), text = 0xFF1A1A14.toInt(), text2 = 0xFF4B4A3C.toInt(), border = 0xFFCFC6AC.toInt(),
        accent = 0xFF8A2B22.toInt(), accent2 = 0xFF225A8A.toInt(), progress = 0xFF8A2B22.toInt())

    @Test fun appearancePreferenceMapping() {
        assertTrue(LookResolver.isDark(0, true)); assertFalse(LookResolver.isDark(0, false))
        assertFalse(LookResolver.isDark(1, true)); assertTrue(LookResolver.isDark(2, false))
        assertFalse(LookResolver.isDark(99, false))
    }
    @Test fun everyModeHonoursBothAppearances() {
        for (m in modes) for (dark in listOf(false, true)) for (base in listOf(ThemeCore.DEFAULT, ThemeCore.preset("aurora")!!, custom, lightCustom)) {
            val app = LookResolver.forApp(m, base, dark); val g = LookResolver.forGame(m, base, false, game, dark); val ga = LookResolver.forGame(m, base, true, game, dark)
            for (l in listOf(app, g, ga)) {
                assertEquals("$m ${base.id} dark=$dark", dark, l.dark); assertEquals(dark, l.scheme.dark)
                assertEquals(dark, ColorMath.luminance(l.scheme.bg) < 0.179)
                assertTrue("$m ${base.id} dark=$dark: ${DnaRules.check(l.scheme)}", DnaRules.check(l.scheme).isEmpty())
            }
        }
    }
    @Test fun gameIdentityIsIndependentOfAppearance() {
        val l = LookResolver.forGame(ThemeMode.DYNAMIC, ThemeCore.DEFAULT, false, game, false); val d = LookResolver.forGame(ThemeMode.DYNAMIC, ThemeCore.DEFAULT, false, game, true)
        assertSame(game.light, l.scheme); assertSame(game.dark, d.scheme)
        assertEquals(l.style, d.style); assertEquals(l.seed, d.seed)
        assertSame(game, l.dna)
    }
    @Test fun gameThemeNeverForcesDarkMode() {
        // regression: custom / preset / game themes used to be dark whatever the Appearance setting said
        for (m in listOf(ThemeMode.PRESET, ThemeMode.CUSTOM, ThemeMode.DYNAMIC)) assertFalse(LookResolver.forGame(m, ThemeCore.preset("crimson")!!, false, game, false).dark)
    }
    @Test fun otherModesIgnoreTheGame() {
        for (m in listOf(ThemeMode.DEFAULT, ThemeMode.PRESET, ThemeMode.CUSTOM)) {
            val l = LookResolver.forGame(m, custom, false, game, true)
            assertNull(l.dna)
        }
    }
    @Test fun missingGameIdentityUsesTheAppLook() {
        val l = LookResolver.forGame(ThemeMode.DYNAMIC, custom, false, null, true)
        assertEquals(LookResolver.forApp(ThemeMode.DYNAMIC, custom, true).scheme, l.scheme)
        assertEquals(ThemeCore.schemeOf(ThemeCore.DEFAULT, true), l.scheme)
    }
    @Test fun dynamicModeOnOrdinaryScreensUsesTheAppDefault() {
        assertEquals(ThemeCore.schemeOf(ThemeCore.DEFAULT, false), LookResolver.forApp(ThemeMode.DYNAMIC, custom, false).scheme)
    }
    @Test fun gameAccentsReplaceOnlyTheAccents() {
        val base = ThemeCore.preset("aurora")!!
        val plain = LookResolver.forGame(ThemeMode.PRESET, base, false, game, true).scheme
        val acc = LookResolver.forGame(ThemeMode.PRESET, base, true, game, true).scheme
        assertEquals(plain.bg, acc.bg); assertEquals(plain.card, acc.card)
        assertNotEquals(plain.primary, acc.primary)
        assertTrue(DnaRules.check(acc).isEmpty())
    }
    @Test fun everyPresetIsReadableInLightAndDark() {
        for (p in ThemeCore.PRESETS) for (dark in listOf(false, true)) {
            val s = ThemeCore.schemeOf(p, dark)
            assertTrue("${p.id} dark=$dark ${DnaRules.check(s)}", DnaRules.check(s).isEmpty())
            assertEquals("${p.id} dark=$dark", dark, ColorMath.luminance(s.bg) < 0.179)
        }
    }
    @Test fun counterpartFlipsPolarityAndKeepsCharacter() {
        val l = ThemeCore.counterpart(custom, false)
        assertFalse(ThemeCore.isDark(l)); assertEquals(custom.style, l.style); assertEquals(custom.id, l.id)
        assertTrue(ColorOps.hueDistance(ColorMath.toHsl(l.accent)[0], ColorMath.toHsl(custom.accent)[0]) < 5f)
        val d = ThemeCore.counterpart(lightCustom, true); assertTrue(ThemeCore.isDark(d))
    }
    @Test fun defaultThemeKeepsItsHandAuthoredLightVariant() {
        assertEquals(ThemeCore.sanitize(ThemeCore.DEFAULT_LIGHT).accent, ThemeCore.forAppearance(ThemeCore.DEFAULT, false).accent)
        assertFalse(ThemeCore.isDark(ThemeCore.forAppearance(ThemeCore.DEFAULT, false)))
        assertTrue(ThemeCore.isDark(ThemeCore.forAppearance(ThemeCore.DEFAULT, true)))
    }
    @Test fun sanitizeFixesUnreadableLightAndDarkThemes() {
        val bad = custom.copy(text = custom.bg, text2 = custom.surface, accent = custom.surface)
        val s = ThemeCore.sanitize(bad)
        val surfaces = listOf(s.bg, s.bg2, s.surface)
        assertTrue(ColorOps.worst(s.text, surfaces) >= 7.0 || ColorOps.worst(s.text, surfaces) >= 4.5)
        assertTrue(ColorOps.worst(s.accent, surfaces) >= 4.5)
        val badLight = lightCustom.copy(text = lightCustom.bg)
        assertTrue(ColorOps.worst(ThemeCore.sanitize(badLight).text, listOf(badLight.bg, badLight.bg2, badLight.surface)) >= 4.5)
    }
    @Test fun styledPresetsKeepTheirStyleInBothAppearances() {
        val p = ThemeCore.preset("paper")!!
        assertFalse(ThemeCore.isDark(p))
        for (dark in listOf(false, true)) assertEquals(DnaStyles.PAPER, LookResolver.forApp(ThemeMode.PRESET, p, dark).style)
        assertTrue(LookResolver.forApp(ThemeMode.DEFAULT, p, true).style.isPlain)
    }
    @Test fun themeDnaCarriesBothSchemes() {
        val d = ThemeCore.dnaOf(custom); assertFalse(d.light.dark); assertTrue(d.dark.dark); assertTrue(d.accessibility.ok)
    }
}
