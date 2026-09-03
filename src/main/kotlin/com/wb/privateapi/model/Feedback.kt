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

    /**
     * Сырой список фотографий отзыва.
     * Новая структура WB: каждая фото — `{"id": N, "key": "p/uuid",
     * "isBlurred": bool, "isReady": bool}`.
     */
    @Suppress("UNCHECKED_CAST")
    val photos: List<Map<String, Any?>> get() = (raw["photos"] as? List<Map<String, Any?>>) ?: emptyList()

    /**
     * URL фотографий отзыва.
     *
     * Новая структура WB API: фото приходят как `{"key": "p/uuid", ...}`.
     * Полный URL: `{FEEDBACK_BASE}/{key}`.
     * Старая структура (`minSizeUri`) больше не поддерживается.
     *
     * @return список полных URL фотографий
     */
    fun getPhotos(): List<String> {
        return photos.mapNotNull { it["key"] as? String }
            .map { p -> Urls.Images.FEEDBACK_BASE + p }
    }

    /**
     * Информация о видео в отзыве (если есть).
     *
     * WB API возвращает `"video": {"id": "p/uuid", "durationSec": N, "isReady": bool}`.
     * @return [FeedbackVideo] или `null`, если видео нет
     */
    val video: FeedbackVideo?
        get() {
            val v = raw["video"] as? Map<*, *> ?: return null
            val id = v["id"] as? String ?: return null
            val durationSec = (v["durationSec"] as? Number)?.toInt() ?: 0
            val isReady = v["isReady"] as? Boolean ?: false
            return FeedbackVideo(id = id, durationSec = durationSec, isReady = isReady)
        }

    /**
     * URL видео отзыва (если есть и готово к показу).
     *
     * Строится по тому же CDN, что и фото: `{FEEDBACK_BASE}/{videoId}`.
     * @return полный URL видео или `null`
     */
    fun getVideoUrl(): String? {
        val v = video ?: return null
        if (!v.isReady) return null
        return Urls.Images.FEEDBACK_BASE + v.id
    }

    override fun toString(): String =
        "Feedback(id=$id, valuation=$productValuation, photos=${photos.size}, video=${video != null})"
}

/**
 * Видео в отзыве.
 *
 * @property id идентификатор видео (`"partition/uuid"`)
 * @property durationSec длительность в секундах
 * @property isReady готово ли видео к просмотру
 */
data class FeedbackVideo(
    val id: String,
    val durationSec: Int,
    val isReady: Boolean
)
