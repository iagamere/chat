package com.abdo.ps4monitor

import kotlin.math.max
import kotlin.math.min

/**
 * Turns measured artwork ([CoverFeatures]) into a coherent [DesignDna]. Nothing here knows a game's name: the identity comes from
 * continuous scores (paper-like, misty, dramatic, organic ...) computed from the measurements, so unseen games get an identity too.
 *
 *  - Colours: both a light and a dark scheme are always generated from the same hue family; the artwork's own polarity only decides
 *    which of the two is the more faithful one. Every scheme is then repaired against [DnaRules] (4.5:1 text, 3:1 graphics).
 *  - Composition: the best-scoring archetype supplies the shape family, borders, separators, motif, texture and atmosphere; its
 *    strength follows how clearly the artwork matches it and how confident the analysis is.
 */
object DnaGenerator {
    private fun ramp(x: Float, lo: Float, hi: Float) = ((x - lo) / (hi - lo)).coerceIn(0f, 1f)

    /** Archetype scores (0..1) for diagnostics and tests. */
    fun scores(f: CoverFeatures): Map<String, Float> {
        val lightness = ramp(f.lightFraction, 0.25f, 0.60f)
        val dull = 1f - ramp(f.meanSaturation, 0.22f, 0.50f)
        val open = ramp(f.negativeSpace + 0.5f * f.flatness, 0.15f, 0.45f)
        val paper = 0.45f * lightness + 0.25f * dull + 0.30f * open

        val cool = ramp(-f.warmth, 0.10f, 0.50f)
        val dimMid = 1f - ramp(f.meanLuma, 0.45f, 0.70f)
        val soft = 1f - ramp(f.edgeDensity, 0.22f, 0.50f)
        val vivid = ramp(f.colorfulness, 0.20f, 0.50f)
        val mist = cool * (0.5f + 0.5f * soft) * (0.6f + 0.4f * dimMid) * (0.5f + 0.5f * vivid)

        val contrast = ramp(f.spread, 0.18f, 0.50f)
        val busy = ramp(f.edgeDensity, 0.12f, 0.32f)
        val darkness = ramp(f.darkFraction, 0.25f, 0.55f)
        val hueRed = 1f - ramp(min(ColorOps.hueDistance(f.dominantHue, 355f), 80f), 20f, 80f)
        val hueViolet = 1f - ramp(ColorOps.hueDistance(f.dominantHue, 285f), 25f, 70f)
        val hot = max(ramp(f.warmth, 0.10f, 0.50f), max(hueRed, hueViolet))
        val drama = contrast * (0.4f + 0.6f * max(busy, darkness)) * (0.5f + 0.5f * hot) * (0.7f + 0.3f * vivid) * (0.35f + 0.65f * max(darkness, vivid))

        val green = 1f - ramp(ColorOps.hueDistance(f.dominantHue, 110f), 25f, 75f)
        val organic = green * (0.5f + 0.5f * vivid) * (0.6f + 0.4f * busy)
        return mapOf("paper" to paper.coerceIn(0f, 1f), "mist" to mist.coerceIn(0f, 1f), "angular" to drama.coerceIn(0f, 1f), "organic" to organic.coerceIn(0f, 1f))
    }

    /** Picks the composition for the artwork: the best-matching template, scaled by how clear the match and the analysis are. */
    fun styleFor(f: CoverFeatures): StyleSpec = styleRaw(f).normalized()
    private fun styleRaw(f: CoverFeatures): StyleSpec {
        val sc = scores(f)
        val best = sc.maxByOrNull { it.value } ?: return DnaStyles.PLAIN
        if (best.value < 0.20f) return DnaStyles.PLAIN
        val base = DnaStyles.byId(best.key) ?: return DnaStyles.PLAIN
        val strength = (0.35f + 0.65f * ramp(best.value, 0.20f, 0.70f)) * (0.5f + 0.5f * f.confidence)
        val s = DnaStyles.scaled(base, strength)
        val soft = 1f - ramp(f.edgeDensity, 0.15f, 0.45f)
        val busy = 1f - soft
        return when (best.key) {
            "paper" -> s.copy(shape = s.shape.copy(radius = 4f + 4f * soft))
            "mist" -> s.copy(shape = s.shape.copy(radius = 18f + 8f * soft), atmosphere = s.atmosphere.copy(angle = 80f + 40f * (f.balanceX + 1f) / 2f))
            "angular" -> s.copy(shape = s.shape.copy(cut = 6f + 8f * busy), atmosphere = s.atmosphere.copy(particleCount = (s.atmosphere.particleCount * (1f - 0.4f * soft)).toInt()))
            "organic" -> s.copy(shape = s.shape.copy(radius = 22f + 8f * ramp(f.colorfulness, 0.2f, 0.6f)))
            else -> s
        }
    }

