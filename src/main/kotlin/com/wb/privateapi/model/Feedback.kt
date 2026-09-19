package com.wb.privateapi.model

import com.wb.privateapi.constant.Urls
import com.wb.privateapi.util.formatUrl

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
     *
     * Названо `rawPhotos`, а не `photos`, чтобы синтезированный JVM-геттер
     * свойства (`getPhotos()`) не конфликтовал с методом [getPhotos].
     */
    @Suppress("UNCHECKED_CAST")
    val rawPhotos: List<Map<String, Any?>> get() = (raw["photos"] as? List<Map<String, Any?>>) ?: emptyList()

    /**
     * URL фотографий отзыва в полном размере.
     *
     * `key` приходит как `"{shard}/{uuid}"` (например, `"5/2e23a361-…"`);
     * шард зашит в имя CDN-хоста (`mow-feedback-uuid-{shard}-cdn-{shard}.geobasket.ru`).
     * Старый единый домен `feedbackphotos.wbstatic.net` выведен из эксплуатации.
     *
     * @return список полных URL фотографий (размер `fs`)
     */
    fun getPhotos(): List<String> {
        return rawPhotos.mapNotNull { it["key"] as? String }
            .mapNotNull { key -> parseShardedKey(key) }
            .map { (shard, uuid) -> formatUrl(Urls.Feedback.PHOTO, shard, uuid, "fs") }
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
     * URL HLS-плейлиста видео отзыва (если есть и готово к показу).
     * Прямого mp4-файла CDN не отдаёт — только `index.m3u8` + `.ts`-чанки
     * на `mow-videofeedback-{shard}-cdn-{shard}.geobasket.ru`.
     *
     * @return URL плейлиста или `null`
     */
    fun getVideoUrl(): String? {
        val v = video ?: return null
        if (!v.isReady) return null
        val (shard, uuid) = parseShardedKey(v.id) ?: return null
        return formatUrl(Urls.Feedback.VIDEO_PLAYLIST, shard, uuid)
    }

    override fun toString(): String =
        "Feedback(id=$id, valuation=$productValuation, photos=${rawPhotos.size}, video=${video != null})"

    companion object {
        /**
         * Разбирает `"{shard}/{uuid}"` в пару (шард, дополненный нулём до 2
         * цифр; например, `"6"` → `"06"`, `"10"` → `"10"`, `uuid`).
         */
        private fun parseShardedKey(key: String): Pair<String, String>? {
            val slash = key.indexOf('/')
            if (slash <= 0 || slash == key.length - 1) return null
            val shard = key.substring(0, slash).toIntOrNull() ?: return null
            val uuid = key.substring(slash + 1)
            return shard.toString().padStart(2, '0') to uuid
        }
    }
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
