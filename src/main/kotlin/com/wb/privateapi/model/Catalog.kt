@file:Suppress("UNCHECKED_CAST")

package com.wb.privateapi.model

import com.wb.privateapi.constant.Constants

/**
 * Обёртка над результатом поиска/каталога. Перенос `WBCatalog` из `WBCatalog.js`.
 *
 * Хранит тип/значение каталога, число страниц, плоский массив товаров
 * (сырые объекты из API WB) и общее количество товаров.
 *
 * [page] и [getPosition] повторяют поведение JS версии 1-в-1.
 */
class Catalog(
    val catalogType: Any?,
    val catalogValue: Any?,
    val pages: Int,
    val products: List<Map<String, Any?>>,
    val totalProducts: Int
) {
    /** Конструктор из мапы ответа API (ключи как в JS: `catalog_type`, `catalog_value`, …). */
    constructor(data: Map<String, Any?>) : this(
        catalogType = data["catalog_type"],
        catalogValue = data["catalog_value"],
        pages = (data["pages"] as? Number)?.toInt() ?: 0,
        products = (data["products"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList(),
        totalProducts = (data["totalProducts"] as? Number)?.toInt() ?: 0
    )

    /**
     * Срез товаров страницы.
     *
     * @param number номер страницы, 1-based
     * @return товары страницы или пустой список при некорректном номере / выходе за границы
     */
    fun page(number: Int): List<Map<String, Any?>> {
        if (number < 1) return emptyList()
        val startIndex = (number - 1) * Constants.PRODUCTS_PER_PAGE
        if (startIndex >= products.size) return emptyList()
        return products.subList(
            startIndex,
            minOf(startIndex + Constants.PRODUCTS_PER_PAGE, products.size)
        )
    }

    /**
     * Позиция товара (`nm_id`) в массиве [products], 0-based; `-1`, если не найден.
     */
    fun getPosition(productId: Long): Int = products.indexOfFirst { item ->
        (item["id"] as? Number)?.toLong() == productId
    }

    override fun toString(): String =
        "Catalog(type=$catalogType, pages=$pages, products=${products.size}, total=$totalProducts)"
}
