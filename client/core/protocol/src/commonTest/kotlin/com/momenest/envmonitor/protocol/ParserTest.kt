package com.momenest.envmonitor.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 解析器要能吞下比自己新或舊的韌體送來的 JSON——欄位缺漏只讓該欄位變 null，
 * 不能整包失敗導致畫面空白。這些測試把「容忍度」明確定義下來。
 */
class ReadingParserTest {

    @Test
    fun `解析韌體的典型輸出`() {
        val json = """{"air_temp":24.58,"air_hum":58.07,"water_temp":31.00,"soil":2100,"water_level":1500}"""
        assertEquals(
            SensorReading(24.58f, 58.07f, 31.00f, 2100, 1500),
            ReadingParser.parse(json),
        )
    }

    @Test
    fun `讀取失敗的欄位是 JSON null 對應到 Kotlin null`() {
        // 韌體對讀不到的感測器送 null（reading_format.cpp），不是送 0
        val json = """{"air_temp":null,"air_hum":null,"water_temp":null,"soil":null,"water_level":null}"""
        assertEquals(SensorReading(), ReadingParser.parse(json))
    }

    @Test
    fun `部分欄位失敗時其餘欄位仍正常`() {
        val json = """{"air_temp":24.5,"air_hum":null,"water_temp":null,"soil":2100,"water_level":null}"""
        assertEquals(
            SensorReading(airTemp = 24.5f, soilRaw = 2100),
            ReadingParser.parse(json),
        )
    }

    @Test
    fun `欄位完全缺漏時視為 null 而不是解析失敗`() {
        assertEquals(SensorReading(airTemp = 24.5f), ReadingParser.parse("""{"air_temp":24.5}"""))
    }

    @Test
    fun `多出來的未知欄位會被忽略`() {
        // 韌體版本比 App 新時會發生
        val json = """{"air_temp":24.5,"future_sensor":123,"nested":{"a":1}}"""
        assertEquals(SensorReading(airTemp = 24.5f), ReadingParser.parse(json))
    }

    @Test
    fun `負溫度可以正確解析`() {
        assertEquals(-12.5f, ReadingParser.parse("""{"air_temp":-12.5}""")?.airTemp)
    }

    @Test
    fun `整數寫法的溫度也能解析成浮點`() {
        assertEquals(31f, ReadingParser.parse("""{"water_temp":31}""")?.waterTemp)
    }

    @Test
    fun `字串型別的數值視為無效`() {
        // 韌體不會這樣送；真的收到代表資料有問題，寧可顯示「--」也不要顯示錯的值
        assertNull(ReadingParser.parse("""{"air_temp":"24.5"}""")?.airTemp)
    }

    @Test
    fun `不是 JSON 物件時整份回傳 null`() {
        assertNull(ReadingParser.parse(""))
        assertNull(ReadingParser.parse("not json"))
        assertNull(ReadingParser.parse("[1,2,3]"))
        assertNull(ReadingParser.parse("""{"air_temp":"""))
    }

    @Test
    fun `位元組版本與字串版本結果相同`() {
        val json = """{"air_temp":24.58,"soil":2100}"""
        assertEquals(ReadingParser.parse(json), ReadingParser.parse(json.encodeToByteArray()))
    }
}

class StatusParserTest {

    @Test
    fun `解析 WiFi 正常且上傳成功的狀態`() {
        assertEquals(
            DeviceStatus(wifiConnected = true, uploadState = UploadState.SUCCESS, ipAddress = "192.168.31.158"),
            StatusParser.parse("""{"wifi":1,"upload":1,"ip":"192.168.31.158"}"""),
        )
    }

    @Test
    fun `WiFi 斷線時 IP 是空字串`() {
        assertEquals(
            DeviceStatus(wifiConnected = false, uploadState = UploadState.IDLE, ipAddress = ""),
            StatusParser.parse("""{"wifi":0,"upload":0,"ip":""}"""),
        )
    }

    @Test
    fun `上傳狀態碼對應韌體定義`() {
        assertEquals(UploadState.IDLE, StatusParser.parse("""{"upload":0}""")?.uploadState)
        assertEquals(UploadState.SUCCESS, StatusParser.parse("""{"upload":1}""")?.uploadState)
        assertEquals(UploadState.FAILED, StatusParser.parse("""{"upload":2}""")?.uploadState)
    }

    @Test
    fun `未知的上傳狀態碼變成 UNKNOWN 而不是崩潰`() {
        assertEquals(UploadState.UNKNOWN, StatusParser.parse("""{"upload":99}""")?.uploadState)
    }

    @Test
    fun `欄位缺漏時保守地當成未連線`() {
        val status = assertNotNull(StatusParser.parse("{}"))
        assertEquals(false, status.wifiConnected)
        assertEquals(UploadState.UNKNOWN, status.uploadState)
        assertEquals("", status.ipAddress)
    }

    @Test
    fun `壞掉的 JSON 回傳 null`() {
        assertNull(StatusParser.parse("nope"))
        assertNull(StatusParser.parse(""))
    }
}

class DeviceInfoParserTest {

    @Test
    fun `解析韌體 1_1_0 的完整輸出`() {
        val json = """{"fw":"1.1.0","built":"Aug  4 2026","chip":"esp32","heap":123456}"""
        assertEquals(
            DeviceInfo("1.1.0", "Aug  4 2026", "esp32", 123456L),
            DeviceInfoParser.parse(json),
        )
    }

    @Test
    fun `缺少 heap 時其餘欄位仍可用`() {
        assertEquals(
            DeviceInfo("1.1.0", "", "", null),
            DeviceInfoParser.parse("""{"fw":"1.1.0"}"""),
        )
    }

    @Test
    fun `壞掉的 JSON 回傳 null`() {
        assertNull(DeviceInfoParser.parse(""))
        assertNull(DeviceInfoParser.parse("garbage"))
    }

    @Test
    fun `位元組版本與字串版本結果相同`() {
        val json = """{"fw":"1.1.0","chip":"esp32"}"""
        assertEquals(DeviceInfoParser.parse(json), DeviceInfoParser.parse(json.encodeToByteArray()))
    }
}
