package com.wb.privateapi.session

import com.wb.privateapi.constant.Constants
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Конфигурация и фабрика HTTP-сессии. Перенос `SessionBuilder` + `Session`
 * из `SessionBuilder.js` на OkHttp.
 *
 * Реализует:
 *  - connection pooling (`keepAlive` 30s, `maxSockets` per host)
 *  - общие заголовки браузера (`User-Agent`, `Origin`, `Referer`, …)
 *  - чтение токена из `.wbaas_token` (`{ token, expires_at }`)
 *  - кеширование `deviceid` в файле (аналог localStorage `wbx__sessionID`)
 *  - подмену URL на `www.wildberries.ru/__internal/<subdomain>/` при наличии токена
 *  - retry с экспоненциальным backoff (`2^attempt * 1000 + rand*1000`, cap 10s)
 *  - `qs.stringify`-стиль построения query (`arrayFormat: "comma"`, без кодирования)
 *
 * Сессия иммутабельна по токену: после [create] токен и заголовки фиксируются.
 */
class SessionBuilder private constructor(
    val config: SessionConfig
) {

    /**
     * Создаёт [Session] для использования фасадом и моделями.
     */
    fun create(): Session = Session(config)

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SessionBuilder::class.java)

        /** Домены WB, для которых подтверждён proxy-путь `/__internal/<subdomain>/`. */
        private val PROXY_DOMAINS: Set<String> = setOf(
            "catalog", "search", "card", "suggests",
            "recom", "meta", "banners", "user-geo-data",
            "u-catalog", "u-search", "u-card", "u-suggests",
            "u-recom", "search-tags", "u-search-tags"
        )

        /** `https://<subdomain>.wb.ru/` → переписывается на `__internal`. */
        private val PROXY_URL_REGEX: Pattern =
            Pattern.compile("^https://([\\w-]+)\\.wb\\.ru/")

        private const val TOKEN_FILE_NAME = ".wbaas_token"
        private const val DEVICE_ID_FILE_NAME = ".deviceid"
        private val DEVICE_ID_REGEX: Pattern = Pattern.compile("^site_[0-9a-f]{32}$")

        /**
         * Переписывает `https://<subdomain>.wb.ru/...` на
         * `https://www.wildberries.ru/__internal/<subdomain>/...`,
         * если `subdomain` входит в [PROXY_DOMAINS]. Повтор `toProxyUrl` из JS.
                 */
        fun toProxyUrl(url: String): String {
            val matcher = PROXY_URL_REGEX.matcher(url)
            if (!matcher.find()) return url
            val subdomain = matcher.group(1) ?: return url
            return if (subdomain in PROXY_DOMAINS) {
                url.replaceFirst(
                    PROXY_URL_REGEX.toRegex(),
                    "https://www.wildberries.ru/__internal/$subdomain/"
                )
            } else {
                url
            }
        }

        /**
         * Создаёт [SessionBuilder] с настройками по умолчанию.
         *
         * @param wbaasToken токен `x_wbaas_token`; если `null` — читается из файла
         *                   `.wbaas_token` (текущая директория или `~`).
         * @param timeout таймаут запроса
         * @param retries число повторных попыток на 429/5xx
         * @param maxSockets макс. одновременных соединений
         * @param userAgent значение заголовка `User-Agent`
         */
        fun create(
            wbaasToken: String? = null,
            timeout: Duration = Duration.ofSeconds(30),
            retries: Int = 3,
            maxSockets: Int = 10,
            userAgent: String = Constants.USER_AGENT,
            requestLogger: RequestLogger? = null
        ): Session {
            val token = wbaasToken ?: readToken()
            val headers = Headers.Builder()
                .set("User-Agent", userAgent)
                .set("Accept-Encoding", "gzip, deflate, br")
                .set("Accept", "application/json, text/plain, */*")
                .set("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                .set("Origin", "https://www.wildberries.ru")
                .set("Referer", "https://www.wildberries.ru/")
                .set("Cache-Control", "no-cache")
                .apply {
                    if (token != null) {
                        // setAntibotToken: только для запросов к __internal/* (www.wildberries.ru)
                        set("Cookie", "x_wbaas_token=$token")
                    }
                }
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(timeout)
                .connectionPool(
                    ConnectionPool(
                        maxIdleConnections = maxSockets,
                        keepAliveDuration = 30,
                        timeUnit = TimeUnit.SECONDS
                    )
                )
                .retryOnConnectionFailure(true)
                .build()

            val config = SessionConfig(
                client = client,
                commonHeaders = headers,
                timeout = timeout,
                retries = retries,
                maxSockets = maxSockets,
                hasToken = token != null,
                requestLogger = requestLogger
            )
            return Session(config)
        }

        /**
         * Читает токен из `.wbaas_token`: JSON `{ "token": "...", "expires_at": <ms> }`.
         * Ищет файл в текущей директории, затем в домашней (`~`). Повтор `readToken` из JS.
         * Возвращает `null`, если файла нет, JSON некорректен или токен истёк.
         */
        fun readToken(): String? {
            val file = findTokenFile() ?: return null
            return try {
                val content = file.readText(StandardCharsets.UTF_8).trim()
                parseTokenJson(content)
            } catch (_: IOException) {
                null
            }
        }

        private fun findTokenFile(): File? {
            val cwd = File(TOKEN_FILE_NAME)
            if (cwd.isFile) return cwd
            val home = File(System.getProperty("user.home"), TOKEN_FILE_NAME)
            return if (home.isFile) home else null
        }

        // Минимальный парсер {"token": "...", "expires_at": <ms>}, чтобы не тащить JSON-библиотеку.
        internal fun parseTokenJson(content: String): String? {
            val token = extractJsonString(content, "token") ?: return null
            val expiresAt = extractJsonNumber(content, "expires_at")
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) return null
            return token
        }

        private fun extractJsonString(json: String, key: String): String? {
            val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            val m = pattern.matcher(json)
            return if (m.find()) m.group(1) else null
        }

        private fun extractJsonNumber(json: String, key: String): Long? {
            val pattern = Pattern.compile("\"$key\"\\s*:\\s*(-?\\d+)")
            val m = pattern.matcher(json)
            return if (m.find()) m.group(1)?.toLongOrNull() else null
        }

        /**
         * Возвращает стабильный `deviceid`, кешируя его в файле `.deviceid`.
         * Алгоритм фронтенда WB: `site_` + UUID v4 без дефисов. Повтор `getDeviceId` из JS.
         */
        fun getDeviceId(): String {
            val file = File(DEVICE_ID_FILE_NAME)
            try {
                if (file.isFile) {
                    val cached = file.readText(StandardCharsets.UTF_8).trim()
                    if (DEVICE_ID_REGEX.matcher(cached).matches()) return cached
                }
            } catch (_: IOException) {
                // fall through to regeneration
            }

            val deviceId = "site_${UUID.randomUUID().toString().replace("-", "")}"
            try {
                file.writeText(deviceId, StandardCharsets.UTF_8)
            } catch (_: IOException) {
                // ignore — deviceId остаётся в памяти
            }
            return deviceId
        }
    }
}

/** Диагностический логгер исходящих запросов (аналог `requestLogger` в JS). */
fun interface RequestLogger {
    fun log(event: RequestLogEvent)
}

/** Событие лога запроса: метод, итоговый URL и опциональное тело (для POST). */
data class RequestLogEvent(
    val method: String,
    val url: String,
    val body: Any? = null
)
