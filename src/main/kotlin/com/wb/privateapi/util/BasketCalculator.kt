package com.wb.privateapi.util

/**
 * Алгоритм выбора CDN-корзины (basket) для товара по его `nm_id`.
 *
 * Перенос `BASKETS` / `VIDEO_BASKETS` и функций `getBasketNumber`,
 * `getVideoBasket` из `Utils.js`.
 *
 * `vol = floor(nm_id / 100000)` — верхняя граница корзины (индекс + 1).
 * Источник: WB JS `volHostV2()` (`staticbasket_route_map`).
 *
 * Для видео используется другой алгоритм: `vol = nm_id % 144` —
 * источник: WB JS `volVideoHost()` (`videonme_route_map`).
 */
object BasketCalculator {

    /**
     * Пороговые значения `vol` (по возрастанию) для выбора basket изображений/карточек.
     * Индекс в массиве + 1 = номер корзины. Последний элемент — `Long.MAX_VALUE` (аналог `Infinity`).
     */
    private val BASKETS: LongArray = longArrayOf(
        143, 287, 431, 719, 1007, 1061, 1115, 1169, 1313, 1601,
        1655, 1919, 2045, 2189, 2405, 2621, 2837, 3053, 3269, 3485,
        3701, 3917, 4133, 4349, 4565, 4877, 5189, 5501, 5813, 6125,
        6437, 6749, 7061, 7373, 7685, 7997, 8309, 8741, 9173, 9605,
        10373, 11141, 11909, 12677, 13445, 14213,
        Long.MAX_VALUE // basket-47 (default)
    )

    /**
     * Пороговые значения `vol` для выбора корзины видео.
     * Источник: WB JS `videonme_route_map`.
     */
    private val VIDEO_BASKETS: LongArray = longArrayOf(
        11, 23, 35, 47, 59, 71, 83, 95, 107, 119, 131, 143,
        Long.MAX_VALUE // basket-13 (default)
    )

    /** Номер basket для изображений/карточек товара, дополненный нулём до 2 символов ("01".."47"). */
    fun getBasketNumber(productId: Long): String {
        val vol = productId / 100000
        return pad2(basketIndex(BASKETS, vol) + 1)
    }

    /** Номер basket для видео, дополненный нулём до 2 символов ("01".."13"). */
    fun getVideoBasket(vol: Long): String = pad2(basketIndex(VIDEO_BASKETS, vol) + 1)

    /** `vol = floor(nm_id / 100000)` — верхняя граница корзины (аналог JS `getBasketVolume`). */
    fun getBasketVolume(productId: Long): Long = productId / 100000

    /** `part = floor(nm_id / 1000)` — сегмент пути CDN. */
    fun getBasketPart(productId: Long): Long = productId / 1000

    /** `vol = nm_id % 144` — vol для видео. */
    fun getVideoVol(productId: Long): Long = productId % 144

    /** `part = floor(nm_id / 10000)` — сегмент пути CDN для видео. */
    fun getVideoPart(productId: Long): Long = productId / 10000

    private fun basketIndex(thresholds: LongArray, vol: Long): Int {
        for (i in thresholds.indices) {
            if (vol <= thresholds[i]) return i
        }
        return thresholds.lastIndex
    }

    private fun pad2(value: Int): String = value.toString().padStart(2, '0')
}
