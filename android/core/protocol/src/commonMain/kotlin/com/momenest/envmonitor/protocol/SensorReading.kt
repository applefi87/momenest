package com.momenest.envmonitor.protocol

import kotlinx.serialization.Serializable

/**
 * 感測器即時讀值。
 *
 * 欄位為 null 代表設備回報該感測器異常或未連接（韌體 JSON 裡該 key 為 null）。
 */
@Serializable
data class SensorReading(
    val airTemp: Float? = null,
    val airHum: Float? = null,
    val waterTemp: Float? = null,
    val soilRaw: Int? = null,
    val waterLevelRaw: Int? = null
)
