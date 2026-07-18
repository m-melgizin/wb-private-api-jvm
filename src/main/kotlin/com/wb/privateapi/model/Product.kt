@file:Suppress("UNCHECKED_CAST")

package com.wb.privateapi.model

import com.wb.privateapi.constant.AppType
import com.wb.privateapi.constant.Constants
import com.wb.privateapi.constant.Currency
import com.wb.privateapi.constant.DestinationInfo
import com.wb.privateapi.constant.Destinations
import com.wb.privateapi.constant.Locale
import com.wb.privateapi.constant.Urls
import com.wb.privateapi.error.WbException
import com.wb.privateapi.session.Session
import com.wb.privateapi.session.SessionBuilder
import com.wb.privateapi.util.Crc16
import com.wb.privateapi.util.ImageUrlBuilder
import com.wb.privateapi.util.formatUrl
import com.wb.privateapi.util.ImageUrlBuilder.ImageType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Товар WB. Перенос `WBProduct` из `WBProduct.js`.
 *
 * JS хранит все поля ответа API как собственные свойства (`Object.assign(this, product)`)
 * и несколько вычисляемых полей (`stocks`, `promo`, `feedbacks`). В Kotlin:
 *  - [raw] — исходная мапа полей товара (доступ через [get])
 *  - типизированные геттеры для часто используемых полей
 *  - [stocks], [promo], [feedbacks], [totalQuestions] — мутируемые, заполняются suspend-методами
 *
 * [create] повторяет JS `WBProduct.create` — параллельно подгружает card/details/seller
 * и количество вопросов.
 */
