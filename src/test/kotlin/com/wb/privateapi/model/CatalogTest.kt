package com.wb.privateapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogTest {

    private fun product(id: Long): Map<String, Any?> = mapOf("id" to id, "name" to "p$id")

    @Test
    fun `page returns PRODUCTS_PER_PAGE slice`() {
        val products = (1L..250L).map { product(it) }
        val catalog = Catalog(
            catalogType = "search",
            catalogValue = "test",
            pages = 3,
            products = products,
            totalProducts = 250
        )
        assertEquals(100, catalog.page(1).size)
        assertEquals(100, catalog.page(2).size)
        assertEquals(50, catalog.page(3).size)
    }

    @Test
    fun `page returns empty for out-of-range and invalid page numbers`() {
        val catalog = Catalog(
            catalogType = null, catalogValue = null,
            pages = 1,
            products = listOf(product(1L)),
            totalProducts = 1
        )
        assertTrue(catalog.page(0).isEmpty())
        assertTrue(catalog.page(99).isEmpty())
        assertTrue(catalog.page(-1).isEmpty())
    }

    @Test
    fun `getPosition returns 0-based index or -1`() {
        val products = listOf(product(10L), product(20L), product(30L))
        val catalog = Catalog(null, null, 1, products, 3)
        assertEquals(0, catalog.getPosition(10L))
        assertEquals(2, catalog.getPosition(30L))
        assertEquals(-1, catalog.getPosition(999L))
    }

    @Test
    fun `constructor from response map parses fields`() {
        val data = mapOf<String, Any?>(
            "catalog_type" to "brand",
            "catalog_value" to "brand=1",
            "pages" to 5,
            "totalProducts" to 500,
            "products" to listOf(mapOf("id" to 1L), mapOf("id" to 2L))
        )
        val catalog = Catalog(data)
        assertEquals("brand", catalog.catalogType)
        assertEquals(5, catalog.pages)
        assertEquals(500, catalog.totalProducts)
        assertEquals(2, catalog.products.size)
    }

    @Test
    fun `constructor from response map handles missing fields`() {
        val catalog = Catalog(emptyMap())
        assertEquals(0, catalog.pages)
        assertEquals(0, catalog.totalProducts)
        assertTrue(catalog.products.isEmpty())
    }
}
