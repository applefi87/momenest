/**********************************************************************
 * SensorReading.kt — 一筆感測讀值與其 JSON 解析
 *
 * 格式與韌體 EnvMonitor/reading_format.cpp 產生的完全相同（WiFi 上傳與 BLE
 * 推播共用同一份序列化），所以這個解析器同時也適用雲端 API 的回應。
 **********************************************************************/
package com.momenest.envmonitor.protocol

/**
 * 一筆感測讀值。
 *
 * null 代表**讀取失敗或感測器未接**——韌體對這種情況送的是 JSON null
 * （土壤/水位讀到 0 也視為未接），不是送 0。UI 要顯示「--」而不是「0」，
 * 否則使用者會以為土壤濕度真的是 0%。
 *
 * [soilRaw] / [waterLevelRaw] 是**原始 ADC 值**，換算百分比是顯示端的事
 * （見 [CalibrationMath]）——這是專案的既有慣例，韌體、雲端、App 三邊一致。
 */
data class SensorReading(
    val airTemp: Float? = null,
    val airHum: Float? = null,
    val waterTemp: Float? = null,
    val soilRaw: Int? = null,
    val waterLevelRaw: Int? = null,
)

object ReadingParser {

    /**
     * 解析 readings characteristic 的 JSON：
     * `{"air_temp":24.58,"air_hum":58.07,"water_temp":31.00,"soil":2100,"water_level":1500}`
     *
     * @return 整份 JSON 格式錯誤時回 null；個別欄位缺漏或為 JSON null 時該欄位為 null
     */
    fun parse(json: String): SensorReading? = toReading(parseJsonObject(json))

    /** characteristic 拿到的原始位元組版本（UTF-8） */
    fun parse(bytes: ByteArray): SensorReading? = toReading(parseJsonObject(bytes))

    private fun toReading(obj: kotlinx.serialization.json.JsonObject?): SensorReading? {
        if (obj == null) return null
        return SensorReading(
            airTemp = obj.floatOrNull("air_temp"),
            airHum = obj.floatOrNull("air_hum"),
            waterTemp = obj.floatOrNull("water_temp"),
            soilRaw = obj.intOrNull("soil"),
            waterLevelRaw = obj.intOrNull("water_level"),
        )
    }
}
