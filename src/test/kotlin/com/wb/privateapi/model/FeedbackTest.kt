package com.wb.privateapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedbackTest {

    @Test
    fun `getPhotos builds sharded geobasket URLs from key format`() {
        val feedback = Feedback(
            mapOf(
                "id" to "123",
                "productValuation" to 5,
                "text" to "ok",
                "photos" to listOf(
                    mapOf("id" to 1, "key" to "6/uuid-123", "isBlurred" to false, "isReady" to true)
                )
            )
        )
        val urls = feedback.getPhotos()
        assertEquals(1, urls.size)
        assertEquals(
            "https://mow-feedback-uuid-06-cdn-06.geobasket.ru/uuid-123/fs.webp",
            urls[0]
        )
    }

    @Test
    fun `getPhotos returns empty when no photos`() {
        val feedback = Feedback(mapOf("id" to "1"))
        assertTrue(feedback.getPhotos().isEmpty())
    }

    @Test
    fun `getPhotos returns empty when photos list is null`() {
        val feedback = Feedback(mapOf("id" to "1", "photos" to null))
        assertTrue(feedback.getPhotos().isEmpty())
    }

    @Test
    fun `getPhotos skips photos without key`() {
        val feedback = Feedback(
            mapOf(
                "id" to "1",
                "photos" to listOf(
                    mapOf("id" to 1, "isBlurred" to false),
                    mapOf("id" to 2, "key" to "7/uuid-456", "isBlurred" to false)
                )
            )
        )
        val urls = feedback.getPhotos()
        assertEquals(1, urls.size)
        assertTrue(urls[0].endsWith("uuid-456/fs.webp"))
    }

    @Test
    fun `typed accessors read raw fields`() {
        val feedback = Feedback(mapOf("id" to "aW31ilTsJvjipGb62YJ0", "text" to "good", "productValuation" to 4))
        assertEquals("aW31ilTsJvjipGb62YJ0", feedback.id)
        assertEquals("good", feedback.text)
        assertEquals(4, feedback.productValuation)
    }

    @Test
    fun `operator get exposes arbitrary field`() {
        val feedback = Feedback(mapOf("custom" to "value"))
        assertEquals("value", feedback["custom"])
    }

    @Test
    fun `video returns null when no video`() {
        val feedback = Feedback(mapOf("id" to "1"))
        assertNull(feedback.video)
    }

    @Test
    fun `video parses video field correctly`() {
        val feedback = Feedback(
            mapOf(
                "id" to "abc123",
                "video" to mapOf(
                    "id" to "8/7ac67184-3cf7-4626-a31c-17b902c7d368",
                    "durationSec" to 18,
                    "isReady" to true
                )
            )
        )
        val v = feedback.video
        assertNotNull(v)
        assertEquals("8/7ac67184-3cf7-4626-a31c-17b902c7d368", v!!.id)
        assertEquals(18, v.durationSec)
        assertTrue(v.isReady)
    }

    @Test
    fun `video returns null when video is not ready`() {
        val feedback = Feedback(
            mapOf(
                "id" to "abc123",
                "video" to mapOf(
                    "id" to "8/uuid-not-ready",
                    "durationSec" to 10,
                    "isReady" to false
                )
            )
        )
        assertNotNull(feedback.video)
        assertNull(feedback.getVideoUrl())
    }

    @Test
    fun `getVideoUrl builds sharded HLS playlist URL`() {
        val feedback = Feedback(
            mapOf(
                "id" to "abc123",
                "video" to mapOf(
                    "id" to "8/7ac67184-3cf7-4626-a31c-17b902c7d368",
                    "durationSec" to 18,
                    "isReady" to true
                )
            )
        )
        val url = feedback.getVideoUrl()
        assertEquals(
            "https://mow-videofeedback-08-cdn-08.geobasket.ru/7ac67184-3cf7-4626-a31c-17b902c7d368/index.m3u8",
            url
        )
    }

    @Test
    fun `getVideoUrl returns null when no video`() {
        val feedback = Feedback(mapOf("id" to "1"))
        assertNull(feedback.getVideoUrl())
    }
}
