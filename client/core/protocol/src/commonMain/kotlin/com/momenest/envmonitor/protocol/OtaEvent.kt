/**********************************************************************
 * OtaEvent.kt — OTA 過程回報給 UI 的事件
 **********************************************************************/
package com.momenest.envmonitor.protocol

sealed interface OtaEvent {

    /** 已送出 BEGIN 並取得設備確認，接下來開始灌位元組 */
    data object Started : OtaEvent

    /** 傳輸中。percent 與設備螢幕上顯示的算法相同（見 [otaPercent]） */
    data class Sending(val percent: Int, val bytesSent: Long, val totalBytes: Long) : OtaEvent

    /** 已送出 END，設備正在驗證映像與 CRC */
    data object Verifying : OtaEvent

    /**
     * 更新完成。
     *
     * [confirmedByDevice] 為 false 代表沒等到設備的 END_OK 就逾時了。這**通常仍是成功**：
     * 設備驗證通過後會立刻重開機，notify 常常來不及送達手機。刻意把這兩種情況區分開，
     * 而不是一律報「成功」，是為了不對使用者說謊——UI 會提示重新連線確認版本。
     */
    data class Completed(val confirmedByDevice: Boolean) : OtaEvent

    /** 更新失敗。任何失敗都不會影響設備上正在跑的舊韌體（A/B 雙分區） */
    data class Failed(val reason: OtaFailure, val detail: String? = null) : OtaEvent
}

enum class OtaFailure {
    /** 選到的檔案是 0 bytes */
    EMPTY_FIRMWARE,

    /** 設備拒絕開始（分區不足、已在更新中），或根本沒回應 */
    BEGIN_REJECTED,

    /** 寫入失敗或連線中斷 */
    TRANSPORT_ERROR,

    /** 設備回報 0xEE 錯誤 */
    DEVICE_ERROR,

    /** 使用者取消，或設備主動中止 */
    ABORTED,
}
