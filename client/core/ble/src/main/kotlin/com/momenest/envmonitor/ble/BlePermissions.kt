/*
 * BlePermissions.kt — BLE 執行期權限清單的唯一事實來源。
 *
 * Android 12 (API 31) 把藍牙權限整組換掉，舊寫法在新機上會靜默掃不到任何東西。
 * 這裡刻意做成「吃 sdkInt 參數的純函式」而不是直接讀 Build.VERSION.SDK_INT：
 * Build.VERSION.SDK_INT 在 JVM 單元測試中永遠是 0（它不是編譯期常數，
 * mockable android.jar 給不出值），做成參數就能不靠 Robolectric 直接測全部版本。
 */
package com.momenest.envmonitor.ble

import android.Manifest

/** BLE 執行期權限的版本差異處理。 */
object BlePermissions {

    /** Android 12。藍牙權限改制的分水嶺。 */
    private const val SDK_ANDROID_12 = 31

    /**
     * 需要在執行期向使用者請求的權限清單。
     *
     * - API 31 起：`BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`。manifest 的 scan 權限
     *   已宣告 `neverForLocation`，所以**不再需要**定位權限（要了反而被使用者質疑）。
     * - API 30 以下：`BLUETOOTH` / `BLUETOOTH_ADMIN` 是安裝時授予的 normal 權限，
     *   不必請求；但掃描結果可用來推測位置，故系統要求 `ACCESS_FINE_LOCATION`。
     *
     * @param sdkInt 目標裝置的 `Build.VERSION.SDK_INT`
     */
    fun runtimePermissions(sdkInt: Int): List<String> =
        if (sdkInt >= SDK_ANDROID_12) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /**
     * 從 [runtimePermissions] 中挑出尚未取得的項目。
     *
     * @param granted 已授權的權限字串集合（呼叫端用 `ContextCompat.checkSelfPermission` 蒐集）
     * @return 仍缺少的權限；空清單代表可以開始掃描
     */
    fun missing(sdkInt: Int, granted: Set<String>): List<String> =
        runtimePermissions(sdkInt).filterNot { it in granted }
}
