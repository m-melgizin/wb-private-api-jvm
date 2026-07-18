package com.wb.privateapi.util

import com.wb.privateapi.util.ImageUrlBuilder.ImageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageUrlBuilderTest {

    /**
     * Эталон: tests/Utils.test.js → Card.imageURL(177899980, "BIG", 3)
     * ожидается подстрока:
     *   https://basket-12.wbbasket.ru/vol1778/part177899/177899980/images/big/3.webp
     */
    @Test
    fun `image url matches JS reference for BIG type`() {
        val url = ImageUrlBuilder.imageUrl(177899980L, ImageType.BIG, 3)
        assertTrue(
            url.contains("https://basket-12.wbbasket.ru/vol1778/part177899/177899980/images/big/3.webp"),
            "url=$url"
        )
        assertTrue(url.contains("?r="), "cache-buster expected, url=$url")
    }

    /** Эталон: tests/Utils.test.js → Brand.imageURL(87238) */
    @Test
    fun `brand image url matches JS reference`() {
        val url = ImageUrlBuilder.brandImageUrl(87238)
        assertTrue(
            url.contains("https://static-basket-01.wbbasket.ru/vol0/brand-flow-logos/by-id/87238.webp"),
            "url=$url"
        )
    }

    @Test
    fun `video url uses video basket path`() {
        val url = ImageUrlBuilder.videoUrl(177899980L, "hls", "1440p")
        assertTrue(url.contains(".m3u8") || url.contains("video"), "url=$url")
    }

    @Test
    fun `mp4 video url returns preview path`() {
        val url = ImageUrlBuilder.videoUrl(177899980L, "mp4", "360p")
        assertTrue(url.contains("360p") || url.endsWith(".mp4") || url.contains("mp4"), "url=$url")
    }

    @Test
    fun `image type fromName falls back to SMALL`() {
        assertEquals(ImageType.SMALL, ImageType.fromName(null))
        assertEquals(ImageType.SMALL, ImageType.fromName("unknown"))
        assertEquals(ImageType.BIG, ImageType.fromName("big"))
        assertEquals(ImageType.BIG, ImageType.fromName("BIG"))
    }
}
