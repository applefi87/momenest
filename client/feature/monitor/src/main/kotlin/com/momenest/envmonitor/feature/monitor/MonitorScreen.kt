// MonitorScreen.kt — 即時讀值畫面（無狀態）。
//
// 刻意做成無狀態（只吃 state 與 callback）：UI 測試可以直接餵各種狀態進來驗證
// 排版與互動，不需要 Hilt、不需要 ViewModel、更不需要真的藍牙設備。
// 接線的工作全部留給 MonitorRoute。
package com.momenest.envmonitor.feature.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.momenest.envmonitor.designsystem.ReadingCard
import com.momenest.envmonitor.designsystem.SectionCard
import com.momenest.envmonitor.designsystem.StatusDot

/** 一列兩張卡，與網頁版 `.cards{grid-template-columns:repeat(2,1fr)}` 一致 */
private const val CARDS_PER_ROW = 2

@Composable
internal fun MonitorScreen(
    state: MonitorUiState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onOtaClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Header(state)

        Spacer(Modifier.height(20.dp))

        ConnectButton(state, onConnectClick, onDisconnectClick)

        Spacer(Modifier.height(20.dp))

        ReadingGrid(state)

        if (state.otaSupported) {
            Spacer(Modifier.height(24.dp))
            OtaEntry(state, onOtaClick)
        }

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(16.dp))
            ErrorBanner(message, onDismissError)
        }
    }
}

@Composable
private fun Header(state: MonitorUiState) {
    Column {
        Text(
            text = "環境監測 · 藍牙",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 三態小圓點：連上綠、失敗紅、其餘灰（掃描中也算「還不知道」）
            StatusDot(
                connected = when {
                    state.isConnected -> true
                    state.connection is com.momenest.envmonitor.ble.ConnectionState.Failed -> false
                    else -> null
                },
            )
            Text(
                text = state.statusLine,
                modifier = Modifier
                    .testTag("status_line")
                    .padding(start = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectButton(
    state: MonitorUiState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
) {
    val label = when {
        state.isConnected -> "中斷連接"
        state.isBusy -> "搜尋中…"
        else -> "連接設備"
    }

    if (state.isConnected) {
        // 已連線用次要樣式：中斷不是主要動作，不該搶視覺焦點
        OutlinedButton(
            onClick = onDisconnectClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connect_button"),
        ) { Text(label) }
    } else {
        Button(
            onClick = onConnectClick,
            // 掃描 / 連線進行中就鎖住，避免連點觸發第二次掃描互相搶 GATT
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connect_button"),
        ) { Text(label) }
    }
}

/**
 * 兩欄卡片。
 *
 * 用 chunked 手動排而不是 LazyVerticalGrid：只有固定五張卡，
 * 而且外層已經是可捲動的 Column，巢狀 lazy 容器反而會有量測衝突。
 * 奇數時最後一張佔滿整列，與網頁版 `.card:last-child:nth-child(odd)` 一致。
 */
@Composable
private fun ReadingGrid(state: MonitorUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.tiles.chunked(CARDS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    ReadingCard(
                        label = tile.label,
                        value = tile.value,
                        unit = tile.unit,
                        accentColor = Color(tile.accentArgb),
                        modifier = Modifier.weight(1f),
                    )
                }
                // 補一個等寬的空位，讓落單的卡片不會被撐成整列寬
                if (row.size < CARDS_PER_ROW) {
                    Spacer(Modifier.weight((CARDS_PER_ROW - row.size).toFloat()))
                }
            }
        }
    }
}

@Composable
private fun OtaEntry(state: MonitorUiState, onOtaClick: () -> Unit) {
    SectionCard(title = "韌體更新") {
        Text(
            text = state.firmwareVersion?.let { "目前版本 $it" } ?: "版本未知（韌體較舊）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOtaClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ota_entry"),
        ) { Text("透過藍牙更新韌體") }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    SectionCard(title = "發生問題") {
        Text(
            text = message,
            modifier = Modifier.testTag("error_message"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onDismiss) { Text("知道了") }
    }
}
