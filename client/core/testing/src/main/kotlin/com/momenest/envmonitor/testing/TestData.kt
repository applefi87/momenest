/**
 * TestData.kt — 各模組共用的測試樣本資料。
 *
 * 集中在這裡的理由：讀值／狀態的樣本散落在各測試檔時，欄位一改就要到處找、
 * 而且很容易出現「這個測試用 24.5、那個用 25.0」的無意義差異。統一從這裡取，
 * 測試就只需要覆寫自己真正在乎的那一兩個欄位。
 *
 * 同時提供頂層函式與 [TestData] 物件兩種呼叫方式（`sampleReading()` 或
 * `TestData.sampleReading()`），兩者是同一份實作，選順手的用即可。
 *
 * 本檔位於 src/main 而非 src/test，理由見 MainDispatcherRule.kt 檔頭。
 */
package com.momenest.envmonitor.testing

import com.momenest.envmonitor.protocol.DeviceInfo
import com.momenest.envmonitor.protocol.DeviceStatus
import com.momenest.envmonitor.protocol.SensorReading
import com.momenest.envmonitor.protocol.UploadState

// 樣本值直接取自韌體實測的 readings / status JSON，維持與真機一致的數量級
private const val SAMPLE_AIR_TEMP = 24.58f
private const val SAMPLE_AIR_HUM = 58.07f
private const val SAMPLE_WATER_TEMP = 31.0f
private const val SAMPLE_SOIL_RAW = 2100
private const val SAMPLE_WATER_LEVEL_RAW = 1500

private const val SAMPLE_IP = "192.168.31.158"

private const val SAMPLE_FW_VERSION = "1.1.0"
private const val SAMPLE_BUILD_DATE = "2026-08-04"
private const val SAMPLE_CHIP = "esp32"
private const val SAMPLE_FREE_HEAP = 123_456L

private const val DEFAULT_FIRMWARE_SIZE = 4096

/** 共用測試樣本（物件版；與同名頂層函式等價） */
object TestData {

    /** 五個感測欄位都有值的一筆讀值；要測缺值就把對應參數傳 null */
    fun sampleReading(
        airTemp: Float? = SAMPLE_AIR_TEMP,
        airHum: Float? = SAMPLE_AIR_HUM,
        waterTemp: Float? = SAMPLE_WATER_TEMP,
        soilRaw: Int? = SAMPLE_SOIL_RAW,
        waterLevelRaw: Int? = SAMPLE_WATER_LEVEL_RAW,
    ): SensorReading = SensorReading(
        airTemp = airTemp,
        airHum = airHum,
        waterTemp = waterTemp,
        soilRaw = soilRaw,
        waterLevelRaw = waterLevelRaw,
    )

    /** WiFi 已連線、上次上傳成功的設備狀態 */
    fun sampleStatus(
        wifiConnected: Boolean = true,
        uploadState: UploadState = UploadState.SUCCESS,
        ipAddress: String = SAMPLE_IP,
    ): DeviceStatus = DeviceStatus(
        wifiConnected = wifiConnected,
        uploadState = uploadState,
        ipAddress = ipAddress,
    )

    /** 新韌體（有 device_info characteristic）會回報的設備資訊 */
    fun sampleDeviceInfo(
        firmwareVersion: String = SAMPLE_FW_VERSION,
        buildDate: String = SAMPLE_BUILD_DATE,
        chip: String = SAMPLE_CHIP,
        freeHeapBytes: Long? = SAMPLE_FREE_HEAP,
    ): DeviceInfo = DeviceInfo(
        firmwareVersion = firmwareVersion,
        buildDate = buildDate,
        chip = chip,
        freeHeapBytes = freeHeapBytes,
    )

    /**
     * 假韌體位元組。
     *
     * 內容刻意用可預測的遞增 pattern（第 i 個位元組 = i % 256）而不是隨機值：
     * 分塊傳輸出錯時（漏塊、重送、順序顛倒）從內容就看得出來。
     *
     * 註：OTA 分塊傳輸本身的正確性由 :core:protocol 的 OtaUploaderTest 驗證
     * （那裡有自己的 TestGattTransport），這裡的樣本只給 feature 層測試用。
     */
    fun sampleFirmware(sizeBytes: Int = DEFAULT_FIRMWARE_SIZE): ByteArray =
        ByteArray(sizeBytes) { (it % 256).toByte() }
}

/** @see TestData.sampleReading */
fun sampleReading(
    airTemp: Float? = SAMPLE_AIR_TEMP,
    airHum: Float? = SAMPLE_AIR_HUM,
    waterTemp: Float? = SAMPLE_WATER_TEMP,
    soilRaw: Int? = SAMPLE_SOIL_RAW,
    waterLevelRaw: Int? = SAMPLE_WATER_LEVEL_RAW,
): SensorReading = TestData.sampleReading(airTemp, airHum, waterTemp, soilRaw, waterLevelRaw)

/** @see TestData.sampleStatus */
fun sampleStatus(
    wifiConnected: Boolean = true,
    uploadState: UploadState = UploadState.SUCCESS,
    ipAddress: String = SAMPLE_IP,
): DeviceStatus = TestData.sampleStatus(wifiConnected, uploadState, ipAddress)

/** @see TestData.sampleDeviceInfo */
fun sampleDeviceInfo(
    firmwareVersion: String = SAMPLE_FW_VERSION,
    buildDate: String = SAMPLE_BUILD_DATE,
    chip: String = SAMPLE_CHIP,
    freeHeapBytes: Long? = SAMPLE_FREE_HEAP,
): DeviceInfo = TestData.sampleDeviceInfo(firmwareVersion, buildDate, chip, freeHeapBytes)

/** @see TestData.sampleFirmware */
fun sampleFirmware(sizeBytes: Int = DEFAULT_FIRMWARE_SIZE): ByteArray =
    TestData.sampleFirmware(sizeBytes)
