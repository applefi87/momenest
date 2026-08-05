/*
 * OtaUiState.kt — 韌體更新畫面的 UI 狀態模型。
 *
 * 這裡只放「畫面要顯示什麼」的純資料，完全不碰 Android API，
 * 好讓 OtaScreen 維持無狀態（stateless）：@Preview 與 UI 測試都能直接把
 * 狀態餵進去，不需要 ViewModel、Hilt 或真設備。
 */
package com.momenest.envmonitor.feature.ota

/**
 * 使用者選到的韌體檔。
 *
 * 位元組一次整包讀進記憶體而不是邊傳邊讀：SAF 的 Uri 讀取權限可能在畫面
 * 重建（旋轉、切背景）後失效，傳到一半才讀不到檔案會直接毀掉這次更新。
 * 大小上限由 [FirmwareReader] 把關，避免大檔 OOM。
 *
 * @param fileName  顯示用檔名（provider 沒給就是預設名）
 * @param sizeBytes 實際讀到的位元組數
 * @param bytes     韌體內容
 */
data class SelectedFirmware(
    val fileName: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
) {
    // data class 自動產生的 equals 對 ByteArray 是比參照，不覆寫的話兩份內容
    // 相同的韌體永遠「不相等」，Compose 的重組判斷與測試斷言會全部失準。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectedFirmware) return false
        return fileName == other.fileName &&
            sizeBytes == other.sizeBytes &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/** 更新流程的階段；UI 只靠這個決定要顯示什麼、鎖住哪些按鈕 */
enum class OtaPhase { IDLE, UPLOADING, VERIFYING, SUCCESS, FAILED }

/**
 * 韌體更新畫面的完整狀態。
 *
 * @param connected      是否已連上設備
 * @param otaSupported   設備是否有 OTA characteristics（舊韌體可能沒有）
 * @param currentVersion 設備回報的目前韌體版本；未知為 null
 * @param firmware       已選擇的韌體檔
 * @param phase          目前階段
 * @param percent        0..100 進度
 * @param message        給使用者看的一行說明（繁中）
 * @param canStart       「開始更新」是否可按；一律由 [recalculateCanStart] 算出，
 *                       不要在別處自己拼條件，否則兩邊算法遲早分岔
 */
data class OtaUiState(
    val connected: Boolean = false,
    val otaSupported: Boolean = false,
    val currentVersion: String? = null,
    val firmware: SelectedFirmware? = null,
    val phase: OtaPhase = OtaPhase.IDLE,
    val percent: Int = 0,
    val message: String = "",
    val canStart: Boolean = false,
)

/** 傳輸進行中（UI 要鎖住按鈕、螢幕保持不熄） */
internal val OtaUiState.isTransferring: Boolean
    get() = phase == OtaPhase.UPLOADING || phase == OtaPhase.VERIFYING

/**
 * 重算 [OtaUiState.canStart]。
 *
 * 設成單一計算點：任何會影響條件的欄位（連線、OTA 支援、選檔、階段）改完後
 * 都接一次這個函式，UI 就不會出現「明明在傳輸中卻還能按開始」這種狀態。
 */
internal fun OtaUiState.recalculateCanStart(): OtaUiState =
    copy(canStart = connected && otaSupported && firmware != null && !isTransferring)
