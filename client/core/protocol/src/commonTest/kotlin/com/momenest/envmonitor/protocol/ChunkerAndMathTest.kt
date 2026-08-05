package com.momenest.envmonitor.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OtaProgressTest {

    @Test
    fun `total 為 0 或負數回 0 而不是除以零`() {
        assertEquals(0, otaPercent(0, 0))
        assertEquals(0, otaPercent(100, 0))
        assertEquals(0, otaPercent(100, -1))
    }

    @Test
    fun `尚未傳送時是 0`() {
        assertEquals(0, otaPercent(0, 1000))
    }

    @Test
    fun `傳送過半時是 50`() {
        assertEquals(50, otaPercent(500, 1000))
    }

    @Test
    fun `傳完時是 100`() {
        assertEquals(100, otaPercent(1000, 1000))
    }

    @Test
    fun `超過總量仍是 100 不會超出`() {
        assertEquals(100, otaPercent(1500, 1000))
    }

    @Test
    fun `無條件捨去與韌體 otaPercent 一致`() {
        // 韌體是整數除法（received * 100 / total），這裡必須同樣捨去而不是四捨五入，
        // 否則手機與設備螢幕會顯示差 1% 的數字
        assertEquals(33, otaPercent(1, 3))
        assertEquals(66, otaPercent(2, 3))
        assertEquals(99, otaPercent(999, 1000))
    }

    @Test
    fun `大檔案不會因為先乘 100 而溢位`() {
        // 8 MB 韌體：8388608 * 100 遠小於 Long 上限
        assertEquals(50, otaPercent(4_194_304L, 8_388_608L))
    }
}

class FirmwareChunkerTest {

    @Test
    fun `整除時每塊都是滿的`() {
        val data = ByteArray(10) { it.toByte() }
        val chunks = FirmwareChunker.chunks(data, 5).toList()
        assertEquals(2, chunks.size)
        assertContentEquals(byteArrayOf(0, 1, 2, 3, 4), chunks[0])
        assertContentEquals(byteArrayOf(5, 6, 7, 8, 9), chunks[1])
    }

    @Test
    fun `有餘數時最後一塊較短`() {
        val data = ByteArray(7) { it.toByte() }
        val chunks = FirmwareChunker.chunks(data, 3).toList()
        assertEquals(3, chunks.size)
        assertEquals(1, chunks.last().size)
        assertContentEquals(byteArrayOf(6), chunks.last())
    }

    @Test
    fun `空資料產生空序列`() {
        assertEquals(0, FirmwareChunker.chunks(ByteArray(0), 512).toList().size)
    }

    @Test
    fun `分塊大小為 1 時每個位元組一塊`() {
        val chunks = FirmwareChunker.chunks(ByteArray(4) { it.toByte() }, 1).toList()
        assertEquals(4, chunks.size)
        assertTrue(chunks.all { it.size == 1 })
    }

    @Test
    fun `分塊大小大於資料長度時只有一塊`() {
        val chunks = FirmwareChunker.chunks(ByteArray(10), 512).toList()
        assertEquals(1, chunks.size)
        assertEquals(10, chunks[0].size)
    }

    @Test
    fun `串接所有分塊會還原成原始資料`() {
        // 這是 OTA 正確性的根本：設備收到的位元組必須與原檔一模一樣
        val data = ByteArray(1000) { (it % 256).toByte() }
        val joined = FirmwareChunker.chunks(data, 137).toList()
            .fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        assertContentEquals(data, joined)
    }

    @Test
    fun `分塊大小不合法會擲例外`() {
        // 0 會讓傳輸迴圈永遠前進不了
        assertFailsWith<IllegalArgumentException> { FirmwareChunker.chunks(ByteArray(4), 0).toList() }
        assertFailsWith<IllegalArgumentException> { FirmwareChunker.chunks(ByteArray(4), -1).toList() }
    }

    @Test
    fun `chunkCount 與實際切出來的塊數一致`() {
        listOf(0L to 512, 1L to 512, 512L to 512, 513L to 512, 1000L to 137).forEach { (size, chunk) ->
            val actual = FirmwareChunker.chunks(ByteArray(size.toInt()), chunk).toList().size.toLong()
            assertEquals(actual, FirmwareChunker.chunkCount(size, chunk), "size=$size chunk=$chunk")
        }
    }

    @Test
    fun `chunkCount 對不合法輸入擲例外`() {
        assertFailsWith<IllegalArgumentException> { FirmwareChunker.chunkCount(100, 0) }
        assertFailsWith<IllegalArgumentException> { FirmwareChunker.chunkCount(-1, 512) }
    }
}

class CalibrationMathTest {

    @Test
    fun `線性換算`() {
        assertEquals(0, CalibrationMath.adcToPercent(1000, 1000, 3000))
        assertEquals(50, CalibrationMath.adcToPercent(2000, 1000, 3000))
        assertEquals(100, CalibrationMath.adcToPercent(3000, 1000, 3000))
    }

    @Test
    fun `超出範圍的值會被夾在 0 到 100`() {
        assertEquals(0, CalibrationMath.adcToPercent(500, 1000, 3000))
        assertEquals(100, CalibrationMath.adcToPercent(9999, 1000, 3000))
    }

    @Test
    fun `反向刻度一樣線性換算`() {
        // 電容式與電阻式土壤探針的 ADC 方向相反，兩種都要支援
        assertEquals(100, CalibrationMath.adcToPercent(1000, 3000, 1000))
        assertEquals(50, CalibrationMath.adcToPercent(2000, 3000, 1000))
        assertEquals(0, CalibrationMath.adcToPercent(3000, 3000, 1000))
    }

    @Test
    fun `未校準時回 null 讓 UI 顯示原始 ADC`() {
        assertNull(CalibrationMath.adcToPercent(2000, null, 3000))
        assertNull(CalibrationMath.adcToPercent(2000, 1000, null))
        assertNull(CalibrationMath.adcToPercent(2000, null, null))
    }

    @Test
    fun `min 等於 max 時回 null 而不是除以零`() {
        assertNull(CalibrationMath.adcToPercent(2000, 1500, 1500))
    }

    @Test
    fun `hasSoil 與 hasWater 反映校準是否可用`() {
        assertTrue(Calibration(soilMin = 1000, soilMax = 3000).hasSoil)
        assertTrue(!Calibration(soilMin = 1000).hasSoil)
        assertTrue(!Calibration(soilMin = 1500, soilMax = 1500).hasSoil)
        assertTrue(Calibration(waterMin = 0, waterMax = 4095).hasWater)
        assertTrue(!Calibration().hasWater)
    }

    @Test
    fun `soilPercent 與 waterPercent 各自取用對應的校準值`() {
        val cal = Calibration(soilMin = 1000, soilMax = 3000, waterMin = 0, waterMax = 4000)
        assertEquals(50, CalibrationMath.soilPercent(2000, cal))
        assertEquals(25, CalibrationMath.waterPercent(1000, cal))
    }
}
