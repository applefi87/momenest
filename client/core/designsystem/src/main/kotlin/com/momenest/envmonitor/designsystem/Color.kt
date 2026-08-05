/**********************************************************************
 * Color.kt — 全 App 色票（唯一事實來源）
 *
 * 數值逐一取自網頁版 cloud/src/ble-app.html 的 CSS 變數。兩端刻意共用同一組
 * 色碼，使用者在手機 App 與網頁上看到的必須是同一個產品；改配色要兩邊一起改。
 *
 * 網頁版卡片用半透明 + backdrop-blur 做 iOS 毛玻璃，Compose 沒有等價的低成本
 * 背景模糊（RenderEffect 要 API 31 且吃 GPU），故這裡改用不透明底色 + 極細
 * 邊框，視覺重量接近但在 minSdk 26 的舊機上不掉幀。
 **********************************************************************/
package com.momenest.envmonitor.designsystem

import androidx.compose.ui.graphics.Color

// ---- 淺色（對應 ble-app.html 的 :root）----

/** 頁面背景 #F5F5F7 */
val LightBackground = Color(0xFFF5F5F7)

/** 卡片底色 #FFFFFF（網頁版是 72% 白 + blur，這裡取其視覺結果） */
val LightSurface = Color(0xFFFFFFFF)

/** 主文字 #1D1D1F */
val LightTextPrimary = Color(0xFF1D1D1F)

/** 次文字（標籤、單位、說明）#86868B */
val LightTextSecondary = Color(0xFF86868B)

/** 強調色（按鈕、進度條）#007AFF */
val LightAccent = Color(0xFF007AFF)

/** 卡片邊框 rgba(0,0,0,.06)；同時當進度條軌道底色，與網頁版一致 */
val LightCardBorder = Color(0x0F000000)

// ---- 深色（對應 ble-app.html 的 prefers-color-scheme:dark）----

/** 頁面背景純黑，OLED 省電且與 iOS 深色一致 */
val DarkBackground = Color(0xFF000000)

/** 卡片底色 #1C1C1E */
val DarkSurface = Color(0xFF1C1C1E)

/** 主文字 #F5F5F7 */
val DarkTextPrimary = Color(0xFFF5F5F7)

/** 次文字 #98989D */
val DarkTextSecondary = Color(0xFF98989D)

/** 深色版強調色 #0A84FF（比淺色版亮一階，避免在黑底上顯得沉） */
val DarkAccent = Color(0xFF0A84FF)

/** 卡片邊框 rgba(255,255,255,.08) */
val DarkCardBorder = Color(0x14FFFFFF)

// ---- 深淺共用 ----

/** 成功／連線正常 #30D158（深淺共用，iOS 系統綠） */
val SuccessGreen = Color(0xFF30D158)

/** 錯誤／連線中斷 #FF453A（深淺共用，iOS 系統紅） */
val ErrorRed = Color(0xFFFF453A)

// ---- 感測項目色（卡片左上小圓點）----

/** 氣溫 */
val SensorAirTemp = Color(0xFFFF9F0A)

/** 水溫 */
val SensorWaterTemp = Color(0xFF30D158)

/** 空氣濕度 */
val SensorHumidity = Color(0xFF64D2FF)

/** 土壤濕度 */
val SensorSoil = Color(0xFFFF6723)

/** 水位 */
val SensorWaterLevel = Color(0xFFBF5AF2)

/**
 * 感測項目色的查表入口。
 *
 * key 用韌體 / 網頁共用的欄位名（`air_temp`、`water_temp`、`air_hum`、`soil`、
 * `water_level`），這樣 feature 層拿到 JSON 欄位名就能直接查色，不必再維護一份
 * 對照表。
 */
object SensorAccents {

    /** @param key 感測欄位名；未知 key 回強調藍（不當成錯誤，避免畫面缺色塊） */
    fun forKey(key: String): Color = when (key) {
        "air_temp" -> SensorAirTemp
        "water_temp" -> SensorWaterTemp
        "air_hum" -> SensorHumidity
        "soil" -> SensorSoil
        "water_level" -> SensorWaterLevel
        else -> LightAccent
    }

    /**
     * 同 [forKey]，但回傳 0xAARRGGBB 的 Long。
     *
     * 給 feature 層那些「不想在 UiState 裡放 Compose 型別」的資料類用
     * （例如 `ReadingTile.accentArgb`）；還原成 Color 只要 `Color(argb)`。
     */
    fun argbFor(key: String): Long = when (key) {
        "air_temp" -> 0xFFFF9F0A
        "water_temp" -> 0xFF30D158
        "air_hum" -> 0xFF64D2FF
        "soil" -> 0xFFFF6723
        "water_level" -> 0xFFBF5AF2
        else -> 0xFF007AFF
    }
}
