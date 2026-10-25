package com.abdo.ps4monitor

/**
 * Versioned JSON for the design system.
 *
 *  - DNA files (`"format":"ps4monitor.dna"`, `"v":2`): a complete identity, both appearances.
 *  - Theme files (profiles): v1 = the original 9-colour format of app 6.1 (still read), v2 = the same plus an optional `style`.
 *
 * Everything read from a file is validated field by field: colours must be hex, numbers are clamped, unknown enum values fall back to the
 * default, sizes are capped, and a DNA's colours are repaired for readability. Newer, unsupported versions are rejected with a reason
 * instead of being half-read.
 */
sealed class Parsed<out T> {
    class Ok<T>(val value: T, val notes: List<String> = emptyList()) : Parsed<T>()
    class Err(val reason: String) : Parsed<Nothing>()
}

object DnaJson {
    const val FORMAT = "ps4monitor.dna"
    const val MAX_CHARS = 24_000
    const val THEME_VERSION = 2

    enum class Kind { DNA, THEME, INVALID }

    /** What a pasted text is, without importing it. */
    fun detect(text: String): Kind {
        val o = MiniJson.tryParse(text.trim(), MAX_CHARS) as? Map<*, *> ?: return Kind.INVALID
        return if (o["format"] == FORMAT) Kind.DNA else if (o.containsKey("bg") && o.containsKey("accent")) Kind.THEME else Kind.INVALID
    }

    // ------------------------------------------------------------------ small readers
    private fun Map<*, *>.str(k: String, def: String = "") = (this[k] as? String) ?: def
    private fun Map<*, *>.num(k: String, def: Float, lo: Float, hi: Float): Float { val d = (this[k] as? Double)?.toFloat() ?: return def; return d.coerceIn(lo, hi) }
    private fun Map<*, *>.int(k: String, def: Int, lo: Int, hi: Int): Int { val d = (this[k] as? Double) ?: return def; return d.toInt().coerceIn(lo, hi) }
    private fun Map<*, *>.bool(k: String, def: Boolean) = (this[k] as? Boolean) ?: def
    private fun Map<*, *>.obj(k: String) = this[k] as? Map<*, *>
    private inline fun <reified E : Enum<E>> Map<*, *>.enum(k: String, def: E): E = enumValues<E>().firstOrNull { it.name == this[k] } ?: def

    // ------------------------------------------------------------------ style
    fun styleToMap(s: StyleSpec): Map<String, Any?> = linkedMapOf(
        "id" to s.id,
        "shape" to linkedMapOf("family" to s.shape.family.name, "radius" to s.shape.radius, "cut" to s.shape.cut),
        "ornament" to linkedMapOf("motif" to s.ornament.motif.name, "motifAlpha" to s.ornament.motifAlpha, "border" to s.ornament.border.name,
            "borderWidth" to s.ornament.borderWidth, "divider" to s.ornament.divider.name, "cornerMark" to s.ornament.cornerMark),
        "texture" to linkedMapOf("kind" to s.texture.kind.name, "strength" to s.texture.strength),
        "atmosphere" to linkedMapOf("glow" to s.atmosphere.glow, "rays" to s.atmosphere.rays, "angle" to s.atmosphere.angle, "vignette" to s.atmosphere.vignette,
            "particles" to s.atmosphere.particles.name, "particleCount" to s.atmosphere.particleCount),
        "type" to linkedMapOf("face" to s.type.face.name, "weight" to s.type.weight, "tracking" to s.type.tracking),
        "layout" to linkedMapOf("density" to s.layout.density.name),
        "motion" to linkedMapOf("animated" to s.motion.animated, "speed" to s.motion.speed),
    )

