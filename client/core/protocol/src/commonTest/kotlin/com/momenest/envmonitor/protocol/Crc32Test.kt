package com.momenest.envmonitor.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * CRC32 是手機端與韌體端**各自獨立實作**的同一個演算法，一旦兩邊不一致，
 * 每次 OTA 都會在最後一刻被設備判定為損毀而失敗。這些已知向量是兩邊共同的
 * 驗收標準（韌體端 EnvMonitor/tests/test_crc32.cpp 驗的是同一組數字）。
 */
class Crc32Test {

    @Test
    fun `空資料的 CRC32 是 0`() {
        assertEquals(0L, Crc32.compute(ByteArray(0)))
    }

    @Test
    fun `標準向量 123456789 的 CRC32 是 0xCBF43926`() {
        assertEquals(0xCBF43926L, Crc32.compute("123456789".encodeToByteArray()))
    }

    @Test
    fun `標準向量 a 的 CRC32 是 0xE8B7BE43`() {
        assertEquals(0xE8B7BE43L, Crc32.compute("a".encodeToByteArray()))
    }

    @Test
    fun `標準向量 abc 的 CRC32 是 0x352441C2`() {
        assertEquals(0x352441C2L, Crc32.compute("abc".encodeToByteArray()))
    }

    @Test
    fun `結果一定落在 uint32 範圍內`() {
        // 挑一組會讓最高位為 1 的資料，驗證沒有因為 Int 有號而變成負數
        val crc = Crc32.compute("a".encodeToByteArray())
        assertEquals(true, crc in 0..0xFFFFFFFFL, "CRC 應為無號值，實際 $crc")
    }

    @Test
    fun `指定區間與先切出子陣列再計算的結果相同`() {
        val data = "XX123456789YY".encodeToByteArray()
        assertEquals(0xCBF43926L, Crc32.compute(data, offset = 2, length = 9))
    }

    @Test
    fun `分段串流計算與一次算完整段結果相同`() {
        val data = ByteArray(1000) { (it % 251).toByte() }

        var crc = Crc32.init()
        crc = Crc32.update(crc, data, 0, 300)
        crc = Crc32.update(crc, data, 300, 1)
        crc = Crc32.update(crc, data, 301, 699)

        assertEquals(Crc32.compute(data), Crc32.final(crc))
    }

    @Test
    fun `串流計算沒有餵任何資料時等於空資料的結果`() {
        assertEquals(Crc32.compute(ByteArray(0)), Crc32.final(Crc32.init()))
    }

    @Test
    fun `區間超出範圍會擲例外`() {
        val data = ByteArray(4)
        assertFailsWith<IllegalArgumentException> { Crc32.compute(data, 0, 5) }
        assertFailsWith<IllegalArgumentException> { Crc32.compute(data, 3, 2) }
        assertFailsWith<IllegalArgumentException> { Crc32.compute(data, -1, 2) }
    }
}
