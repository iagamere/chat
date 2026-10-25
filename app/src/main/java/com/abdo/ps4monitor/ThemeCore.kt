package com.abdo.ps4monitor

/**
 * Pure (JVM-testable) half of the theme engine: the token set of a theme, built-in presets, light/dark counterparts, readability repair
 * and the conversion to a [DnaScheme]. [ThemeEngine] (Theme.kt) adds the observable state and SharedPreferences on top.
 */

enum class ThemeMode { DEFAULT, PRESET, CUSTOM, DYNAMIC }

/**
 * One palette (ARGB ints) + the [style] it is drawn with. Presets, user profiles share this type. A palette has the polarity it was authored in;
 * [ThemeCore.forAppearance] derives the other polarity, so the Light / Dark / System setting works for every theme.
 */
data class AppTheme(
    val id: String, val name: String, val builtin: Boolean,
    val bg: Int, val bg2: Int, val surface: Int,
    val accent: Int, val accent2: Int,
    val text: Int, val text2: Int, val border: Int, val progress: Int,
    /** custom/preset profiles only: keep the profile's background but take the current game's accents */
    val gameAccents: Boolean = false,
    /** the preset this profile was copied from (for "restore original values") */
    val basePreset: String? = null,
    /** how the theme is composed (shape, borders, ornaments ...); "plain" = the classic look */
    val style: StyleSpec = DnaStyles.PLAIN,
)

object ThemeCore {
    private fun c(h: Long) = (0xFF000000L or h).toInt()
    private fun p(id: String, n: String, bg: Long, bg2: Long, sf: Long, a: Long, a2: Long, tx: Long, tx2: Long, bd: Long, pr: Long = a, style: StyleSpec = DnaStyles.PLAIN) =
        AppTheme(id, n, true, c(bg), c(bg2), c(sf), c(a), c(a2), c(tx), c(tx2), c(bd), c(pr), style = style)

    /** App default: the deep navy / indigo look of the redesign. */
    val DEFAULT = p("default", "PS4 Monitor", 0x0A1022, 0x0E1730, 0x141E3D, 0x7C6BFF, 0x4FC3F7, 0xF1F3FF, 0xA9B2D6, 0x2A3769)
    /** The light counterpart of the default theme, hand-authored (keeps the indigo / blue accents of the previous light scheme). */
    val DEFAULT_LIGHT = p("default", "PS4 Monitor", 0xFBF9FF, 0xF3F0FA, 0xE9E4F5, 0x5B3FD6, 0x2F5FA8, 0x1B1830, 0x4A4663, 0xD3CCE6)

    val PRESETS: List<AppTheme> = listOf(
        DEFAULT,
        p("midnight", "Midnight", 0x0D0E10, 0x131519, 0x1B1E24, 0x9AA7B8, 0x6C7A8C, 0xEDEFF2, 0xA2A9B3, 0x2B2F37),
        p("aurora", "Aurora", 0x06141C, 0x0A1E29, 0x0F2B3A, 0x2DD4BF, 0x38BDF8, 0xE8F8FA, 0x9CC7CF, 0x1C4152),
        p("violet", "Violet Pulse", 0x0F0B1A, 0x161026, 0x201737, 0xA78BFA, 0xE879F9, 0xF3EFFF, 0xB5A9D6, 0x3A2B63),
        p("emerald", "Emerald Abyss", 0x050B08, 0x0A1510, 0x10201A, 0x34D399, 0x84CC16, 0xE9FBF2, 0x9CC4B0, 0x1D4032),
        p("crimson", "Crimson Night", 0x0E0A0B, 0x161012, 0x20171A, 0xE5484D, 0xF59E0B, 0xF8EEEE, 0xC2A6A8, 0x44262B),
        p("arctic", "Arctic Blue", 0x07111F, 0x0B1A30, 0x112645, 0x7DD3FC, 0x60A5FA, 0xEEF7FF, 0xA3BDD6, 0x214470),
        p("gold", "Solar Gold", 0x0B0A07, 0x14120C, 0x1E1B12, 0xE0B341, 0xD97706, 0xFBF6E8, 0xC4B896, 0x453D22),
        p("rose", "Rose Nebula", 0x120A14, 0x1B101F, 0x271629, 0xF472B6, 0xC084FC, 0xFDEEF7, 0xCDA6BF, 0x4A2849),
        // styled presets: they show what the design system can do without needing a game
        p("paper", "Ink & Paper", 0xFAF6EC, 0xF2ECDD, 0xE8E0CC, 0xA8332B, 0x8A6D1F, 0x1A1713, 0x4D463A, 0xCBBF9F, style = DnaStyles.PAPER),
        p("mistveil", "Mist Veil", 0x070D1E, 0x0C1630, 0x131F42, 0x5FD7F2, 0x8FA8FF, 0xEAF3FF, 0xA6B7DA, 0x25396B, style = DnaStyles.MIST),
        p("ember", "Ember Edge", 0x0B0810, 0x130E1B, 0x1D1528, 0xFF4D5E, 0xB36BFF, 0xFBF1F4, 0xC7B3C2, 0x4A2640, style = DnaStyles.ANGULAR),
        p("verdant", "Verdant", 0x07110C, 0x0D1B13, 0x15271C, 0x6FD18A, 0xD6C75A, 0xEEF8EF, 0xA9C7B0, 0x24402E, style = DnaStyles.ORGANIC),
    )
    fun preset(id: String?) = PRESETS.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ colour validation
    fun parseHex(s: String): Int? = ColorOps.parseHex(s)
    fun hex(c: Int) = ColorOps.toHex(c)

