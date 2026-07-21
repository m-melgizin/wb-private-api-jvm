package com.wb.privateapi.integration

import com.wb.privateapi.constant.AppType
import com.wb.privateapi.constant.Constants
import com.wb.privateapi.constant.Currency
import com.wb.privateapi.constant.Destinations
import com.wb.privateapi.constant.Locale
import com.wb.privateapi.constant.Urls
import com.wb.privateapi.util.ImageUrlBuilder
import com.wb.privateapi.util.ImageUrlBuilder.ImageType
import com.wb.privateapi.util.BasketCalculator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Перенос `Constants.URLs.integration.test.js`.
 *
 * Проверки соответствия URL-констант, версий API, доменов и констант.
 * Чистые тесты — не требуют токена.
 */
class ConstantsUrlsIntegrationTest {

    // --- Версии API в URL ---
    @Test
    fun `api versions are up to date`() {
        val cases = mapOf(
            Urls.Search.EXACTMATCH to "v18",
            Urls.Supplier.FILTERS to "v8",
            Urls.Supplier.CATALOG to "v4",
            Urls.Product.DETAILS to "v4",
            Urls.Product.FEEDBACKS to "v2",
            Urls.Search.HINT to "v7"
        )
        cases.forEach { (url, version) ->
            Assertions.assertTrue(url.contains(version), "$url не содержит $version")
        }
    }

    // --- Домены ---
    // * wildberries.ru
    // * search.wb.ru
    // * catalog.wb.ru
    // * card.wb.ru
    // * wb.ru
    // * wbbasket.ru
    // * wbstatic.net
    // * static-basket-01.wbbasket.ru

    @Test
    fun `domain wildberries ru`() {
        listOf(
            Urls.PROMOS,
            Urls.Product.EXTRADATA,
            Urls.Product.QUESTIONS,
            Urls.Supplier.CATALOG_INTERNAL,
            Urls.Supplier.FILTERS_INTERNAL,
            Urls.Supplier.SHIPMENT,
            Urls.Search.EXACTMATCH_INTERNAL,
            Urls.Search.SIMILAR_BY_NM
        ).forEach { Assertions.assertTrue(it.contains("wildberries.ru"), "$it без wildberries.ru") }
    }

    @Test
    fun `domain search wb ru`() {
        Assertions.assertTrue(Urls.Search.EXACTMATCH.contains("search.wb.ru"))
    }

    @Test
    fun `domain catalog wb ru`() {
        listOf(Urls.Supplier.CATALOG, Urls.Supplier.FILTERS, Urls.Brand.CATALOG)
            .forEach { Assertions.assertTrue(it.contains("catalog.wb.ru"), "$it без catalog.wb.ru") }
    }

    @Test
    fun `domain card wb ru`() {
        listOf(Urls.Product.DETAILS, Urls.Product.DELIVERYDATA, Urls.Search.LIST)
            .forEach { Assertions.assertTrue(it.contains("card.wb.ru"), "$it без card.wb.ru") }
    }

    @Test
    fun `domain wb ru`() {
        listOf(Urls.Product.FEEDBACKS, Urls.Search.HINT)
            .forEach { Assertions.assertTrue(it.contains("wb.ru"), "$it без wb.ru") }
    }

    @Test
    fun `domain wbbasket ru`() {
        listOf(
            Urls.Brand.IMAGE, Urls.Product.CARD, Urls.Product.SELLERS,
            Urls.Images.TINY, Urls.Images.BIG, Urls.Images.SMALL, Urls.Images.MEDIUM
        ).forEach { Assertions.assertTrue(it.contains("wbbasket.ru"), "$it без wbbasket.ru") }
    }

    @Test
    fun `domain wbstatic net`() {
        listOf(Urls.Images.FEEDBACK_BASE)
            .forEach { Assertions.assertTrue(it.contains("wbstatic.net"), "$it без wbstatic.net") }
    }

    @Test
    fun `domain static basket`() {
        listOf(Urls.Supplier.INFO)
            .forEach { Assertions.assertTrue(it.contains("static-basket-01.wbbasket.ru"), "$it без static-basket") }
    }

    // --- Значения констант ---
    @Test
    fun `constants have expected values`() {
        Assertions.assertEquals(100, Constants.PRODUCTS_PER_PAGE)
        Assertions.assertEquals(100, Constants.PAGES_PER_CATALOG)
        Assertions.assertEquals(20, Constants.FEEDBACKS_PER_PAGE)
        Assertions.assertEquals(30, Constants.QUESTIONS_PER_PAGE)

        Assertions.assertEquals(1, AppType.DESKTOP.value)
        Assertions.assertEquals(32, AppType.ANDROID.value)
        Assertions.assertEquals(64, AppType.IOS.value)

        Assertions.assertEquals("rub", Currency.RUB.value)
        Assertions.assertEquals("ru", Locale.RU.value)
    }

    @Test
    fun `destination Moscow has ids array`() {
        val dest = Destinations.MOSCOW
        Assertions.assertTrue(dest.ids.isNotEmpty())
        Assertions.assertTrue(dest.ids[0] < 0) // отрицательный id в WB
    }

    // --- Соответствие URL и методов ---
    @Test
    fun `search URL contains exactmatch`() {
        Assertions.assertTrue(Urls.Search.EXACTMATCH.contains("exactmatch"))
    }

    @Test
    fun `supplier URL patterns`() {
        Assertions.assertTrue(Urls.Supplier.INFO.contains("supplier-by-id"))
        Assertions.assertTrue(Urls.Supplier.FILTERS.contains("sellers/v8/filters"))
        Assertions.assertTrue(Urls.Supplier.CATALOG.contains("sellers/v4/catalog"))
    }

    @Test
    fun `product card URL pattern`() {
        Assertions.assertTrue(Urls.Product.CARD.contains("basket-{0}.wbbasket.ru"))
        Assertions.assertTrue(Urls.Product.SELLERS.contains("basket-{0}.wbbasket.ru"))
        Assertions.assertTrue(Urls.Product.DETAILS.contains("card.wb.ru/cards/v4/detail"))
    }

    @Test
    fun `image URL patterns`() {
        Assertions.assertTrue(Urls.Images.BIG.contains("basket-{0}.wbbasket.ru"))
        Assertions.assertTrue(Urls.Images.BIG.contains("images/big/{4}.webp"))
    }

    @Test
    fun `feedbacks URL pattern`() {
        Assertions.assertTrue(Urls.Product.FEEDBACKS.contains("feedbacks{0}.wb.ru"))
    }

    @Test
    fun `questions URL pattern`() {
        Assertions.assertTrue(Urls.Product.QUESTIONS.contains("questions.wildberries.ru"))
    }

    @Test
    fun `brand image URL pattern`() {
        val url = ImageUrlBuilder.brandImageUrl(87238)
        Assertions.assertTrue(url.contains("basket"), "brandImageUrl: $url")
        Assertions.assertTrue(url.contains("87238"), "brandImageUrl: $url")
    }

    @Test
    fun `image URL generated from built-in constants`() {
        val url = ImageUrlBuilder.imageUrl(177899980L, ImageType.BIG, 3)
        Assertions.assertTrue(url.contains("basket-"))
        Assertions.assertTrue(url.contains("wbbasket.ru"))
        println("imageURL: $url")
    }
}