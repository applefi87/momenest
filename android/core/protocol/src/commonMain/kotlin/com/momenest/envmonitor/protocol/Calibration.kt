package com.momenest.envmonitor.protocol

import kotlinx.serialization.Serializable

/**
 * 土壤與水位 ADC 校準值。
 *
 * @param soilMin   0% 土壤濕度對應的 ADC（乾燥）
 * @param soilMax   100% 土壤濕度對應的 ADC（水中）
 * @param waterMin  0% 水位對應的 ADC（空容器）
 * @param waterMax  100% 水位對應的 ADC（滿水位）
 */
@Serializable
data class Calibration(
    val soilMin: Int? = null,
    val soilMax: Int? = null,
    val waterMin: Int? = null,
    val waterMax: Int? = null
)
