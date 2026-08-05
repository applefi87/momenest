package com.momenest.envmonitor.feature.ota

import com.google.common.truth.Truth.assertThat
import com.momenest.envmonitor.protocol.OtaEvent
import com.momenest.envmonitor.protocol.OtaFailure
import org.junit.Test

class OtaMessagesTest {

    private val base = OtaUiState(
        connected = true,
        otaSupported = true,
        firmware = SelectedFirmware("app.bin", 1024, ByteArray(1024)),
    ).recalculateCanStart()

    @Test
    fun `開始傳輸後進入上傳階段`() {
        val state = OtaMessages.reduce(base, OtaEvent.Started)
        assertThat(state.phase).isEqualTo(OtaPhase.UPLOADING)
        assertThat(state.percent).isEqualTo(0)
        assertThat(state.message).contains("請勿鎖屏")
    }

    @Test
    fun `傳輸中的進度會反映在狀態上`() {
        val state = OtaMessages.reduce(base, OtaEvent.Sending(42, 430, 1024))
        assertThat(state.phase).isEqualTo(OtaPhase.UPLOADING)
        assertThat(state.percent).isEqualTo(42)
        assertThat(state.message).contains("42%")
    }

    @Test
    fun `驗證中固定顯示 100 趴`() {
        // 位元組已經全部送出，進度條停在 100 等設備驗證，不要退回去
        val state = OtaMessages.reduce(base, OtaEvent.Verifying)
        assertThat(state.phase).isEqualTo(OtaPhase.VERIFYING)
        assertThat(state.percent).isEqualTo(100)
    }

    @Test
    fun `設備確認完成時訊息肯定`() {
        val state = OtaMessages.reduce(base, OtaEvent.Completed(confirmedByDevice = true))
        assertThat(state.phase).isEqualTo(OtaPhase.SUCCESS)
        assertThat(state.percent).isEqualTo(100)
        assertThat(state.message).contains("更新完成")
    }

    @Test
    fun `未取得設備確認時要提示自行確認版本`() {
        // 不對使用者謊稱「已完成」——設備多半已重開機，但沒收到回報就是沒收到
        val state = OtaMessages.reduce(base, OtaEvent.Completed(confirmedByDevice = false))
        assertThat(state.phase).isEqualTo(OtaPhase.SUCCESS)
        assertThat(state.message).contains("重新連線")
    }

    @Test
    fun `失敗訊息一定要說明舊韌體不受影響`() {
        // 使用者最怕的是「我把設備刷壞了」，這句話必須出現在每一種失敗上
        OtaFailure.entries.forEach { reason ->
            val state = OtaMessages.reduce(base, OtaEvent.Failed(reason))
            assertThat(state.phase).isEqualTo(OtaPhase.FAILED)
            assertThat(state.message).contains("舊韌體不受影響")
        }
    }

    @Test
    fun `每種失敗原因都有專屬的繁中說明`() {
        val messages = OtaFailure.entries.map {
            OtaMessages.reduce(base, OtaEvent.Failed(it)).message
        }
        assertThat(messages.toSet()).hasSize(OtaFailure.entries.size)
    }

    @Test
    fun `失敗時附帶的細節會顯示出來`() {
        val state = OtaMessages.reduce(
            base,
            OtaEvent.Failed(OtaFailure.DEVICE_ERROR, "CRC 校驗不符（傳輸過程資料損毀）"),
        )
        assertThat(state.message).contains("CRC 校驗不符")
    }

    @Test
    fun `傳輸中不能再按開始更新`() {
        // 兩個傳輸同時灌 flash 一定會把韌體寫壞，canStart 必須跟著階段連動
        assertThat(OtaMessages.reduce(base, OtaEvent.Started).canStart).isFalse()
        assertThat(OtaMessages.reduce(base, OtaEvent.Verifying).canStart).isFalse()
    }

    @Test
    fun `失敗之後可以直接重試`() {
        val state = OtaMessages.reduce(base, OtaEvent.Failed(OtaFailure.TRANSPORT_ERROR))
        assertThat(state.canStart).isTrue()
    }

    @Test
    fun `斷線狀態下即使流程結束也不能開始`() {
        val disconnected = base.copy(connected = false).recalculateCanStart()
        assertThat(OtaMessages.reduce(disconnected, OtaEvent.Failed(OtaFailure.ABORTED)).canStart)
            .isFalse()
    }

    @Test
    fun `位元組數會轉成人看得懂的單位`() {
        assertThat(OtaMessages.humanSize(0)).isEqualTo("0 B")
        assertThat(OtaMessages.humanSize(512)).isEqualTo("512 B")
        assertThat(OtaMessages.humanSize(1023)).isEqualTo("1023 B")
        assertThat(OtaMessages.humanSize(1024)).isEqualTo("1.0 KB")
        assertThat(OtaMessages.humanSize(12_595)).isEqualTo("12.3 KB")
        assertThat(OtaMessages.humanSize(1024L * 1024)).isEqualTo("1.0 MB")
        assertThat(OtaMessages.humanSize(1_258_291)).isEqualTo("1.2 MB")
    }

    @Test
    fun `負數大小不會印出奇怪的東西`() {
        assertThat(OtaMessages.humanSize(-1)).isEqualTo("0 B")
    }

    @Test
    fun `小數點一律用點而不是逗號`() {
        // 部分地區的預設 locale 會把小數點印成逗號，看起來像壞掉
        assertThat(OtaMessages.humanSize(1536)).contains(".")
        assertThat(OtaMessages.humanSize(1536)).doesNotContain(",")
    }
}
