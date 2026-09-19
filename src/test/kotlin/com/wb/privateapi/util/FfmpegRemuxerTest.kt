package com.wb.privateapi.util

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.IOException

class FfmpegRemuxerTest {

    @Test
    fun `remuxToMp4 throws and cleans up destination when source is unreadable`() = runBlocking {
        val dest = File.createTempFile("ffmpeg-remuxer-test", ".mp4").apply { delete() }
        val missingSource = "/no/such/file-${System.nanoTime()}.m3u8"

        assertThrows<IOException> {
            runBlocking { FfmpegRemuxer.remuxToMp4(missingSource, dest) }
        }
        assertFalse(dest.exists(), "destination должен быть удалён при ошибке ffmpeg")
    }
}
