package com.abdo.ps4monitor

import org.junit.Assert.*
import org.junit.Test

class ColorOpsTest {
    @Test fun ensureRaisesContrastOnDarkBackground() {
        val bg = 0xFF101010.toInt()
        val fg = ColorOps.ensure(0xFF303030.toInt(), listOf(bg), 4.5)
        assertTrue(ColorMath.contrast(fg, bg) >= 4.5)
    }
    @Test fun ensureDarkensOnLightBackground() {
        val bg = 0xFFF5F0E0.toInt()
        val fg = ColorOps.ensure(0xFFC0C0A0.toInt(), listOf(bg), 4.5)
        assertTrue(ColorMath.contrast(fg, bg) >= 4.5)
        assertTrue(ColorMath.luminance(fg) < ColorMath.luminance(bg))
    }
    @Test fun ensureKeepsAcceptableColourUnchanged() {
        val bg = 0xFF000000.toInt(); val fg = 0xFFE0E0E0.toInt()
        assertEquals(fg, ColorOps.ensure(fg, listOf(bg), 4.5))
    }
    @Test fun ensureWorksAgainstSeveralBackgroundsAtOnce() {
        val bgs = listOf(0xFF202020.toInt(), 0xFF808080.toInt(), 0xFF202040.toInt())
        val fg = ColorOps.ensure(0xFF777777.toInt(), bgs, 4.5)
        assertTrue(ColorOps.worst(fg, bgs) >= 4.5 - 1e-9 || fg == ColorOps.WHITE || fg == ColorOps.BLACK)
    }
    @Test fun readableOnPicksALegibleExtreme() {
        for (bg in listOf(0xFF7C6BFF.toInt(), 0xFFE0B341.toInt(), 0xFF101820.toInt(), 0xFF808080.toInt())) {
            assertTrue(ColorMath.contrast(ColorOps.readableOn(bg, 200f), bg) >= 4.5)
        }
    }
    @Test fun parseHexAcceptsValidAndRejectsInvalid() {
        assertEquals(0xFF112233.toInt(), ColorOps.parseHex("#112233"))
        assertEquals(0xFFAABBCC.toInt(), ColorOps.parseHex("abc"))
        assertNull(ColorOps.parseHex("#12345")); assertNull(ColorOps.parseHex("#GG0000")); assertNull(ColorOps.parseHex("")); assertNull(ColorOps.parseHex("12345678"))
    }
    @Test fun hexRoundTrip() { assertEquals("#0A1022", ColorOps.toHex(0xFF0A1022.toInt())) }
    @Test fun hueDistanceWrapsAroundTheWheel() {
        assertEquals(20.0, ColorOps.hueDistance(350f, 10f).toDouble(), 1e-3)
        assertEquals(180.0, ColorOps.hueDistance(0f, 180f).toDouble(), 1e-3)
    }
}
