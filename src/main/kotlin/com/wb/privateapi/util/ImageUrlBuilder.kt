package com.wb.privateapi.util

import com.wb.privateapi.constant.Urls

/**
 * Построение URL изображений и видео товаров/брендов.
 *
 * Перенос `imageURL`, `videoURL`, `brandImageURL` из `Utils.js`.
 *
 * Тип изображения (`SMALL`, `BIG`, `TINY`, `MEDIUM`) выбирает CDN-папку
 * и размер; JS-версия индексировала `Constants.URLS.IMAGES[imageType]`,
 * здесь — через [ImageType].
 */
object ImageUrlBuilder {

    /** Тип изображения товара → соответствующий URL-шаблон из [Urls.Images]. */
    enum class ImageType(val template: String) {
        TINY(Urls.Images.TINY),
        BIG(Urls.Images.BIG),
        SMALL(Urls.Images.SMALL),
        MEDIUM(Urls.Images.MEDIUM);

        companion object {
            /** Имя из JS-варианта (`SMALL`/`BIG`/…); регистронезависимо. */
            fun fromName(name: String?): ImageType =
                entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SMALL
        }
    }

    /**
     * URL изображения товара.
     *
     * @param productId `nm_id` товара
     * @param imageType тип/размер изображения, по умолчанию [ImageType.SMALL]
     * @param order порядковый номер изображения (с 1), по умолчанию 1
     * @return полный URL с cache-buster `?r=<timestamp>`
     */
    fun imageUrl(productId: Long, imageType: ImageType = ImageType.SMALL, order: Int = 1): String {
        val vol = BasketCalculator.getBasketVolume(productId)
        val part = BasketCalculator.getBasketPart(productId)
        val basket = BasketCalculator.getBasketNumber(productId)
        val random = System.currentTimeMillis()
        return "${formatUrl(imageType.template, basket, vol, part, productId, order)}?r=$random"
    }

    /**
     * URL видео товара. Повторяет WB `urlVideoProduct()` из фронтенд-JS.
     *
     * @param productId `nm_id` товара
     * @param videoFormat `"hls"` → m3u8-плейлист, `"mp4"` → preview-файл (360p)
     * @param quality качество HLS; WB всегда использует `"1440p"`. MP4-preview всегда `"360p"`
     * @return полный URL видео
     */
    fun videoUrl(productId: Long, videoFormat: String = "hls", quality: String = "1440p"): String {
        val id = productId
        val vol = BasketCalculator.getVideoVol(id)
        val part = BasketCalculator.getVideoPart(id)
        val basket = BasketCalculator.getVideoBasket(vol)
        val template = if (videoFormat.equals("mp4", ignoreCase = true)) {
            Urls.Video.MP4
        } else {
            Urls.Video.HLS
        }
        return formatUrl(template, basket, vol, part, id, quality)
    }

    /** URL логотипа бренда по его id. */
    fun brandImageUrl(brandId: Any?): String = formatUrl(Urls.Brand.IMAGE, brandId ?: "")
}
