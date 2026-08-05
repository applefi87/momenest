// MonitorUiState.kt — 即時讀值畫面的狀態模型。
//
// 這裡刻意只放「畫面能直接畫出來的東西」：數值已格式化成字串、顏色已決定好。
// 換算與文案都往前推到 ReadingTileMapper / StatusLineFormatter 這兩個純函式，
// 於是繁瑣的邊界條件都能用 JVM 單元測試涵蓋，UI 測試只需驗排版與互動。
package com.momenest.envmonitor.feature.monitor

import com.momenest.envmonitor.ble.ConnectionState

/**
 * 單張讀值卡的顯示資料（已完成單位與百分比換算，UI 只負責畫）。
 *
 * @param key        欄位鍵，與韌體 JSON 一致："air_temp" / "water_temp" /
 *                   "air_hum" / "soil" / "water_level"
 * @param label      顯示名稱（繁中）
 * @param value      已格式化的數值；無資料為 "--"
 * @param unit       "°C" / "%" / ""（未校準的原始 ADC 沒有單位）
 * @param accentArgb 對應 designsystem 感測項目色的 ARGB 值
 */
data class ReadingTile(
    val key: String,
    val label: String,
    val value: String,
    val unit: String,
    val accentArgb: Long,
)

/**
 * 即時讀值畫面的完整狀態。
 *
 * 預設值就是「App 剛開啟、還沒連上任何設備」的樣子，因此 ViewModel 的
 * `stateIn` 可以直接拿它當初始值，畫面不會有空白的中間狀態。
 */
data class MonitorUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val tiles: List<ReadingTile> = emptyList(),
    val statusLine: String = "未連接",
    /** 設備回報的韌體版本；舊韌體沒有 device_info characteristic 時為 null */
    val firmwareVersion: String? = null,
    val otaSupported: Boolean = false,
    /** 一次性的錯誤訊息（例如連線失敗），使用者關閉後清為 null */
    val errorMessage: String? = null,
) {
    val isConnected: Boolean get() = connection is ConnectionState.Connected

    /** 掃描或連線進行中——按鈕要鎖住，避免連點觸發第二次掃描 */
    val isBusy: Boolean
        get() = connection is ConnectionState.Scanning || connection is ConnectionState.Connecting
}
