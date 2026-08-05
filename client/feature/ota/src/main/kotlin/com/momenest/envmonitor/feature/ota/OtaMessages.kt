// OtaMessages.kt — 把 OtaEvent 折成 UI 狀態，以及數字的人話化。
//
// 做成純函式的 reducer：整個更新流程的狀態轉移就集中在這一個 when 裡，
// 每一條分支都能用 JVM 測試釘死。ViewModel 因此只剩「收事件、丟給 reduce」。
package com.momenest.envmonitor.feature.ota

import com.momenest.envmonitor.protocol.OtaEvent
import com.momenest.envmonitor.protocol.OtaFailure
import java.util.Locale

private const val BYTES_PER_KB = 1024.0
private const val BYTES_PER_MB = BYTES_PER_KB * 1024.0

object OtaMessages {

    fun reduce(state: OtaUiState, event: OtaEvent): OtaUiState = when (event) {
        OtaEvent.Started -> state.copy(
            phase = OtaPhase.UPLOADING,
            percent = 0,
            message = "已開始傳輸，過程中請勿鎖屏或離開此畫面",
        )

        is OtaEvent.Sending -> state.copy(
            phase = OtaPhase.UPLOADING,
            percent = event.percent,
            message = "傳輸中 ${event.percent}%" +
                "（${humanSize(event.bytesSent)} / ${humanSize(event.totalBytes)}）",
        )

        OtaEvent.Verifying -> state.copy(
            phase = OtaPhase.VERIFYING,
            percent = 100,
            message = "傳輸完成，設備驗證中…",
        )

        is OtaEvent.Completed -> state.copy(
            phase = OtaPhase.SUCCESS,
            percent = 100,
            // 沒收到設備確認時不謊稱「已完成」——設備多半已重開機，但要讓使用者自己確認
            message = if (event.confirmedByDevice) {
                "更新完成，設備正在重新啟動"
            } else {
                "已送出更新，設備應已重新啟動（重新連線可確認版本）"
            },
        )

        is OtaEvent.Failed -> state.copy(
            phase = OtaPhase.FAILED,
            message = buildString {
                append("更新失敗：")
                append(describe(event.reason))
                event.detail?.takeIf { it.isNotBlank() }?.let { append("（$it）") }
                // 這句很重要：使用者最怕的是「設備被我刷壞了」
                append("。設備上的舊韌體不受影響，可以直接重試。")
            },
        )
    }.recalculateCanStart()

    private fun describe(reason: OtaFailure): String = when (reason) {
        OtaFailure.EMPTY_FIRMWARE -> "選到的檔案是空的"
        OtaFailure.BEGIN_REJECTED -> "設備拒絕開始更新"
        OtaFailure.TRANSPORT_ERROR -> "傳輸中斷"
        OtaFailure.DEVICE_ERROR -> "設備回報錯誤"
        OtaFailure.ABORTED -> "更新已中止"
    }

    /**
     * 位元組數 → 人話。
     *
     * 指定 [Locale.US]：部分地區的預設 locale 會把小數點印成逗號。
     */
    fun humanSize(bytes: Long): String = when {
        bytes < 0 -> "0 B"
        bytes < BYTES_PER_KB -> "$bytes B"
        bytes < BYTES_PER_MB -> String.format(Locale.US, "%.1f KB", bytes / BYTES_PER_KB)
        else -> String.format(Locale.US, "%.1f MB", bytes / BYTES_PER_MB)
    }
}
