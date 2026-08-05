package com.momenest.envmonitor.feature.ota

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.protocol.OtaEvent
import com.momenest.envmonitor.protocol.OtaFailure
import com.momenest.envmonitor.testing.FakeEnvMonitorClient
import com.momenest.envmonitor.testing.MainDispatcherRule
import com.momenest.envmonitor.testing.sampleDeviceInfo
import com.momenest.envmonitor.testing.sampleFirmware
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class OtaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val client = FakeEnvMonitorClient()

    /** FirmwareReader 是 fun interface，測試直接用 lambda 當替身即可 */
    private var readResult: SelectedFirmware? =
        SelectedFirmware("app.bin", 4096, sampleFirmware(4096))
    private var lastRequestedUri: String? = null

    private fun viewModel() = OtaViewModel(
        client = client,
        firmwareReader = { uri ->
            lastRequestedUri = uri
            readResult
        },
    )

    /** 讓設備處於「已連線且支援 OTA」的正常狀態 */
    private fun deviceReady() {
        client.connectionState.value = ConnectionState.Connected("env-monitor", "AA:BB")
        client.otaSupported.value = true
        client.deviceInfo.value = sampleDeviceInfo(firmwareVersion = "1.1.0")
    }

    @Test
    fun `初始狀態不能開始更新`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertThat(state.canStart).isFalse()
            assertThat(state.phase).isEqualTo(OtaPhase.IDLE)
            assertThat(state.firmware).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `設備連線資訊會同步進畫面狀態`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            deviceReady()

            val state = expectMostRecentItem()
            assertThat(state.connected).isTrue()
            assertThat(state.otaSupported).isTrue()
            assertThat(state.currentVersion).isEqualTo("1.1.0")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `未連線時選了檔案仍不能開始`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onFirmwarePicked("content://firmware")

            val state = expectMostRecentItem()
            assertThat(state.firmware).isNotNull()
            assertThat(state.canStart).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `設備不支援 OTA 時不能開始`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            client.connectionState.value = ConnectionState.Connected("env-monitor", "AA:BB")
            client.otaSupported.value = false
            vm.onFirmwarePicked("content://firmware")

            assertThat(expectMostRecentItem().canStart).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `連線且選好檔案後就能開始`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://firmware")

            val state = expectMostRecentItem()
            assertThat(state.canStart).isTrue()
            assertThat(state.message).contains("app.bin")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `選檔時會把系統給的位置原樣交給讀取器`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onFirmwarePicked("content://com.android.providers/document/1234")
            expectMostRecentItem()

            assertThat(lastRequestedUri).isEqualTo("content://com.android.providers/document/1234")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `檔案讀不到時給明確錯誤且不能開始`() = runTest {
        readResult = null
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://broken")

            val state = expectMostRecentItem()
            assertThat(state.phase).isEqualTo(OtaPhase.FAILED)
            assertThat(state.firmware).isNull()
            assertThat(state.canStart).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `成功流程會走完並把韌體交給 client`() = runTest {
        client.otaEvents = listOf(
            OtaEvent.Started,
            OtaEvent.Sending(50, 2048, 4096),
            OtaEvent.Verifying,
            OtaEvent.Completed(confirmedByDevice = true),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://firmware")
            expectMostRecentItem()

            vm.startUpdate()

            val state = expectMostRecentItem()
            assertThat(state.phase).isEqualTo(OtaPhase.SUCCESS)
            assertThat(state.percent).isEqualTo(100)
            assertThat(client.lastUploadedFirmware).isEqualTo(sampleFirmware(4096))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `失敗流程會顯示錯誤且可以重試`() = runTest {
        client.otaEvents = listOf(
            OtaEvent.Started,
            OtaEvent.Failed(OtaFailure.DEVICE_ERROR, "CRC 校驗不符"),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://firmware")
            expectMostRecentItem()

            vm.startUpdate()

            val state = expectMostRecentItem()
            assertThat(state.phase).isEqualTo(OtaPhase.FAILED)
            assertThat(state.message).contains("CRC 校驗不符")
            assertThat(state.canStart).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `未選檔就按開始不會呼叫 client`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            deviceReady()

            vm.startUpdate()

            assertThat(client.lastUploadedFirmware).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `傳輸中重複按開始不會啟動第二條傳輸`() = runTest {
        // 兩個傳輸同時灌 flash 一定會把韌體寫壞，這是必須擋住的
        client.otaEvents = listOf(OtaEvent.Started, OtaEvent.Sending(10, 400, 4096))
        client.otaEventDelayMillis = 1_000
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://firmware")
            expectMostRecentItem()

            vm.startUpdate()
            vm.startUpdate()
            vm.startUpdate()

            // 三次呼叫只應該有一次真的送出韌體
            assertThat(client.uploadCallCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `取消更新後回到閒置狀態`() = runTest {
        client.otaEvents = listOf(OtaEvent.Started, OtaEvent.Sending(10, 400, 4096))
        client.otaEventDelayMillis = 1_000
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://firmware")
            expectMostRecentItem()

            vm.startUpdate()
            vm.cancelUpdate()

            val state = expectMostRecentItem()
            assertThat(state.phase).isEqualTo(OtaPhase.IDLE)
            assertThat(state.percent).isEqualTo(0)
            assertThat(state.message).contains("不受影響")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `取消之後可以重新開始`() = runTest {
        client.otaEvents = listOf(OtaEvent.Started)
        client.otaEventDelayMillis = 1_000
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://firmware")
            expectMostRecentItem()

            vm.startUpdate()
            vm.cancelUpdate()

            assertThat(expectMostRecentItem().canStart).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset 會清掉已選檔案`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://firmware")
            expectMostRecentItem()

            vm.reset()

            val state = expectMostRecentItem()
            assertThat(state.firmware).isNull()
            assertThat(state.phase).isEqualTo(OtaPhase.IDLE)
            assertThat(state.message).isEmpty()
            assertThat(state.canStart).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `傳輸中斷線後就不能再開始`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            deviceReady()
            vm.onFirmwarePicked("content://firmware")
            assertThat(expectMostRecentItem().canStart).isTrue()

            client.connectionState.value = ConnectionState.Disconnected

            assertThat(expectMostRecentItem().canStart).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
