package com.momenest.envmonitor.feature.monitor

import com.google.common.truth.Truth.assertThat
import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.protocol.DeviceStatus
import com.momenest.envmonitor.protocol.UploadState
import org.junit.Test

class StatusLineFormatterTest {

    private val connected = ConnectionState.Connected("env-monitor", "AA:BB:CC:DD:EE:FF")

    private fun status(wifi: Boolean, ip: String) =
        DeviceStatus(wifiConnected = wifi, uploadState = UploadState.SUCCESS, ipAddress = ip)

    @Test
    fun `未連線時顯示未連接`() {
        assertThat(StatusLineFormatter.format(ConnectionState.Disconnected, null))
            .isEqualTo("未連接")
    }

    @Test
    fun `掃描與連線中各有專屬文案`() {
        assertThat(StatusLineFormatter.format(ConnectionState.Scanning, null))
            .isEqualTo("搜尋設備中…")
        assertThat(StatusLineFormatter.format(ConnectionState.Connecting, null))
            .isEqualTo("連線中…")
    }

    @Test
    fun `連線失敗時把原因一起顯示出來`() {
        // 只說「失敗」對使用者沒有幫助，原因才是能不能自行排除的關鍵
        val line = StatusLineFormatter.format(ConnectionState.Failed("請先開啟藍牙"), null)
        assertThat(line).contains("請先開啟藍牙")
    }

    @Test
    fun `剛連上還沒收到狀態時只說已連接`() {
        // 不要在收到第一筆 status 之前憑空猜 WiFi 狀態
        assertThat(StatusLineFormatter.format(connected, null)).isEqualTo("已連接")
    }

    @Test
    fun `WiFi 正常時附上 IP`() {
        assertThat(StatusLineFormatter.format(connected, status(true, "192.168.31.158")))
            .isEqualTo("已連接 · WiFi 正常 · 192.168.31.158")
    }

    @Test
    fun `WiFi 斷線時不顯示 IP 也不留下多餘分隔號`() {
        assertThat(StatusLineFormatter.format(connected, status(false, "")))
            .isEqualTo("已連接 · WiFi 斷線")
    }

    @Test
    fun `IP 為空白字串時不顯示 IP 段落`() {
        assertThat(StatusLineFormatter.format(connected, status(true, "   ")))
            .isEqualTo("已連接 · WiFi 正常")
    }

    @Test
    fun `BLE 已連線但設備 WiFi 斷線是可能且要如實呈現的組合`() {
        // BLE 通不代表雲端上傳正常，兩段是獨立的
        val line = StatusLineFormatter.format(connected, status(false, "192.168.1.5"))
        assertThat(line).contains("WiFi 斷線")
    }
}
