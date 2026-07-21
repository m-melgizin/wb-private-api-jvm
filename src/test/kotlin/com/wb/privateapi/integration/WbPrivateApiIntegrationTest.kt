package com.wb.privateapi.integration

import com.wb.privateapi.CatalogFilter
import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Destinations
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Перенос `WBPrivateAPI.test.js`.
 *
 * Требует .wbaas_token в корне проекта.
 * Запуск: ./gradlew test --tests "*WbPrivateApiIntegration*"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WbPrivateApiIntegrationTest {

    private val api = WbPrivateApi(destination = Destinations.MOSCOW)

    @BeforeAll
    fun checkToken() {
        Assumptions.assumeTrue(IntegrationHelper.TOKEN_AVAILABLE, "Нет .wbaas_token")
    }

    @Test
    fun `searchTotalProducts returns positive for popular query`() = runBlocking {
        val total = api.searchTotalProducts("Платье")
        println("totalProducts('Платье'): $total")
        Assertions.assertTrue(total > 0)
    }

    @Test
    fun `searchTotalProducts returns positive for rare query`() = runBlocking {
        val total = api.searchTotalProducts("тату чебурашка")
        println("totalProducts('тату чебурашка'): $total")
        Assertions.assertTrue(total > 0)
    }

    @Test
    fun `searchCustomFilters returns brands and suppliers`() = runBlocking {
        val result = api.searchCustomFilters("конструктор детский", listOf("fbrand", "fsupplier"))
        println("searchCustomFilters keys: ${result.keys}")
        println("searchCustomFilters result: ${result.toString().take(300)}")
        // WB может вернуть результаты в разных полях; проверяем что ответ не пуст
        Assertions.assertTrue(result.isNotEmpty(), "searchCustomFilters должен вернуть непустой ответ")
    }

    @Test
    fun `getQueryMetadata returns preset catalog type`() = runBlocking {
        val meta = api.getQueryMetadata("Платье")
        println("metadata: type=${meta.catalogType} value=${meta.catalogValue}")
        Assertions.assertEquals("preset", meta.catalogType)
        Assertions.assertTrue((meta.catalogValue as? String)?.startsWith("preset=") == true)
    }

    @Test
    fun `search three pages returns products`() = runBlocking {
        val catalog = api.search("платье", 3, 3)
        println("search 3 pages: ${catalog.products.size} products (pages=${catalog.pages})")
        // Может быть меньше 300, если товаров меньше; проверяем что есть хоть что-то
        Assertions.assertTrue(catalog.products.size >= 1, "Должен быть хотя бы один товар за 3 страницы")
    }

    @Test
    fun `filter by brand`() = runBlocking {
        val filters = listOf(CatalogFilter("fbrand", 244907))
        val catalog = api.search("Швабра zetter", 1, 0, filters)
        println("brand filter: ${catalog.products.size} products")
        Assertions.assertTrue(catalog.products.isNotEmpty())
    }

    @Test
    fun `filter by supplier`() = runBlocking {
        val filters = listOf(CatalogFilter("fsupplier", 206198))
        val catalog = api.search("Швабра zetter", 1, 0, filters)
        println("supplier filter: ${catalog.products.size} products")
        Assertions.assertTrue(catalog.products.isNotEmpty())
    }

    @Test
    fun `filter by brand and supplier`() = runBlocking {
        val filters = listOf(CatalogFilter("fbrand", 244907), CatalogFilter("fsupplier", 206198))
        val catalog = api.search("Швабра zetter", 1, 0, filters)
        println("brand+supplier filter: ${catalog.products.size} products")
        Assertions.assertTrue(catalog.products.isNotEmpty())
    }

    @Test
    fun `pageCount reduces pages when less available`() = runBlocking {
        val pageCount = 100
        val catalog = api.search("nokia 3310", pageCount)
        println("pageCount=$pageCount catalog.pages=${catalog.pages}")
        Assertions.assertTrue(pageCount > catalog.pages)
    }

    @Test
    fun `keyHint returns suggests`() = runBlocking {
        val hints = api.keyHint("Платье")
        println("keyHint type: ${hints?.let { it::class.simpleName }}")
        Assertions.assertNotNull(hints)
    }

    @Test
    fun `searchSimilarByNm returns results`() = runBlocking {
        val similar = api.searchSimilarByNm(60059650)
        println("searchSimilarByNm: ${similar?.let { it::class.simpleName }}")
        Assertions.assertNotNull(similar)
    }

    @Test
    fun `getListOfProducts returns products`() = runBlocking {
        val ids = (304390393L..304390402L).toList()
        val list = api.getListOfProducts(ids)
        println("getListOfProducts: ${list.size}")
        Assertions.assertTrue(list.isNotEmpty())
    }

    // --- Supplier tests ---

    @Test
    fun `getSupplierInfo returns supplier data`() = runBlocking {
        val info = api.getSupplierInfo(1136572)
        println("getSupplierInfo: id=${info["supplierId"]} name=${info["supplierName"]}")
        Assertions.assertEquals(1136572, (info["supplierId"] as? Number)?.toLong())
    }

    @Test
    fun `getSupplierProductCount returns positive`() = runBlocking {
        val total = api.getSupplierProductCount(18740)
        println("getSupplierProductCount(18740): $total")
        Assertions.assertTrue(total > 0)
    }

    @Test
    fun `getSupplierCatalogAll returns catalog with products`() = runBlocking {
        val catalog = api.getSupplierCatalogAll(18740, 1)
        println("supplierCatalog: total=${catalog.totalProducts} pages=${catalog.pages} products=${catalog.products.size}")
        Assertions.assertTrue(catalog.totalProducts > 0)
        Assertions.assertTrue(catalog.pages > 0)
    }

    @Test
    fun `getSupplierCatalogPage returns products`() = runBlocking {
        val products = api.getSupplierCatalogPage(18740, 1, 0)
        println("supplierCatalogPage: ${products.size} products")
        Assertions.assertTrue(products.isNotEmpty())
        Assertions.assertNotNull(products.first()["id"])
    }

    @Test
    fun `keyHint returns contains suggest`() = runBlocking {
        @Suppress("UNCHECKED_CAST")
        val hints = api.keyHint("Платье") as? Map<String, Any?>
        val suggests = hints?.get("suggests") as? List<*>
        println("suggests: ${suggests?.size}")
        Assertions.assertNotNull(suggests)
        Assertions.assertTrue(suggests!!.isNotEmpty())
    }
}