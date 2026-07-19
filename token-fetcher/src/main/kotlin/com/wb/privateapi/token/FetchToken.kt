package com.wb.privateapi.token

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Автоматическое получение `x_wbaas_token` из реального браузера через Playwright.
 *
 * В отличие от JS-скрипта `scripts/get-wb-token.js` (ручной запуск в DevTools
 * + копирование JSON), здесь процесс полностью автоматизирован:
 * запускается Chromium, открывается wildberries.ru, ожидается появление
 * cookie `x_wbaas_token`, и токен сразу пишется в `.wbaas_token`.
 *
 * Режим по умолчанию — **headless** (для VPS без рабочего стола).
 * Headed-режим (`--headed`) полезен, если антибот WB агрессивно детектит
 * headless и подставляет капчу — в headed пользователь проходит её вручную,
 * скрипт продолжает ждать cookie.
 *
 * Запуск:
 * ```
 * ./gradlew :token-fetcher:run                                   # headless, ./.wbaas_token
 * ./gradlew :token-fetcher:run --args="--headed"                 # видимое окно
 * ./gradlew :token-fetcher:run --args="--out ~/.wbaas_token --timeout 60"
 * ```
 */
object FetchToken {

    const val WB_URL = "https://www.wildberries.ru"
    const val COOKIE_NAME = "x_wbaas_token"
    const val DEFAULT_OUTPUT = ".wbaas_token"
    const val DEFAULT_TIMEOUT_SECONDS = 30L

    /**
     * Получить токен и сохранить его в файл.
     *
     * @param headed `true` — видимое окно браузера; `false` (по умолчанию) — headless.
     * @param output файл для записи результата (`{token, expires_at}` JSON).
     * @param timeoutSeconds макс. время ожидания cookie после загрузки страницы.
     * @param browserType `"chromium"` (по умолчанию), `"firefox"`, `"webkit"`.
     * @return записанный [TokenResult] или `null`, если cookie не появилась за таймаут.
     */
    fun fetch(
        headed: Boolean = false,
        output: File = File(DEFAULT_OUTPUT),
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        browserType: String = "chromium"
    ): TokenResult? {
        println("Запуск Playwright ($browserType, headless=${!headed})...")
        // playwright создаёт отдельный процесс browser-driver; оборачиваем в use для гарантированного закрытия.
        com.microsoft.playwright.Playwright.create().use { playwright ->
            val browserTypeSel = when (browserType.lowercase(Locale.getDefault())) {
                "firefox" -> playwright.firefox()
                "webkit" -> playwright.webkit()
                else -> playwright.chromium()
            }
            val browser = browserTypeSel.launch(
                com.microsoft.playwright.BrowserType.LaunchOptions()
                    .setHeadless(!headed)
                    .setArgs(listOf("--disable-blink-features=AutomationControlled"))
            )
            browser.use { b ->
                val context = b.newContext(
                    com.microsoft.playwright.Browser.NewContextOptions()
                        .setUserAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/146.0.0.0 Safari/537.36"
                        )
                        .setViewportSize(1366, 768)
                        .setLocale("ru-RU")
                )
                val page = context.newPage()

                println("Открываю $WB_URL ...")
                //WB主页会执行JS-челлендж антибота; ждём networkidle, но не падаем, если часть запросов висит.
                try {
                    page.navigate(WB_URL, com.microsoft.playwright.Page.NavigateOptions()
                        .setTimeout(timeoutSeconds.toDouble() * 1000.0)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE))
                } catch (e: com.microsoft.playwright.PlaywrightException) {
                    println("Предупреждение: навигация не завершилась чисто (${e.message}), продолжаю опрос cookie...")
                }

                println("Жду cookie '$COOKIE_NAME' (таймаут ${timeoutSeconds}с)...")
                val token = waitForCookie(context, timeoutSeconds)
                if (token == null) {
                    println("ОШИБКА: cookie '$COOKIE_NAME' не появилась за ${timeoutSeconds}с.")
                    println("Возможные причины: антибот заблокировал headless → попробуйте --headed и пройдите капчу вручную.")
                    return null
                }

                val expiresAt = token.expires?.toLong() ?: (System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000)
                val result = TokenResult(
                    token = token.value,
                    expiresAt = expiresAt,
                    expiresAtIso = Instant.ofEpochMilli(expiresAt).toString()
                )
                writeTokenFile(output, result)
                println("ГОТОВО. Токен сохранён в: ${output.absolutePath}")
                println("Истекает: ${result.expiresAtIso}")
                return result
            }
        }
    }

    /**
     * Опрос `context.cookies()` каждые 500мс, пока не появится [COOKIE_NAME]
     * или не истечёт [timeoutSeconds]. В headed-режиме пользователь может
     * проходить капчу — опрос продолжается весь таймаут.
     */
    private fun waitForCookie(
        context: com.microsoft.playwright.BrowserContext,
        timeoutSeconds: Long
    ): CookieValue? {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        var lastLogged = 0L
        while (System.currentTimeMillis() < deadline) {
            val cookies = context.cookies()
            val found = cookies.firstOrNull { it.name == COOKIE_NAME && it.value.isNotEmpty() }
            if (found != null) {
                return CookieValue(value = found.value, expires = found.expires.takeIf { it > 0 })
            }
            if (System.currentTimeMillis() - lastLogged > 5000) {
                val remaining = (deadline - System.currentTimeMillis()) / 1000
                println("  ... ещё жду (осталось ${remaining}с), cookie пока нет")
                lastLogged = System.currentTimeMillis()
            }
            Thread.sleep(500)
        }
        return null
    }

    /** Запись `{token, expires_at}` в файл (формат, который читает `SessionBuilder.readToken`). */
    private fun writeTokenFile(file: File, result: TokenResult) {
        val json = buildJsonObject {
            put("token", result.token)
            put("expires_at", result.expiresAt)
        }
        file.writeText(Json.encodeToString(JsonObject.serializer(), json), Charsets.UTF_8)
    }

    private data class CookieValue(val value: String, val expires: Double?)
}

