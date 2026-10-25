package com.abdo.ps4monitor

import org.junit.Assert.*
import org.junit.Test

class MiniJsonTest {
    @Test fun parsesAllValueTypes() {
        val m = MiniJson.parse("""{"a":1,"b":-2.5e1,"c":"x\n\u00e9\"","d":[true,false,null],"e":{}}""") as Map<*, *>
        assertEquals(1.0, m["a"]); assertEquals(-25.0, m["b"]); assertEquals("x\n\u00e9\"", m["c"]); assertEquals(listOf(true, false, null), m["d"]); assertEquals(emptyMap<String, Any?>(), m["e"])
    }
    @Test fun writerEscapesAndRoundTrips() {
        val v = linkedMapOf("s" to "q\"\\\n\t\u0001", "n" to 3, "f" to 0.25f, "l" to listOf(1, "a", null), "b" to true)
        val back = MiniJson.parse(MiniJson.write(v)) as Map<*, *>
        assertEquals("q\"\\\n\t\u0001", back["s"]); assertEquals(3.0, back["n"]); assertEquals(0.25, back["f"]); assertEquals(listOf(1.0, "a", null), back["l"])
    }
    @Test fun rejectsBadJson() {
        for (x in listOf("", "{", "{\"a\"}", "{\"a\":}", "[1,]", "[1 2]", "tru", "\"unterminated", "{\"a\":1} x", "'single'", "{a:1}", "-", "[\"\\q\"]", "\"\u0001\""))
            assertNull(x, MiniJson.tryParse(x))
    }
    @Test fun enforcesLimits() {
        assertNull(MiniJson.tryParse("[" .repeat(30) + "]".repeat(30)))
        assertNull(MiniJson.tryParse("[" + "1,".repeat(7000) + "1]"))
        assertNull(MiniJson.tryParse("[1]", maxChars = 2))
    }
    @Test fun prettyOutputParsesToTheSameValue() {
        val v = mapOf("a" to listOf(1, 2), "b" to mapOf("c" to "d"))
        assertEquals(MiniJson.parse(MiniJson.write(v)), MiniJson.parse(MiniJson.write(v, pretty = true)))
    }
}
