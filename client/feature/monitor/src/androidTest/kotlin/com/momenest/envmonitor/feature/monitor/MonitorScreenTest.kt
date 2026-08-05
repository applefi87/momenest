/*
 * MonitorScreenTest.kt — 讀值畫面的 UI 測試（需要實機或模擬器）。
 *
 * 這裡只驗「給定狀態 → 畫面長什麼樣、按鈕接到哪」，換算與文案的正確性
 * 已由 ReadingTileMapperTest / StatusLineFormatterTest 在 JVM 上驗完。
 * 定位一律用 testTag 而非顯示文字，文案改了測試不會跟著碎掉。
 *
 * 註：測試方法名刻意不含空白。instrumented 測試最終要打包成 dex，
 * 含空白的方法名在部分工具鏈上會出問題，與 JVM 單元測試的慣例不同。
 */
package com.momenest.envmonitor.feature.monitor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.momenest.envmonitor.ble.ConnectionState
import com.momenest.envmonitor.protocol.Calibration
import com.momenest.envmonitor.protocol.SensorReading
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MonitorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun state(
        connection: ConnectionState = ConnectionState.Disconnected,
        reading: SensorReading? = null,
        otaSupported: Boolean = false,
        errorMessage: String? = null,
    ) = MonitorUiState(
        connection = connection,
        tiles = ReadingTileMapper.toTiles(reading, Calibration()),
        statusLine = StatusLineFormatter.format(connection, null),
        otaSupported = otaSupported,
        errorMessage = errorMessage,
    )

    private fun setScreen(
        uiState: MonitorUiState,
        onConnect: () -> Unit = {},
        onDisconnect: () -> Unit = {},
        onOta: () -> Unit = {},
        onDismissError: () -> Unit = {},
    ) {
        composeRule.setContent {
            MonitorScreen(
                state = uiState,
                onConnectClick = onConnect,
                onDisconnectClick = onDisconnect,
                onOtaClick = onOta,
                onDismissError = onDismissError,
            )
        }
    }

    @Test
    fun 未連線時顯示連接設備按鈕() {
        setScreen(state())

        composeRule.onNodeWithTag("connect_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("連接設備").assertIsDisplayed()
    }

    @Test
    fun 已連線時按鈕變成中斷連接() {
        setScreen(state(ConnectionState.Connected("env-monitor", "AA:BB")))

        composeRule.onNodeWithText("中斷連接").assertIsDisplayed()
    }

    @Test
    fun 掃描中時連線按鈕不可按() {
        // 連點會觸發第二次掃描，兩條流程互相搶 GATT
        setScreen(state(ConnectionState.Scanning))

        composeRule.onNodeWithTag("connect_button").assertIsNotEnabled()
    }

    @Test
    fun 五張讀值卡都會顯示() {
        setScreen(state(reading = SensorReading(airTemp = 24.5f)))

        listOf("氣溫", "水溫", "濕度", "土壤", "水位").forEach { label ->
            composeRule.onNodeWithTag("reading_card_$label").assertIsDisplayed()
        }
    }

    @Test
    fun 沒有讀值時五張卡都顯示佔位符號() {
        // 關鍵：不能顯示成 0，否則使用者會以為真的量到 0
        setScreen(state())

        composeRule.onAllNodesWithText("--").assertCountEquals(5)
    }

    @Test
    fun 支援更新時才出現韌體更新入口() {
        setScreen(state(otaSupported = true))

        composeRule.onNodeWithTag("ota_entry").assertIsDisplayed()
    }

    @Test
    fun 不支援更新時沒有韌體更新入口() {
        setScreen(state(otaSupported = false))

        composeRule.onNodeWithTag("ota_entry").assertDoesNotExist()
    }

    @Test
    fun 點連線按鈕會觸發連線動作() {
        var clicked = false
        setScreen(state(), onConnect = { clicked = true })

        composeRule.onNodeWithTag("connect_button").performClick()

        assertTrue("連線按鈕沒有觸發 onConnectClick", clicked)
    }

    @Test
    fun 已連線時點按鈕觸發的是中斷而不是連線() {
        var connectCalled = false
        var disconnectCalled = false
        setScreen(
            state(ConnectionState.Connected("env-monitor", "AA:BB")),
            onConnect = { connectCalled = true },
            onDisconnect = { disconnectCalled = true },
        )

        composeRule.onNodeWithTag("connect_button").performClick()

        assertTrue("應觸發中斷連線", disconnectCalled)
        assertFalse("不該觸發連線", connectCalled)
    }

    @Test
    fun 點更新入口會觸發導覽動作() {
        var navigated = false
        setScreen(state(otaSupported = true), onOta = { navigated = true })

        composeRule.onNodeWithTag("ota_entry").performClick()

        assertTrue("更新入口沒有觸發導覽", navigated)
    }

    @Test
    fun 有錯誤訊息時顯示錯誤區塊() {
        setScreen(state(errorMessage = "缺少藍牙權限"))

        composeRule.onNodeWithTag("error_message").assertIsDisplayed()
        composeRule.onNodeWithText("缺少藍牙權限").assertIsDisplayed()
    }

    @Test
    fun 沒有錯誤時不顯示錯誤區塊() {
        setScreen(state())

        composeRule.onNodeWithTag("error_message").assertDoesNotExist()
    }

    @Test
    fun 狀態列文字會顯示出來() {
        setScreen(state(ConnectionState.Failed("請先開啟藍牙")))

        composeRule.onNodeWithTag("status_line").assertIsDisplayed()
    }
}
