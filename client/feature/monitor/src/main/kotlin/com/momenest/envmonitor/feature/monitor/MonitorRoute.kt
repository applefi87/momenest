// MonitorRoute.kt — 把 MonitorViewModel 接到無狀態的 MonitorScreen 上。
//
// 這層刻意只做接線：畫面本身不知道 Hilt 或 ViewModel 的存在，
// 才能在 UI 測試裡被單獨拿出來測。
package com.momenest.envmonitor.feature.monitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MonitorRoute(
    onNavigateToOta: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = hiltViewModel(),
) {
    // collectAsStateWithLifecycle 而非 collectAsState：App 進背景時停止收集，
    // 免得看不見的畫面還在消耗 BLE 推播與重組
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MonitorScreen(
        state = state,
        onConnectClick = viewModel::connect,
        onDisconnectClick = viewModel::disconnect,
        onOtaClick = onNavigateToOta,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}
