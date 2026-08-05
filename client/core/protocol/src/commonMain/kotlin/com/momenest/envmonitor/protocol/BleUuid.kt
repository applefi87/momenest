/**********************************************************************
 * BleUuid.kt — 平台無關的 128-bit BLE UUID
 *
 * 為什麼不直接用 java.util.UUID：這個模組是 KMP 的 commonMain，要能編到
 * iOS/Native，而 java.* 只存在於 JVM。一旦 commonMain 出現 java.util.UUID，
 * 整個多平台就只是「宣告了 target 卻編不過」的假象。
 *
 * 各平台在自己的實作層做轉換即可：
 *   Android → java.util.UUID.fromString(bleUuid.text)
 *   iOS     → CBUUID(string: bleUuid.text)
 **********************************************************************/
package com.momenest.envmonitor.protocol

/**
 * 一個 128-bit BLE UUID，內部一律以**小寫正規形式**保存
 * （`8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01`）。
 *
 * 正規化的理由：BLE 世界裡大小寫混用很常見，若直接拿字串比對，
 * 大寫的 `8F2A0001-…` 與小寫的 `8f2a0001-…` 會被判定成不同 UUID，
 * 而症狀是「連得上但找不到 service」——這種 bug 非常難查。
 */
class BleUuid private constructor(val text: String) {

    override fun equals(other: Any?): Boolean = other is BleUuid && text == other.text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = text

    companion object {
        /** 128-bit UUID 的標準字串長度：32 個十六進位字元 + 4 個連字號 */
        private const val CANONICAL_LENGTH = 36

        /** 連字號在正規形式中的位置（8-4-4-4-12） */
        private val HYPHEN_POSITIONS = intArrayOf(8, 13, 18, 23)

        /**
         * 解析並正規化。
         *
         * @throws IllegalArgumentException 格式不合法（長度、連字號位置或非十六進位字元）
         */
        fun of(text: String): BleUuid =
            ofOrNull(text) ?: throw IllegalArgumentException("不是合法的 128-bit UUID：$text")

        /** 同 [of]，但格式不合法時回傳 null 而不是擲例外 */
        fun ofOrNull(text: String): BleUuid? {
            val trimmed = text.trim()
            if (trimmed.length != CANONICAL_LENGTH) return null

            val lower = trimmed.lowercase()
            for (i in lower.indices) {
                val c = lower[i]
                if (i in HYPHEN_POSITIONS) {
                    if (c != '-') return null
                } else {
                    val isHex = c in '0'..'9' || c in 'a'..'f'
                    if (!isHex) return null
                }
            }
            return BleUuid(lower)
        }
    }
}
