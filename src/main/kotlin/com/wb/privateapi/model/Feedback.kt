package com.wb.privateapi.model

import com.wb.privateapi.constant.Urls

/**
 * Отзыв на товар. Перенос `WBFeedback` из `WBFeedback.js`.
 *
 * JS использует `Object.assign(this, feedback)` — все поля ответа API
 * становятся свойствами экземпляра. В Kotlin храним исходный `raw: Map`
 * и предоставляем типизированные геттеры для известных полей плюс
 * доступ к произвольным полям через [get].
 */
class Feedback(val raw: Map<String, Any?>) {

    /** Произвольное поле ответа (аналог прямого доступа к свойству в JS). */
    operator fun get(key: String): Any? = raw[key]

    /** Идентификатор отзыва. */
    val id: Long? by lazy { (raw["id"] as? Number)?.toLong() }

    /** Текст отзыва. */
    val text: String? get() = raw["text"] as? String

    /** Оценка (1..5). */
    val productValuation: Int? get() = (raw["productValuation"] as? Number)?.toInt()

    /** Сырой список фотографий отзыва (объекты `photo`). */
    @Suppress("UNCHECKED_CAST")
    val photos: List<Map<String, Any?>> get() = (raw["photos"] as? List<Map<String, Any?>>) ?: emptyList()

    /**
     * URL фотографий отзыва заданного размера.
     *
     * @param size суффикс размера; WB API использует поля вида `<size>SizeUri`
     *             (`min`, `c516x516bb`, …). Если `size != "min"`, к нему
     *             добавляется `SizeUri` (поведение JS).
     * @return список полных URL фотографий
     */
    fun getPhotos(size: String = "min"): List<String> {
        val field = if (size == "min") "minSizeUri" else "${size}SizeUri"
        return photos.mapNotNull { it[field] as? String }
            .map { p -> Urls.Images.FEEDBACK_BASE + p }
    }

    override fun toString(): String = "Feedback(id=$id, valuation=$productValuation, photos=${photos.size})"
}
