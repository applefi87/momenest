/*
 * OtaScreenTest.kt — 韌體更新畫面的 UI 測試（需要實機或模擬器）。
 *
 * 因為 OtaScreen 是無狀態的，可以直接把「傳輸到 42%」「已失敗」這種中間狀態
 * 餵進來驗證，不必真的跑一次要好幾分鐘的 OTA。
 *
 * 註：測試方法名刻意不含空白（instrumented 測試要打包成 dex）。
 */
package com.momenest.envmonitor.feature.ota

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OtaScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val firmware = SelectedFirmware("app.bin", 1_258_291, ByteArray(16))

    private fun setScreen(
        state: OtaUiState,
        onPick: () -> Unit = {},
        onStart: () -> Unit = {},
        onCancel: () -> Unit = {},
        onReset: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            OtaScreen(
                state = state,
                onPickClick = onPick,
                onStartClick = onStart,
                onCancelClick = onCancel,
                onResetClick = onReset,
                onBack = onBack,
            )
        }
    }

    private fun ready(
        phase: OtaPhase = OtaPhase.IDLE,
        percent: Int = 0,
        message: String = "",
        withFirmware: Boolean = true,
    ) = OtaUiState(
        connected = true,
        otaSupported = true,
        currentVersion = "1.1.0",
        firmware = if (withFirmware) firmware else null,
        phase = phase,
        percent = percent,
        message = message,
    ).recalculateCanStart()

    @Test
    fun 未選檔時開始按鈕不可按() {
        setScreen(ready(withFirmware = false))

        composeRule.onNodeWithTag("start_ota").assertIsNotEnabled()
        composeRule.onNodeWithText("尚未選擇檔案").assertIsDisplayed()
    }

    @Test
    fun 選好檔案且已連線時開始按鈕可按() {
        setScreen(ready())

        composeRule.onNodeWithTag("start_ota").assertIsEnabled()
    }

    @Test
    fun 選好檔案後顯示檔名與大小() {
        setScreen(ready())

        composeRule.onNodeWithTag("firmware_name").assertIsDisplayed()
        composeRule.onNodeWithText("app.bin · 1.2 MB").assertIsDisplayed()
    }

    @Test
    fun 顯示設備目前的韌體版本() {
        setScreen(ready())

        composeRule.onNodeWithText("設備目前版本 1.1.0").assertIsDisplayed()
    }

    @Test
    fun 未連線時顯示提醒且不能開始() {
        setScreen(OtaUiState(connected = false, otaSupported = false).recalculateCanStart())

        composeRule.onNodeWithTag("ota_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("start_ota").assertIsNotEnabled()
    }

    @Test
    fun 傳輸中顯示進度條並鎖住選檔與開始() {
        setScreen(ready(phase = OtaPhase.UPLOADING, percent = 42, message = "傳輸中 42%"))

        composeRule.onNodeWithTag("ota_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("start_ota").assertIsNotEnabled()
        composeRule.onNodeWithTag("pick_firmware").assertIsNotEnabled()
    }

    @Test
    fun 傳輸中才出現取消按鈕() {
        setScreen(ready(phase = OtaPhase.UPLOADING, percent = 10))
        composeRule.onNodeWithTag("cancel_ota").assertIsDisplayed()
    }

    @Test
    fun 閒置時沒有取消按鈕() {
        setScreen(ready())
        composeRule.onNodeWithTag("cancel_ota").assertDoesNotExist()
    }

    @Test
    fun 點取消會觸發取消動作() {
        var cancelled = false
        setScreen(ready(phase = OtaPhase.UPLOADING, percent = 10), onCancel = { cancelled = true })

        composeRule.onNodeWithTag("cancel_ota").performClick()

        assertTrue("取消按鈕沒有觸發 onCancelClick", cancelled)
    }

    @Test
    fun 點開始會觸發更新動作() {
        var started = false
        setScreen(ready(), onStart = { started = true })

        composeRule.onNodeWithTag("start_ota").performClick()

        assertTrue("開始按鈕沒有觸發 onStartClick", started)
    }

    @Test
    fun 點選擇檔案會觸發選檔動作() {
        var picked = false
        setScreen(ready(), onPick = { picked = true })

        composeRule.onNodeWithTag("pick_firmware").performClick()

        assertTrue("選檔按鈕沒有觸發 onPickClick", picked)
    }

    @Test
    fun 失敗訊息會顯示出來() {
        setScreen(
            ready(phase = OtaPhase.FAILED, message = "更新失敗：傳輸中斷。設備上的舊韌體不受影響。"),
        )

        composeRule.onNodeWithTag("ota_message").assertIsDisplayed()
        composeRule.onNodeWithText("更新失敗：傳輸中斷。設備上的舊韌體不受影響。").assertIsDisplayed()
    }

    @Test
    fun 結束後出現重新選擇檔案的入口() {
        setScreen(ready(phase = OtaPhase.SUCCESS, percent = 100, message = "更新完成"))

        composeRule.onNodeWithTag("reset_ota").assertIsDisplayed()
    }

    @Test
    fun 點返回會觸發返回動作() {
        var backPressed = false
        setScreen(ready(), onBack = { backPressed = true })

        composeRule.onNodeWithTag("back").performClick()

        assertTrue("返回按鈕沒有觸發 onBack", backPressed)
    }
}
