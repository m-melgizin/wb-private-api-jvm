@file:Suppress("UNCHECKED_CAST")

package com.wb.privateapi

import com.wb.privateapi.constant.AppType
import com.wb.privateapi.constant.Constants
import com.wb.privateapi.constant.Currency
import com.wb.privateapi.constant.DestinationInfo
import com.wb.privateapi.constant.Destinations
import com.wb.privateapi.constant.Locale
import com.wb.privateapi.constant.Sex
import com.wb.privateapi.constant.Urls
import com.wb.privateapi.model.Catalog
import com.wb.privateapi.model.Product
import com.wb.privateapi.session.RetryOptions
import com.wb.privateapi.session.Session
import com.wb.privateapi.session.SessionBuilder
import com.wb.privateapi.util.QueryIdGenerator
import com.wb.privateapi.util.formatUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder
import java.util.concurrent.Semaphore
import kotlin.math.ceil
import kotlin.math.min

/**
 * Фасад приватного API Wildberries. Перенос `WBPrivateAPI` из `WBPrivateAPI.js`.
 *
 * Конструктор повторяет JS: направление доставки и опциональный токен.
 * Токен можно передать явно (`wbaasToken`) или он читается из `.wbaas_token`.
 * Все методы — `suspend` (аналог `async`), параллельная загрузка страниц —
 * через [mapConcurrent] (аналог `mapWithConcurrency(5)`).
 *
 * Возвращает [Catalog] с плоскими `Map`-товарами (как JS-версия); обернуть
 * товар в [Product] можно через `Product(map, session, destination)`.
 */
