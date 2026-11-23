package com.aiceviri.core

import com.aiceviri.core.util.Json
import com.aiceviri.core.util.JsonException
import com.aiceviri.core.util.path
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JsonTest {

    @Test
    fun stringifyEscapesSpecialCharacters() {
        val s = Json.stringify(mapOf("a" to "çift \"tırnak\"\nyeni satır\t\\", "n" to 3, "d" to 1.5, "b" to true, "z" to null))
        assertEquals("{\"a\":\"çift \\\"tırnak\\\"\\nyeni satır\\t\\\\\",\"n\":3,\"d\":1.5,\"b\":true,\"z\":null}", s)
    }

    @Test
    fun roundTripPreservesUnicodeAndNesting() {
        val original = mapOf(
            "text" to "Merhaba dünya! 你好 مرحبا 👋",
            "list" to listOf(1L, "iki", listOf(false), mapOf("k" to "v")),
        )
        assertEquals(original, Json.parse(Json.stringify(original)))
    }

    @Test
    fun parsesUnicodeEscapesAndNumbers() {
        val obj = Json.parseObject("""{ "t": "çığ", "i": -12, "f": 2.5e1, "e": [], "o": {} }""")
        assertEquals("çığ", obj["t"])
        assertEquals(-12L, obj["i"])
        assertEquals(25.0, obj["f"])
        assertEquals(emptyList<Any?>(), obj["e"])
        assertEquals(emptyMap<String, Any?>(), obj["o"])
    }

    @Test
    fun pathNavigatesMapsAndLists() {
        val obj = Json.parse("""{"choices":[{"message":{"content":"ok"}}]}""")
        assertEquals("ok", obj.path("choices", 0, "message", "content"))
        assertNull(obj.path("choices", 5, "message"))
        assertNull(obj.path("missing", "x"))
    }

    @Test
    fun rejectsMalformedJson() {
        assertFailsWith<JsonException> { Json.parse("{\"a\":}") }
        assertFailsWith<JsonException> { Json.parse("[1,2") }
        assertFailsWith<JsonException> { Json.parse("{} fazlalık") }
        assertFailsWith<JsonException> { Json.parse("\"kapanmamış") }
    }
}