    /** A preset id ("mist") or a full style object. Anything else, or missing values, fall back to the plain style. */
    fun styleFromAny(v: Any?): StyleSpec {
        if (v is String) return DnaStyles.byId(v) ?: DnaStyles.PLAIN
        val o = v as? Map<*, *> ?: return DnaStyles.PLAIN
        val d = DnaStyles.byId(o.str("id")) ?: DnaStyles.PLAIN
        val sh = o.obj("shape"); val orn = o.obj("ornament"); val tx = o.obj("texture"); val at = o.obj("atmosphere")
        val ty = o.obj("type"); val la = o.obj("layout"); val mo = o.obj("motion")
        val id = o.str("id").take(24).filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "custom" }
        return StyleSpec(id,
            ShapeSpec(sh?.enum("family", d.shape.family) ?: d.shape.family, sh?.num("radius", d.shape.radius, 0f, 40f) ?: d.shape.radius, sh?.num("cut", d.shape.cut, 0f, 24f) ?: d.shape.cut),
            OrnamentSpec(orn?.enum("motif", d.ornament.motif) ?: d.ornament.motif, orn?.num("motifAlpha", d.ornament.motifAlpha, 0f, 0.2f) ?: d.ornament.motifAlpha,
                orn?.enum("border", d.ornament.border) ?: d.ornament.border, orn?.num("borderWidth", d.ornament.borderWidth, 0f, 4f) ?: d.ornament.borderWidth,
                orn?.enum("divider", d.ornament.divider) ?: d.ornament.divider, orn?.bool("cornerMark", d.ornament.cornerMark) ?: d.ornament.cornerMark),
            TextureSpec(tx?.enum("kind", d.texture.kind) ?: d.texture.kind, tx?.num("strength", d.texture.strength, 0f, 0.12f) ?: d.texture.strength),
            AtmosphereSpec(at?.num("glow", d.atmosphere.glow, 0f, 1f) ?: d.atmosphere.glow, at?.num("rays", d.atmosphere.rays, 0f, 1f) ?: d.atmosphere.rays,
                at?.num("angle", d.atmosphere.angle, -360f, 360f) ?: d.atmosphere.angle, at?.num("vignette", d.atmosphere.vignette, 0f, 0.5f) ?: d.atmosphere.vignette,
                at?.enum("particles", d.atmosphere.particles) ?: d.atmosphere.particles, at?.int("particleCount", d.atmosphere.particleCount, 0, 40) ?: d.atmosphere.particleCount),
            TypeSpec(ty?.enum("face", d.type.face) ?: d.type.face, ty?.int("weight", d.type.weight, 300, 900) ?: d.type.weight, ty?.num("tracking", d.type.tracking, -0.05f, 0.15f) ?: d.type.tracking),
            LayoutSpec(la?.enum("density", d.layout.density) ?: d.layout.density),
            MotionSpec(mo?.bool("animated", d.motion.animated) ?: d.motion.animated, mo?.num("speed", d.motion.speed, 0f, 2f) ?: d.motion.speed)).normalized()
    }

    // ------------------------------------------------------------------ DNA
    fun toMap(d: DesignDna): Map<String, Any?> {
        fun sch(s: DnaScheme): Map<String, Any?> { val m = LinkedHashMap<String, Any?>(); for (r in Role.values()) m[r.key] = ColorOps.toHex(s[r]); return m }
        return linkedMapOf("format" to FORMAT, "v" to DNA_SCHEMA, "id" to d.id, "name" to d.name, "source" to d.source.name,
            "fingerprint" to d.fingerprint, "confidence" to d.confidence, "seed" to java.lang.Long.toHexString(d.seed).padStart(16, '0'), "nativeDark" to d.nativeDark,
            "style" to styleToMap(d.style), "light" to sch(d.light), "dark" to sch(d.dark),
            "contrast" to linkedMapOf("text" to d.accessibility.worstText, "graphic" to d.accessibility.worstGraphic))
    }
    fun toJson(d: DesignDna, pretty: Boolean = false) = MiniJson.write(toMap(d), pretty)

    fun parseDna(text: String): Parsed<DesignDna> {
        if (text.length > MAX_CHARS) return Parsed.Err("The file is too large to be a design DNA.")
        val o = (try { MiniJson.parse(text.trim(), MAX_CHARS) } catch (e: MiniJson.JsonException) { return Parsed.Err("Not valid JSON: ${e.message}") }) as? Map<*, *>
            ?: return Parsed.Err("Not a JSON object.")
        if (o["format"] != FORMAT) return Parsed.Err("This is not a design DNA file.")
        val v = (o["v"] as? Double)?.toInt() ?: return Parsed.Err("Missing version.")
        if (v > DNA_SCHEMA) return Parsed.Err("This DNA was made by a newer app version (v$v); update the app to use it.")
        if (v < 2) return Parsed.Err("Unsupported DNA version v$v.")
        val notes = ArrayList<String>()
        fun scheme(key: String, dark: Boolean): DnaScheme? {
            val m = o.obj(key) ?: run { notes += "missing $key scheme"; return null }
            val colors = HashMap<Role, Int>()
            for (r in Role.values()) {
                val h = m[r.key] as? String ?: run { notes += "$key.${r.key} missing"; return null }
                colors[r] = ColorOps.parseHex(h) ?: run { notes += "$key.${r.key} is not a colour"; return null }
            }
            val raw = DnaScheme.of(dark, colors)!!
            val fixed = DnaRules.repair(raw)
            if (fixed != raw) notes += "$key colours were adjusted to stay readable"
            return fixed
        }
        val light = scheme("light", false); val dark = scheme("dark", true)
        if (light == null || dark == null) return Parsed.Err("Incomplete DNA: " + notes.firstOrNull { !it.contains("adjusted") }.orEmpty())
        val id = o.str("id").filter { it.isLetterOrDigit() || it in ":_.-" }.take(64).ifBlank { return Parsed.Err("Missing id.") }
        val name = o.str("name").trim().take(48).ifBlank { id }
        val seed = o.str("seed").takeIf { it.length in 1..16 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }?.let { java.lang.Long.parseUnsignedLong(it, 16) } ?: DnaHash.seed(id)
        val src = o.enum("source", DnaSource.IMPORTED).let { if (it == DnaSource.DEFAULT) DnaSource.IMPORTED else it }
        val fp = o.str("fingerprint").filter { it.isLetterOrDigit() }.take(32).ifBlank { "none" }
        return Parsed.Ok(DesignDna(DNA_SCHEMA, id, name, src, fp, o.num("confidence", 0.5f, 0f, 1f), seed, o.bool("nativeDark", true), light, dark, styleFromAny(o["style"])), notes)
    }

    // ------------------------------------------------------------------ themes (profiles)
    fun themeToMap(t: AppTheme): Map<String, Any?> = linkedMapOf("v" to THEME_VERSION, "id" to t.id, "name" to t.name,
        "bg" to ColorOps.toHex(t.bg), "bg2" to ColorOps.toHex(t.bg2), "surface" to ColorOps.toHex(t.surface), "accent" to ColorOps.toHex(t.accent), "accent2" to ColorOps.toHex(t.accent2),
        "text" to ColorOps.toHex(t.text), "text2" to ColorOps.toHex(t.text2), "border" to ColorOps.toHex(t.border), "progress" to ColorOps.toHex(t.progress),
        "gameAccents" to t.gameAccents, "base" to t.basePreset,
        "style" to (if (DnaStyles.byId(t.style.id) == t.style) t.style.id else styleToMap(t.style)))

    /**
     * Strict import of one theme object: every colour must be valid hex, the name is trimmed, unknown keys are ignored. v1 files (no `style`)
     * and v2 files are accepted; a newer version is rejected (null). [newId] supplies ids for imports that must not reuse the file's id.
     */
    fun themeFromMap(o: Map<*, *>, keepId: Boolean, newId: () -> String): AppTheme? {
        val ver = (o["v"] as? Double)?.toInt() ?: 1
        if (ver > THEME_VERSION || ver < 1) return null
        val name = o.str("name").trim().take(32).ifBlank { return null }
        val bg = ColorOps.parseHex(o.str("bg")) ?: return null; val bg2 = ColorOps.parseHex(o.str("bg2")) ?: return null
        val sf = ColorOps.parseHex(o.str("surface")) ?: return null; val ac = ColorOps.parseHex(o.str("accent")) ?: return null
        val ac2 = ColorOps.parseHex(o.str("accent2")) ?: return null; val tx = ColorOps.parseHex(o.str("text")) ?: return null
        val tx2 = ColorOps.parseHex(o.str("text2")) ?: return null; val bd = ColorOps.parseHex(o.str("border")) ?: return null
        val pr = ColorOps.parseHex(o.str("progress")) ?: return null
        val t = AppTheme(if (keepId) o.str("id").filter { it.isLetterOrDigit() || it in "_-" }.take(32).ifBlank { newId() } else newId(), name, false,
            bg, bg2, sf, ac, ac2, tx, tx2, bd, pr,
            o.bool("gameAccents", false), o.str("base").takeIf { ThemeCore.preset(it) != null }, if (o.containsKey("style")) styleFromAny(o["style"]) else DnaStyles.PLAIN)
        return ThemeCore.sanitize(t)
    }

    /** Result of reading the stored profile list: what could be read, and the raw text of every entry that could not (kept, never dropped). */
    class ListResult(val themes: List<AppTheme>, val rejected: List<String>)

    fun parseThemeList(raw: String, newId: () -> String): ListResult {
        val any = MiniJson.tryParse(raw, 400_000)
        val arr = any as? List<*> ?: return ListResult(emptyList(), if (raw.isBlank() || raw.trim() == "[]") emptyList() else listOf(raw))
        val ok = ArrayList<AppTheme>(); val bad = ArrayList<String>()
        for (e in arr) {
            val t = (e as? Map<*, *>)?.let { themeFromMap(it, true, newId) }
            if (t != null && ok.none { it.id == t.id }) ok += t else bad += MiniJson.write(e)
        }
        return ListResult(ok, bad)
    }
    fun themeListToJson(l: List<AppTheme>) = MiniJson.write(l.map { themeToMap(it) })
}
