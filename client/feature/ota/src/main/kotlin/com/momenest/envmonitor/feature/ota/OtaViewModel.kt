// OtaViewModel.kt — 韌體更新畫面的狀態來源。
//
// 這層很薄，是刻意的：傳輸邏輯在 :core:protocol 的 OtaUploader（已完整測試），
// 狀態轉移在 OtaMessages.reduce（純函式），檔案讀取在 FirmwareReader（可替換）。
// 這裡只負責把三者接起來，並管好「同一時間只能有一個傳輸任務」。
package com.momenest.envmonitor.feature.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.ble.EnvMonitorClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OtaViewModel @Inject constructor(
    private val client: EnvMonitorClient,
    private val firmwareReader: FirmwareReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtaUiState())
    val uiState: StateFlow<OtaUiState> = _uiState.asStateFlow()

    /** 目前的傳輸任務。留著才能取消，也用來擋住重複啟動 */
    private var uploadJob: Job? = null

    init {
        // 設備端狀態（連線、是否支援 OTA、韌體版本）是外來的，持續同步進 UI 狀態；
        // 傳輸過程中若斷線，canStart 會自動變 false，使用者按不到「開始」
        viewModelScope.launch {
            combine(
                client.connectionState,
                client.otaSupported,
                client.deviceInfo,
            ) { connection, otaSupported, info ->
                Triple(
                    connection is ConnectionState.Connected,
                    otaSupported,
                    info?.firmwareVersion?.takeIf { it.isNotBlank() },
                )
            }.collect { (connected, otaSupported, version) ->
                _uiState.update {
                    it.copy(
                        connected = connected,
                        otaSupported = otaSupported,
                        currentVersion = version,
                    ).recalculateCanStart()
                }
            }
        }
    }

    /** @param uriString 系統選檔器回傳的 Uri 字串 */
    fun onFirmwarePicked(uriString: String) {
        viewModelScope.launch {
            val firmware = firmwareReader.read(uriString)
            _uiState.update { state ->
                if (firmware == null) {
                    state.copy(
                        firmware = null,
                        phase = OtaPhase.FAILED,
                        percent = 0,
                        message = "無法讀取這個檔案（可能是空檔、超過 " +
                            "${OtaMessages.humanSize(MAX_FIRMWARE_BYTES)}、或沒有讀取權限）",
                    ).recalculateCanStart()
                } else {
                    state.copy(
                        firmware = firmware,
                        phase = OtaPhase.IDLE,
                        percent = 0,
                        message = "已選擇 ${firmware.fileName}" +
                            "（${OtaMessages.humanSize(firmware.sizeBytes)}）",
                    ).recalculateCanStart()
                }
            }
        }
    }

    fun startUpdate() {
        // 傳輸中重複按不能開第二條：兩個傳輸同時灌 flash 一定會把韌體寫壞
        if (uploadJob?.isActive == true) return

        val state = _uiState.value
        val firmware = state.firmware ?: return
        if (!state.canStart) return

        uploadJob = viewModelScope.launch {
            client.uploadFirmware(firmware.bytes).collect { event ->
                _uiState.update { OtaMessages.reduce(it, event) }
            }
        }
    }

    /**
     * 取消傳輸。
     *
     * 取消 collect 會讓 OtaUploader 的 finally 補送 ABORT，設備因此會
     * Update.abort() 並保留舊韌體——所以這個動作是安全的。
     */
    fun cancelUpdate() {
        uploadJob?.cancel()
        uploadJob = null
        _uiState.update {
            it.copy(
                phase = OtaPhase.IDLE,
                percent = 0,
                message = "已取消更新，設備上的韌體不受影響",
            ).recalculateCanStart()
        }
    }

    /** 回到可以重新選檔的乾淨狀態（成功或失敗後給使用者一個「再來一次」的入口） */
    fun reset() {
        uploadJob?.cancel()
        uploadJob = null
        _uiState.update {
            it.copy(
                firmware = null,
                phase = OtaPhase.IDLE,
                percent = 0,
                message = "",
            ).recalculateCanStart()
        }
    }
}
