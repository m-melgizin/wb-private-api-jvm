package com.wb.privateapi.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BasketCalculatorTest {

    /**
     * Эталон: tests/Utils.test.js → getBasketNumber()
     * sku → expected basket
     * 14381552→01, 14411552→02, 28910126→03, 71840112→04, 72232256→05,
     * 101032256→06, 106332256→07, 111632256→08, 117032256→09, 131499998→10, 165879870→12
     */
    @Test
    fun `basket numbers match JS reference values`() {
        val skus = listOf(
            14381552L, 14411552L, 28910126L, 71840112L, 72232256L, 101032256L,
            106332256L, 111632256L, 117032256L, 131499998L, 165879870L
        )
        val expected = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "12")
        skus.forEachIndexed { i, sku ->
            assertEquals(expected[i], BasketCalculator.getBasketNumber(sku), "sku=$sku")
        }
    }

    @Test
    fun `basket volume and part for 177899980`() {
        // Эталон из теста Card.imageURL: vol1778/part177899
        assertEquals(1778L, BasketCalculator.getBasketVolume(177899980L))
        assertEquals(177899L, BasketCalculator.getBasketPart(177899980L))
    }

    @Test
    fun `basket number is always two-digit zero-padded`() {
        assertEquals(2, BasketCalculator.getBasketNumber(1L).length)
        assertTrue(BasketCalculator.getBasketNumber(1L).matches(Regex("\\d{2}")))
    }

    @Test
    fun `video basket and vol for known nm`() {
        // vol = nm % 144, part = nm / 10000
        val nm = 177899980L
        assertEquals(nm % 144, BasketCalculator.getVideoVol(nm))
        assertEquals(nm / 10000, BasketCalculator.getVideoPart(nm))
        val videoVol = BasketCalculator.getVideoVol(nm)
        val videoBasket = BasketCalculator.getVideoBasket(videoVol)
        assertTrue(videoBasket.matches(Regex("\\d{2}")), "videoBasket=$videoBasket")
    }
}
