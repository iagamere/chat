package com.abdo.ps4monitor

/**
 * Design DNA: the typed description of one visual identity (a game's, a theme's or the app's own).
 *
 *  - [DnaScheme] = every colour role, for ONE appearance (light or dark). A DNA always carries both.
 *  - [StyleSpec] = how the interface is composed: shape family, borders, separators, motifs, texture, atmosphere, type, density, motion.
 *
 * The UI never asks "which game is this"; it only reads these values (see DnaCompose.kt / DnaDecor.kt). Pure Kotlin on purpose.
 */

const val DNA_SCHEMA = 2

// ---------------------------------------------------------------- vocabulary
enum class ShapeFamily { SOFT, CRISP, ANGULAR, ORGANIC }
enum class MotifKind { NONE, WAVES, RAYS, SLASHES, DOTS, VINES }
enum class BorderStyle { NONE, HAIRLINE, DOUBLE, STRONG, ACCENT_EDGE }
enum class DividerStyle { PLAIN, BRUSH, SLASH, MIST, DIAMOND, WAVE }
enum class TextureKind { NONE, PAPER, GRAIN }
enum class ParticleKind { NONE, MOTES, EMBERS }
enum class DisplayFace { SANS, SERIF, CONDENSED }
enum class SpacingDensity { COMPACT, COMFORTABLE, AIRY }
enum class DnaSource { DEFAULT, THEME, GAME, IMPORTED }

/** [radius]: corner radius (dp) of a "large" surface; [cut]: diagonal cut (dp) used by the angular family. */
data class ShapeSpec(val family: ShapeFamily, val radius: Float, val cut: Float)
/** [motifAlpha]: strength of the background motif (0..0.2); [borderWidth] in dp; [cornerMark]: small corner ornament on panels. */
data class OrnamentSpec(val motif: MotifKind, val motifAlpha: Float, val border: BorderStyle, val borderWidth: Float, val divider: DividerStyle, val cornerMark: Boolean)
data class TextureSpec(val kind: TextureKind, val strength: Float)
/** Light and air: [glow] soft accent light, [rays] diagonal light shafts, [angle] their direction in degrees, [vignette] edge darkening. */
data class AtmosphereSpec(val glow: Float, val rays: Float, val angle: Float, val vignette: Float, val particles: ParticleKind, val particleCount: Int)
/** [tracking] in em; it is ignored for Arabic, where letter spacing would break the joined letters. */
data class TypeSpec(val face: DisplayFace, val weight: Int, val tracking: Float)
data class LayoutSpec(val density: SpacingDensity)
data class MotionSpec(val animated: Boolean, val speed: Float)

/** The composition half of a DNA. [id] names a preset ("plain", "paper", ...) or "custom". */
data class StyleSpec(
    val id: String, val shape: ShapeSpec, val ornament: OrnamentSpec, val texture: TextureSpec,
    val atmosphere: AtmosphereSpec, val type: TypeSpec, val layout: LayoutSpec, val motion: MotionSpec,
) {
    /** The same style with every fractional value rounded to 3 decimals: exactly what a JSON file can carry, so export / import is lossless. */
    fun normalized(): StyleSpec {
        fun r(x: Float) = Math.round(x * 1000f) / 1000f
        return copy(shape = shape.copy(radius = r(shape.radius), cut = r(shape.cut)), ornament = ornament.copy(motifAlpha = r(ornament.motifAlpha), borderWidth = r(ornament.borderWidth)),
            texture = texture.copy(strength = r(texture.strength)), atmosphere = atmosphere.copy(glow = r(atmosphere.glow), rays = r(atmosphere.rays), angle = r(atmosphere.angle), vignette = r(atmosphere.vignette)),
            type = type.copy(tracking = r(type.tracking)), motion = motion.copy(speed = r(motion.speed)))
    }
    /** True when nothing decorative is drawn (the app's classic look): lets the UI skip all overlay work. */
    val isPlain: Boolean get() = ornament.motif == MotifKind.NONE && texture.kind == TextureKind.NONE && atmosphere.glow <= 0.01f &&
        atmosphere.rays <= 0.01f && atmosphere.vignette <= 0.01f && atmosphere.particles == ParticleKind.NONE
}

