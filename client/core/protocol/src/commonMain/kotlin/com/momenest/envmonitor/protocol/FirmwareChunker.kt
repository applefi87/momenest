/**********************************************************************
 * FirmwareChunker.kt — 把韌體位元組切成適合單筆 BLE 寫入的分塊
 *
 * 分塊大小不是固定的：要看實際協商到的 MTU（見 GattContract.chunkSizeForMtu），
 * 所以切割邏輯獨立出來、可單獨測試，而不是埋在傳輸迴圈裡。
 **********************************************************************/
package com.momenest.envmonitor.protocol

object FirmwareChunker {

    /**
     * 把 [data] 切成每塊最多 [chunkSize] 位元組（最後一塊可能較短）。
     *
     * 回傳 [Sequence] 而不是 List：1MB 韌體切成 512B 會是 2048 塊，
     * 一次全部實體化等於把整份韌體再複製一份到記憶體裡；惰性產生只留當下那一塊。
     *
     * @throws IllegalArgumentException chunkSize <= 0（否則會產生無限迴圈）
     */
    fun chunks(data: ByteArray, chunkSize: Int): Sequence<ByteArray> {
        require(chunkSize > 0) { "chunkSize 必須大於 0，收到 $chunkSize" }
        if (data.isEmpty()) return emptySequence()

        return sequence {
            var offset = 0
            while (offset < data.size) {
                val end = minOf(offset + chunkSize, data.size)
                yield(data.copyOfRange(offset, end))
                offset = end
            }
        }
    }

    /**
     * 不實際切割就算出塊數（給 UI 預估進度用）：ceil(totalBytes / chunkSize)。
     *
     * @throws IllegalArgumentException chunkSize <= 0 或 totalBytes < 0
     */
    fun chunkCount(totalBytes: Long, chunkSize: Int): Long {
        require(chunkSize > 0) { "chunkSize 必須大於 0，收到 $chunkSize" }
        require(totalBytes >= 0) { "totalBytes 不可為負，收到 $totalBytes" }
        if (totalBytes == 0L) return 0
        return (totalBytes + chunkSize - 1) / chunkSize
    }
}
