package com.wb.privateapi.integration

import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Constants
import com.wb.privateapi.constant.Destinations
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Перенос `WBCatalog.test.js`.
 *
 * Требует .wbaas_token в корне проекта.
 * Запуск: ./gradlew test --tests "*WbCatalogIntegration*"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WbCatalogIntegrationTest {

    private val api = WbPrivateApi(destination = Destinations.MOSCOW)

    @BeforeAll
    fun checkToken() {
        Assumptions.assumeTrue(IntegrationHelper.TOKEN_AVAILABLE, "Нет .wbaas_token")
    }

    @Test
    fun `page returns correct slices`() = runBlocking {
        val catalog = api.search("Очки женские", 2)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Поиск не дал товаров")
        println("products: ${catalog.products.size}, total: ${catalog.totalProducts}")

        if (catalog.products.size < Constants.PRODUCTS_PER_PAGE * 2) {
            println("⚠️  Загружено меньше 2 страниц — проверка ослаблена")
        }
        // page(1) — первые 100 товаров
        Assertions.assertTrue(catalog.page(1).isNotEmpty())
        // page(3) для 2 страниц — пусто
        if (catalog.pages >= 2) {
            Assertions.assertEquals(Constants.PRODUCTS_PER_PAGE, catalog.page(1).size)
            Assertions.assertEquals(Constants.PRODUCTS_PER_PAGE, catalog.page(2).size)
        }
        Assertions.assertTrue(catalog.page(3).isEmpty())
        // элементы не undefined
        if (catalog.page(2).isNotEmpty()) {
            Assertions.assertNotNull(catalog.page(2)[0])
        }
    }

    @Test
    fun `getPosition finds correct index`() = runBlocking {
        val catalog = api.search("Менструальные чаши", 2)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Поиск не дал товаров")

        if (catalog.products.size > 130) {
            val sku = (catalog.products[130]["id"] as Number).toLong()
            val position = catalog.getPosition(sku)
            Assertions.assertEquals(130, position)
        }

        val firstSku = (catalog.products[0]["id"] as Number).toLong()
        val firstPos = catalog.getPosition(firstSku)
        Assertions.assertEquals(0, firstPos)
    }

    @Test
    fun `getPosition returns -1 for non-existent product`() = runBlocking {
        val catalog = api.search("Менструальные чаши", 3)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Поиск не дал товаров")

        val position = catalog.getPosition(0L)
        Assertions.assertEquals(-1, position)
    }
}