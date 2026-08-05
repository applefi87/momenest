// StatusLineFormatter.kt — 連線狀態 + 設備狀態 → 標題下方那一行文字。
//
// 純函式，理由同 ReadingTileMapper：文案的組合規則（WiFi 通不通、有沒有 IP）
// 全部可測，而且改文案時一眼就知道會影響哪些情境。
package com.momenest.envmonitor.feature.monitor

import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.protocol.DeviceStatus

object StatusLineFormatter {

    fun format(connection: ConnectionState, status: DeviceStatus?): String = when (connection) {
        ConnectionState.Disconnected -> "未連接"
        ConnectionState.Scanning -> "搜尋設備中…"
        ConnectionState.Connecting -> "連線中…"
        is ConnectionState.Failed -> "連線失敗：${connection.reason}"
        is ConnectionState.Connected -> connectedLine(status)
    }

    /**
     * 已連線時順帶顯示設備自己的 WiFi 狀態——BLE 通不代表雲端上傳正常，
     * 兩者是獨立的，分開顯示才能一眼看出是哪一段出問題。
     */
    private fun connectedLine(status: DeviceStatus?): String {
        // 剛連上、還沒收到第一筆 status 時只說「已連接」，不要憑空猜 WiFi 狀態
        if (status == null) return "已連接"

        return buildString {
            append("已連接 · ")
            append(if (status.wifiConnected) "WiFi 正常" else "WiFi 斷線")
            // 沒連上 WiFi 時韌體送的 IP 是空字串，此時不要留一個孤零零的分隔號
            if (status.ipAddress.isNotBlank()) {
                append(" · ")
                append(status.ipAddress)
            }
        }
    }
}
