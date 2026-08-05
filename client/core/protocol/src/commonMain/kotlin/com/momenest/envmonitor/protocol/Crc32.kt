/**********************************************************************
 * Crc32.kt — CRC-32 (IEEE 802.3)
 *
 * 用途：OTA 傳完後把整份韌體的 CRC 一起送給設備，設備用自己累積的 CRC 比對，
 * 相符才切換啟動分區。BLE 本身有 CRC，但那是「每個封包」的；跨數千個封包的
 * 整體完整性（漏塊、順序錯亂）要靠這一層才驗得出來。
 *
 * 刻意自己實作而不是用 java.util.zip.CRC32：這是 KMP 的 commonMain，
 * 不能有 java.*。而且自己寫也才能保證與韌體 ota_protocol.cpp 的
 * crc32Update() 是同一份演算法。
 *
 * 參數（三邊必須一致：本檔、韌體、任何第三方工具）：
 *   多項式 0xEDB88320（反射形式）、初值 0xFFFFFFFF、輸出取反
 * 這組參數與 java.util.zip.CRC32、zlib crc32()、gzip 相同。
 **********************************************************************/
package com.momenest.envmonitor.protocol

/**
 * CRC-32（IEEE 802.3）。回傳值是 0..0xFFFFFFFF 的無號結果，放在 [Long] 裡
 * （Kotlin 的 Int 是有號的，直接回 Int 會讓大於 0x7FFFFFFF 的結果變負數，
 * 送進封包時很容易算錯位元組）。
 */
object Crc32 {

    private const val POLYNOMIAL: UInt = 0xEDB88320u
    private const val INITIAL: UInt = 0xFFFFFFFFu

    /**
     * 逐位元運算而非 256 項查表：省下 1KB 記憶體，對手機端毫無差別，
     * 也讓這份實作與韌體端（ESP32 flash 很緊，同樣不建表）長得一樣好對照。
     */
    private fun update(crc: UInt, byte: Byte): UInt {
        var c = crc xor (byte.toUInt() and 0xFFu)
        repeat(8) {
            c = if ((c and 1u) != 0u) (c shr 1) xor POLYNOMIAL else c shr 1
        }
        return c
    }

    /** 整段資料的 CRC32 */
    fun compute(data: ByteArray): Long = compute(data, 0, data.size)

    /**
     * 指定區間的 CRC32。
     *
     * @throws IllegalArgumentException offset / length 超出 [data] 範圍
     */
    fun compute(data: ByteArray, offset: Int, length: Int): Long {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "區間超出範圍：offset=$offset length=$length size=${data.size}"
        }
        var crc = INITIAL
        for (i in offset until offset + length) {
            crc = update(crc, data[i])
        }
        return (crc xor INITIAL).toLong() and 0xFFFFFFFFL
    }

    /**
     * 串流版：資料是分塊到達時用這組 API，結果與一次算完整段相同。
     *
     * 用法：`var c = init(); c = update(c, chunk); …; val crc = final(c)`
     */
    fun init(): UInt = INITIAL

    fun update(crc: UInt, data: ByteArray, offset: Int = 0, length: Int = data.size - offset): UInt {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "區間超出範圍：offset=$offset length=$length size=${data.size}"
        }
        var c = crc
        for (i in offset until offset + length) {
            c = update(c, data[i])
        }
        return c
    }

    fun final(crc: UInt): Long = (crc xor INITIAL).toLong() and 0xFFFFFFFFL
}
