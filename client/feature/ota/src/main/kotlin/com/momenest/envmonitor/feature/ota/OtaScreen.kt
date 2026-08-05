// OtaScreen.kt — 韌體更新畫面（無狀態）。
//
// 只吃 state 與 callback，所以 UI 測試可以直接把「傳輸到 42%」這種中間狀態
// 餵進來驗證，不需要真的跑一次 OTA。
package com.momenest.envmonitor.feature.ota

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.momenest.envmonitor.designsystem.OtaProgressBar
import com.momenest.envmonitor.designsystem.SectionCard

@Composable
internal fun OtaScreen(
    state: OtaUiState,
    onPickClick: () -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onResetClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = "韌體更新",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.currentVersion?.let { "設備目前版本 $it" } ?: "設備版本未知（韌體較舊）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        if (!state.connected) {
            Notice("尚未連線到設備。請先回上一頁連線，再回來更新韌體。")
            Spacer(Modifier.height(16.dp))
        } else if (!state.otaSupported) {
            Notice("這台設備的韌體不支援藍牙更新，需要先用 USB 燒錄一次含 OTA 功能的韌體。")
            Spacer(Modifier.height(16.dp))
        }

        SectionCard(title = "韌體檔案") {
            OutlinedButton(
                onClick = onPickClick,
                // 傳輸中換檔會讓進度與內容對不上，直接鎖住
                enabled = !state.isTransferring,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_firmware"),
            ) { Text("選擇 .bin 韌體檔") }

            Spacer(Modifier.height(12.dp))

            Text(
                text = state.firmware
                    ?.let { "${it.fileName} · ${OtaMessages.humanSize(it.sizeBytes)}" }
                    ?: "尚未選擇檔案",
                modifier = Modifier.testTag("firmware_name"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "更新") {
            Button(
                onClick = onStartClick,
                enabled = state.canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_ota"),
            ) { Text("開始更新") }

            if (state.phase != OtaPhase.IDLE) {
                Spacer(Modifier.height(12.dp))
                OtaProgressBar(percent = state.percent)
            }

            if (state.message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.message,
                    modifier = Modifier.testTag("ota_message"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state.phase) {
                        OtaPhase.FAILED -> MaterialTheme.colorScheme.error
                        OtaPhase.SUCCESS -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (state.isTransferring) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onCancelClick,
                    modifier = Modifier.testTag("cancel_ota"),
                ) { Text("取消更新") }
            }

            if (state.phase == OtaPhase.SUCCESS || state.phase == OtaPhase.FAILED) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onResetClick,
                    modifier = Modifier.testTag("reset_ota"),
                ) { Text("重新選擇檔案") }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "說明：.bin 需在電腦用 Arduino IDE 的「Sketch → Export Compiled Binary」" +
                "產生後傳到手機。藍牙傳輸較慢，1MB 韌體約需數分鐘，過程中請勿鎖屏或離開。" +
                "更新失敗不會影響設備上正在運作的舊韌體。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onBack, modifier = Modifier.testTag("back")) { Text("返回") }
    }
}

@Composable
private fun Notice(text: String) {
    SectionCard(title = "注意") {
        Text(
            text = text,
            modifier = Modifier.testTag("ota_notice"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
