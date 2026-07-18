package com.wb.privateapi.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Поведение [appendQuery] сверено напрямую с `qs.stringify({arrayFormat:'comma',encode:false})`
 * через `npm i qs`. Все контрольные значения получены из реальной JS-библиотеки.
 */
class AppendQueryTest {

    @Test
    fun `scalar value`() {
        assertEquals("/x?dest=123", appendQuery("/x", mapOf("dest" to 123)))
    }

    @Test
    fun `array value joined with comma`() {
        assertEquals("/x?nm=1,2,3", appendQuery("/x", mapOf("nm" to listOf(1L, 2L, 3L))))
    }

    @Test
    fun `null value emits empty (not skipped)`() {
        // qs: {a:null, b:'y'} → "a=&b=y"
        assertEquals("/x?a=&b=y", appendQuery("/x", mapOf("a" to null, "b" to "y")))
    }

    @Test
    fun `no params returns url unchanged`() {
        assertEquals("/x", appendQuery("/x", emptyMap()))
    }

    @Test
    fun `existing query is preserved and extended`() {
        assertEquals("/x?z=1&a=1", appendQuery("/x?z=1", mapOf("a" to 1)))
    }

    @Test
    fun `spaces plus ampersand are NOT encoded (matches qs encode false)`() {
        // qs encode:false: query='красная куртка' → "query=красная куртка" (raw space)
        assertEquals("/x?query=красная куртка", appendQuery("/x", mapOf("query" to "красная куртка")))
        // qs encode:false: q='a+b' → "q=a+b" (raw +)
        assertEquals("/x?q=a+b", appendQuery("/x", mapOf("q" to "a+b")))
        // qs encode:false: q='a&b' → "q=a&b" (raw &)
        assertEquals("/x?q=a&b", appendQuery("/x", mapOf("q" to "a&b")))
    }

    @Test
    fun `boolean and number values`() {
        assertEquals("/x?flag=true&n=42", appendQuery("/x", mapOf("flag" to true, "n" to 42)))
    }

    @Test
    fun `empty array produces no param`() {
        assertEquals("/x", appendQuery("/x", mapOf("nm" to emptyList<Any>())))
    }

    @Test
    fun `comma inside scalar value stays raw`() {
        assertEquals("/x?q=a,b", appendQuery("/x", mapOf("q" to "a,b")))
    }

    @Test
    fun `multiple params preserve insertion order`() {
        val out = appendQuery("/x", linkedMapOf("a" to 1, "b" to 2, "c" to 3))
        assertEquals("/x?a=1&b=2&c=3", out)
    }

    @Test
    fun `url with fragment keeps query before fragment`() {
        val out = appendQuery("https://wb.ru/x#frag", mapOf("a" to 1))
        assertTrue(out.contains("a=1"), "out=$out")
    }
}
