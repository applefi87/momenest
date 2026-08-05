package com.momenest.envmonitor.protocol

/**
 * OTA 過程中的非同步事件。
 */
sealed interface OtaEvent {
    /** 傳輸中 */
    data class Progress(val percent: Int) : OtaEvent
    
    /** 傳輸完成，設備正在校驗並寫入 Flash */
    data object Verifying : OtaEvent
    
    /** 更新成功 */
    data object Success : OtaEvent
    
    /** 更新失敗 */
    data class Failure(val message: String) : OtaEvent
}
