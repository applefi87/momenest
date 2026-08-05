/**
 * FakeCalibrationRepository.kt — CalibrationRepository 的記憶體版測試替身。
 *
 * 正式實作用 DataStore，需要 Android Context 與檔案系統，在純 JVM 單元測試裡跑不動；
 * 這個 fake 用一條 MutableStateFlow 取代，語意（寫入後訂閱者立刻收到新值）一致。
 *
 * 本檔位於 src/main 而非 src/test，理由見 MainDispatcherRule.kt 檔頭。
 */
package com.momenest.envmonitor.testing

import com.momenest.envmonitor.data.CalibrationRepository
import com.momenest.envmonitor.protocol.Calibration
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 記憶體版 [CalibrationRepository]。
 *
 * 測試端可直接 `repository.calibration.value = Calibration(soilMin = 3000, soilMax = 1200)`
 * 模擬使用者已完成校準的情境。
 *
 * @param initial 初始校準值，預設為全部未設定
 */
class FakeCalibrationRepository(
    initial: Calibration = Calibration(),
) : CalibrationRepository {

    override val calibration: MutableStateFlow<Calibration> = MutableStateFlow(initial)

    override suspend fun update(calibration: Calibration) {
        this.calibration.value = calibration
    }

    override suspend fun clear() {
        // 與 DataStore 版一致：清空等於回到「四個欄位都沒設定」
        calibration.value = Calibration()
    }
}
