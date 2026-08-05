// DataStoreCalibrationRepository.kt — CalibrationRepository 的 DataStore Preferences 實作。
//
// 為什麼用 DataStore 而不是 SharedPreferences：
//   1. SharedPreferences 的 commit() 會阻塞呼叫端（常常就是主執行緒），apply() 則是在背景
//      默默寫入，失敗時呼叫端完全不會知道。DataStore 全程 suspend，寫入失敗會以例外浮出來。
//   2. DataStore 的讀取天生就是 Flow，能直接接進 ViewModel 的 combine / stateIn 資料流；
//      SharedPreferences 得自己包一層 OnSharedPreferenceChangeListener 再轉 Flow，
//      還要記得反註冊，是常見的洩漏來源。
//
// 測試取捨：本模組刻意不寫單元測試。preferencesDataStore 需要真的 Context 與檔案系統，
// 在 JVM 單元測試裡驗到的其實是 DataStore 自身的行為，而不是本模組的邏輯（本模組除了
// key 對應之外沒有分支邏輯），成本高而價值低。依賴這層的 feature 模組改用
// :core:testing 的 FakeCalibrationRepository 來測。

package com.momenest.envmonitor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.momenest.envmonitor.protocol.Calibration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/** DataStore 檔名；同一程序內只能有一個實例指向這個名字。 */
private const val DATA_STORE_NAME = "calibration"

// 用 Context 的擴充屬性委派建立，是官方建議的作法：實例綁在 Context 上且只會初始化一次。
// 若改成在類別內 new 一個 DataStore，Hilt 之外的地方（例如測試或第二個 Repository）
// 再建一次就會因「同一檔案被開兩次」直接擲例外。
private val Context.calibrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATA_STORE_NAME,
)

// key 名稱與規格一致，也刻意用 snake_case 對齊韌體 / 雲端 JSON 的欄位命名習慣。
private val KEY_SOIL_MIN = intPreferencesKey("soil_min")
private val KEY_SOIL_MAX = intPreferencesKey("soil_max")
private val KEY_WATER_MIN = intPreferencesKey("water_min")
private val KEY_WATER_MAX = intPreferencesKey("water_max")

/**
 * 以 DataStore Preferences（檔名 `calibration`）保存校準值的實作。
 *
 * @param context application context；由 Hilt 注入，不會抓到 Activity 而造成洩漏。
 */
class DataStoreCalibrationRepository @Inject constructor(
    @ApplicationContext context: Context,
) : CalibrationRepository {

    private val dataStore = context.calibrationDataStore

    override val calibration: Flow<Calibration> = dataStore.data
        .catch { cause ->
            // 磁碟讀取失敗（檔案損毀、儲存空間異常、權限被撤）若讓例外往上竄，
            // 收這條 Flow 的整個監測畫面會跟著崩潰。校準值只是把 ADC 換算成百分比的
            // 加分項，讀不到時降級成「沒有校準值」，UI 自然會退回顯示原始 ADC，
            // 比整頁掛掉合理得多。非 IOException 屬於程式錯誤，照樣往上拋不吞。
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { preferences -> preferences.toCalibration() }

    /**
     * 覆寫整組校準值；null 欄位會被刪除。
     *
     * @throws IOException 寫入失敗（磁碟已滿、權限問題）。這裡不吞掉，
     *   讓呼叫端能決定要不要提示使用者重試。
     */
    override suspend fun update(calibration: Calibration) {
        dataStore.edit { preferences ->
            // null 一律 remove 而不是略過：只寫不刪會讓舊值殘留，
            // 使用者清掉校準後百分比仍照舊值換算，看起來像是設定沒生效。
            preferences.setOrRemove(KEY_SOIL_MIN, calibration.soilMin)
            preferences.setOrRemove(KEY_SOIL_MAX, calibration.soilMax)
            preferences.setOrRemove(KEY_WATER_MIN, calibration.waterMin)
            preferences.setOrRemove(KEY_WATER_MAX, calibration.waterMax)
        }
    }

    /** @throws IOException 寫入失敗，理由同 [update]。 */
    override suspend fun clear() {
        dataStore.edit { preferences ->
            // 只刪自己的 key，不用 preferences.clear()：這個檔案日後若加進別的偏好設定，
            // 整份清空會連帶誤刪。
            preferences.remove(KEY_SOIL_MIN)
            preferences.remove(KEY_SOIL_MAX)
            preferences.remove(KEY_WATER_MIN)
            preferences.remove(KEY_WATER_MAX)
        }
    }
}

/** 缺 key 時 `Preferences[key]` 就是 null，正好等於「未設定」語意，不必再補預設值。 */
private fun Preferences.toCalibration(): Calibration = Calibration(
    soilMin = this[KEY_SOIL_MIN],
    soilMax = this[KEY_SOIL_MAX],
    waterMin = this[KEY_WATER_MIN],
    waterMax = this[KEY_WATER_MAX],
)

private fun MutablePreferences.setOrRemove(key: Preferences.Key<Int>, value: Int?) {
    if (value == null) {
        remove(key)
    } else {
        this[key] = value
    }
}
