package com.abdo.ps4monitor

import org.junit.Assert.*
import org.junit.Test

class DnaJsonTest {
    private val dna = DnaGenerator.generate(CoverAnalyzer.analyze(TestImages.mistLike().px, 128, 128), "CUSA00002", "My \"Game\" é")
    private fun ok(p: Parsed<DesignDna>) = (p as Parsed.Ok).value
    private fun err(p: Parsed<*>) = (p as Parsed.Err).reason

    @Test fun roundTripKeepsEverything() {
        val json = DnaJson.toJson(dna); val back = ok(DnaJson.parseDna(json))
        assertEquals(dna.light, back.light); assertEquals(dna.dark, back.dark); assertEquals(dna.style, back.style)
        assertEquals(dna.seed, back.seed); assertEquals(dna.id, back.id); assertEquals(dna.name, back.name); assertEquals(dna.nativeDark, back.nativeDark)
        assertEquals(dna.fingerprint, back.fingerprint); assertEquals(dna.confidence.toDouble(), back.confidence.toDouble(), 1e-3)
        assertEquals(json, DnaJson.toJson(back))                          // stable output
        assertEquals(DnaJson.toJson(dna, pretty = true).replace(Regex("\\s"), ""), json.replace(Regex("\\s"), ""))
    }
    @Test fun detectsKinds() {
        assertEquals(DnaJson.Kind.DNA, DnaJson.detect(DnaJson.toJson(dna)))
        assertEquals(DnaJson.Kind.THEME, DnaJson.detect(MiniJson.write(DnaJson.themeToMap(ThemeCore.preset("aurora")!!))))
        for (x in listOf("", "{", "[]", "null", "hello", "{\"a\":1}")) assertEquals(x, DnaJson.Kind.INVALID, DnaJson.detect(x))
    }
    @Test fun malformedInputIsRejectedWithAReasonAndNeverThrows() {
        for (x in listOf("", "{", "[]", "null", "42", "\"str\"", "{\"format\":\"ps4monitor.dna\"}", "{\"format\":\"other\",\"v\":2}", "\u0000\u0001", "{\"format\":\"ps4monitor.dna\",\"v\":2,\"light\":{},\"dark\":{}}"))
            assertTrue(x, DnaJson.parseDna(x) is Parsed.Err)
    }
    @Test fun newerVersionIsRefusedNotHalfRead() {
        val j = DnaJson.toJson(dna).replace("\"v\":2", "\"v\":99")
        assertTrue(err(DnaJson.parseDna(j)).contains("newer"))
        assertTrue(DnaJson.parseDna(DnaJson.toJson(dna).replace("\"v\":2", "\"v\":1")) is Parsed.Err)
    }
    @Test fun invalidAndMissingColoursAreRejected() {
        val j = DnaJson.toJson(dna)
        assertTrue(DnaJson.parseDna(j.replaceFirst("\"primary\":\"#", "\"primary\":\"#ZZ")) is Parsed.Err)
        assertTrue(DnaJson.parseDna(j.replaceFirst("\"muted\":", "\"mutedX\":")) is Parsed.Err)
    }
    @Test fun unreadableColoursAreRepairedOnImport() {
        val m = MiniJson.parse(DnaJson.toJson(dna)) as MutableMap<String, Any?>
        val dark = (m["dark"] as MutableMap<String, Any?>); dark["text"] = dark["bg"]; dark["muted"] = dark["bg"]
        val r = DnaJson.parseDna(MiniJson.write(m)) as Parsed.Ok
        assertTrue(DnaRules.check(r.value.dark).isEmpty()); assertTrue(r.notes.any { it.contains("adjusted") })
    }
    @Test fun valuesAreClampedAndUnknownEnumsFallBack() {
        val m = MiniJson.parse(DnaJson.toJson(dna)) as MutableMap<String, Any?>
        val st = m["style"] as MutableMap<String, Any?>
        (st["shape"] as MutableMap<String, Any?>).apply { put("radius", 9999.0); put("family", "NOT_A_FAMILY") }
        (st["atmosphere"] as MutableMap<String, Any?>).apply { put("particleCount", 100000.0); put("glow", -4.0) }
        val s = ok(DnaJson.parseDna(MiniJson.write(m))).style
        assertEquals(40f, s.shape.radius, 0f); assertEquals(dna.style.shape.family, s.shape.family)
        assertEquals(40, s.atmosphere.particleCount); assertEquals(0f, s.atmosphere.glow, 0f)
    }
    @Test fun oversizedAndDeeplyNestedInputIsRefused() {
        assertTrue(DnaJson.parseDna(" ".repeat(DnaJson.MAX_CHARS + 1)) is Parsed.Err)
        assertTrue(DnaJson.parseDna("[".repeat(5000)) is Parsed.Err)
        assertNull(MiniJson.tryParse("{\"a\":".repeat(40) + "1" + "}".repeat(40)))
    }
    @Test fun importedIdsAndNamesAreSanitised() {
        val m = MiniJson.parse(DnaJson.toJson(dna)) as MutableMap<String, Any?>
        m["id"] = "../../evil id<script>"; m["name"] = "  " + "n".repeat(500)
        val d = ok(DnaJson.parseDna(MiniJson.write(m)))
        assertTrue(d.id.all { it.isLetterOrDigit() || it in ":_.-" }); assertTrue(d.name.length <= 48)
    }

