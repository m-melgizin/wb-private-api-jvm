plugins {
    kotlin("jvm")
    application
}

// Тянем версию Kotlin и репозитории из корневого проекта (subprojects-блок в root build.gradle.kts).

val playwrightVersion = "1.44.0"

dependencies {
    // Playwright — управление реальным Chromium/Firefox/WebKit.
    // Скачивает браузер (~150MB) в кеш при первом `playwright install`.
    implementation("com.microsoft.playwright:playwright:$playwrightVersion")

    // kotlinx.serialization для записи .wbaas_token (JSON).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Логирование.
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.14")
}

application {
    // main() — top-level функция в FetchToken.kt → генерируется класс FetchTokenKt
    mainClass.set("com.wb.privateapi.token.FetchTokenKt")
    // По умолчанию — headless (для VPS без рабочего стола).
    // Headed-режим включается аргументом --headed.
    applicationDefaultJvmArgs = listOf("-Dtoken.fetcher.headless=true")
}

tasks.test {
    useJUnitPlatform()
}
