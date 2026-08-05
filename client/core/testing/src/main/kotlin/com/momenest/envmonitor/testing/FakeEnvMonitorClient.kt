/**
 * FakeEnvMonitorClient.kt — EnvMonitorClient 的測試替身。
 *
 * feature 層的 ViewModel 只依賴 EnvMonitorClient 介面，換上這個 fake 之後就能在
 * 純 JVM 單元測試裡驗證「設備推了新讀值 → UI 狀態怎麼變」，完全不需要真設備、
 * 不需要模擬器、也不需要藍牙權限。
 *
 * 狀態一律用 MutableStateFlow 對外曝露（正式實作只給 StateFlow），測試端直接寫
 * `client.readings.value = ...` 就能模擬設備行為。
 *
 * 本檔位於 src/main 而非 src/test，理由見 MainDispatcherRule.kt 檔頭。
 */
package com.momenest.envmonitor.testing

import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.ble.EnvMonitorClient
import com.momenest.envmonitor.protocol.DeviceInfo
import com.momenest.envmonitor.protocol.DeviceStatus
import com.momenest.envmonitor.protocol.GattContract
import com.momenest.envmonitor.protocol.OtaEvent
import com.momenest.envmonitor.protocol.SensorReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** 假設備的預設藍牙位址，格式照 Android 的 BluetoothDevice.getAddress() */
private const val FAKE_ADDRESS = "AA:BB:CC:DD:EE:FF"

/**
 * [EnvMonitorClient] 的可程式化替身。
 *
 * 用法：
 * ```
 * val client = FakeEnvMonitorClient()
 * client.connectionState.value = ConnectionState.Connected("env-monitor", "AA:BB:CC:DD:EE:FF")
 * client.readings.value = sampleReading()
 * ```
 */
class FakeEnvMonitorClient : EnvMonitorClient {

    override val connectionState: MutableStateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val readings: MutableStateFlow<SensorReading?> = MutableStateFlow(null)

    override val status: MutableStateFlow<DeviceStatus?> = MutableStateFlow(null)

    override val deviceInfo: MutableStateFlow<DeviceInfo?> = MutableStateFlow(null)

    override val otaSupported: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** [connect] 被呼叫的次數 */
    var connectCallCount: Int = 0

    /** [disconnect] 被呼叫的次數 */
    var disconnectCallCount: Int = 0

    /** 設了就讓 [connect] 擲出這個例外，用來測「連線失敗要顯示錯誤訊息」 */
    var connectError: Throwable? = null

    /** [uploadFirmware] 要回放的事件序列，由測試端指定 */
    var otaEvents: List<OtaEvent> = emptyList()

    /** 最後一次 [uploadFirmware] 收到的位元組；沒被呼叫過為 null */
    var lastUploadedFirmware: ByteArray? = null

    /**
     * [uploadFirmware] 被呼叫的次數。
     *
     * 用來驗證「傳輸中重複按開始不會啟動第二條傳輸」——同時有兩個傳輸在灌 flash
     * 一定會把設備韌體寫壞，是必須擋住的情境。
     */
    var uploadCallCount: Int = 0

    /**
     * [otaEvents] 各事件之間的間隔毫秒數，預設 0（一口氣送完）。
     *
     * 設成 >0 可讓測試觀察到中間狀態（例如「傳輸中不能重複按開始」）；
     * 在 runTest 的虛擬時間下不會真的等待。
     */
    var otaEventDelayMillis: Long = 0

    override suspend fun connect() {
        connectCallCount++
        connectError?.let { throw it }

        // 沒指定錯誤就視為連線成功。若測試端已自行把狀態設成 Connected 就不覆寫，
        // 免得蓋掉測試刻意安排的裝置名稱／位址。
        if (connectionState.value !is ConnectionState.Connected) {
            connectionState.value = ConnectionState.Connected(GattContract.DEVICE_NAME, FAKE_ADDRESS)
        }
    }

    override fun disconnect() {
        disconnectCallCount++
        connectionState.value = ConnectionState.Disconnected
    }

    override fun uploadFirmware(bytes: ByteArray): Flow<OtaEvent> {
        // 在呼叫當下就記錄，讓「有沒有把選到的檔案交給 client」這種斷言不必先收集冷流
        lastUploadedFirmware = bytes
        uploadCallCount++
        val events = otaEvents
        val gapMillis = otaEventDelayMillis
        return flow {
            events.forEach { event ->
                if (gapMillis > 0) delay(gapMillis)
                emit(event)
            }
        }
    }
}
