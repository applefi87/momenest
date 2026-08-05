package com.momenest.envmonitor.protocol

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 抽象的 GATT 傳輸層。
 * 
 * 讓協定層不必依賴 Android 的 BluetoothGatt，方便在 JVM 上測試。
 */
interface GattTransport {
    /** 寫入特徵值 */
    suspend fun write(characteristic: UUID, value: ByteArray)
    
    /** 訂閱特徵值通知 */
    fun notifications(characteristic: UUID): Flow<ByteArray>
    
    /** 目前協商到的 MTU */
    fun negotiatedMtu(): Int
}

class GattTransportException(message: String) : Exception(message)
