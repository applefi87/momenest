// ReadingTileMapper.kt — 感測讀值 + 校準值 → 畫面上的五張卡。
//
// 抽成純函式的理由：這裡集中了所有「顯示規則」的邊界條件（沒資料怎麼辦、
// 沒校準怎麼辦、小數幾位、順序），全部都能用毫秒級的 JVM 測試涵蓋，
// UI 測試就只需要驗排版與互動，不必為了測一個小數點去跑模擬器。
package com.momenest.envmonitor.feature.monitor

import com.momenest.envmonitor.protocol.Calibration
import com.momenest.envmonitor.protocol.CalibrationMath
import com.momenest.envmonitor.protocol.SensorReading
import java.util.Locale

/** 沒有資料時顯示的佔位符號。刻意不用 0，否則使用者會以為真的量到 0 */
internal const val NO_VALUE = "--"

// 感測項目色，與 :core:designsystem 的 SensorColors 及網頁版 ble-app.html 一致
private const val COLOR_AIR_TEMP = 0xFFFF9F0AL
private const val COLOR_WATER_TEMP = 0xFF30D158L
private const val COLOR_AIR_HUM = 0xFF64D2FFL
private const val COLOR_SOIL = 0xFFFF6723L
private const val COLOR_WATER_LEVEL = 0xFFBF5AF2L

object ReadingTileMapper {

    /**
     * @param reading null 代表尚未收到任何讀值（剛連上或未連線）——
     *               仍回傳五張「--」的卡而不是空清單，畫面才不會在連線瞬間跳動
     */
    fun toTiles(reading: SensorReading?, calibration: Calibration): List<ReadingTile> = listOf(
        temperatureTile("air_temp", "氣溫", reading?.airTemp, COLOR_AIR_TEMP),
        temperatureTile("water_temp", "水溫", reading?.waterTemp, COLOR_WATER_TEMP),
        percentTile("air_hum", "濕度", reading?.airHum, COLOR_AIR_HUM),
        adcTile(
            key = "soil",
            label = "土壤",
            raw = reading?.soilRaw,
            percent = reading?.soilRaw?.let { CalibrationMath.soilPercent(it, calibration) },
            colorArgb = COLOR_SOIL,
        ),
        adcTile(
            key = "water_level",
            label = "水位",
            raw = reading?.waterLevelRaw,
            percent = reading?.waterLevelRaw?.let { CalibrationMath.waterPercent(it, calibration) },
            colorArgb = COLOR_WATER_LEVEL,
        ),
    )

    private fun temperatureTile(key: String, label: String, value: Float?, colorArgb: Long) =
        ReadingTile(
            key = key,
            label = label,
            value = value?.let(::oneDecimal) ?: NO_VALUE,
            unit = if (value == null) "" else "°C",
            accentArgb = colorArgb,
        )

    private fun percentTile(key: String, label: String, value: Float?, colorArgb: Long) =
        ReadingTile(
            key = key,
            label = label,
            value = value?.let(::oneDecimal) ?: NO_VALUE,
            unit = if (value == null) "" else "%",
            accentArgb = colorArgb,
        )

    /**
     * 土壤 / 水位：有校準就顯示百分比，沒校準就老實顯示原始 ADC。
     *
     * 不套用猜測的預設校準值——顯示一個看似精準但其實錯誤的百分比，
     * 比直接顯示原始數字更糟。
     */
    private fun adcTile(
        key: String,
        label: String,
        raw: Int?,
        percent: Int?,
        colorArgb: Long,
    ): ReadingTile = when {
        raw == null -> ReadingTile(key, label, NO_VALUE, "", colorArgb)
        percent != null -> ReadingTile(key, label, percent.toString(), "%", colorArgb)
        else -> ReadingTile(key, label, raw.toString(), "", colorArgb)
    }

    /**
     * 固定一位小數。
     *
     * 指定 [Locale.US]：部分地區（德文、法文…）的預設 locale 會把小數點印成逗號，
     * 讓數字看起來像壞掉了。
     */
    private fun oneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)
}
