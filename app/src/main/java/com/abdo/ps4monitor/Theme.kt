package com.abdo.ps4monitor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Observable state + persistence of the theme choice. The colour logic itself lives in [ThemeCore] / [LookResolver] (pure, unit-tested);
 * how a [Look] is rendered lives in DnaCompose.kt. The light / dark / system choice is [Store.theme] and is independent of everything here.
 *
 * Stored in the "app" SharedPreferences: th_mode, th_preset, th_profile, th_gameaccent, th_profiles (JSON list, v1 or v2 entries),
 * th_schema (2 once migrated), th_profiles_v1_backup (the untouched pre-migration text), th_profiles_rejected (entries that could
 * not be read: kept, never deleted), fx (effects level 0..2).
 */
object ThemeEngine {
    val DEFAULT: AppTheme get() = ThemeCore.DEFAULT
    val PRESETS: List<AppTheme> get() = ThemeCore.PRESETS
    fun preset(id: String?) = ThemeCore.preset(id)

    // ------------------------------------------------------------------ state (observable)
    var mode by mutableStateOf(ThemeMode.DEFAULT); private set
    var presetId by mutableStateOf("default"); private set
    var activeProfileId by mutableStateOf<String?>(null); private set
    var profiles by mutableStateOf<List<AppTheme>>(emptyList()); private set
    /** optional: keep the chosen global theme but take the current game's accent colours */
    var applyGameAccents by mutableStateOf(false); private set
    /** decoration level: 0 off, 1 still, 2 animated */
    var fx by mutableIntStateOf(2); private set

    /** The saved choice, without any game-specific adaptation. */
    val base: AppTheme get() = when (mode) {
        ThemeMode.PRESET -> preset(presetId) ?: DEFAULT
        ThemeMode.CUSTOM -> profiles.firstOrNull { it.id == activeProfileId } ?: DEFAULT
        else -> DEFAULT
    }

    fun init(sp: android.content.SharedPreferences) {
        mode = runCatching { ThemeMode.valueOf(sp.getString("th_mode", "DEFAULT")!!) }.getOrDefault(ThemeMode.DEFAULT)
        presetId = sp.getString("th_preset", "default") ?: "default"
        activeProfileId = sp.getString("th_profile", null)
        applyGameAccents = sp.getBoolean("th_gameaccent", false)
        fx = sp.getInt("fx", 2).coerceIn(0, 2)
        val raw = runCatching { sp.getString("th_profiles", "[]") }.getOrNull() ?: "[]"
        val res = DnaJson.parseThemeList(raw) { newId() }
        profiles = res.themes
        val ed = sp.edit()
        if (sp.getInt("th_schema", 1) < 2) {
            // one-time upgrade: keep the original text, then write the v2 format
            if (raw.isNotBlank() && raw.trim() != "[]") ed.putString("th_profiles_v1_backup", raw)
            ed.putString("th_profiles", DnaJson.themeListToJson(res.themes)).putInt("th_schema", 2)
        }
        if (res.rejected.isNotEmpty()) {
            val old = runCatching { (MiniJson.tryParse(sp.getString("th_profiles_rejected", "[]") ?: "[]") as? List<*>)?.map { it.toString() } }.getOrNull() ?: emptyList()
            ed.putString("th_profiles_rejected", MiniJson.write((old + res.rejected).distinct().takeLast(50)))
        }
        ed.apply()
        if (mode == ThemeMode.CUSTOM && profiles.none { it.id == activeProfileId }) mode = ThemeMode.DEFAULT
        if (mode == ThemeMode.PRESET && preset(presetId) == null) mode = ThemeMode.DEFAULT
    }
    private fun sp() = Store.sp
    fun chooseMode(m: ThemeMode) { mode = m; sp().edit().putString("th_mode", m.name).apply() }
    fun selectPreset(id: String) { if (preset(id) == null) return; presetId = id; chooseMode(ThemeMode.PRESET); sp().edit().putString("th_preset", id).apply() }
    fun selectProfile(id: String) { if (profiles.none { it.id == id }) return; activeProfileId = id; chooseMode(ThemeMode.CUSTOM); sp().edit().putString("th_profile", id).apply() }
    fun setGameAccents(b: Boolean) { applyGameAccents = b; sp().edit().putBoolean("th_gameaccent", b).apply() }
    fun setFx(level: Int) { fx = level.coerceIn(0, 2); sp().edit().putInt("fx", fx).apply() }
    fun resetToDefault() { presetId = "default"; chooseMode(ThemeMode.DEFAULT); sp().edit().putString("th_preset", "default").apply() }

