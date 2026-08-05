/**********************************************************************
 * DeviceInfo.kt — 韌體版本資訊與其 JSON 解析
 *
 * 來源是 device_info characteristic（韌體 1.1.0 起才有）：
 * {"fw":"1.1.0","built":"Aug  4 2026","chip":"esp32","heap":123456}
 *
 * 存在的理由：OTA 完成後設備會重開機並自動斷線，手機無從得知新韌體是否真的
 * 生效。有了版本欄位，重新連線就能直接看到「現在跑的是哪一版」，
 * 把更新流程的最後一哩補上。
 *
 * 舊韌體沒有這個 characteristic，讀不到屬**正常情況**，不可視為連線失敗。
 **********************************************************************/
package com.momenest.envmonitor.protocol

import kotlinx.serialization.json.JsonObject

data class DeviceInfo(
    val firmwareVersion: String,
    val buildDate: String,
    val chip: String,
    /** 可用堆積記憶體；韌體沒送或格式不符時為 null */
    val freeHeapBytes: Long?,
)

object DeviceInfoParser {

    fun parse(json: String): DeviceInfo? = toInfo(parseJsonObject(json))

    fun parse(bytes: ByteArray): DeviceInfo? = toInfo(parseJsonObject(bytes))

    private fun toInfo(obj: JsonObject?): DeviceInfo? {
        if (obj == null) return null
        return DeviceInfo(
            firmwareVersion = obj.stringOrNull("fw").orEmpty(),
            buildDate = obj.stringOrNull("built").orEmpty(),
            chip = obj.stringOrNull("chip").orEmpty(),
            freeHeapBytes = obj.longOrNull("heap"),
        )
    }
}
