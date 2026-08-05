/**********************************************************************
 * OtaProgress.kt — OTA 進度百分比
 *
 * 語意刻意與韌體 EnvMonitor/ota_protocol.cpp 的 otaPercent() 完全相同，
 * 這樣手機螢幕與設備螢幕顯示的數字才會一致（設備依已收位元組算、
 * 手機依已送位元組算，演算法一致才不會一邊 87% 一邊 88%）。
 **********************************************************************/
package com.momenest.envmonitor.protocol

/**
 * @param received 已傳送 / 已接收的位元組數
 * @param total    韌體總位元組數
 * @return 0..100。total <= 0 回 0；received >= total 回 100；其餘無條件捨去。
 */
fun otaPercent(received: Long, total: Long): Int {
    if (total <= 0L) return 0
    if (received >= total) return 100
    if (received <= 0L) return 0
    // 先乘再除會溢位嗎：received 上限是韌體大小（數 MB），乘 100 遠小於 Long 上限，安全
    return ((received * 100L) / total).toInt()
}
