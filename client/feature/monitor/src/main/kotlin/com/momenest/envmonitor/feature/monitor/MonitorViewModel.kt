// MonitorViewModel.kt — 即時讀值畫面的狀態來源。
//
// 職責只有兩件事：把 client 與校準設定的資料流合併成一份 UI 狀態、
// 把使用者動作轉成 client 呼叫。所有換算與文案都委派給
// ReadingTileMapper / StatusLineFormatter 這兩個純函式，
// 這裡因此薄到幾乎沒有分支，也就沒有藏 bug 的空間。
package com.momenest.envmonitor.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.ble.EnvMonitorClient
import com.momenest.envmonitor.data.CalibrationRepository
import com.momenest.envmonitor.protocol.DeviceInfo
import com.momenest.envmonitor.protocol.DeviceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI 狀態訂閱者離開多久後才停止上游收集。5 秒是為了讓轉螢幕不會重新連一次 */
private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val client: EnvMonitorClient,
    calibrationRepository: CalibrationRepository,
) : ViewModel() {

    /** 連線流程本身丟出的例外（例如權限被撤銷造成的 SecurityException） */
    private val errorMessage = MutableStateFlow<String?>(null)

    // combine 一次最多吃 5 條流，所以先把設備端的四條併成一包再與其他合併
    private val device = combine(
        client.connectionState,
        client.status,
        client.deviceInfo,
        client.otaSupported,
    ) { connection, status, info, otaSupported ->
        DeviceSnapshot(connection, status, info, otaSupported)
    }

    val uiState: StateFlow<MonitorUiState> = combine(
        device,
        client.readings,
        calibrationRepository.calibration,
        errorMessage,
    ) { snapshot, reading, calibration, error ->
        MonitorUiState(
            connection = snapshot.connection,
            tiles = ReadingTileMapper.toTiles(reading, calibration),
            statusLine = StatusLineFormatter.format(snapshot.connection, snapshot.status),
            firmwareVersion = snapshot.info?.firmwareVersion?.takeIf { it.isNotBlank() },
            otaSupported = snapshot.otaSupported,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        // 初始值就是「還沒連線」的完整樣貌，畫面不會有空白的中間狀態
        initialValue = MonitorUiState(
            tiles = ReadingTileMapper.toTiles(null, com.momenest.envmonitor.protocol.Calibration()),
        ),
    )

    fun connect() {
        errorMessage.value = null
        viewModelScope.launch {
            // client 把預期內的失敗都轉成 ConnectionState.Failed，
            // 這裡的 catch 只是最後一道防線（例如權限在執行中被撤銷）
            runCatching { client.connect() }
                .onFailure { errorMessage.value = it.message ?: "連線發生未預期的錯誤" }
        }
    }

    fun disconnect() {
        client.disconnect()
    }

    fun dismissError() {
        errorMessage.value = null
    }

    private data class DeviceSnapshot(
        val connection: ConnectionState,
        val status: DeviceStatus?,
        val info: DeviceInfo?,
        val otaSupported: Boolean,
    )
}
