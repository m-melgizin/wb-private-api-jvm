package com.wb.privateapi.integration

import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Destinations
import com.wb.privateapi.model.Product
import kotlinx.coroutines.runBlocking
import java.io.File

class RealWbTest {
    @org.junit.jupiter.api.Test
    fun `search calculator and show 3 feedbacks`() = runBlocking {
        val log = StringBuilder()
        fun L(msg: String) { log.appendLine(msg); System.err.println(msg) }

        val api = WbPrivateApi(destination = Destinations.MOSCOW)
        L("token loaded: ${api.session().hasToken()}")

        // Тестируем searchTotalProducts и getQueryMetadata отдельно
        // Проверяем поиск через totalProducts (работает)
        val total = api.searchTotalProducts("калькулятор")
        L("totalProducts=$total")

        // Прямой сырой запрос к EXACTMATCH_INTERNAL (как текст)
        L("=== RAW RESPONSE (text) ===")
        try {
            val rawText = api.session().get(
                com.wb.privateapi.constant.Urls.Search.EXACTMATCH_INTERNAL,
                params = mapOf(
                    "appType" to 1,
                    "curr" to "rub",
                    "dest" to -1257786,
                    "query" to "калькулятор",
                    "resultset" to "catalog",
                    "sort" to "popular",
                    "spp" to 30,
                    "suppressSpellcheck" to true,
                    "limit" to 1
                ),
                responseType = com.wb.privateapi.session.ResponseType.TEXT
            )
            L("status: ${rawText.status}")
            val text = rawText.data as? String ?: "null"
            L("response length: ${text.length}")
            L("response preview: ${text.take(500)}")
        } catch (e: Exception) {
            L("RAW ERROR: ${e::class.simpleName}: ${e.message}")
        }

        val catalog = api.search("калькулятор", pageCount = 1)
        L("search: total=${catalog.totalProducts} pages=${catalog.pages} products=${catalog.products.size}")
        org.junit.jupiter.api.Assertions.assertTrue(catalog.products.isNotEmpty())

        val first = catalog.products.first()
        val nmId = (first["id"] as Number).toLong()
        L("nmId=$nmId name=${first["name"]}")

        val product = Product.create(nmId, api.session())
        L("name=${product.name} brand=${product.brand} price=${product.currentPrice} questions=${product.totalQuestions}")

        val feedbacks = product.getFeedbacks()
        L("feedbacks total: ${feedbacks.size}")
        for (fb in feedbacks.take(3)) {
            L("--- [${fb.productValuation}] ${fb.text?.take(200)}")
        }

        File("build/reports/realWbTest.log").writeText(log.toString())
    }
}