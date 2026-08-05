/**********************************************************************
 * DeviceStatus.kt — 設備連線健康度（WiFi / 雲端上傳 / IP）與其 JSON 解析
 *
 * 來源是 status characteristic，由韌體 ble.cpp 產生：
 * {"wifi":1,"upload":1,"ip":"192.168.31.158"}
 **********************************************************************/
package com.momenest.envmonitor.protocol

import kotlinx.serialization.json.JsonObject

/**
 * 韌體的雲端上傳狀態（status JSON 的 upload 欄位，對應 net.cpp 的 uploadState）。
 *
 * 數值必須與韌體一致：0 尚未上傳、1 成功、2 失敗。
 * [UNKNOWN] 是防禦性的——韌體日後多加狀態碼時，舊版 App 不會崩潰，只顯示未知。
 */
enum class UploadState(val code: Int) {
    /** 尚未上傳過（剛開機） */
    IDLE(0),

    /** 最近一次上傳成功 */
    SUCCESS(1),

    /** 最近一次上傳失敗 */
    FAILED(2),

    /** 韌體回了預期外的值 */
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): UploadState = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class DeviceStatus(
    val wifiConnected: Boolean,
    val uploadState: UploadState,
    /** 未連上 WiFi 時韌體送的是空字串，不是 null */
    val ipAddress: String,
)

object StatusParser {

    fun parse(json: String): DeviceStatus? = toStatus(parseJsonObject(json))

    fun parse(bytes: ByteArray): DeviceStatus? = toStatus(parseJsonObject(bytes))

    private fun toStatus(obj: JsonObject?): DeviceStatus? {
        if (obj == null) return null
        return DeviceStatus(
            // 欄位缺漏時保守地當成「沒連上」，而不是樂觀地當成正常
            wifiConnected = (obj.intOrNull("wifi") ?: 0) != 0,
            uploadState = UploadState.fromCode(obj.intOrNull("upload") ?: UploadState.UNKNOWN.code),
            ipAddress = obj.stringOrNull("ip").orEmpty(),
        )
    }
}
