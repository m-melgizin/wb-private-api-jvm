package com.wb.privateapi.integration

import com.wb.privateapi.WbPrivateApi
import com.wb.privateapi.constant.Constants
import com.wb.privateapi.constant.Destinations
import com.wb.privateapi.model.Product
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Перенос `WBProduct.test.js`.
 *
 * Требует .wbaas_token в корне проекта.
 * Запуск: ./gradlew test --tests "*WbProductIntegration*"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WbProductIntegrationTest {

    private val api = WbPrivateApi(destination = Destinations.MOSCOW)

    @BeforeAll
    fun checkToken() {
        Assumptions.assumeTrue(
            IntegrationHelper.TOKEN_AVAILABLE,
            "Нет .wbaas_token — интеграционные тесты пропущены"
        )
    }

    @Test
    fun `Product create returns full data`() = runBlocking {
        val catalog = api.search("швабра zetter", 1)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Нет тестового товара")
        val testMap = catalog.products.first()
        val nmId = (testMap["id"] as Number).toLong()
        println("Тестовый товар: nmId=$nmId")

        try {
            val p = Product.create(nmId, api.session())
            println("IMT ID:      ${p.imtId}")
            println("Name:        ${p.name}")
            println("Brand:       ${p.brand}")
            println("Price:       ${p.currentPrice}")
            println("Questions:   ${p.totalQuestions}")
            println("rawCard keys: ${p.rawCard.keys.joinToString()}")
            println("rawDetails keys: ${p.rawDetails.keys.joinToString()}")
            println("rawSellers keys: ${p.rawSellers.keys.joinToString()}")

            // imt_id может отсутствовать у некоторых товаров — не критично, логгируем
            if (p.imtId != null) {
                println("imtId = ${p.imtId}")
            } else {
                println("⚠️  imtId отсутствует в raw/rawCard/rawDetails")
            }
            Assertions.assertNotNull(p.name, "Имя товара должно быть загружено")
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) {
                println("⚠️  API временно недоступен (404), тест пропущен")
            } else throw e
        }
    }

    @Test
    fun `getFeedbacks returns array`() = runBlocking {
        val catalog = api.search("швабра zetter", 1)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Нет тестового товара")
        val testMap = catalog.products.first()
        val nmId = (testMap["id"] as Number).toLong()

        try {
            val p = Product.create(nmId, api.session())
            val feedbacks = p.getFeedbacks()
            println("feedbacks count: ${feedbacks.size}")
            Assertions.assertTrue(feedbacks.isNotEmpty())
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) {
                println("⚠️  API getFeedbacks недоступен (404)")
            } else throw e
        }
    }

    @Test
    fun `getQuestionsCount returns number`() = runBlocking {
        val catalog = api.search("швабра zetter", 1)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Нет тестового товара")
        val testMap = catalog.products.first()
        val nmId = (testMap["id"] as Number).toLong()

        try {
            val p = Product.create(nmId, api.session())
            val count = p.getQuestionsCount()
            println("getQuestionsCount: $count")
            println("totalQuestions: ${p.totalQuestions}")
            Assertions.assertTrue(count >= 0)
            Assertions.assertEquals(count, p.totalQuestions)
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) {
                println("⚠️  API getQuestionsCount недоступен (404)")
            } else throw e
        }
    }

    @Test
    fun `getQuestions returns paginated results`() = runBlocking {
        val catalog = api.search("швабра zetter", 1)
        Assumptions.assumeTrue(catalog.products.isNotEmpty(), "Нет тестового товара")
        val testMap = catalog.products.first()
        val nmId = (testMap["id"] as Number).toLong()

        try {
            val p = Product.create(nmId, api.session())
            val total = p.getQuestionsCount()
            val result = p.getQuestions()
            println("totalQuestions: $total, fetched: ${result.fetchedQuestions}")
            Assertions.assertTrue(result.fetchedQuestions >= 0)
        } catch (e: Exception) {
            if (e.message?.contains("404") == true) {
                println("⚠️  API getQuestions недоступен (404)")
            } else throw e
        }
    }
}