package com.wb.privateapi.integration

import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Destinations
import java.io.File

/**
 * Общий хелпер для интеграционных тестов.
 *
 * - проверяет наличие файла `.wbaas_token` (и валидность `expires_at`);
 * - находит тестовый товар через поиск;
 * - предоставляет [WbPrivateApi] и тестовый `nmId`.
 */
object IntegrationHelper {

    @JvmStatic
    fun isTokenAvailable(): Boolean {
        val file = File(".wbaas_token").takeIf { it.isFile } ?: return false
        return try {
            val text = file.readText()
            // минимальный парсер: {"token":"...","expires_at":<ms>}
            val expiresAt = Regex("\"expires_at\"\\s*:\\s*(-?\\d+)").find(text)
                ?.groupValues?.getOrNull(1)?.toLongOrNull()
            expiresAt == null || expiresAt > System.currentTimeMillis()
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    val TOKEN_AVAILABLE: Boolean = isTokenAvailable()
}