    // ------------------------------------------------------------------ colours
    private class Tint(val h: Float, val s: Float)

    private fun tintOf(c: Int, keyHue: Float, minSat: Float): Tint {
        val x = ColorMath.toHsl(c)
        return if (x[1] < 0.08f) Tint(keyHue, minSat) else Tint(x[0], x[1])
    }

    /**
     * [native]: this appearance matches the artwork's own brightness, so it keeps the artwork's tint at full strength; the other one is
     * a calmer relative of it.
     */
    private fun buildScheme(dark: Boolean, native: Boolean, tint: Tint, accentHsl: FloatArray, h2: Float, s2: Float, h3: Float): DnaScheme {
        val h = tint.h
        val sBg = (tint.s * (if (native) 1f else 0.75f)).coerceIn(0.06f, 0.55f)
        val m = HashMap<Role, Int>()
        if (dark) {
            m[Role.BG] = ColorOps.hsl(h, sBg, 0.060f); m[Role.LOW] = ColorOps.hsl(h, sBg, 0.080f); m[Role.BG2] = ColorOps.hsl(h, sBg * 0.95f, 0.100f)
            m[Role.CARD] = ColorOps.hsl(h, sBg * 0.75f, 0.135f); m[Role.HIGH] = ColorOps.hsl(h, sBg * 0.60f, 0.180f)
            m[Role.TEXT] = ColorOps.hsl(h, 0.15f, 0.95f); m[Role.MUTED] = ColorOps.hsl(h, 0.12f, 0.74f)
            m[Role.OUTLINE] = ColorOps.hsl(h, 0.10f, 0.55f); m[Role.OUTLINE_VARIANT] = ColorOps.hsl(h, sBg * 0.9f, 0.24f)
        } else {
            // at very high lightness a pastel tint needs a high HSL saturation to be visible at all
            val sl = (tint.s.coerceAtLeast(0.25f) * (if (native) 1.4f else 1.1f)).coerceIn(0.30f, 0.85f)
            m[Role.BG] = ColorOps.hsl(h, sl, 0.968f); m[Role.LOW] = ColorOps.hsl(h, sl, 0.955f); m[Role.BG2] = ColorOps.hsl(h, sl, 0.940f)
            m[Role.CARD] = ColorOps.hsl(h, sl * 0.95f, 0.915f); m[Role.HIGH] = ColorOps.hsl(h, sl * 0.9f, 0.885f)
            m[Role.TEXT] = ColorOps.hsl(h, 0.30f, 0.09f); m[Role.MUTED] = ColorOps.hsl(h, 0.18f, 0.30f)
            m[Role.OUTLINE] = ColorOps.hsl(h, 0.10f, 0.42f); m[Role.OUTLINE_VARIANT] = ColorOps.hsl(h, sl, 0.80f)
        }
        val card = m[Role.CARD]!!
        val ha = accentHsl[0]; val sa = accentHsl[1].coerceIn(0.40f, 0.95f)
        val primary = ColorOps.hsl(ha, sa, if (dark) 0.66f else 0.38f)
        val secondary = ColorOps.hsl(h2, s2.coerceIn(0.40f, 0.90f), if (dark) 0.68f else 0.36f)
        val tertiary = ColorOps.hsl(h3, 0.55f, if (dark) 0.68f else 0.36f)
        m[Role.PRIMARY] = primary; m[Role.ON_PRIMARY] = ColorOps.readableOn(primary, ha)
        m[Role.PRIMARY_CONTAINER] = ColorOps.mix(card, primary, if (dark) 0.28f else 0.16f); m[Role.ON_PRIMARY_CONTAINER] = m[Role.TEXT]!!
        m[Role.SECONDARY] = secondary; m[Role.ON_SECONDARY] = ColorOps.readableOn(secondary, h2)
        m[Role.SECONDARY_CONTAINER] = ColorOps.mix(card, secondary, if (dark) 0.18f else 0.12f); m[Role.ON_SECONDARY_CONTAINER] = m[Role.TEXT]!!
        m[Role.TERTIARY] = tertiary; m[Role.ON_TERTIARY] = ColorOps.readableOn(tertiary, h3)
        m[Role.TERTIARY_CONTAINER] = ColorOps.mix(card, tertiary, if (dark) 0.22f else 0.14f); m[Role.ON_TERTIARY_CONTAINER] = m[Role.TEXT]!!
        ThemeCore.fixedStatusColors(dark, m)
        m[Role.PROGRESS] = primary; m[Role.GLOW] = ColorOps.hsl(ha, 0.8f, if (dark) 0.60f else 0.50f)
        m[Role.HERO_TOP] = ColorOps.mix(m[Role.BG]!!, primary, if (dark) 0.20f else 0.12f); m[Role.HERO_BOTTOM] = m[Role.BG]!!
        return DnaRules.repair(DnaScheme.of(dark, m)!!)
    }

