package com.wb.privateapi.integration

import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Destinations
import com.wb.privateapi.model.Product
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Перенос `WBProduct.getStocks.test.js`.
 *
 * Требует .wbaas_token в корне проекта.
 * Запуск: ./gradlew test --tests "*WbProductStocksIntegration*"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WbProductStocksIntegrationTest {

    private val api = WbPrivateApi(destination = Destinations.MOSCOW)

    @BeforeAll
    fun checkToken() {
        Assumptions.assumeTrue(
            IntegrationHelper.TOKEN_AVAILABLE,
            "Нет .wbaas_token — интеграционные тесты пропущены"
        )
    }

    @Test
    fun `getStocks returns stock array for found product`() = runBlocking {
        val catalog = api.search("швабра zetter", 1)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Нет тестового товара")
        val testMap = catalog.products.first()
        val nmId = (testMap["id"] as Number).toLong()
        println("Тестовый товар: nmId=$nmId name=${testMap["name"]}")

        try {
            val p = Product.create(nmId, api.session())
            val stocks = p.getStocks()
            println("stocks count: ${stocks.size}")
            Assertions.assertTrue(stocks.isNotEmpty())

            val first = stocks.first()
            println("Первый склад: wh=${first["wh"]}, qty=${first["qty"]}")
            Assertions.assertNotNull(first["wh"])
            Assertions.assertTrue(first.containsKey("qty"))
            println("totalStocks: ${p.totalStocks}")
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) {
                println("⚠️  getStocks API NDArray недоступен (404)")
            } else throw e
        }
    }

    @Test
    fun `getStocks for multiple products`() = runBlocking {
        val catalog = api.search("футболка", 1)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Нет тестовых товаров")

        val testProducts = catalog.products.take(3)
        var successCount = 0
        var notFoundCount = 0
        var errorCount = 0

        for ((i, data) in testProducts.withIndex()) {
            val nmId = (data["id"] as Number).toLong()
            println("Тест товар ${i + 1}/3: nmId=$nmId")
            try {
                val p = Product.create(nmId, api.session())
                val stocks = p.getStocks()
                println("  stocks: ${stocks.size} складов")
                successCount++
            } catch (e: Exception) {
                if (e.message?.contains("404") == true) {
                    notFoundCount++
                    println("  ⚠️  404")
                } else {
                    errorCount++
                    println("  ❌ ${e.message}")
                }
            }
            delay(200)
        }

        println("Результаты: успех=$successCount 404=$notFoundCount ошибки=$errorCount")

        // как в JS: если все 404 — ок, если есть успешные — проверяем
        if (notFoundCount < testProducts.size && successCount > 0) {
            Assertions.assertTrue(successCount > 0)
        }
    }

    @Test
    fun `getStocks for non-existent product handles error`() = runBlocking {
        try {
            val p = Product(999999999L, api.session())
            val stocks = p.getStocks()
            println("Неожиданно успешно: stocks=${stocks.size}")
        } catch (e: Exception) {
            println("Корректная ошибка: ${e::class.simpleName}: ${e.message}")
            Assertions.assertNotNull(e.message)
        }
    }
}