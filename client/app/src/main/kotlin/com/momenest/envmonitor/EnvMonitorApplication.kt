// EnvMonitorApplication.kt — Hilt 的物件圖進入點。
//
// :app 是純組裝層，這個類別刻意保持空的：任何初始化邏輯（BLE、偏好設定）
// 都該由 Hilt 在真正需要時才建立，放在 Application 裡會拖慢冷啟動，
// 也讓那段邏輯變得無法在單元測試中驗證。

package com.momenest.envmonitor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * App 的 [Application] 實作。
 *
 * `@HiltAndroidApp` 會產生整個 App 的 Hilt 元件（`SingletonComponent`），
 * 各模組的 `@Module` 才有地方被安裝；沒有這個註解，`@AndroidEntryPoint`
 * 的 Activity 會在執行期直接崩潰。
 */
@HiltAndroidApp
class EnvMonitorApplication : Application()