// ---------------------------------------------------------------- colour roles
/** One entry per colour role. [key] is the JSON name. */
enum class Role(val key: String) {
    BG("bg"), BG2("bg2"), LOW("low"), CARD("card"), HIGH("high"),
    TEXT("text"), MUTED("muted"), OUTLINE("outline"), OUTLINE_VARIANT("outlineVariant"),
    PRIMARY("primary"), ON_PRIMARY("onPrimary"), PRIMARY_CONTAINER("primaryContainer"), ON_PRIMARY_CONTAINER("onPrimaryContainer"),
    SECONDARY("secondary"), ON_SECONDARY("onSecondary"), SECONDARY_CONTAINER("secondaryContainer"), ON_SECONDARY_CONTAINER("onSecondaryContainer"),
    TERTIARY("tertiary"), ON_TERTIARY("onTertiary"), TERTIARY_CONTAINER("tertiaryContainer"), ON_TERTIARY_CONTAINER("onTertiaryContainer"),
    ERROR("error"), ON_ERROR("onError"), ERROR_CONTAINER("errorContainer"), ON_ERROR_CONTAINER("onErrorContainer"),
    SUCCESS("success"), WARNING("warning"), PROGRESS("progress"), GLOW("glow"), HERO_TOP("heroTop"), HERO_BOTTOM("heroBottom");
    companion object { fun byKey(k: String) = values().firstOrNull { it.key == k } }
}

/**
 * All colours of one appearance. [bg] page, [bg2]/[low]/[card]/[high] = container levels from lowest to highest emphasis.
 * Roles are semantic: text colours are generated per background, never derived from a single one.
 */
class DnaScheme(val dark: Boolean, private val c: IntArray) {
    init { require(c.size == Role.values().size) { "scheme needs ${Role.values().size} colours" } }
    operator fun get(r: Role): Int = c[r.ordinal]
    fun with(vararg changes: Pair<Role, Int>): DnaScheme { val n = c.copyOf(); for ((r, v) in changes) n[r.ordinal] = ColorOps.opaque(v); return DnaScheme(dark, n) }
    fun toMap(): Map<Role, Int> = Role.values().associateWith { c[it.ordinal] }
    val bg get() = get(Role.BG); val bg2 get() = get(Role.BG2); val low get() = get(Role.LOW); val card get() = get(Role.CARD); val high get() = get(Role.HIGH)
    val text get() = get(Role.TEXT); val muted get() = get(Role.MUTED)
    val primary get() = get(Role.PRIMARY); val secondary get() = get(Role.SECONDARY); val tertiary get() = get(Role.TERTIARY)
    val outline get() = get(Role.OUTLINE); val outlineVariant get() = get(Role.OUTLINE_VARIANT)
    override fun equals(other: Any?) = other is DnaScheme && other.dark == dark && other.c.contentEquals(c)
    override fun hashCode() = c.contentHashCode() * 31 + if (dark) 1 else 0
    override fun toString() = "DnaScheme(dark=$dark, bg=${ColorOps.toHex(bg)}, primary=${ColorOps.toHex(primary)})"

    companion object {
        fun of(dark: Boolean, m: Map<Role, Int>): DnaScheme? {
            val a = IntArray(Role.values().size)
            for (r in Role.values()) a[r.ordinal] = ColorOps.opaque(m[r] ?: return null)
            return DnaScheme(dark, a)
        }
    }
}

/** What was measured while generating a DNA; recomputed on import, never trusted from a file. */
data class ContrastReport(val worstText: Double, val worstGraphic: Double) {
    val ok: Boolean get() = worstText >= 4.5 - 1e-9 && worstGraphic >= 3.0 - 1e-9
}

/** The complete identity. [nativeDark]: the artwork's own polarity (metadata only; the user's appearance setting decides what is shown). */
class DesignDna(
    val schema: Int, val id: String, val name: String, val source: DnaSource, val fingerprint: String, val confidence: Float,
    val seed: Long, val nativeDark: Boolean, val light: DnaScheme, val dark: DnaScheme, val style: StyleSpec,
) {
    init { require(!light.dark && dark.dark) { "light/dark schemes swapped" } }
    fun scheme(isDark: Boolean) = if (isDark) dark else light
    val accessibility: ContrastReport by lazy {
        val a = DnaRules.report(light); val b = DnaRules.report(dark)
        ContrastReport(minOf(a.worstText, b.worstText), minOf(a.worstGraphic, b.worstGraphic))
    }
    fun withStyle(s: StyleSpec) = DesignDna(schema, id, name, source, fingerprint, confidence, seed, nativeDark, light, dark, s)
    fun withName(n: String) = DesignDna(schema, id, n, source, fingerprint, confidence, seed, nativeDark, light, dark, style)
    override fun toString() = "DesignDna($id, ${style.id}, conf=$confidence)"
}

/** What the UI actually renders with: one resolved appearance of one identity. */
class Look(val dark: Boolean, val scheme: DnaScheme, val style: StyleSpec, val seed: Long, val dna: DesignDna?)

// ---------------------------------------------------------------- style presets (vocabulary shared by games and custom themes)
object DnaStyles {
    private fun style(id: String, shape: ShapeSpec, orn: OrnamentSpec, tex: TextureSpec, atm: AtmosphereSpec, type: TypeSpec, lay: LayoutSpec, mo: MotionSpec) =
        StyleSpec(id, shape, orn, tex, atm, type, lay, mo)
    private val noAtm = AtmosphereSpec(0f, 0f, 0f, 0f, ParticleKind.NONE, 0)