    /**
     * [key]: a stable id of the game (title id); [name]: display name. Never throws; unusable artwork gives the neutral fallback so a missing
     * or broken cover simply looks like the app itself.
     */
    fun generate(f: CoverFeatures, key: String, name: String = ""): DesignDna {
        if (!f.usable) return fallback(key, name)
        val keyHue = when {
            f.colorfulness >= 0.04f -> f.dominantHue
            ColorMath.toHsl(f.accent)[1] >= 0.15f -> ColorMath.toHsl(f.accent)[0]
            else -> ColorMath.toHsl(f.lightColor).let { if (it[1] >= 0.08f) it[0] else 230f }
        }
        val darkTint = tintOf(f.darkColor, keyHue, 0.12f)
        val lightTint = tintOf(f.lightColor, keyHue, 0.12f)
        val acc = ColorMath.toHsl(f.accent).let { if (it[1] < 0.25f) floatArrayOf(keyHue, 0.40f, it[2]) else it }
        val sec = f.secondary?.let { ColorMath.toHsl(it) }
        // no second colour in the artwork: a neighbouring hue, pulled towards red for warm accents and towards violet for cool ones
        val h2 = sec?.get(0) ?: ((acc[0] + (if (acc[0] < 180f) -38f else 38f) + 360f) % 360f); val s2 = sec?.get(1) ?: acc[1]
        val h3 = (h2 + 110f) % 360f
        val nativeDark = f.darkNative
        val dark = buildScheme(true, nativeDark, darkTint, acc, h2, s2, h3)
        val light = buildScheme(false, !nativeDark, lightTint, acc, h2, s2, h3)
        val seed = DnaHash.seed(key + "|" + f.fingerprint)
        return DesignDna(DNA_SCHEMA, "game:$key", name.ifBlank { key }, DnaSource.GAME, f.fingerprint, Math.round(f.confidence * 1000f) / 1000f, seed, nativeDark, light, dark, styleFor(f))
    }

    /** Neutral identity: the app's own look (used while the cover is loading, when it is missing, or when analysis failed). */
    fun fallback(key: String, name: String = ""): DesignDna {
        val d = ThemeCore.dnaOf(ThemeCore.DEFAULT)
        return DesignDna(DNA_SCHEMA, "game:$key", name.ifBlank { key }, DnaSource.GAME, "none", 0f, DnaHash.seed(key), true, d.light, d.dark, DnaStyles.PLAIN)
    }
}
