package com.wb.privateapi.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class UrlFormatTest {

    @Test
    fun `replaces positional placeholders`() {
        assertEquals("a-b-c", formatUrl("{0}-{1}-{2}", "a", "b", "c"))
    }

    @Test
    fun `handles numeric args`() {
        assertEquals("/vol1778/part177899/177899980", formatUrl("/vol{0}/part{1}/{2}", 1778, 177899, 177899980))
    }

    @Test
    fun `returns template when no args and no placeholders`() {
        assertEquals("plain", formatUrl("plain"))
    }

    /** string-format: `format("https://x/{0}")` без аргументов → `"https://x/"`. */
    @Test
    fun `missing placeholder replaced with empty string when no args`() {
        assertEquals("https://x/", formatUrl("https://x/{0}"))
    }

    @Test
    fun `does not touch percent signs`() {
        // String.format would choke on %; formatUrl must leave it alone
        assertEquals("100% done", formatUrl("100% done"))
    }

    /** string-format: `format("{0}-{1}", "a")` → `"a-"` (отсутствующий {1} → пусто). */
    @Test
    fun `missing index replaced with empty string`() {
        assertEquals("a-", formatUrl("{0}-{1}", "a"))
        assertFalse(formatUrl("{0}-{1}", "a").contains("{1}"))
    }

    @Test
    fun `does not misinterpret literal braces`() {
        assertEquals("{x}", formatUrl("{x}"))
    }
}
