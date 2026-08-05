/**********************************************************************
 * GattTransport.kt — 傳輸埠（port）
 *
 * 把「怎麼跟 GATT 講話」抽象成介面，讓 OtaUploader 這類編排邏輯完全不依賴
 * 任何平台 API——於是它可以在純 JVM（甚至 iOS）上跑完整單元測試，
 * 不需要真的藍牙設備或模擬器。
 *
 * 這跟韌體端把 reading_format / ota_protocol 抽成不依賴 Arduino API 的純 C、
 * 好在 PC 上用 g++ 測試，是同一個設計思路（見 EnvMonitor/tests/README.md）。
 *
 * 平台實作：Android 見 :core:ble 的 AndroidGattTransport。
 **********************************************************************/
package com.momenest.envmonitor.protocol

import kotlinx.coroutines.flow.Flow

interface GattTransport {

    /**
     * 寫入 characteristic，**Write With Response**——等設備確認才返回。
     *
     * 這是 OTA 的天然流量控制：設備把上一塊寫進 flash 後才回應，手機才送下一塊，
     * 不會塞爆設備的接收緩衝。改成 Write Without Response 雖然快，
     * 但要自己實作流控，風險高很多。
     *
     * @throws GattTransportException 寫入失敗或連線中斷
     */
    suspend fun write(characteristic: BleUuid, value: ByteArray)

    /** 訂閱該 characteristic 的 notify 位元組流 */
    fun notifications(characteristic: BleUuid): Flow<ByteArray>

    /**
     * 目前協商到的 ATT MTU。
     *
     * 未知或尚未協商時回傳 23（藍牙規格的預設值）——保守估計比樂觀猜測安全，
     * 猜大了會導致封包被默默截斷。
     */
    fun negotiatedMtu(): Int
}

class GattTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
