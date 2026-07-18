package com.wb.privateapi.session

import com.wb.privateapi.constant.HttpStatus
import com.wb.privateapi.error.WbException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import kotlin.math.min
import kotlin.random.Random

/**
 * HTTP-сессия на OkHttp с retry/backoff. Перенос `Session` из `SessionBuilder.js`.
 *
 * `get` / `post` — `suspend`-функции (аналог `async`), выполняются в `Dispatchers.IO`.
 * Возвращают [ResponseData] со статусом и распарсенным телом (JSON / текст).
 *
 * Поведение повторяет JS:
 *  - экспоненциальный backoff: `min(2^attempt * 1000 + rand*1000, 10000)` ms
 *  - retry при 429, 5xx или сетевой ошибке (см. [defaultRetryCondition])
 *  - при успехе (2xx) тело парсится и возвращается
 *  - при финальной неудаче бросается [WbException] через [WbException.byStatus]
 *  - deviceid добавляется только для запросов к `www.wildberries.ru`
 *    (т.е. когда URL был переписан на `__internal`) — см. [resolveUrl]
 */
class Session internal constructor(
    val config: SessionConfig
) {
    private val logger: Logger = LoggerFactory.getLogger(Session::class.java)

    private val client: OkHttpClient get() = config.client
    private val commonHeaders: Headers get() = config.commonHeaders

    /** Признак наличия антибот-токена (Cookie). */
    fun hasToken(): Boolean = config.hasToken

    /**
     * Переписывает URL на `__internal`, если у сессии есть токен.
     * Повтор `Session.resolveUrl` из JS.
     */
    fun resolveUrl(url: String): String =
        if (hasToken()) SessionBuilder.toProxyUrl(url) else url

    /**
     * GET-запрос с query-параметрами, retry и авто-парсингом ответа.
     *
     * @param url шаблонный URL без query
     * @param params query-параметры (значения-массивы сериализуются через запятую,
     *               кодирование отключено — повтор `qs.stringify({arrayFormat:'comma',encode:false})`)
     * @param headers дополнительные заголовки запроса
     * @param retryOptions переопределение retry-политики
     * @param responseType `"json"` / `"text"` / `"auto"` (по Content-Type + эвристика)
     */
    suspend fun get(
        url: String,
        params: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        retryOptions: RetryOptions = RetryOptions.DEFAULT,
        responseType: ResponseType = ResponseType.AUTO
    ): ResponseData = withContext(Dispatchers.IO) {
        val resolved = resolveUrl(url)
        val isInternal = resolved != url
        val fullUrl = appendQuery(resolved, params)

        config.requestLogger?.log(RequestLogEvent("GET", fullUrl))

        val mergedHeaders = Headers.Builder()
            .addAll(commonHeaders)
            .apply {
                if (isInternal) set("deviceid", SessionBuilder.getDeviceId())
                headers.forEach { (k, v) -> set(k, v) }
            }
            .build()

        executeWithRetry(
            method = "GET",
            url = url,
            fullUrl = fullUrl,
            body = null,
            headers = mergedHeaders,
            retryOptions = retryOptions,
            responseType = responseType
        )
    }

    /**
     * POST-запрос с JSON-телом. URL не переписывается (поведение JS: `post` шлёт в исходный URL).
     *
     * @param url URL эндпойнта
     * @param body сериализуемое тело (кодируется в JSON)
     * @param headers дополнительные заголовки
     * @param retryOptions переопределение retry-политики
     */
    suspend fun post(
        url: String,
        body: Any?,
        headers: Map<String, String> = emptyMap(),
        retryOptions: RetryOptions = RetryOptions.DEFAULT
    ): ResponseData = withContext(Dispatchers.IO) {
        config.requestLogger?.log(RequestLogEvent("POST", url, body))

        val mergedHeaders = Headers.Builder()
            .addAll(commonHeaders)
            .set("Content-Type", "application/json")
            .apply { headers.forEach { (k, v) -> set(k, v) } }
            .build()

        val requestBody: RequestBody? = body?.let {
            val json = if (it is String) it else defaultJsonEncode(it)
            json.toRequestBody("application/json; charset=utf-8".toMediaType())
        }

        executeWithRetry(
            method = "POST",
            url = url,
            fullUrl = url,
            body = requestBody,
            headers = mergedHeaders,
            retryOptions = retryOptions,
            responseType = ResponseType.AUTO
        )
    }

    private suspend fun executeWithRetry(
        method: String,
        url: String,
        fullUrl: String,
        body: RequestBody?,
        headers: Headers,
        retryOptions: RetryOptions,
        responseType: ResponseType
    ): ResponseData {
        val retries = retryOptions.retries
        val retryCondition = retryOptions.retryCondition ?: ::defaultRetryCondition

        var lastError: WbException? = null
        var attempt = 0
        while (attempt <= retries) {
            if (attempt > 0) {
                val delayMs = min((1L shl attempt) * 1000L + Random.nextLong(0, 1000), 10_000L)
                logger.debug("Retry attempt attempt={} url={}", attempt, url)
                delay(delayMs)
            }

            val builder = Request.Builder().url(fullUrl).headers(headers)
            when (method) {
                "GET" -> builder.get()
                "POST" -> if (body != null) builder.post(body) else builder.post(EMPTY_JSON_BODY)
            }
            val request = builder.build()

            val response: Response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                val shouldRetry = attempt < retries && retryCondition(RetryContext(null, e, attempt, url, method))
                if (!shouldRetry) throw WbException.UnknownException(0, e)
                lastError = WbException.UnknownException(0, e)
                attempt++
                continue
            }

            val status = response.code
            val data = response.use { readResponseData(it, responseType) }
            response.close()

            if (status in 200..299) {
                return ResponseData(status, data)
            }

            val shouldRetry = attempt < retries && retryCondition(RetryContext(status, null, attempt, url, method))
            if (shouldRetry) {
                lastError = WbException.byStatus(status)
                attempt++
                continue
            }
            logger.error("Request failed url={} status={} data={}", url, status, data)
            throw WbException.byStatus(status)
        }
        throw lastError ?: WbException.UnknownException(0)
    }

    /** Условие retry по умолчанию: 429, 5xx или наличие ошибки. Повтор `defaultRetryCondition` из JS. */
    fun defaultRetryCondition(ctx: RetryContext): Boolean =
        ctx.status == HttpStatus.TOO_MANY_REQUESTS ||
            (ctx.status != null && ctx.status >= HttpStatus.INTERNAL_SERVER_ERROR) ||
            ctx.error != null

    /**
     * Парсинг тела ответа. Повтор `readResponseData` из JS.
     * `"json"` — всегда JSON, `"text"` — всегда текст, `"auto"` — по Content-Type,
     * иначе текст, и если он начинается с `{` / `[` — попытка JSON.parse.
     */
    private fun readResponseData(response: Response, responseType: ResponseType): Any? {
        val body = response.body ?: return null
        val bytes = body.bytes()
        val text = String(bytes, Charsets.UTF_8)

        if (responseType == ResponseType.TEXT) return text
        if (responseType == ResponseType.JSON) return tryParseJson(text)

        val contentType = response.header("Content-Type") ?: ""
        if (contentType.contains("application/json", ignoreCase = true)) {
            return tryParseJson(text)
        }
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            tryParseJson(text) ?: text
        } else {
            text
        }
    }

    private fun tryParseJson(text: String): Any? =
        try {
            defaultJsonParse(text)
        } catch (_: Exception) {
            null
        }

    companion object
}

