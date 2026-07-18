package com.wb.privateapi.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionBuilderTokenTest {

    @Test
    fun `parses valid token JSON`() {
        val json = """{"token":"abc123","expires_at":${System.currentTimeMillis() + 60_000}}"""
        assertEquals("abc123", SessionBuilder.parseTokenJson(json))
    }

    @Test
    fun `returns null for expired token`() {
        val json = """{"token":"abc123","expires_at":${System.currentTimeMillis() - 60_000}}"""
        assertNull(SessionBuilder.parseTokenJson(json))
    }

    @Test
    fun `returns null for tokenless JSON`() {
        assertNull(SessionBuilder.parseTokenJson("""{"expires_at":123}"""))
    }

    @Test
    fun `parses token without expires_at`() {
        assertEquals("tok", SessionBuilder.parseTokenJson("""{"token":"tok"}"""))
    }

    @Test
    fun `getDeviceId returns site_ prefix and 32 hex chars`() {
        val id = SessionBuilder.getDeviceId()
        assertTrue(id.matches(Regex("site_[0-9a-f]{32}")), "id=$id")
    }
}
