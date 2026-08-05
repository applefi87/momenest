package com.momenest.envmonitor.ble

import com.momenest.envmonitor.protocol.DeviceInfo
import com.momenest.envmonitor.protocol.DeviceStatus
import com.momenest.envmonitor.protocol.OtaEvent
import com.momenest.envmonitor.protocol.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 環境監測儀的通訊介面。
 * 
 * 封裝了 BLE 的連線管理與資料交換。
 */
interface EnvMonitorClient {
    /** 目前連線狀態 */
    val connectionState: StateFlow<ConnectionState>
    
    /** 感測器即時讀值流 */
    val readings: StateFlow<SensorReading?>
    
    /** 設備運行狀態流 */
    val status: StateFlow<DeviceStatus?>
    
    /** 設備硬體資訊（連線後讀取一次） */
    val deviceInfo: StateFlow<DeviceInfo?>
    
    /** 是否支援 OTA 更新 */
    val otaSupported: StateFlow<Boolean>
    
    /** 開始連線 */
    suspend fun connect()
    
    /** 斷開連線 */
    fun disconnect()
    
    /** 上傳韌體 */
    fun uploadFirmware(bytes: ByteArray): Flow<OtaEvent>
}
