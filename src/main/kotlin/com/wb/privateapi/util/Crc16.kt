package com.wb.privateapi.util

/**
 * CRC16-ARC для выбора партиции feedbacks.
 *
 * Перенос `numToUint8Array` + `crc16Arc` из `WBProduct.js`.
 *
 * Алгоритм JS (`numToUint8Array`) разбивает число на 8 байт little-endian,
 * используя `r % 256` / `Math.floor(r / 256)` — это работает с числами
 * до 2^53 (Number). Для `nm_id` товара (обычно < 2^31) этого достаточно.
 *
 * Здесь `value` интерпретируется как `Long` и раскладывается на 8 байт
 * little-endian через маски — результат идентичен JS для всех `nm_id`.
 *
 * Сам CRC16: полином `0xA001` (reflected), начальное значение `0x0000`,
 * XOR-out `0x0000` — это CRC-16/ARC. Побитовая реализация повторяет JS:
 * для каждого байта: `crc ^= byte`, затем 8 сдвигов с xor полинома
 * при младшем бите = 1.
 */
object Crc16 {

    /** Полином CRC-16/ARC (reflected 0x8005). */
    private const val POLY = 0xA001

    /** CRC16-ARC от `value`, интерпретируемого как 8 байт little-endian. */
    fun crc16Arc(value: Long): Int {
        val bytes = toUint8ArrayLittleEndian(value, length = 8)
        var crc = 0
        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor POLY
                } else {
                    crc ushr 1
                }
            }
        }
        return crc
    }

    /**
     * Разложить `value` на `length` байт little-endian.
     * Аналог JS `numToUint8Array` для длин, помещающихся в `Long`.
     */
    private fun toUint8ArrayLittleEndian(value: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        var r = value
        for (i in 0 until length) {
            out[i] = (r and 0xFF).toByte()
            r = r ushr 8
        }
        return out
    }
}
