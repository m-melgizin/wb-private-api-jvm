package com.wb.privateapi.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlin.random.Random

/**
 * Генерация идентификаторов запроса и псевдо-пользователей.
 *
 * Перенос `genNewUserID`, `formatDateForQueryId`, `getQueryIdForSearch`
 * из `Utils.js`. Значения используются в заголовке `x-queryid` и в
 * query-параметрах поиска WB.
 *
 * Алгоритм `genNewUserID` намеренно сохранён 1-в-1: конкатенация
 * случайного int из диапазона `[0, 2^30)` и unix-секунд.
 */
object QueryIdGenerator {

    private val QUERY_DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /**
     * Псевдо-userID: `<случайное число из [0, 2^30)><unix-секунды>`.
     *
     * В JS: `Math.floor(Math.random() * Math.pow(2, 30)).toString() + t.toString()`.
     * `.toString()` для целого числа без аргументов — это десятичное представление.
     */
    fun genNewUserId(now: LocalDateTime = LocalDateTime.now()): String {
        val t = now.toEpochSecond(java.time.ZoneOffset.UTC)
        val e = (Random.nextDouble() * 2.0.pow(30)).toLong().toString() + t.toString()
        return e
    }

    /** Дата-время в формате `yyyyMMddHHmmss` (UTC) для queryId. */
    fun formatDateForQueryId(date: LocalDateTime = LocalDateTime.now()): String =
        date.format(QUERY_DATE_FORMAT)

    /** `qid<genNewUserID><yyyyMMddHHmmss>` — значение для заголовка `x-queryid`. */
    fun getQueryIdForSearch(now: LocalDateTime = LocalDateTime.now()): String =
        "qid${genNewUserId(now)}${formatDateForQueryId(now)}"
}
