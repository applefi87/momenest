package com.momenest.envmonitor.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.ble.EnvMonitorClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val client: EnvMonitorClient
) : ViewModel() {

    // 這裡我們先簡單實作，直接將 client 的狀態映射到 UI State
    val uiState: StateFlow<MonitorUiState> = combine(
        client.connectionState,
        client.readings,
        client.deviceInfo
    ) { connection, readings, deviceInfo ->
        MonitorUiState(
            connection = connection,
            tiles = emptyList(), // 這裡之後要根據 readings 轉換 ReadingTile
            statusLine = when (connection) {
                is ConnectionState.Disconnected -> "尚未連接"
                is ConnectionState.Scanning -> "搜尋設備中..."
                is ConnectionState.Connecting -> "建立連線中..."
                is ConnectionState.Connected -> "已連線至 ${connection.deviceName ?: "設備"}"
                is ConnectionState.Failed -> "失敗: ${connection.reason}"
            },
            firmwareVersion = deviceInfo?.firmwareVersion,
            otaSupported = false // 暫時寫死
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonitorUiState()
    )

    fun startConnect() {
        viewModelScope.launch {
            client.connect()
        }
    }

    fun disconnect() {
        client.disconnect()
    }
}