/**
 * Построение query-строки в стиле `qs.stringify({arrayFormat:'comma',encode:false})`.
 *
 * Поведение повторяет JS `qs` (проверено напрямую через `npm i qs`):
 *  - `null` значения сериализуются как пустая строка: `a=` (НЕ пропускаются)
 *  - массивы склеиваются через запятую: `key=v1,v2,v3`
 *  - **никакого URL-кодирования**: пробел, `+`, `&`, `=`, `<`, `>` идут как есть
 *    (`encode:false` отключает экранирование полностью)
 *
 * Если URL уже содержит `?query`, новые параметры добавляются через `&`.
 */
internal fun appendQuery(url: String, params: Map<String, Any?>): String {
    if (params.isEmpty()) return url
    val base = url.substringBefore('?')
    val existingQuery = url.substringAfter('?', "")
    val parts = mutableListOf<String>()
    params.forEach { (key, value) ->
        val joined = joinValue(value) ?: return@forEach
        parts.add("$key=$joined")
    }
    if (parts.isEmpty()) return url
    val merged = (if (existingQuery.isNotEmpty()) "$existingQuery&" else "") + parts.joinToString("&")
    return "$base?$merged"
}

/**
 * Сериализация одного значения параметра в стиле `qs` (`encode:false`).
 *  - `null` → `""` (параметр остаётся с пустым значением)
 *  - массив/Iterable → элементы через запятую без кодирования
 *  - скаляр → строковое представление без кодирования
 * Возвращает `null` только если значение — пустой массив.
 */
private fun joinValue(value: Any?): String? = when (value) {
    null -> ""
    is Iterable<*> -> {
        val items = value.mapNotNull { scalar(it) }
        if (items.isEmpty()) null else items.joinToString(",")
    }
    is Array<*> -> {
        val items = value.mapNotNull { scalar(it) }
        if (items.isEmpty()) null else items.joinToString(",")
    }
    else -> scalar(value) ?: ""
}

private fun scalar(value: Any?): String? = when (value) {
    null -> null
    is Boolean -> if (value) "true" else "false"
    is Number -> value.toString()
    else -> value.toString()
}

/** Конфигурация сессии: пул соединений, заголовки, retry-параметры. */
data class SessionConfig(
    val client: OkHttpClient,
    val commonHeaders: Headers,
    val timeout: Duration,
    val retries: Int,
    val maxSockets: Int,
    val hasToken: Boolean,
    val requestLogger: RequestLogger?
)

/** Результат запроса: HTTP-статус + распарсенное тело. */
data class ResponseData(
    val status: Int,
    val data: Any?
)

/** Тип ожидаемого ответа (повтор `responseType` из JS). */
enum class ResponseType { AUTO, JSON, TEXT }

/** Переопределение retry-поведения для конкретного вызова. */
data class RetryOptions(
    val retries: Int,
    val retryCondition: ((RetryContext) -> Boolean)? = null
) {
    companion object {
        val DEFAULT = RetryOptions(retries = 3)
    }
}

/** Контекст для принятия решения о retry. */
data class RetryContext(
    val status: Int?,
    val error: Throwable?,
    val attempt: Int,
    val url: String,
    val method: String
)

private val EMPTY_JSON_BODY: RequestBody = "".toRequestBody("application/json".toMediaType())
