package com.wb.privateapi.constant

enum class AppType(val value: Int) {
    DESKTOP(1),
    ANDROID(32),
    IOS(64)
}

enum class Locale(val value: String) {
    RU("ru")
}

enum class Currency(val value: String) {
    RUB("rub")
}

enum class Sex(val value: String) {
    FEMALE("female"),
    MALE("male"),
    COMMON("common")
}

object Constants {
    const val PRODUCTS_PER_PAGE = 100
    const val PAGES_PER_CATALOG = 100
    const val FEEDBACKS_PER_PAGE = 20
    const val QUESTIONS_PER_PAGE = 30
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
    const val MAX_CONCURRENCY = 5
}