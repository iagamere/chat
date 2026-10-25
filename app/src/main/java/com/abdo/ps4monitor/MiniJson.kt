package com.abdo.ps4monitor

/**
 * A small, strict JSON reader / writer with hard limits (size, depth, node count). It is used for the design-system files instead of
 * org.json because the same code then runs unchanged in plain JVM unit tests, and because untrusted imports must never be able to
 * exhaust memory or the stack. Values: Map<String, Any?>, List<Any?>, String, Double, Boolean, null.
 */
object MiniJson {
    class JsonException(msg: String) : Exception(msg)
    private const val MAX_DEPTH = 12
    private const val MAX_NODES = 6000

    fun parse(text: String, maxChars: Int = 200_000): Any? {
        if (text.length > maxChars) throw JsonException("too large")
        val p = Parser(text); p.skipWs()
        val v = p.value(0)
        p.skipWs()
        if (p.i != text.length) throw JsonException("trailing characters at ${p.i}")
        return v
    }
    fun tryParse(text: String, maxChars: Int = 200_000): Any? = try { parse(text, maxChars) } catch (e: JsonException) { null } catch (e: StackOverflowError) { null }

    private class Parser(val s: String) {
        var i = 0; var nodes = 0
        fun skipWs() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }
        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw JsonException("too deep")
            if (++nodes > MAX_NODES) throw JsonException("too many values")
            if (i >= s.length) throw JsonException("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true); 'f' -> lit("false", false); 'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw JsonException("unexpected '$c' at $i")
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) throw JsonException("bad literal at $i"); i += w.length; return v }
        fun obj(depth: Int): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; skipWs()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                skipWs(); if (i >= s.length || s[i] != '"') throw JsonException("key expected at $i")
                val k = str(); skipWs()
                if (i >= s.length || s[i] != ':') throw JsonException("':' expected at $i"); i++; skipWs()
                m[k] = value(depth + 1); skipWs()
                if (i >= s.length) throw JsonException("unexpected end")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return m }
                throw JsonException("',' or '}' expected at $i")
            }
        }
        fun arr(depth: Int): List<Any?> {
            val l = ArrayList<Any?>(); i++; skipWs()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                skipWs(); l.add(value(depth + 1)); skipWs()
                if (i >= s.length) throw JsonException("unexpected end")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return l }
                throw JsonException("',' or ']' expected at $i")
            }
        }
        fun str(): String {
            val sb = StringBuilder(); i++
            while (true) {
                if (i >= s.length) throw JsonException("unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) throw JsonException("bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> { if (i + 4 > s.length) throw JsonException("bad \\u"); sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: throw JsonException("bad \\u")); i += 4 }
                            else -> throw JsonException("bad escape '$e'")
                        }
                    }
                    c < ' ' -> throw JsonException("control character in string")
                    else -> sb.append(c)
                }
                if (sb.length > 100_000) throw JsonException("string too long")
            }
        }
        fun num(): Double {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(st, i).toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw JsonException("bad number at $st")
        }
    }

    fun write(v: Any?, pretty: Boolean = false): String { val sb = StringBuilder(); put(sb, v, if (pretty) 0 else -1); return sb.toString() }
    private fun nl(sb: StringBuilder, ind: Int) { if (ind >= 0) { sb.append('\n'); repeat(ind) { sb.append("  ") } } }
    private fun put(sb: StringBuilder, v: Any?, ind: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Float -> sb.append(num(v.toDouble()))
            is Double -> sb.append(num(v))
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{'); var first = true
                for ((k, x) in v) { if (!first) sb.append(','); first = false; nl(sb, if (ind < 0) -1 else ind + 1); quote(sb, k.toString()); sb.append(':'); if (ind >= 0) sb.append(' '); put(sb, x, if (ind < 0) -1 else ind + 1) }
                nl(sb, ind); sb.append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                sb.append('['); var first = true
                for (x in v) { if (!first) sb.append(','); first = false; nl(sb, if (ind < 0) -1 else ind + 1); put(sb, x, if (ind < 0) -1 else ind + 1) }
                nl(sb, ind); sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }
    private fun num(d: Double): String = if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, d).trimEnd('0').trimEnd('.')
    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\""); c == '\\' -> sb.append("\\\\"); c == '\n' -> sb.append("\\n"); c == '\r' -> sb.append("\\r"); c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u%04x".format(c.code)); else -> sb.append(c)
        }
        sb.append('"')
    }
}