    /** A theme whose page colour is darker than the contrast pivot (luminance 0.179) is a dark theme: white text reads better on it. */
    fun isDark(t: AppTheme) = ColorMath.luminance(ColorOps.opaque(t.bg)) < 0.179

    /**
     * Makes a user theme safe: opaque colours, text >= 7:1 (secondary text 4.5:1), accents >= 4.5:1 (they are used as text and as graphics)
     * on every surface of the theme. Works for light and dark palettes alike.
     */
    fun sanitize(t: AppTheme): AppTheme {
        val bg = ColorOps.opaque(t.bg); val bg2 = ColorOps.opaque(t.bg2); val sf = ColorOps.opaque(t.surface)
        val surfaces = listOf(bg, bg2, sf)
        return t.copy(bg = bg, bg2 = bg2, surface = sf,
            text = ColorOps.ensure(t.text, surfaces, 7.0), text2 = ColorOps.ensure(t.text2, surfaces, 4.5),
            accent = ColorOps.ensure(t.accent, surfaces, 4.5), accent2 = ColorOps.ensure(t.accent2, surfaces, 4.5),
            progress = ColorOps.ensure(t.progress, surfaces, 3.0), border = ColorOps.opaque(t.border))
    }

    // ------------------------------------------------------------------ light / dark counterparts
    /** The theme in the requested polarity: itself when it already has it, otherwise its derived counterpart. */
    fun forAppearance(t: AppTheme, dark: Boolean): AppTheme =
        if (isDark(t) == dark) sanitize(t) else counterpart(t, dark)

    /**
     * Derives the other polarity of a palette: hue and character stay, lightness is re-planned. Surfaces keep a tint of the page hue,
     * accents are moved to a readable lightness for the new surfaces. [toDark] = the polarity wanted.
     */
    fun counterpart(t: AppTheme, toDark: Boolean): AppTheme {
        if (!toDark && t.id == "default") return sanitize(DEFAULT_LIGHT.copy(name = t.name, builtin = t.builtin, style = t.style, gameAccents = t.gameAccents, basePreset = t.basePreset))
        val hb = ColorMath.toHsl(ColorOps.opaque(t.bg))
        val h = hb[0]
        val ha = ColorMath.toHsl(ColorOps.opaque(t.accent)); val ha2 = ColorMath.toHsl(ColorOps.opaque(t.accent2))
        val hp = ColorMath.toHsl(ColorOps.opaque(t.progress))
        return if (toDark) {
            val s = (hb[1] * 0.9f).coerceIn(0.08f, 0.55f)
            sanitize(t.copy(
                bg = ColorOps.hsl(h, s, 0.06f), bg2 = ColorOps.hsl(h, s, 0.095f), surface = ColorOps.hsl(h, s * 0.95f, 0.135f),
                accent = ColorOps.hsl(ha[0], ha[1].coerceAtLeast(0.35f), maxOf(ha[2], 0.64f)), accent2 = ColorOps.hsl(ha2[0], ha2[1].coerceAtLeast(0.35f), maxOf(ha2[2], 0.66f)),
                text = ColorOps.hsl(h, 0.15f, 0.95f), text2 = ColorOps.hsl(h, 0.12f, 0.74f), border = ColorOps.hsl(h, s * 0.9f, 0.24f),
                progress = ColorOps.hsl(hp[0], hp[1].coerceAtLeast(0.35f), maxOf(hp[2], 0.64f))))
        } else {
            val s = (hb[1] * 0.6f).coerceIn(0.05f, 0.45f)
            sanitize(t.copy(
                bg = ColorOps.hsl(h, s, 0.968f), bg2 = ColorOps.hsl(h, s, 0.94f), surface = ColorOps.hsl(h, s * 0.95f, 0.91f),
                accent = ColorOps.hsl(ha[0], ha[1].coerceAtLeast(0.40f), minOf(ha[2], 0.38f)), accent2 = ColorOps.hsl(ha2[0], ha2[1].coerceAtLeast(0.40f), minOf(ha2[2], 0.36f)),
                text = ColorOps.hsl(h, 0.30f, 0.09f), text2 = ColorOps.hsl(h, 0.18f, 0.30f), border = ColorOps.hsl(h, s, 0.80f),
                progress = ColorOps.hsl(hp[0], hp[1].coerceAtLeast(0.40f), minOf(hp[2], 0.38f))))
        }
    }