class WbPrivateApi(
    val destination: DestinationInfo = Destinations.MOSCOW,
    wbaasToken: String? = null
) {
    private val session: Session = SessionBuilder.create(wbaasToken = wbaasToken)

    /** Последний id направления (аналог `dest = destination.ids.at(-1)`). */
    val dest: Int = destination.lastId

    /** HTTP-сессия (для передачи в [Product]). */
    fun session(): Session = session

    /**
     * Выбор external/internal URL для эндпойнта.
     * JS использует _internal-версию напрямую (без proxy-переписывания),
     * когда токен есть, и external — когда его нет.
     */
    private fun searchUrl(external: String, internal: String): String =
        if (session.hasToken()) internal else external

    /**
     * Устанавливает токен `x_wbaas_token` (Cookie) для доступа к `__internal`.
     * Повтор `setToken` из JS.
     *
     * Примечание: текущая реализация [Session] фиксирует токен при создании.
     * Для переключения токена создайте новый экземпляр [WbPrivateApi].
     */
    fun setToken(@Suppress("UNUSED_PARAMETER") token: String) {
        // Session хранит токен иммутабельно; для смены токена нужен новый SessionBuilder.
        // Этот метод оставлен для API-совместимости с JS-версией.
        throw UnsupportedOperationException(
            "Token is immutable in this build. Create a new WbPrivateApi(wbaasToken = ...) instead."
        )
    }

    /**
     * Поиск товаров по ключевому слову. Повтор `search` из JS.
     *
     * @param keyword поисковый запрос
     * @param pageCount число страниц (0 = все доступные, ограничено `PAGES_PER_CATALOG`)
     * @param retries число повторных попыток на страницу
     * @param filters массив фильтров `{ type, value }`
     * @return [Catalog] с товарами
     */
    suspend fun search(
        keyword: String,
        pageCount: Int = 0,
        retries: Int = 0,
        filters: List<CatalogFilter> = emptyList()
    ): Catalog {
        val totalProducts = searchTotalProducts(keyword)
        if (totalProducts == 0) {
            return Catalog(
                catalogType = null,
                catalogValue = null,
                pages = 0,
                products = emptyList(),
                totalProducts = 0
            )
        }

        val metadata = getQueryMetadata(keyword, limit = 0, withProducts = false, page = 1, retries = retries)
        val catalogConfig = CatalogConfig(keyword = keyword, catalogType = metadata.catalogType, catalogValue = metadata.catalogValue)

        var totalPages = getPageCount(totalProducts)
        if (pageCount > 0 && pageCount < totalPages) {
            totalPages = pageCount
        }

        val threads = (1..totalPages).toList()
        val parsedPages = mapConcurrent(threads, Constants.MAX_CONCURRENCY) { page ->
            getCatalogPage(catalogConfig, page, retries, filters)
        }

        val products = mutableListOf<Map<String, Any?>>()
        for (page in parsedPages) {
            products.addAll(page)
        }

        return Catalog(
            catalogType = catalogConfig.catalogType,
            catalogValue = catalogConfig.catalogValue,
            pages = totalPages,
            products = products,
            totalProducts = totalProducts
        )
    }

    /**
     * Метаданные поискового запроса: `catalog_type`, `catalog_value`, товары.
     * Повтор `getQueryMetadata` из JS.
     */
    suspend fun getQueryMetadata(
        keyword: String,
        limit: Int = 0,
        @Suppress("UNUSED_PARAMETER") withProducts: Boolean = false,
        page: Int = 1,
        retries: Int = 0,
        suppressSpellcheck: Boolean = true
    ): QueryMetadata {
        val params = mutableMapOf<String, Any?>(
            "appType" to AppType.DESKTOP.value,
            "curr" to Currency.RUB.value,
            "dest" to dest,
            "query" to keyword,
            "resultset" to "catalog",
            "sort" to "popular",
            "spp" to "30",
            "suppressSpellcheck" to suppressSpellcheck
        )
        if (page != 1) params["page"] = page
        if (limit != 100) params["limit"] = limit

        val res = session.get(
            searchUrl(Urls.Search.EXACTMATCH, Urls.Search.EXACTMATCH_INTERNAL),
            params = params,
            headers = mapOf("x-queryid" to QueryIdGenerator.getQueryIdForSearch()),
            retryOptions = RetryOptions(retries = retries)
        )
        val data = res.data as? Map<String, Any?> ?: return QueryMetadata(null, null, emptyList())
        val metadata = data["metadata"] as? Map<String, Any?>

        if (metadata != null && metadata.containsKey("catalog_type") && metadata.containsKey("catalog_value")) {
            val products = (data["data"] as? Map<String, Any?>)?.get("products") as? List<*>
                ?: data["products"] as? List<*>
            return QueryMetadata(
                catalogType = metadata["catalog_type"],
                catalogValue = metadata["catalog_value"],
                products = (products as? List<Map<String, Any?>>) ?: emptyList()
            )
        }

        if (data.containsKey("shardKey") && data.containsKey("query")) {
            return QueryMetadata(
                catalogType = data["shardKey"],
                catalogValue = data["query"],
                products = emptyList()
            )
        }
        return QueryMetadata(null, null, emptyList())
    }

    /** Общее число товаров по ключевому слову. Повтор `searchTotalProducts` из JS. */
    suspend fun searchTotalProducts(keyword: String): Int {
        val res = session.get(
            searchUrl(Urls.Search.EXACTMATCH, Urls.Search.EXACTMATCH_INTERNAL),
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "curr" to Currency.RUB.value,
                "locale" to Locale.RU.value,
                "lang" to Locale.RU.value,
                "dest" to dest,
                "query" to keyword,
                "resultset" to "catalog",
                "limit" to 0
            ),
            headers = mapOf("x-queryid" to QueryIdGenerator.getQueryIdForSearch())
        )
        val data = res.data as? Map<String, Any?> ?: return 0
        return (data["total"] as? Number)?.toInt() ?: 0
    }

    /** Общее число товаров поставщика. Повтор `getSupplierProductCount` из JS. */
    suspend fun getSupplierProductCount(supplierId: Long): Int {
        val res = session.get(
            Urls.Supplier.CATALOG,
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "curr" to Currency.RUB.value,
                "dest" to dest,
                "supplier" to supplierId,
                "limit" to 0
            )
        )
        val data = res.data as? Map<String, Any?> ?: return 0
        return (data["total"] as? Number)?.toInt() ?: 0
    }

    /** Общее число товаров бренда. Повтор `getBrandProductCount` из JS. */
    suspend fun getBrandProductCount(brandId: Long): Int {
        val res = session.get(
            Urls.Brand.CATALOG,
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "curr" to Currency.RUB.value,
                "dest" to dest,
                "brand" to brandId,
                "limit" to 0
            )
        )
        val data = res.data as? Map<String, Any?> ?: return 0
        return (data["total"] as? Number)?.toInt() ?: 0
    }

    /** Сырой ответ страницы каталога бренда. Повтор `getBrandCatalog` из JS. */
    suspend fun getBrandCatalog(brandId: Long, page: Int = 1): Map<String, Any?> {
        val res = session.get(
            Urls.Brand.CATALOG,
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "curr" to Currency.RUB.value,
                "dest" to dest,
                "lang" to Locale.RU.value,
                "page" to page,
                "sort" to "popular",
                "spp" to "30",
                "brand" to brandId
            )
        )
        return (res.data as? Map<String, Any?>) ?: emptyMap()
    }

    /** Товары со страницы каталога бренда. Повтор `getBrandCatalogPage` из JS. */
    suspend fun getBrandCatalogPage(brandId: Long, page: Int = 1, retries: Int = 0): List<Map<String, Any?>> {
        val res = session.get(
            Urls.Brand.CATALOG,
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "curr" to Currency.RUB.value,
                "dest" to dest,
                "lang" to Locale.RU.value,
                "page" to page,
                "sort" to "popular",
                "spp" to "30",
                "brand" to brandId
            ),
            retryOptions = RetryOptions(retries = retries)
        )
        return extractProducts(res.data)
    }

    /**
     * Поиск с произвольными фильтрами. Повтор `searchCustomFilters` из JS.
     * @param filters массив имён фильтров (`"fbrand"`, `"fsupplier"`, …)
     */
    suspend fun searchCustomFilters(keyword: String, filters: List<String>): Map<String, Any?> {
        val res = session.get(
            searchUrl(Urls.Search.EXACTMATCH, Urls.Search.EXACTMATCH_INTERNAL),
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "curr" to Currency.RUB.value,
                "dest" to dest,
                "lang" to Locale.RU.value,
                "query" to keyword,
                "resultset" to "filters",
                "sort" to "popular",
                "filters" to filters.joinToString(";")
            ),
            headers = mapOf("x-queryid" to QueryIdGenerator.getQueryIdForSearch())
        )
        val data = res.data as? Map<String, Any?> ?: return emptyMap()
        return (data["data"] as? Map<String, Any?>) ?: emptyMap()
    }

    /**
     * Товары со страницы поискового каталога. Повтор `getCatalogPage` из JS.
     *
     * @param catalogConfig `{ keyword, catalog_type, catalog_value }`
     * @param page номер страницы
     * @param retries число повторных попыток
     * @param filters массив фильтров
     * @throws IllegalStateException если WB вернул `preset=11111111` (BAD CATALOG VALUE)
     * @throws WbException если ответ содержит `error`/`code` без массива товаров
     */
    suspend fun getCatalogPage(
        catalogConfig: CatalogConfig,
        page: Int = 1,
        retries: Int = 0,
        filters: List<CatalogFilter> = emptyList()
    ): List<Map<String, Any?>> {
        val params = mutableMapOf<String, Any?>(
            "appType" to AppType.DESKTOP.value,
            "curr" to Currency.RUB.value,
            "dest" to dest,
            "query" to catalogConfig.keyword.lowercase(),
            "resultset" to "catalog",
            "sort" to "popular",
            "spp" to "30",
            "suppressSpellcheck" to false
        )
        if (page != 1) params["page"] = page
        for (filter in filters) {
            params[filter.type] = filter.value
        }

        val referrer = "https://www.wildberries.ru/catalog/0/search.aspx?page=2&sort=popular&search=" +
            URLEncoder.encode(catalogConfig.keyword.lowercase(), Charsets.UTF_8)

        val res = session.get(
            searchUrl(Urls.Search.EXACTMATCH, Urls.Search.EXACTMATCH_INTERNAL),
            params = params,
            headers = mapOf(
                "x-queryid" to QueryIdGenerator.getQueryIdForSearch(),
                "Referer" to referrer
            ),
            retryOptions = RetryOptions(retries = retries)
        )

        val data = res.data as? Map<String, Any?> ?: emptyMap()
        val metadata = data["metadata"] as? Map<String, Any?>
        if (metadata?.get("catalog_value") == "preset=11111111") {
            throw IllegalStateException("BAD CATALOG VALUE - 11111111")
        }

        val products = extractProducts(data)
        if (products.isEmpty() && (data["error"] != null || data["code"] != null)) {
            val message = (data["error"] as? String) ?: "unexpected WB catalog response"
            throw WbCatalogException(
                detail = "WB catalog request failed: $message",
                status = res.status,
                data = data
            )
        }
        return products
    }

    /** Подсказки по запросу. Повтор `keyHint` из JS. */
    suspend fun keyHint(query: String): Any? {
        val res = session.get(
            Urls.Search.HINT,
            params = mapOf(
                "query" to query,
                "gender" to Sex.COMMON.value,
                "locale" to Locale.RU.value,
                "lang" to Locale.RU.value,
                "appType" to AppType.DESKTOP.value
            )
        )
        return res.data
    }

    /** Похожие товары по `nm_id`. Повтор `searchSimilarByNm` из JS. */
    suspend fun searchSimilarByNm(productId: Long): Any? {
        val res = session.get(Urls.Search.SIMILAR_BY_NM, params = mapOf("nm" to productId))
        return res.data
    }

    /**
     * Данные о доставке для списка `nm_id`. Повтор `getDeliveryDataByNms` из JS.
     * @param productIds список `nm_id`
     */
    suspend fun getDeliveryDataByNms(productIds: List<Long>, retries: Int = 0): List<Map<String, Any?>> {
        val res = session.get(
            Urls.Product.DELIVERYDATA,
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "locale" to Locale.RU.value,
                "dest" to dest,
                "nm" to productIds.joinToString(";")
            ),
            retryOptions = RetryOptions(retries = retries)
        )
        return extractProducts(res.data)
    }

    /** Активные промо. Повтор `getPromos` из JS. */
    suspend fun getPromos(): Any? {
        val res = session.get(Urls.PROMOS)
        return res.data
    }

    /** Товары по списку `nm_id`. Повтор `getListOfProducts` из JS. */
    suspend fun getListOfProducts(productIds: List<Long>): List<Map<String, Any?>> {
        val res = session.get(
            Urls.Search.LIST,
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "dest" to dest,
                "curr" to Currency.RUB.value,
                "lang" to Locale.RU.value,
                "nm" to productIds.joinToString(";")
            )
        )
        return extractProducts(res.data)
    }

    /** Информация о поставщике. Повтор `getSupplierInfo` из JS. */
    suspend fun getSupplierInfo(sellerId: Long): Map<String, Any?> {
        val res = session.get(formatUrl(Urls.Supplier.INFO, sellerId))
        return (res.data as? Map<String, Any?>) ?: emptyMap()
    }

    /** Информация об отгрузках поставщика. Повтор `getSupplierShipment` из JS. */
    suspend fun getSupplierShipment(sellerId: Long): Map<String, Any?> {
        val res = session.get(
            formatUrl(Urls.Supplier.SHIPMENT, sellerId),
            headers = mapOf("x-client-name" to "site")
        )
        return (res.data as? Map<String, Any?>) ?: emptyMap()
    }

    /** Сырой ответ страницы каталога поставщика. Повтор `getSupplierCatalog` из JS. */
    suspend fun getSupplierCatalog(supplierId: Long, page: Int = 1): Map<String, Any?> {
        val res = session.get(
            Urls.Supplier.CATALOG,
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "curr" to Currency.RUB.value,
                "dest" to dest,
                "lang" to Locale.RU.value,
                "page" to page,
                "sort" to "popular",
                "spp" to "30",
                "supplier" to supplierId
            )
        )
        return (res.data as? Map<String, Any?>) ?: emptyMap()
    }

    /**
     * Весь каталог поставщика с пагинацией. Повтор `getSupplierCatalogAll` из JS.
     * @param supplierId id поставщика
     * @param pageCount число страниц (0 = все)
     * @param retries число повторных попыток
     */
    suspend fun getSupplierCatalogAll(supplierId: Long, pageCount: Int = 0, retries: Int = 0): Catalog {
        val totalProducts = getSupplierProductCount(supplierId)
        if (totalProducts == 0) {
            return Catalog(
                catalogType = "supplier",
                catalogValue = "supplier=$supplierId",
                pages = 0,
                products = emptyList(),
                totalProducts = 0
            )
        }

        var totalPages = getPageCount(totalProducts)
        if (pageCount > 0 && pageCount < totalPages) {
            totalPages = pageCount
        }

        val threads = (1..totalPages).toList()
        val parsedPages = mapConcurrent(threads, Constants.MAX_CONCURRENCY) { page ->
            getSupplierCatalogPage(supplierId, page, retries)
        }

        val products = mutableListOf<Map<String, Any?>>()
        for (page in parsedPages) {
            products.addAll(page)
        }

        return Catalog(
            catalogType = "supplier",
            catalogValue = "supplier=$supplierId",
            pages = totalPages,
            products = products,
            totalProducts = totalProducts
        )
    }

    /** Товары со страницы каталога поставщика. Повтор `getSupplierCatalogPage` из JS. */
    suspend fun getSupplierCatalogPage(supplierId: Long, page: Int = 1, retries: Int = 0): List<Map<String, Any?>> {
        val res = session.get(
            Urls.Supplier.CATALOG,
            params = mapOf(
                "appType" to AppType.DESKTOP.value,
                "curr" to Currency.RUB.value,
                "dest" to dest,
                "lang" to Locale.RU.value,
                "page" to page,
                "sort" to "popular",
                "spp" to "30",
                "supplier" to supplierId
            ),
            retryOptions = RetryOptions(retries = retries)
        )
        return extractProducts(res.data)
    }

    /** Число страниц по общему количеству товаров (cap `PAGES_PER_CATALOG`). Повтор `getPageCount`. */
    fun getPageCount(totalProducts: Int): Int =
        min(ceil(totalProducts.toDouble() / Constants.PRODUCTS_PER_PAGE).toInt(), Constants.PAGES_PER_CATALOG)

    /**
     * Параллельная обработка с ограничением конкуренции. Повтор `mapWithConcurrency` из JS.
     *
     * Сохраняет порядок результатов по индексам входного списка.
     */
    private suspend fun <T, R> mapConcurrent(
        items: List<T>,
        concurrency: Int,
        mapper: suspend (T) -> R
    ): List<R> = coroutineScope {
        val results = ArrayList<R>(items.size)
        val sem = Semaphore(concurrency.coerceAtLeast(1))
        val deferred = items.map { item ->
            async {
                sem.acquire()
                try {
                    mapper(item)
                } finally {
                    sem.release()
                }
            }
        }
        results.addAll(deferred.awaitAll())
        results
    }

    /** Извлечь товары из ответа (`data.products` или `products`). */
    private fun extractProducts(data: Any?): List<Map<String, Any?>> {
        val map = data as? Map<String, Any?> ?: return emptyList()
        val dataField = map["data"] as? Map<String, Any?>
        val fromData = dataField?.get("products") as? List<*>
        val list = fromData ?: (map["products"] as? List<*>) ?: return emptyList()
        return list.mapNotNull { it as? Map<String, Any?> }
    }
}

/** Конфигурация каталога для [WbPrivateApi.getCatalogPage]. */
data class CatalogConfig(
    val keyword: String,
    val catalogType: Any?,
    val catalogValue: Any?
)

/** Фильтр каталога (`type` → имя, `value` → значение). */
data class CatalogFilter(val type: String, val value: Any)

/** Результат [WbPrivateApi.getQueryMetadata]. */
data class QueryMetadata(
    val catalogType: Any?,
    val catalogValue: Any?,
    val products: List<Map<String, Any?>>
)

/** Ошибка ответа каталога (повтор `throw error` с `status`/`response` из JS). */
class WbCatalogException(
    val detail: String,
    val status: Int,
    val data: Map<String, Any?>
) : Exception(detail)
