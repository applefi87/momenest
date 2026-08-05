/*
 * GattOperationQueue.kt — 把所有 GATT 操作序列化的閘門。
 *
 * Android 的 BluetoothGatt 底層只維護「一個進行中的請求」。同時送出兩個操作
 * （例如一邊寫 CCCD 一邊讀 characteristic），第二個會直接回 false，或更糟：
 * 回 true 但回呼永遠不來，協程就這樣掛死。這是 Android BLE 最常見的坑，
 * 官方 API 卻完全沒有幫忙擋，只能自己排隊。
 *
 * 這裡不自己造 job 佇列，直接用 Mutex：它是公平（FIFO）的，
 * 而且例外與取消都會正確釋放鎖，不會把後續操作永遠卡住。
 */
package com.momenest.envmonitor.ble

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * GATT 操作序列化佇列。所有 `writeCharacteristic` / `readCharacteristic` /
 * `writeDescriptor` 都必須包在 [execute] 裡。
 */
class GattOperationQueue {

    private val mutex = Mutex()

    /**
     * 依序執行 [block]；同一時間只會有一個 block 在跑，其餘呼叫端依抵達順序等待。
     *
     * [block] 擲出的例外會原樣往外傳（不吞），呼叫端取消時鎖也會釋放，
     * 兩種情況佇列都能繼續服務下一個操作。
     */
    suspend fun <T> execute(block: suspend () -> T): T = mutex.withLock { block() }
}
