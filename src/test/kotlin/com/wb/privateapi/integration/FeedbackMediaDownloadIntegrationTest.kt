package com.wb.privateapi.integration

import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Destinations
import com.wb.privateapi.model.Feedback
import com.wb.privateapi.model.Product
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.StandardOpenOption

/**
 * Демонстрационный сквозной сценарий: получить отзывы товара и скачать
 * из них фото и видео.
 *
 * 1. Ищем товар (перебираем несколько запросов, пока не найдётся отзыв
 *    с фото и отзыв с видео — по одному товару они не гарантированы).
 * 2. [Product.getFeedbacks] — получаем отзывы.
 * 3. [Feedback.getPhotos] — скачиваем фото отзыва как есть (webp).
 * 4. [Feedback.downloadVideoMp4] — скачиваем видео отзыва, перемультиплексированное в mp4.
 *
 * Файлы/видео CDN отдаёт анонимно, без токена. Сам поиск товара — через
 * публичный WB API; так же, как в остальных интеграционных тестах, требует
 * `.wbaas_token` (см. [IntegrationHelper]) на случай, если WB включит антибот
 * на поиск в вашей сети.
 *
 * Результаты сохраняются в `build/tmp/feedback-media/` — путь печатается
 * в конце теста.
 *
 * Запуск: ./gradlew test --tests "*FeedbackMediaDownload*"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedbackMediaDownloadIntegrationTest {

    private val api = WbPrivateApi(destination = Destinations.MOSCOW)
    private val httpClient = HttpClient.newHttpClient()

    @BeforeAll
    fun checkToken() {
        Assumptions.assumeTrue(
            IntegrationHelper.TOKEN_AVAILABLE,
            "Нет .wbaas_token — интеграционные тесты пропущены"
        )
    }

    @Test
    fun `getFeedbacks then download photos and video`() = runBlocking {
        // Наличие фото/видео у конкретного товара не гарантировано и меняется
        // со временем — перебираем несколько популярных запросов.
        val searchQueries = listOf("ноутбук игровой", "робот пылесос", "наушники беспроводные", "телефон")

        var feedbackWithPhotos: Feedback? = null
        var feedbackWithVideo: Feedback? = null

        for (query in searchQueries) {
            val catalog = try {
                api.search(query, 1)
            } catch (e: Exception) {
                println("⚠️  Поиск \"$query\" не удался (${e.message}) — пробуем следующий запрос")
                continue
            }
            if (catalog.products.isEmpty()) continue
            val nmId = (catalog.products.first()["id"] as Number).toLong()

            val product = try {
                Product.create(nmId, api.session())
            } catch (e: Exception) {
                println("⚠️  Не удалось загрузить товар nmId=$nmId (${e.message})")
                continue
            }

            val feedbacks = product.getFeedbacks()
            println("[$query] nmId=$nmId, отзывов: ${feedbacks.size}")

            if (feedbackWithPhotos == null) {
                feedbackWithPhotos = feedbacks.firstOrNull { it.getPhotos().isNotEmpty() }
            }
            if (feedbackWithVideo == null) {
                feedbackWithVideo = feedbacks.firstOrNull { it.getVideoUrl() != null }
            }

            if (feedbackWithPhotos != null && feedbackWithVideo != null) break
        }

        Assumptions.assumeTrue(feedbackWithPhotos != null, "Не нашли ни одного отзыва с фото")

        val outDir = File("build/tmp/feedback-media").apply { mkdirs() }

        // --- Фото ---
        val photoUrls = feedbackWithPhotos!!.getPhotos()
        println("Фото в отзыве id=${feedbackWithPhotos.id}: ${photoUrls.size}")
        photoUrls.forEachIndexed { i, url ->
            val file = File(outDir, "photo-$i.webp")
            downloadTo(url, file)
            println("  фото $i -> ${file.absolutePath} (${file.length()} байт)")
            Assertions.assertTrue(file.length() > 0, "Фото $url скачалось пустым")
        }

        // --- Видео ---
        if (feedbackWithVideo != null) {
            val mp4 = File(outDir, "video.mp4")
            val result = feedbackWithVideo.downloadVideoMp4(mp4)
            println("Видео в отзыве id=${feedbackWithVideo.id} -> ${result?.absolutePath} (${mp4.length()} байт)")
            Assertions.assertNotNull(result)
            Assertions.assertTrue(mp4.length() > 0, "Видео скачалось пустым")
        } else {
            println("⚠️  Не нашли отзыв с видео — среди проверенных товаров видео не встретилось")
        }

        println("Результаты: ${outDir.absolutePath}")
    }

    private fun downloadTo(url: String, dest: File) {
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofFile(
                dest.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
        )
        Assertions.assertEquals(200, response.statusCode(), "Не удалось скачать $url")
    }
}
