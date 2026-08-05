package com.momenest.envmonitor.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OtaReportDecoderTest {

    @Test
    fun `解出 BEGIN_OK`() {
        assertEquals(OtaDeviceReport.BeginOk, OtaReportDecoder.decode(byteArrayOf(0x01, 0x00)))
    }

    @Test
    fun `解出 END_OK`() {
        assertEquals(OtaDeviceReport.EndOk, OtaReportDecoder.decode(byteArrayOf(0x02, 0x00)))
    }

    @Test
    fun `解出 ABORTED`() {
        assertEquals(OtaDeviceReport.Aborted, OtaReportDecoder.decode(byteArrayOf(0x03, 0x00)))
    }

    @Test
    fun `解出進度並帶百分比`() {
        assertEquals(
            OtaDeviceReport.Progress(87),
            OtaReportDecoder.decode(byteArrayOf(0x10, 87)),
        )
    }

    @Test
    fun `進度 100 不會因為位元組有號而變負數`() {
        assertEquals(
            OtaDeviceReport.Progress(100),
            OtaReportDecoder.decode(byteArrayOf(0x10, 100)),
        )
    }

    @Test
    fun `解出錯誤並帶錯誤碼`() {
        assertEquals(
            OtaDeviceReport.DeviceError(6),
            OtaReportDecoder.decode(byteArrayOf(0xEE.toByte(), 6)),
        )
    }

    @Test
    fun `錯誤碼大於 127 不會變成負數`() {
        // 0xEE 與 detail 都是無號位元組，直接 toInt() 會得到負數，必須 and 0xFF
        assertEquals(
            OtaDeviceReport.DeviceError(200),
            OtaReportDecoder.decode(byteArrayOf(0xEE.toByte(), 200.toByte())),
        )
    }

    @Test
    fun `只有 1 個位元組時 detail 視為 0`() {
        assertEquals(OtaDeviceReport.BeginOk, OtaReportDecoder.decode(byteArrayOf(0x01)))
        assertEquals(OtaDeviceReport.Progress(0), OtaReportDecoder.decode(byteArrayOf(0x10)))
    }

    @Test
    fun `空陣列回傳 null`() {
        assertNull(OtaReportDecoder.decode(ByteArray(0)))
    }

    @Test
    fun `未知狀態碼回傳 Unrecognized 而不是崩潰`() {
        // 韌體版本比 App 新時會發生，必須容忍
        assertEquals(
            OtaDeviceReport.Unrecognized(0x7F),
            OtaReportDecoder.decode(byteArrayOf(0x7F, 0x00)),
        )
    }

    @Test
    fun `多餘的位元組會被忽略`() {
        assertEquals(
            OtaDeviceReport.Progress(50),
            OtaReportDecoder.decode(byteArrayOf(0x10, 50, 99, 99)),
        )
    }

    @Test
    fun `每個韌體錯誤碼都有繁中說明`() {
        (1..6).forEach { code ->
            val text = OtaReportDecoder.describeErrorCode(code)
            assertTrue(text.isNotBlank(), "錯誤碼 $code 沒有說明")
            assertTrue(!text.startsWith("未知錯誤"), "錯誤碼 $code 應該要有專屬說明")
        }
    }

    @Test
    fun `沒定義的錯誤碼給通用說明並附上原始碼`() {
        assertTrue(OtaReportDecoder.describeErrorCode(99).contains("99"))
    }
}
