package com.momenest.envmonitor.feature.monitor

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.protocol.Calibration
import com.momenest.envmonitor.protocol.SensorReading
import com.momenest.envmonitor.testing.FakeCalibrationRepository
import com.momenest.envmonitor.testing.FakeEnvMonitorClient
import com.momenest.envmonitor.testing.MainDispatcherRule
import com.momenest.envmonitor.testing.sampleDeviceInfo
import com.momenest.envmonitor.testing.sampleStatus
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MonitorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val client = FakeEnvMonitorClient()
    private val calibration = FakeCalibrationRepository()

    private fun viewModel() = MonitorViewModel(client, calibration)

    @Test
    fun `初始狀態是未連線且已有五張佔位卡`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertThat(state.connection).isEqualTo(ConnectionState.Disconnected)
            assertThat(state.tiles).hasSize(5)
            assertThat(state.statusLine).isEqualTo("未連接")
            assertThat(state.otaSupported).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `設備推來新讀值時卡片跟著更新`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()   // 初始值

            client.readings.value = SensorReading(airTemp = 26.4f)

            val updated = awaitItem()
            assertThat(updated.tiles.first { it.key == "air_temp" }.value).isEqualTo("26.4")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `校準值改變時百分比立刻重算`() = runTest {
        client.readings.value = SensorReading(soilRaw = 2000)
        val vm = viewModel()

        vm.uiState.test {
            val before = awaitItem()
            assertThat(before.tiles.first { it.key == "soil" }.value).isEqualTo("2000")

            calibration.calibration.value = Calibration(soilMin = 1000, soilMax = 3000)

            val after = awaitItem()
            assertThat(after.tiles.first { it.key == "soil" }.value).isEqualTo("50")
            assertThat(after.tiles.first { it.key == "soil" }.unit).isEqualTo("%")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `連上並收到狀態後狀態列包含 WiFi 與 IP`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()

            client.connectionState.value = ConnectionState.Connected("env-monitor", "AA:BB")
            client.status.value = sampleStatus(wifiConnected = true, ipAddress = "192.168.31.158")

            val state = expectMostRecentItem()
            assertThat(state.statusLine).contains("WiFi 正常")
            assertThat(state.statusLine).contains("192.168.31.158")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `韌體版本來自設備資訊`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            client.deviceInfo.value = sampleDeviceInfo(firmwareVersion = "1.1.0")

            assertThat(expectMostRecentItem().firmwareVersion).isEqualTo("1.1.0")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `舊韌體沒有版本資訊時顯示為 null 而不是空字串`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            client.deviceInfo.value = sampleDeviceInfo(firmwareVersion = "")

            assertThat(expectMostRecentItem().firmwareVersion).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `設備支援 OTA 時才開放更新入口`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertThat(awaitItem().otaSupported).isFalse()

            client.otaSupported.value = true

            assertThat(expectMostRecentItem().otaSupported).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connect 會呼叫到 client`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.connect()
            assertThat(client.connectCallCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connect 擲出例外時轉成畫面上的錯誤訊息`() = runTest {
        client.connectError = SecurityException("缺少藍牙權限")
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.connect()

            assertThat(expectMostRecentItem().errorMessage).isEqualTo("缺少藍牙權限")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissError 會清掉錯誤訊息`() = runTest {
        client.connectError = IllegalStateException("壞掉了")
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.connect()
            assertThat(expectMostRecentItem().errorMessage).isNotNull()

            vm.dismissError()

            assertThat(expectMostRecentItem().errorMessage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnect 會呼叫到 client`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.disconnect()
            assertThat(client.disconnectCallCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `重新連線前會先清掉上一次的錯誤訊息`() = runTest {
        client.connectError = IllegalStateException("第一次失敗")
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.connect()
            assertThat(expectMostRecentItem().errorMessage).isNotNull()

            client.connectError = null
            vm.connect()

            assertThat(expectMostRecentItem().errorMessage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
