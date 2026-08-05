/*
 * ConnectionState.kt — App 與環境監測器之間的連線狀態。
 *
 * 用 sealed interface 而不是 enum：Connected / Failed 必須各自攜帶資料
 * （裝置名稱與位址、失敗原因），UI 才能直接顯示而不必另外查表；
 * 而 sealed 讓 when 少寫一個 else，日後新增狀態時編譯器會逼所有分支跟上。
 */
package com.momenest.envmonitor.ble

/** 連線狀態機。所有失敗（藍牙未開、權限不足、找不到設備）一律收斂成 [Failed]，不擲例外。 */
sealed interface ConnectionState {

    /** 尚未連線，或使用者主動中斷 */
    data object Disconnected : ConnectionState

    /** 掃描中（廣播 SERVICE_UUID 的設備） */
    data object Scanning : ConnectionState

    /** 已掃到設備，正在建立 GATT 連線與探索服務 */
    data object Connecting : ConnectionState

    /**
     * 已連線且服務探索完成。
     *
     * @param deviceName 設備廣播名稱；無權限或設備未提供時為 null
     * @param address    藍牙 MAC 位址
     */
    data class Connected(val deviceName: String?, val address: String) : ConnectionState

    /**
     * 連線流程失敗。
     *
     * @param reason 給使用者看的繁體中文說明（例如「請先開啟藍牙」）
     */
    data class Failed(val reason: String) : ConnectionState
}
