/**********************************************************************
 * Calibration.kt — 土壤 / 水位的 ADC 校準與百分比換算
 *
 * 慣例（韌體、雲端儀表板、網頁版 BLE App 三邊一致）：
 * 設備送的一律是原始 ADC 值，0~100% 的換算只在顯示端做。
 * 這樣校準值改了不必重燒韌體，歷史資料也不會因為換算方式改變而失真。
 **********************************************************************/
package com.momenest.envmonitor.protocol

/**
 * 校準值：min 對應 0%、max 對應 100%。
 *
 * 全部為 null 代表使用者還沒校準——此時 UI 直接顯示原始 ADC 值，
 * 而不是硬套一組猜測的預設值，給出看似精準卻錯誤的百分比。
 *
 * @param soilMin   0% 土壤濕度對應的 ADC（乾燥）
 * @param soilMax   100% 土壤濕度對應的 ADC（水中）
 * @param waterMin  0% 水位對應的 ADC（空容器）
 * @param waterMax  100% 水位對應的 ADC（滿水位）
 */
data class Calibration(
    val soilMin: Int? = null,
    val soilMax: Int? = null,
    val waterMin: Int? = null,
    val waterMax: Int? = null,
) {
    val hasSoil: Boolean get() = soilMin != null && soilMax != null && soilMin != soilMax
    val hasWater: Boolean get() = waterMin != null && waterMax != null && waterMin != waterMax
}

object CalibrationMath {

    /**
     * 原始 ADC → 0..100%，與網頁儀表板 / ble-app.html 的換算一致。
     *
     * min > max 時視為**反向刻度**（電容式與電阻式土壤探針的方向剛好相反，
     * 兩種都有人接），一樣線性換算，不當成錯誤。
     *
     * @return min/max 缺一不可，或兩者相同（會除以 0）時回 null，代表「無法換算」
     */
    fun adcToPercent(raw: Int, min: Int?, max: Int?): Int? {
        if (min == null || max == null || min == max) return null
        val ratio = (raw - min).toDouble() / (max - min).toDouble()
        return (ratio * 100.0).toInt().coerceIn(0, 100)
    }

    fun soilPercent(raw: Int, cal: Calibration): Int? = adcToPercent(raw, cal.soilMin, cal.soilMax)

    fun waterPercent(raw: Int, cal: Calibration): Int? = adcToPercent(raw, cal.waterMin, cal.waterMax)
}
