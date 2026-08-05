// OtaRoute.kt — 把 OtaViewModel、系統選檔器、螢幕常亮接到無狀態的 OtaScreen 上。
package com.momenest.envmonitor.feature.ota

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * `.bin` 沒有標準 MIME type，各家檔案管理員回報的也不一致
 * （application/octet-stream、application/macbinary…），
 * 過濾太嚴會讓使用者在選檔器裡看到自己的韌體是灰色的，所以開放全部類型。
 */
private val FIRMWARE_MIME_TYPES = arrayOf("*/*")

@Composable
fun OtaRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OtaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // 使用者按取消時 uri 是 null，維持原狀就好
        uri?.let { viewModel.onFirmwarePicked(it.toString()) }
    }

    // BLE 傳 1MB 要好幾分鐘，螢幕一熄常常伴隨系統降低 BLE 排程優先權甚至斷線，
    // 傳輸期間強制常亮；離開畫面或傳完就還原，不要一直吃電
    val view = LocalView.current
    DisposableEffect(state.isTransferring) {
        view.keepScreenOn = state.isTransferring
        onDispose { view.keepScreenOn = false }
    }

    OtaScreen(
        state = state,
        onPickClick = { picker.launch(FIRMWARE_MIME_TYPES) },
        onStartClick = viewModel::startUpdate,
        onCancelClick = viewModel::cancelUpdate,
        onResetClick = viewModel::reset,
        onBack = onBack,
        modifier = modifier,
    )
}