    // ---- themes / migration
    private val v1 = """{"v":1,"id":"u1abc","name":"Old profile","bg":"#0D0E10","bg2":"#131519","surface":"#1B1E24","accent":"#9AA7B8","accent2":"#6C7A8C","text":"#EDEFF2","text2":"#A2A9B3","border":"#2B2F37","progress":"#9AA7B8","gameAccents":true,"base":"midnight"}"""
    @Test fun version1ThemesMigrateWithoutLosingData() {
        val t = DnaJson.themeFromMap(MiniJson.parse(v1) as Map<*, *>, true) { "new" }!!
        assertEquals("u1abc", t.id); assertEquals("Old profile", t.name); assertTrue(t.gameAccents); assertEquals("midnight", t.basePreset)
        assertEquals(ColorOps.parseHex("#0D0E10"), t.bg); assertTrue(t.style.isPlain); assertFalse(t.builtin)
        assertEquals(ColorOps.parseHex("#2B2F37"), t.border)
    }
    @Test fun version2ThemesKeepTheirStyleAsPresetIdOrObject() {
        val t = ThemeCore.preset("mistveil")!!.copy(builtin = false, id = "u9")
        val m = DnaJson.themeToMap(t); assertEquals("mist", m["style"])
        assertEquals(DnaStyles.MIST, DnaJson.themeFromMap(MiniJson.parse(MiniJson.write(m)) as Map<*, *>, true) { "x" }!!.style)
        val custom = t.copy(style = DnaStyles.MIST.copy(id = "custom", shape = DnaStyles.MIST.shape.copy(radius = 11f)))
        val back = DnaJson.themeFromMap(MiniJson.parse(MiniJson.write(DnaJson.themeToMap(custom))) as Map<*, *>, true) { "x" }!!
        assertEquals(11f, back.style.shape.radius, 0f); assertEquals("custom", back.style.id)
    }
    @Test fun newerOrBrokenThemesAreRejected() {
        val bad = listOf(v1.replace("\"v\":1", "\"v\":3"), v1.replace("#0D0E10", "#0D0E1"), v1.replace("\"name\":\"Old profile\"", "\"name\":\"  \""), v1.replace("\"accent\":\"#9AA7B8\",", ""))
        for (b in bad) assertNull(b, DnaJson.themeFromMap(MiniJson.parse(b) as Map<*, *>, true) { "x" })
    }
    @Test fun storedListKeepsEveryEntryEvenWhenSomeCannotBeRead() {
        val raw = "[" + v1 + "," + v1.replace("#0D0E10", "nope").replace("u1abc", "u2") + ",42," + v1.replace("\"v\":1", "\"v\":7").replace("u1abc", "u3") + "]"
        val r = DnaJson.parseThemeList(raw) { "n" }
        assertEquals(1, r.themes.size); assertEquals(3, r.rejected.size)
        assertTrue(r.rejected.any { it.contains("nope") }); assertTrue(r.rejected.any { it.contains("\"v\":7") })
    }
    @Test fun corruptStoredListIsPreservedNotDiscarded() {
        val r = DnaJson.parseThemeList("{this is not json") { "n" }
        assertTrue(r.themes.isEmpty()); assertEquals(listOf("{this is not json"), r.rejected)
        assertTrue(DnaJson.parseThemeList("[]") { "n" }.rejected.isEmpty()); assertTrue(DnaJson.parseThemeList("") { "n" }.rejected.isEmpty())
    }
    @Test fun duplicateIdsAreKeptAsRejectedInsteadOfLost() {
        val r = DnaJson.parseThemeList("[$v1,$v1]") { "n" }
        assertEquals(1, r.themes.size); assertEquals(1, r.rejected.size)
    }
    @Test fun listRoundTrip() {
        val l = listOf(ThemeCore.preset("paper")!!.copy(builtin = false, id = "a"), ThemeCore.preset("ember")!!.copy(builtin = false, id = "b", gameAccents = true))
        val back = DnaJson.parseThemeList(DnaJson.themeListToJson(l)) { "n" }
        assertEquals(l.map { it.id }, back.themes.map { it.id }); assertTrue(back.rejected.isEmpty())
        assertEquals(DnaStyles.ANGULAR, back.themes[1].style); assertTrue(back.themes[1].gameAccents)
    }
    @Test fun dnaCanBecomeAProfile() {
        val t = ThemeCore.themeFromScheme("p1", "  From DNA  ", dna.scheme(dna.nativeDark), dna.style)
        assertEquals("From DNA", t.name); assertEquals(dna.style, t.style)
        assertTrue(DnaRules.check(ThemeCore.schemeOf(ThemeCore.sanitize(t), true)).isEmpty())
    }
}
