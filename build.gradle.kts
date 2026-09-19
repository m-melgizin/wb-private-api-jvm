plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "com.wb"
version = "0.1.0"

repositories {
    mavenCentral()
}

val okHttpVersion = "4.12.0"
val coroutinesVersion = "1.7.3"
val ffmpegVersion = "8.1.2-1.5.14"

// Общие настройки для всех подпроектов (версия Kotlin, репозитории, toolchain).
subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        kotlin {
            jvmToolchain(21)
        }
    }
}

dependencies {
    // OkHttp client
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Бандленный ffmpeg (нативные бинарники под все платформы) — для ремукса
    // HLS-видео отзывов в mp4 без установки ffmpeg в систему пользователя.
    implementation("org.bytedeco:ffmpeg-platform:$ffmpegVersion")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.22")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okHttpVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}