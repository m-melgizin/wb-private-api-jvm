package com.wb.privateapi.model

import com.wb.privateapi.constant.Urls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedbackTest {

    @Test
    fun `getPhotos with min size builds full URLs`() {
        val feedback = Feedback(
            mapOf(
                "id" to 123L,
                "productValuation" to 5,
                "text" to "ok",
                "photos" to listOf(
                    mapOf("minSizeUri" to "/a/b.jpg", "c516x516SizeUri" to "/a/b_516.jpg")
                )
            )
        )
        val min = feedback.getPhotos("min")
        assertEquals(1, min.size)
        assertTrue(min[0].startsWith(Urls.Images.FEEDBACK_BASE), "url=${min[0]}")
        assertTrue(min[0].endsWith("/a/b.jpg"), "url=${min[0]}")

        val big = feedback.getPhotos("c516x516")
        assertEquals(1, big.size)
        assertTrue(big[0].endsWith("/a/b_516.jpg"), "url=${big[0]}")
    }

    @Test
    fun `getPhotos returns empty when no photos`() {
        val feedback = Feedback(mapOf("id" to 1L))
        assertTrue(feedback.getPhotos().isEmpty())
    }

    @Test
    fun `typed accessors read raw fields`() {
        val feedback = Feedback(mapOf("id" to 42L, "text" to "good", "productValuation" to 4))
        assertEquals(42L, feedback.id)
        assertEquals("good", feedback.text)
        assertEquals(4, feedback.productValuation)
    }

    @Test
    fun `operator get exposes arbitrary field`() {
        val feedback = Feedback(mapOf("custom" to "value"))
        assertEquals("value", feedback["custom"])
    }
}