    /** The app's classic look: soft rounded cards, hairline borders, nothing drawn on top. */
    val PLAIN = style("plain", ShapeSpec(ShapeFamily.SOFT, 18f, 0f),
        OrnamentSpec(MotifKind.NONE, 0f, BorderStyle.HAIRLINE, 1f, DividerStyle.PLAIN, false), TextureSpec(TextureKind.NONE, 0f), noAtm,
        TypeSpec(DisplayFace.SANS, 600, 0f), LayoutSpec(SpacingDensity.COMFORTABLE), MotionSpec(false, 0f))
    /** Aged paper and ink: low radii, fine double rules, brush separators, wave pattern, serif headings, lots of air. */
    val PAPER = style("paper", ShapeSpec(ShapeFamily.CRISP, 6f, 0f),
        OrnamentSpec(MotifKind.WAVES, 0.07f, BorderStyle.DOUBLE, 1f, DividerStyle.BRUSH, true), TextureSpec(TextureKind.PAPER, 0.05f),
        AtmosphereSpec(0f, 0f, 0f, 0.12f, ParticleKind.NONE, 0),
        TypeSpec(DisplayFace.SERIF, 600, 0.01f), LayoutSpec(SpacingDensity.AIRY), MotionSpec(false, 0f))
    /** Misty and luminous: organic contours, light shafts, soft particles. */
    val MIST = style("mist", ShapeSpec(ShapeFamily.ORGANIC, 22f, 0f),
        OrnamentSpec(MotifKind.RAYS, 0.10f, BorderStyle.HAIRLINE, 1f, DividerStyle.MIST, false), TextureSpec(TextureKind.NONE, 0f),
        AtmosphereSpec(0.6f, 0.5f, 100f, 0.15f, ParticleKind.MOTES, 18),
        TypeSpec(DisplayFace.SANS, 500, 0.01f), LayoutSpec(SpacingDensity.COMFORTABLE), MotionSpec(true, 0.5f))
    /** Sharp and dramatic: cut corners, diagonal slashes, strong edges, sparks. */
    val ANGULAR = style("angular", ShapeSpec(ShapeFamily.ANGULAR, 12f, 10f),
        OrnamentSpec(MotifKind.SLASHES, 0.08f, BorderStyle.ACCENT_EDGE, 2f, DividerStyle.SLASH, true), TextureSpec(TextureKind.GRAIN, 0.05f),
        AtmosphereSpec(0.45f, 0f, 0f, 0.3f, ParticleKind.EMBERS, 16),
        TypeSpec(DisplayFace.CONDENSED, 700, 0.02f), LayoutSpec(SpacingDensity.COMPACT), MotionSpec(true, 0.8f))
    /** Natural and flowing: asymmetric organic corners, vines, pollen. */
    val ORGANIC = style("organic", ShapeSpec(ShapeFamily.ORGANIC, 26f, 0f),
        OrnamentSpec(MotifKind.VINES, 0.08f, BorderStyle.HAIRLINE, 1f, DividerStyle.WAVE, false), TextureSpec(TextureKind.GRAIN, 0.03f),
        AtmosphereSpec(0.25f, 0f, 0f, 0.1f, ParticleKind.MOTES, 12),
        TypeSpec(DisplayFace.SANS, 600, 0f), LayoutSpec(SpacingDensity.COMFORTABLE), MotionSpec(true, 0.4f))

    val ALL: List<StyleSpec> = listOf(PLAIN, PAPER, MIST, ANGULAR, ORGANIC)
    fun byId(id: String?): StyleSpec? = ALL.firstOrNull { it.id == id }
    fun label(id: String): Pair<String, String> = when (id) {
        "paper" -> "Paper & ink" to "ورق وحبر"; "mist" -> "Mist" to "ضباب"; "angular" -> "Angular" to "حادّ"
        "organic" -> "Organic" to "عضوي"; "custom" -> "Custom" to "مخصص"; else -> "Plain" to "بسيط"
    }

    /** Scales the soft effects of a template: [t] 0 = barely there, 1 = full. Discrete choices (shape family, motif, divider) are kept. */
    fun scaled(s: StyleSpec, t: Float): StyleSpec {
        val k = t.coerceIn(0.25f, 1f)
        return s.copy(
            ornament = s.ornament.copy(motifAlpha = (s.ornament.motifAlpha * k).coerceIn(0f, 0.2f)),
            texture = s.texture.copy(strength = (s.texture.strength * k).coerceIn(0f, 0.12f)),
            atmosphere = s.atmosphere.copy(glow = (s.atmosphere.glow * k).coerceIn(0f, 1f), rays = (s.atmosphere.rays * k).coerceIn(0f, 1f),
                vignette = (s.atmosphere.vignette * k).coerceIn(0f, 0.5f), particleCount = (s.atmosphere.particleCount * k).toInt().coerceIn(0, 40)))
    }
}