    // ------------------------------------------------------------------ profiles
    private fun persist() = sp().edit().putString("th_profiles", DnaJson.themeListToJson(profiles)).apply()
    fun newId() = "u" + java.lang.Long.toString(System.nanoTime(), 36)
    /** Saves a user profile (a built-in preset is never overwritten: it is copied under a new id by the caller). */
    fun saveProfile(t: AppTheme, activate: Boolean) {
        val s = sanitize(t.copy(builtin = false, name = t.name.trim().ifBlank { "My theme" }.take(32)))
        profiles = if (profiles.any { it.id == s.id }) profiles.map { if (it.id == s.id) s else it } else profiles + s
        persist(); if (activate) selectProfile(s.id)
    }
    fun duplicate(t: AppTheme): AppTheme { val d = t.copy(id = newId(), name = t.name.take(26) + " copy", builtin = false, basePreset = if (t.builtin) t.id else t.basePreset); saveProfile(d, false); return d }
    fun deleteProfile(id: String) {
        profiles = profiles.filter { it.id != id }; persist()
        if (activeProfileId == id) { activeProfileId = null; sp().edit().remove("th_profile").apply(); if (mode == ThemeMode.CUSTOM) chooseMode(ThemeMode.DEFAULT) }
    }
    fun editableCopy(t: AppTheme) = if (t.builtin) t.copy(id = newId(), name = t.name + " (mine)", builtin = false, basePreset = t.id) else t

    // ------------------------------------------------------------------ validation helpers (delegates)
    fun parseHex(s: String): Int? = ThemeCore.parseHex(s)
    fun hex(c: Int) = ThemeCore.hex(c)
    fun sanitize(t: AppTheme) = ThemeCore.sanitize(t)

    // ------------------------------------------------------------------ looks
    /** Look of ordinary screens in the given appearance. */
    fun look(dark: Boolean): Look = LookResolver.forApp(mode, base, dark)
    /** Look of one game's pages ([game] null = identity not ready: the app look). */
    fun gameLook(game: DesignDna?, dark: Boolean): Look = LookResolver.forGame(mode, base, applyGameAccents, game, dark)
    /** Tokens of any palette in the given appearance (previews, theme cards). */
    fun tokens(t: AppTheme, dark: Boolean): Tokens = tokensOf(ThemeCore.schemeOf(t, dark))

    // ------------------------------------------------------------------ export / import
    fun exportJson(t: AppTheme): String = MiniJson.write(DnaJson.themeToMap(t))

    /**
     * Import from pasted text: a theme file (v1 or v2) becomes a profile; a design DNA file becomes a profile too (its own palette in the
     * artwork's native appearance, plus its style). Returns the new profile or the reason it was refused.
     */
    fun importText(text: String): Parsed<AppTheme> {
        val t = text.trim()
        return when (DnaJson.detect(t)) {
            DnaJson.Kind.THEME -> {
                val o = MiniJson.tryParse(t, DnaJson.MAX_CHARS) as? Map<*, *> ?: return Parsed.Err("Not a valid theme.")
                val th = DnaJson.themeFromMap(o, keepId = false) { newId() } ?: return Parsed.Err("Not a valid theme (or made by a newer app version).")
                saveProfile(th, false); Parsed.Ok(th)
            }
            DnaJson.Kind.DNA -> when (val r = DnaJson.parseDna(t)) {
                is Parsed.Err -> r
                is Parsed.Ok -> { val d = r.value; val th = ThemeCore.themeFromScheme(newId(), d.name, d.scheme(d.nativeDark), d.style); saveProfile(th, false); Parsed.Ok(th, r.notes) }
            }
            DnaJson.Kind.INVALID -> Parsed.Err("This is not a valid theme.")
        }
    }
}
