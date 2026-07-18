package com.wb.privateapi.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Crc16Test {

    /**
     * Эталонные значения CRC16-ARC посчитаны напрямую из JS-алгоритма
     * (Constants.URLs.utils.test.js, функция `crc16Arc`):
     *   crc16Arc(27334676) = 20412  → 20412 % 100 = 12 < 50  → partition "1"
     *   crc16Arc(12345678) = 34543  → 34543 % 100 = 43 < 50  → partition "1"
     *
     * Комментарии в JS-тесте («27334676 — чётный CRC», «12345678 — нечётный CRC»)
     * не соответствуют фактическим значениям; здесь сверяемся с реальным кодом.
     */
    @Test
    fun `crc16 values match JS algorithm output`() {
        assertEquals(20412, Crc16.crc16Arc(27334676L))
        assertEquals(34543, Crc16.crc16Arc(12345678L))
    }

    @Test
    fun `crc16 partition rule matches JS getFeedbacks`() {
        val cases = listOf(
            27334676L to "1", // crc=20412, %100=12 < 50
            12345678L to "1"  // crc=34543, %100=43 < 50
        )
        cases.forEach { (imtId, expectedPartition) ->
            val crc = Crc16.crc16Arc(imtId)
            val partition = if (crc % 100 >= 50) "2" else "1"
            assertEquals(expectedPartition, partition, "imtId=$imtId crc=$crc")
        }
    }

    /** CRC16 детерминирован для одинакового входа. */
    @Test
    fun `crc16 is deterministic`() {
        assertEquals(Crc16.crc16Arc(12345678L), Crc16.crc16Arc(12345678L))
    }

    /** Для 0 должен вернуть 0 (8 нулевых байт). */
    @Test
    fun `crc16 of zero is zero`() {
        assertEquals(0, Crc16.crc16Arc(0L))
    }

    @Test
    fun `crc16 result is within 16-bit range`() {
        val crc = Crc16.crc16Arc(999999999L)
        assertTrue(crc in 0..0xFFFF, "crc=$crc")
    }
}
