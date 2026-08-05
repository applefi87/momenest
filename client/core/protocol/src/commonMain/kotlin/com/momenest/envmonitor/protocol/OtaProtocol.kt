/**********************************************************************
 * OtaProtocol.kt — BLE OTA 控制封包的編碼與解碼
 *
 * 手機 → 設備（寫入 ota_control）：
 *   BEGIN : [0x01][size: uint32 LE]      開始，帶韌體總位元組數
 *   END   : [0x02]                       結束（舊格式，不帶 CRC）
 *   END   : [0x02][crc32: uint32 LE]     結束（新格式，帶完整性校驗）
 *   ABORT : [0x03]                       中止
 *
 * 設備 → 手機（ota_control 的 notify）：[status][detail]
 *
 * 這份編碼必須與韌體 EnvMonitor/ota_protocol.cpp 的 parseOtaControl() 逐位元組
 * 對稱。位元組序錯一個，設備就會拿到天文數字般的 size 而拒絕更新——或更糟，
 * 拿到看似合理的錯誤 size 而寫壞 flash。所以這裡的每個編碼都有對應的單元測試。
 *
 * 相容性：END 的兩種長度是刻意的。韌體判斷「長度 >= 5 才讀 CRC」，
 * 所以既有的網頁版（只送 1 byte）仍然可以正常更新，不會被這個新欄位打斷。
 **********************************************************************/
package com.momenest.envmonitor.protocol

/** 手機送給設備的 control 封包編碼 */
object OtaControlPacket {

    const val OP_BEGIN: Byte = 0x01
    const val OP_END: Byte = 0x02
    const val OP_ABORT: Byte = 0x03

    /** uint32 的上限；韌體用 uint32_t 存 size 與 crc，超過就不是同一個數了 */
    private const val UINT32_MAX = 0xFFFFFFFFL

    /**
     * @param sizeBytes 韌體總位元組數
     * @throws IllegalArgumentException size <= 0（韌體把 size=0 視為非法封包）
     *         或超出 uint32 範圍
     */
    fun begin(sizeBytes: Long): ByteArray {
        require(sizeBytes > 0) { "韌體大小必須大於 0，收到 $sizeBytes" }
        require(sizeBytes <= UINT32_MAX) { "韌體大小超出 uint32 範圍：$sizeBytes" }
        return byteArrayOf(OP_BEGIN) + uint32Le(sizeBytes)
    }

    /**
     * @param crc32 整份韌體的 CRC32；傳 null 時送 1 byte 的舊格式
     * @throws IllegalArgumentException crc32 超出 uint32 範圍
     */
    fun end(crc32: Long? = null): ByteArray {
        if (crc32 == null) return byteArrayOf(OP_END)
        require(crc32 in 0..UINT32_MAX) { "CRC32 超出 uint32 範圍：$crc32" }
        return byteArrayOf(OP_END) + uint32Le(crc32)
    }

    fun abort(): ByteArray = byteArrayOf(OP_ABORT)

    /** little-endian：低位在前，與韌體 data[1] | data[2]<<8 | data[3]<<16 | data[4]<<24 對應 */
    private fun uint32Le(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}

/**
 * 設備透過 ota_control 的 notify 回報的狀態，數值與韌體 ble_ota.cpp 的 OTA_ST_* 一致。
 */
sealed interface OtaDeviceReport {
    /** 0x01 已接受 BEGIN，flash 分區準備好了 */
    data object BeginOk : OtaDeviceReport

    /** 0x02 驗證通過，即將切換啟動分區並重開機 */
    data object EndOk : OtaDeviceReport

    /** 0x03 已中止，舊韌體不受影響 */
    data object Aborted : OtaDeviceReport

    /** 0x10 設備端依「已收位元組」算出的進度 */
    data class Progress(val percent: Int) : OtaDeviceReport

    /** 0xEE 錯誤，code 見 [OtaReportDecoder.describeErrorCode] */
    data class DeviceError(val code: Int) : OtaDeviceReport

    /** 韌體版本比 App 新，送了 App 還不認得的狀態碼 */
    data class Unrecognized(val status: Int) : OtaDeviceReport
}

object OtaReportDecoder {

    private const val ST_BEGIN_OK = 0x01
    private const val ST_END_OK = 0x02
    private const val ST_ABORTED = 0x03
    private const val ST_PROGRESS = 0x10
    private const val ST_ERROR = 0xEE

    /**
     * @return 空陣列回 null。只有 1 byte 時 detail 視為 0
     *         （韌體一律送 2 bytes，但防禦性地容忍截斷）
     */
    fun decode(bytes: ByteArray): OtaDeviceReport? {
        if (bytes.isEmpty()) return null
        val status = bytes[0].toInt() and 0xFF
        val detail = if (bytes.size > 1) bytes[1].toInt() and 0xFF else 0
        return when (status) {
            ST_BEGIN_OK -> OtaDeviceReport.BeginOk
            ST_END_OK -> OtaDeviceReport.EndOk
            ST_ABORTED -> OtaDeviceReport.Aborted
            ST_PROGRESS -> OtaDeviceReport.Progress(detail)
            ST_ERROR -> OtaDeviceReport.DeviceError(detail)
            else -> OtaDeviceReport.Unrecognized(status)
        }
    }

    /**
     * 錯誤碼 → 使用者看得懂的說明。碼的定義在韌體 ble_ota.cpp 的 notifyStatus 呼叫處，
     * 那裡是唯一事實來源，新增錯誤碼時兩邊都要改。
     */
    fun describeErrorCode(code: Int): String = when (code) {
        1 -> "control 封包格式錯誤"
        2 -> "設備無法開始更新（OTA 分區不足或已在更新中）"
        3 -> "尚未開始就收到結束指令"
        4 -> "韌體驗證失敗（大小不符或映像損毀）"
        5 -> "寫入 flash 失敗"
        6 -> "CRC 校驗不符（傳輸過程資料損毀）"
        else -> "未知錯誤 (code $code)"
    }
}
