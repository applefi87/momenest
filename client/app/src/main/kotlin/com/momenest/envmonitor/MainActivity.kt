// MainActivity.kt — 唯一的 Activity，負責掛上主題、權限閘門與導覽圖。
//
// 全 App 只有一個 Activity（single-activity 架構）：畫面切換交給
// Compose Navigation，Activity 就不必處理 fragment/backstack 的生命週期細節。
// 這個檔案不放任何業務邏輯，只做組裝。

package com.momenest.envmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.momenest.envmonitor.designsystem.EnvMonitorTheme
import com.momenest.envmonitor.navigation.EnvMonitorNavHost
import com.momenest.envmonitor.permission.BluetoothPermissionGate
import dagger.hilt.android.AndroidEntryPoint

/**
 * App 的唯一 Activity。
 *
 * `@AndroidEntryPoint` 讓底下的 `hiltViewModel()` 能取得 Hilt 注入的 ViewModel；
 * 少了它，feature 模組的畫面會在建立 ViewModel 時擲出例外。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnvMonitorTheme {
                EnvMonitorApp()
            }
        }
    }
}

/**
 * App 的根 composable：權限閘門包住導覽圖。
 *
 * 權限閘門放在導覽之外而不是每個畫面各自檢查，是因為兩個畫面都必須有
 * 藍牙權限才有意義；集中在一處也不會出現「A 畫面檢查了、B 畫面忘了」的漏洞。
 */
@Composable
internal fun EnvMonitorApp() {
    Surface(
        // targetSdk 35 起系統強制 edge-to-edge，內容會被畫到狀態列/導覽列底下。
        // 這裡統一補 safeDrawing inset，各 feature 就不必各自處理系統列。
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BluetoothPermissionGate {
            EnvMonitorNavHost()
        }
    }
}
