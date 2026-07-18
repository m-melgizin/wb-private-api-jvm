package com.wb.privateapi.error

import com.wb.privateapi.constant.HttpStatus

/**
 * Иерархия ошибок приватного API Wildberries.
 *
 * Перенос `WB_ERRORS` / `WB_ERRORS_BY_STATUS` из `Constants.js`.
 * Каждый подкласс соответствует конкретному HTTP-статусу и хранит
 * поля, аналогичные JS-версии: `code`, `message`, `name`.
 *
 * Использование:
 * ```
 * try {
 *     api.search("телефон")
 * } catch (e: WbException) {
 *     when (e) {
 *         is WbRateLimitException -> retry()
 *         is WbInvalidTokenException -> refreshToken()
 *         else -> throw e
 *     }
 * }
 * ```
 *
 * Для произвольного HTTP-статуса, не входящего в [HttpStatus] / [WbException],
 * используется [WbUnknownException].
 */
sealed class WbException(
    val code: String,
    override val message: String,
    val status: Int,
    val name: String,
    cause: Throwable? = null
) : Exception("$message: $status", cause) {

    /** 400 */
    class BadRequestException(
        val responseStatus: Int = HttpStatus.BAD_REQUEST,
        cause: Throwable? = null
    ) : WbException("BAD_REQUEST", "BAD_REQUEST", responseStatus, "WBBadRequestError", cause)

    /** 401 */
    class UnauthorizedException(
        val responseStatus: Int = HttpStatus.UNAUTHORIZED,
        cause: Throwable? = null
    ) : WbException("UNAUTHORIZED", "UNAUTHORIZED", responseStatus, "WBUnauthorizedError", cause)

    /** 403 */
    class ForbiddenException(
        val responseStatus: Int = HttpStatus.FORBIDDEN,
        cause: Throwable? = null
    ) : WbException("FORBIDDEN", "FORBIDDEN", responseStatus, "WBForbiddenError", cause)

    /** 404 */
    class NotFoundException(
        val responseStatus: Int = HttpStatus.NOT_FOUND,
        cause: Throwable? = null
    ) : WbException("NOT_FOUND", "NOT_FOUND", responseStatus, "WBNotFoundError", cause)

    /** 405 */
    class MethodNotAllowedException(
        val responseStatus: Int = HttpStatus.METHOD_NOT_ALLOWED,
        cause: Throwable? = null
    ) : WbException(
        "METHOD_NOT_ALLOWED",
        "METHOD_NOT_ALLOWED",
        responseStatus,
        "WBMethodNotAllowedError",
        cause
    )

    /** 409 */
    class ConflictException(
        val responseStatus: Int = HttpStatus.CONFLICT,
        cause: Throwable? = null
    ) : WbException("CONFLICT", "CONFLICT", responseStatus, "WBConflictError", cause)

    /** 429 */
    class RateLimitException(
        val responseStatus: Int = HttpStatus.TOO_MANY_REQUESTS,
        cause: Throwable? = null
    ) : WbException("TOO_MANY_REQUESTS", "TOO_MANY_REQUESTS", responseStatus, "WBRateLimitError", cause)

    /** 498 */
    class InvalidTokenException(
        val responseStatus: Int = HttpStatus.INVALID_TOKEN,
        cause: Throwable? = null
    ) : WbException("INVALID_TOKEN", "INVALID_TOKEN", responseStatus, "WBInvalidTokenError", cause)

    /** 500 */
    class ServerErrorException(
        val responseStatus: Int = HttpStatus.INTERNAL_SERVER_ERROR,
        cause: Throwable? = null
    ) : WbException(
        "INTERNAL_SERVER_ERROR",
        "INTERNAL_SERVER_ERROR",
        responseStatus,
        "WBServerError",
        cause
    )

    /** 501 */
    class NotImplementedException(
        val responseStatus: Int = HttpStatus.NOT_IMPLEMENTED,
        cause: Throwable? = null
    ) : WbException(
        "NOT_IMPLEMENTED",
        "NOT_IMPLEMENTED",
        responseStatus,
        "WBNotImplementedError",
        cause
    )

    /** 503 */
    class ServiceUnavailableException(
        val responseStatus: Int = HttpStatus.SERVICE_UNAVAILABLE,
        cause: Throwable? = null
    ) : WbException(
        "SERVICE_UNAVAILABLE",
        "SERVICE_UNAVAILABLE",
        responseStatus,
        "WBServiceUnavailableError",
        cause
    )

    /**
     * Любой HTTP-статус, не описанный в иерархии WB-ошибок.
     * Сообщение формируется как `Request failed with status code N`.
     */
    class UnknownException(
        val responseStatus: Int,
        cause: Throwable? = null
    ) : WbException(
        code = "UNKNOWN_$responseStatus",
        message = "Request failed with status code $responseStatus",
        status = responseStatus,
        name = "WbUnknownError",
        cause = cause
    )

    companion object {
        /**
         * Подбирает [WbException] по HTTP-статусу, аналогично
         * `Constants.WB_ERRORS_BY_STATUS[status]` из JS-версии.
         * Для неизвестных статусов возвращает [UnknownException].
         */
        fun byStatus(status: Int, cause: Throwable? = null): WbException = when (status) {
            HttpStatus.BAD_REQUEST -> BadRequestException(status, cause)
            HttpStatus.UNAUTHORIZED -> UnauthorizedException(status, cause)
            HttpStatus.FORBIDDEN -> ForbiddenException(status, cause)
            HttpStatus.NOT_FOUND -> NotFoundException(status, cause)
            HttpStatus.METHOD_NOT_ALLOWED -> MethodNotAllowedException(status, cause)
            HttpStatus.CONFLICT -> ConflictException(status, cause)
            HttpStatus.TOO_MANY_REQUESTS -> RateLimitException(status, cause)
            HttpStatus.INVALID_TOKEN -> InvalidTokenException(status, cause)
            HttpStatus.INTERNAL_SERVER_ERROR -> ServerErrorException(status, cause)
            HttpStatus.NOT_IMPLEMENTED -> NotImplementedException(status, cause)
            HttpStatus.SERVICE_UNAVAILABLE -> ServiceUnavailableException(status, cause)
            else -> UnknownException(status, cause)
        }
    }
}