    // ------------------------------------------------------------------ theme -> scheme
    /** Full colour roles for [t] in the requested polarity (the palette is derived first if it has the other one). Always readable. */
    fun schemeOf(t0: AppTheme, dark: Boolean): DnaScheme {
        val t = forAppearance(t0, dark)
        val m = HashMap<Role, Int>()
        m[Role.BG] = t.bg; m[Role.BG2] = t.bg2; m[Role.LOW] = ColorOps.mix(t.bg, t.surface, 0.5f); m[Role.CARD] = t.surface
        m[Role.HIGH] = ColorOps.mix(t.surface, t.text, if (dark) 0.08f else 0.06f)
        m[Role.TEXT] = t.text; m[Role.MUTED] = t.text2
        m[Role.OUTLINE] = ColorOps.mix(t.border, t.text2, 0.35f); m[Role.OUTLINE_VARIANT] = t.border
        m[Role.PRIMARY] = t.accent; m[Role.ON_PRIMARY] = ColorOps.readableOn(t.accent, ColorMath.toHsl(t.accent)[0])
        m[Role.PRIMARY_CONTAINER] = ColorOps.mix(t.surface, t.accent, if (dark) 0.28f else 0.16f); m[Role.ON_PRIMARY_CONTAINER] = t.text
        m[Role.SECONDARY] = t.accent2; m[Role.ON_SECONDARY] = ColorOps.readableOn(t.accent2, ColorMath.toHsl(t.accent2)[0])
        m[Role.SECONDARY_CONTAINER] = ColorOps.mix(t.surface, t.accent2, if (dark) 0.18f else 0.12f); m[Role.ON_SECONDARY_CONTAINER] = t.text
        m[Role.TERTIARY] = t.accent2; m[Role.ON_TERTIARY] = m[Role.ON_SECONDARY]!!
        m[Role.TERTIARY_CONTAINER] = ColorOps.mix(t.surface, t.accent2, if (dark) 0.22f else 0.14f); m[Role.ON_TERTIARY_CONTAINER] = t.text
        fixedStatusColors(dark, m)
        m[Role.PROGRESS] = t.progress; m[Role.GLOW] = t.accent
        m[Role.HERO_TOP] = ColorOps.mix(t.bg2, t.accent, if (dark) 0.14f else 0.08f); m[Role.HERO_BOTTOM] = t.bg
        return DnaRules.repair(DnaScheme.of(dark, m)!!)
    }

    /** Error / success / warning are semantic and do not follow the theme hue; only their lightness depends on the polarity. */
    fun fixedStatusColors(dark: Boolean, m: MutableMap<Role, Int>) {
        m[Role.ERROR] = if (dark) 0xFFFF6B7A.toInt() else 0xFFB3261E.toInt()
        m[Role.ON_ERROR] = if (dark) 0xFF2B0A0E.toInt() else 0xFFFFFFFF.toInt()
        m[Role.ERROR_CONTAINER] = if (dark) 0xFF4A1A22.toInt() else 0xFFF9DEDC.toInt()
        m[Role.ON_ERROR_CONTAINER] = if (dark) 0xFFFFD9DD.toInt() else 0xFF410E0B.toInt()
        m[Role.SUCCESS] = if (dark) 0xFF4ADE80.toInt() else 0xFF1B7F3B.toInt()
        m[Role.WARNING] = if (dark) 0xFFFBBF24.toInt() else 0xFF8A5A00.toInt()
    }

    /** The DNA of a theme: both appearances derived from its palette, composed with its own style. Used by the settings preview and export. */
    fun dnaOf(t: AppTheme): DesignDna = DesignDna(DNA_SCHEMA, "theme:${t.id}", t.name, if (t.id == "default") DnaSource.DEFAULT else DnaSource.THEME,
        "theme", 1f, DnaHash.seed(t.id), isDark(t), schemeOf(t, false), schemeOf(t, true), t.style)

    /** The theme's palette taken from one scheme of a DNA (used when a DNA is imported as a custom profile). */
    fun themeFromScheme(id: String, name: String, s: DnaScheme, style: StyleSpec) = AppTheme(id, name.trim().ifBlank { "My theme" }.take(32), false,
        s.bg, s.bg2, s.card, s.primary, s.secondary, s.text, s.muted, s.outlineVariant, s[Role.PROGRESS], style = style)
}

/** Small deterministic hash helpers (seeds and ids must not depend on the JVM / process). */
object DnaHash {
    fun seed(s: String): Long {
        var h = 0xcbf29ce484222325UL.toLong()
        for (ch in s) { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
        return h
    }
}
