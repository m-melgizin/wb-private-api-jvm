package com.wb.privateapi.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.ffmpeg
import org.bytedeco.javacpp.Loader
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Ремукс HLS (`.m3u8`) в mp4 через ffmpeg, забандленный зависимостью
 * `org.bytedeco:ffmpeg-platform` (нативные бинарники под все платформы
 * внутри jar) — установка ffmpeg в систему пользователя не требуется.
 *
 * Используется для видео из отзывов ([com.wb.privateapi.model.Feedback]):
 * CDN отдаёт только `index.m3u8` + `.ts`-чанки, прямого mp4-файла нет.
 */
object FfmpegRemuxer {

    /** Путь к бинарнику ffmpeg, извлечённому JavaCPP из jar при первом обращении. */
    private val ffmpegPath: String by lazy { Loader.load(ffmpeg::class.java) }

    /**
     * Скачивает плейлист [source] (сам ffmpeg делает HTTP-запросы за все
     * чанки — сегменты вручную загружать не нужно) и перемультиплексирует
     * его в [destination] без перекодирования (`-c copy`).
     *
     * @param source URL `.m3u8`-плейлиста (или локальный путь — для тестов)
     * @param destination путь для результирующего mp4; родительские директории создаются
     * @param timeoutSeconds таймаут на весь процесс ремукса
     * @return [destination] при успехе
     * @throws IOException если ffmpeg завершился с ошибкой или превышен таймаут
     */
    suspend fun remuxToMp4(
        source: String,
        destination: File,
        timeoutSeconds: Long = 120
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val process = ProcessBuilder(
            ffmpegPath,
            "-y",
            "-i", source,
            "-c", "copy",
            "-bsf:a", "aac_adtstoasc",
            "-movflags", "+faststart",
            destination.absolutePath
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            destination.delete()
            throw IOException("ffmpeg не завершился за ${timeoutSeconds}s (source=$source)")
        }
        if (process.exitValue() != 0) {
            destination.delete()
            throw IOException("ffmpeg завершился с кодом ${process.exitValue()} (source=$source):\n$output")
        }
        destination
    }
}