class Product private constructor(
    initial: Map<String, Any?>,
    val session: Session,
    val destination: DestinationInfo = Destinations.MOSCOW
) {
    /** Сырые поля товара (аналог `Object.assign(this, product)`). */
    var raw: Map<String, Any?> = initial
        private set

    /** Необработанный ответ card.json (basket). Заполняется в [getProductData]. */
    var rawCard: Map<String, Any?> = emptyMap()
        private set

    /** Необработанный ответ `cards/v4/detail` (`details`). Заполняется в [getDetailsData]. */
    var rawDetails: Map<String, Any?> = emptyMap()
        private set

    /** Необработанный ответ `sellers.json`. Заполняется в [getSellerData]. */
    var rawSellers: Map<String, Any?> = emptyMap()
        private set

    val stocks: MutableList<Map<String, Any?>> = mutableListOf()
    var promo: Map<String, Any?> = emptyMap()
    val feedbacks: MutableList<Feedback> = mutableListOf()
    var totalQuestions: Int = 0
        private set

    val id: Long by lazy { (raw["id"] as? Number)?.toLong() ?: (rawCard["nm"] as? Number)?.toLong() ?: 0L }
    val name: String? get() = raw["name"] as? String
    val brand: String? get() = (raw["brand"] as? String) ?: (rawSellers["brand"] as? String)
    val salePriceU: Long? get() = (raw["salePriceU"] as? Number)?.toLong()
    val priceU: Long? get() = (raw["priceU"] as? Number)?.toLong()
    val imtId: Long?
        get() = (raw["imt_id"] as? Number)?.toLong()
            ?: (rawCard["imt_id"] as? Number)?.toLong()
            ?: (rawDetails["imt_id"] as? Number)?.toLong()

    /** `dest` — последний id направления доставки (повтор `destination.ids.at(-1)`). */
    val dest: Int = destination.lastId

    /**
     * Сумма `qty` всех остатков первого размера (`details.sizes[0].stocks`).
     * Повтор геттера `totalStocks` из JS. Требует загруженных [rawDetails].
     */
    val totalStocks: Int
        get() {
            val sizes = (rawDetails["sizes"] as? List<*>) ?: return 0
            val firstSize = sizes.firstOrNull() as? Map<String, Any?> ?: return 0
            val sizeStocks = firstSize["stocks"] as? List<*> ?: return 0
            return sizeStocks.sumOf { s ->
                ((s as? Map<String, Any?>)?.get("qty") as? Number)?.toInt() ?: 0
            }
        }

    /** Произвольное поле товара. */
    operator fun get(key: String): Any? = raw[key]

    /**
     * Текущая цена (afterSale). Повтор геттера `currentPrice` из JS:
     * `details.sizes[0].price.product`.
     */
    val currentPrice: Long?
        get() {
            val sizes = (rawDetails["sizes"] as? List<*>) ?: return null
            val first = sizes.firstOrNull() as? Map<String, Any?> ?: return null
            val price = first["price"] as? Map<String, Any?> ?: return null
            return (price["product"] as? Number)?.toLong()
        }

    /** URL главного изображения товара (`SMALL` по умолчанию). */
    fun imageUrl(imageType: ImageType = ImageType.SMALL, order: Int = 1): String =
        ImageUrlBuilder.imageUrl(id, imageType, order)

    /** URL видео товара (HLS-плейлист по умолчанию). */
    fun videoUrl(format: String = "hls", quality: String = "1440p"): String =
        ImageUrlBuilder.videoUrl(id, format, quality)

    /**
     * Заводской конструктор: создаёт «пустой» товар по `nm_id` или из мапы.
     * Аналог JS `new WBProduct(product, { session, destination })`.
     */
    constructor(
        product: Any,
        session: Session = SessionBuilder.create(),
        destination: DestinationInfo = Destinations.MOSCOW
    ) : this(
        initial = if (product is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            product as Map<String, Any?>
        } else {
            mapOf("id" to (product as Number).toLong())
        },
        session = session,
        destination = destination
    )

    companion object {
        /**
         * Создаёт товар и параллельно подгружает card/details/seller + количество вопросов.
         * Повтор `WBProduct.create` из JS.
         */
        suspend fun create(
            productId: Long,
            session: Session = SessionBuilder.create(),
            destination: DestinationInfo = Destinations.MOSCOW
        ): Product {
            val instance = Product(productId, session, destination)
            coroutineScope {
                awaitAll(
                    async { instance.getProductData() },
                    async { instance.getDetailsData() },
                    async { instance.getSellerData() }
                )
            }
            instance.getQuestionsCount()
            return instance
        }
    }

    /**
     * Загрузка `card.json` (basket). Заполняет [rawCard] и [stocks].
     * Повтор `getProductData` из JS.
     */
    suspend fun getProductData(): Product {
        val vol = id / 100000
        val part = id / 1000
        val basket = basketNumber(id)
        val url = formatUrl(Urls.Product.CARD, basket, vol, part, id)
        val res = session.get(url)
        val data = (res.data as? Map<String, Any?>).orEmpty()
        rawCard = data
        stocks.clear()
        val sizes = (data["sizes"] as? List<*>) ?: return this
        for (size in sizes) {
            val sizeMap = size as? Map<String, Any?> ?: continue
            val sizeStocks = sizeMap["stocks"] as? List<*> ?: continue
            for (s in sizeStocks) {
                (s as? Map<String, Any?>)?.let { stocks.add(it) }
            }
        }
        return this
    }

    /**
     * Загрузка `cards/v4/detail`. Заполняет [rawDetails]. Повтор `getDetailsData` из JS.
     */
    suspend fun getDetailsData(): Product {
        val url = if (session.hasToken()) Urls.Product.DETAILS_INTERNAL else Urls.Product.DETAILS
        val params = mapOf(
            "appType" to AppType.DESKTOP.value,
            "curr" to Currency.RUB.value,
            "dest" to dest,
            "spp" to 30,
            "hide_dtype" to 10,
            "lang" to Locale.RU.value,
            "nm" to id
        )
        val res = session.get(url, params = params)
        val data = res.data as? Map<String, Any?> ?: return this
        @Suppress("UNCHECKED_CAST")
        val products = (data["products"] as? List<Map<String, Any?>>).orEmpty()
        rawDetails = products.firstOrNull() ?: emptyMap()
        return this
    }

    /**
     * Загрузка `sellers.json`. Заполняет [rawSellers]. Повтор `getSellerData` из JS.
     */
    suspend fun getSellerData(): Product {
        val vol = id / 100000
        val part = id / 1000
        val basket = basketNumber(id)
        val url = formatUrl(Urls.Product.SELLERS, basket, vol, part, id)
        val res = session.get(url)
        rawSellers = (res.data as? Map<String, Any?>).orEmpty()
        return this
    }

    /**
     * Остатки товара: `details.sizes[0].stocks`. Повтор `getStocks` из JS.
     *
     * Не делает отдельного сетевого запроса — использует [rawDetails], при
     * необходимости подгружая их через [getDetailsData]. Возвращает массив
     * складских остатков первого размера.
     */
    suspend fun getStocks(): List<Map<String, Any?>> {
        if (rawDetails.isEmpty()) {
            getDetailsData()
        }
        val sizes = (rawDetails["sizes"] as? List<*>) ?: return emptyList()
        val firstSize = sizes.firstOrNull() as? Map<String, Any?> ?: return emptyList()
        val sizeStocks = firstSize["stocks"] as? List<*> ?: return emptyList()
        return sizeStocks.mapNotNull { it as? Map<String, Any?> }
    }

    /**
     * Промо-данные товара: `card.panelPromoId`. Повтор `getPromo` из JS.
     *
     * Если `panelPromoId` отсутствует в [rawCard], подгружает card через
     * [getProductData] и пытается снова. Возвращает промо-объект или пустую мапу.
     */
    suspend fun getPromo(): Map<String, Any?> {
        promo = extractPanelPromo() ?: emptyMap()
        if (promo.isEmpty()) {
            getProductData()
            promo = extractPanelPromo() ?: emptyMap()
        }
        return promo
    }

    private fun extractPanelPromo(): Map<String, Any?>? {
        val panelPromoId = rawCard["panelPromoId"] ?: return null
        return mapOf("panelPromoId" to panelPromoId)
    }

    /**
     * Все отзывы. Повтор `getFeedbacks` из JS: выбор партиции через CRC16-ARC
     * от `imt_id` (`% 100 >= 50 → "2"`, иначе `"1"`).
     */
    suspend fun getFeedbacks(): List<Feedback> {
        val imtId = imtId ?: run {
            feedbacks.clear()
            return emptyList()
        }
        val partitionId = if (Crc16.crc16Arc(imtId) % 100 >= 50) "2" else "1"
        val url = formatUrl(Urls.Product.FEEDBACKS, partitionId, imtId)
        val res = session.get(url)
        val data = (res.data as? Map<String, Any?>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val list = (data["feedbacks"] as? List<Map<String, Any?>>).orEmpty()
        feedbacks.clear()
        feedbacks.addAll(list.map { Feedback(it) })
        return feedbacks.toList()
    }

    /**
     * Количество вопросов. Повтор `getQuestionsCount` из JS.
     */
    suspend fun getQuestionsCount(): Int {
        val imtId = imtId ?: run {
            totalQuestions = 0
            return 0
        }
        val res = session.get(
            Urls.Product.QUESTIONS,
            params = mapOf("imtId" to imtId, "onlyCount" to true)
        )
        val data = res.data as? Map<String, Any?>
        totalQuestions = (data?.get("count") as? Number)?.toInt() ?: 0
        return totalQuestions
    }

    /**
     * Все вопросы товара. Повтор `getQuestions` из JS: ограничение WB — `skip < 510`.
     * Возвращает [QuestionsResult]; проверяйте [QuestionsResult.truncated].
     */
    suspend fun getQuestions(): QuestionsResult {
        val totalPages = (totalQuestions + Constants.QUESTIONS_PER_PAGE - 1) / Constants.QUESTIONS_PER_PAGE
        val all = mutableListOf<Question>()
        for (page in 1..totalPages) {
            val skip = (page - 1) * Constants.QUESTIONS_PER_PAGE
            if (skip >= 510) {
                return QuestionsResult(
                    items = all,
                    totalQuestions = totalQuestions,
                    fetchedQuestions = all.size,
                    truncated = totalQuestions > all.size
                )
            }
            val res = session.get(
                Urls.Product.QUESTIONS,
                params = mapOf(
                    "imtId" to (imtId ?: 0L),
                    "skip" to skip,
                    "take" to Constants.QUESTIONS_PER_PAGE
                )
            )
            val data = res.data as? Map<String, Any?> ?: continue
            @Suppress("UNCHECKED_CAST")
            val items = (data["data"] as? List<Map<String, Any?>>).orEmpty()
            if (items.isEmpty()) break
            all.addAll(items.map { Question(it) })
        }
        return QuestionsResult(
            items = all,
            totalQuestions = totalQuestions,
            fetchedQuestions = all.size,
            truncated = false
        )
    }

    /**
     * Информация о видео товара. Повтор `getVideo` из JS:
     * загрузка HLS-плейлиста, подсчёт длительности, разбор на `.ts`-чанки.
     */
    suspend fun getVideo(): VideoInfo {
        val playlistUrl = videoUrl("hls", "1440p")
        val res = try {
            session.get(playlistUrl, responseType = com.wb.privateapi.session.ResponseType.TEXT)
        } catch (_: WbException) {
            return VideoInfo(hasVideo = false, quality = "1440p", playlistUrl = playlistUrl)
        }
        val playlist = res.data as? String ?: return VideoInfo(
            hasVideo = true, quality = "1440p", playlistUrl = playlistUrl,
            error = "playlist fetch failed"
        )

        val extinfLines = playlist.lineSequence()
            .filter { it.startsWith("#EXTINF:") }
            .toList()
        val chunks = extinfLines.size
        val duration = extinfLines.sumOf { line ->
            line.removePrefix("#EXTINF:").trim().takeWhile { it.isDigit() || it == '.' }
                .toDoubleOrNull() ?: 0.0
        }
        val hls = (1..chunks).map { i -> playlistUrl.replace("index.m3u8", "$i.ts") }
        val mp4Preview = videoUrl("mp4", "360p")
        return VideoInfo(
            hasVideo = true,
            quality = "1440p",
            playlistUrl = playlistUrl,
            duration = kotlin.math.round(duration).toInt(),
            chunks = chunks,
            hls = hls,
            mp4Preview = mp4Preview
        )
    }

    /** Локальный расчёт basket-номера (аналог `Utils.Card.getBasketNumber`). */
    private fun basketNumber(productId: Long): String =
        com.wb.privateapi.util.BasketCalculator.getBasketNumber(productId)
}

/**
 * Результат загрузки вопросов. Перенос возвращаемого значения `getQuestions` из JS.
 */
data class QuestionsResult(
    val items: List<Question>,
    val totalQuestions: Int,
    val fetchedQuestions: Int,
    val truncated: Boolean
)

/**
 * Информация о видео товара. Перенос возвращаемого значения `getVideo` из JS.
 */
data class VideoInfo(
    val hasVideo: Boolean,
    val quality: String,
    val playlistUrl: String,
    val duration: Int = 0,
    val chunks: Int = 0,
    val hls: List<String> = emptyList(),
    val mp4Preview: String? = null,
    val error: String? = null
)