/** Результат сохранения токена. */
data class TokenResult(
    val token: String,
    val expiresAt: Long,
    val expiresAtIso: String
)

/**
 * Точка входа CLI.
 *
 * Аргументы:
 *  --headed              видимое окно (по умолчанию headless)
 *  --out <path>          путь к файлу (по умолчанию ./.wbaas_token)
 *  --timeout <sec>       таймаут ожидания cookie (по умолчанию 30)
 *  --browser <type>      chromium|firefox|webkit (по умолчанию chromium)
 *  --install             только скачать браузер Playwright и выйти (без запуска)
 *  -h, --help            справка
 */
fun main(args: Array<String>) {
    val opts = parseArgs(args) ?: exitProcess(0)
    if (opts.help) {
        printHelp()
        return
    }
    if (opts.installOnly) {
        // Запуск браузера триггерит авто-загрузку Chromium в кеш Playwright.
        println("Предзагрузка браузера Playwright (chromium)...")
        com.microsoft.playwright.Playwright.create().use { pw ->
            val browser = pw.chromium().launch(
                com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true)
            )
            browser.close()
        }
        println("Готово. Браузер закеширован, последующие запуски будут быстрее.")
        return
    }
    val result = FetchToken.fetch(
        headed = opts.headed,
        output = File(opts.output),
        timeoutSeconds = opts.timeoutSeconds,
        browserType = opts.browser
    )
    if (result == null) exitProcess(1)
}

private data class CliOptions(
    val headed: Boolean = false,
    val output: String = FetchToken.DEFAULT_OUTPUT,
    val timeoutSeconds: Long = FetchToken.DEFAULT_TIMEOUT_SECONDS,
    val browser: String = "chromium",
    val installOnly: Boolean = false,
    val help: Boolean = false
)

private fun parseArgs(args: Array<String>): CliOptions? {
    var opts = CliOptions()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--headed" -> opts = opts.copy(headed = true)
            "--headless" -> opts = opts.copy(headed = false)
            "--out" -> {
                if (i + 1 >= args.size) { System.err.println("Ошибка: --out требует аргумент"); return null }
                opts = opts.copy(output = args[++i])
            }
            "--timeout" -> {
                if (i + 1 >= args.size) { System.err.println("Ошибка: --timeout требует аргумент"); return null }
                opts = opts.copy(timeoutSeconds = args[++i].toLongOrNull()
                    ?: run { System.err.println("Ошибка: --timeout — число"); return null })
            }
            "--browser" -> {
                if (i + 1 >= args.size) { System.err.println("Ошибка: --browser требует аргумент"); return null }
                opts = opts.copy(browser = args[++i])
            }
            "--install" -> opts = opts.copy(installOnly = true)
            "-h", "--help" -> opts = opts.copy(help = true)
            else -> { System.err.println("Неизвестный аргумент: ${args[i]} (используйте --help)"); return null }
        }
        i++
    }
    return opts
}

private fun printHelp() {
    println("""
        |wb-private-api-jvm :: token-fetcher
        |Автоматическое получение x_wbaas_token из браузера через Playwright.
        |
        |Использование:
        |  ./gradlew :token-fetcher:run [аргументы]
        |
        |Аргументы:
        |  --headed              Видимое окно браузера (по умолчанию headless — для VPS).
        |                        Headed нужен, если антибот WB подставляет капчу в headless.
        |  --out <path>          Путь к файлу токена (по умолчанию ./.wbaas_token).
        |  --timeout <sec>       Таймаут ожидания cookie (по умолчанию 30).
        |  --browser <type>      chromium|firefox|webkit (по умолчанию chromium).
        |  --install             Предзагрузка браузера Chromium в кеш Playwright
        |                        (первый запуск всё равно скачает его автоматически,
        |                        но --install удобен для подготовки VPS заранее).
        |  -h, --help            Эта справка.
        |
        |Примеры:
        |  ./gradlew :token-fetcher:run
        |  ./gradlew :token-fetcher:run --args="--headed --out ~/.wbaas_token --timeout 60"
        |
        |Первый запуск скачает Chromium (~150MB) в кеш Playwright.
        |Токен действителен ~14 дней — повторите запуск при истечении.
    """.trimMargin())
}
