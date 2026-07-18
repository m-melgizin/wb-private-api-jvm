package com.wb.privateapi.error

import com.wb.privateapi.constant.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WbExceptionTest {

    @Test
    fun `byStatus maps known HTTP codes to specific exceptions`() {
        assertEquals(WbException.BadRequestException::class, WbException.byStatus(400)::class)
        assertEquals(WbException.UnauthorizedException::class, WbException.byStatus(401)::class)
        assertEquals(WbException.ForbiddenException::class, WbException.byStatus(403)::class)
        assertEquals(WbException.NotFoundException::class, WbException.byStatus(404)::class)
        assertEquals(WbException.MethodNotAllowedException::class, WbException.byStatus(405)::class)
        assertEquals(WbException.ConflictException::class, WbException.byStatus(409)::class)
        assertEquals(WbException.RateLimitException::class, WbException.byStatus(429)::class)
        assertEquals(WbException.InvalidTokenException::class, WbException.byStatus(498)::class)
        assertEquals(WbException.ServerErrorException::class, WbException.byStatus(500)::class)
        assertEquals(WbException.NotImplementedException::class, WbException.byStatus(501)::class)
        assertEquals(WbException.ServiceUnavailableException::class, WbException.byStatus(503)::class)
    }

    @Test
    fun `byStatus maps unknown codes to UnknownException`() {
        val ex = WbException.byStatus(418) as WbException.UnknownException
        assertEquals(418, ex.status)
        assertEquals(418, ex.responseStatus)
        assertTrue(ex.message.contains("418"))
    }

    @Test
    fun `rate limit exception carries status 429`() {
        val ex = WbException.byStatus(HttpStatus.TOO_MANY_REQUESTS) as WbException.RateLimitException
        assertEquals(429, ex.responseStatus)
        assertEquals("TOO_MANY_REQUESTS", ex.code)
    }

    @Test
    fun `sealed exception can be matched with when`() {
        fun classify(e: WbException): String = when (e) {
            is WbException.RateLimitException -> "retry"
            is WbException.InvalidTokenException -> "refresh"
            is WbException.UnknownException -> "unknown"
            else -> "other"
        }
        assertEquals("retry", classify(WbException.byStatus(429)))
        assertEquals("refresh", classify(WbException.byStatus(498)))
        assertEquals("unknown", classify(WbException.byStatus(418)))
        assertEquals("other", classify(WbException.byStatus(404)))
    }
}
