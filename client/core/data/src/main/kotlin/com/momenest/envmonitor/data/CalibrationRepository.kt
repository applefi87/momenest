// CalibrationRepository.kt — 土壤 / 水位校準值持久化的抽象契約。
//
// 為什麼要介面化：feature 層（ViewModel）只依賴這個介面，測試時就能換上
// :core:testing 的 FakeCalibrationRepository，不必背上 Android Context 與真實磁碟 I/O。

package com.momenest.envmonitor.data

import com.momenest.envmonitor.protocol.Calibration
import kotlinx.coroutines.flow.Flow

/**
 * 土壤 / 水位 ADC 校準值的儲存庫。
 *
 * 校準值的語意（min = 0%、max = 100%）與換算規則都屬於 [Calibration] 與
 * `CalibrationMath`，本介面只負責「存」與「取」，不做任何換算。
 */
interface CalibrationRepository {

    /**
     * 目前的校準值；設定被更動時會再次發射新值。
     *
     * 從未設定過的欄位為 null——顯示端據此改為直接顯示原始 ADC，
     * 這與韌體「讀不到就給 null」的慣例一致，避免用假的預設值誤導使用者。
     */
    val calibration: Flow<Calibration>

    /**
     * 覆寫整組校準值。
     *
     * 欄位為 null 代表「清掉該欄位」而非「維持原值」，
     * 呼叫端要傳完整的一組（讀 → 改 → 寫），以免部分更新讓 min/max 不成對。
     */
    suspend fun update(calibration: Calibration)

    /** 清掉所有校準值，回到未校準狀態（顯示端會退回顯示原始 ADC）。 */
    suspend fun clear()
}
