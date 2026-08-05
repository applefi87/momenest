/**********************************************************************
 * GattContract.kt — BLE GATT 契約常數（UUID / MTU / 分塊大小）
 *
 * 這裡的每一個值都必須與韌體端逐字相同，來源是 EnvMonitor/BLE.md：
 * 對不上就是連得到設備卻讀不到資料，而且 BLE 不會給任何錯誤訊息，
 * 極難除錯——所以刻意集中成單一事實來源，改這裡就要同步改韌體與 BLE.md。
 **********************************************************************/
package com.momenest.envmonitor.protocol

/**
 * GATT 契約 — 必須與韌體 EnvMonitor/ble.cpp、ble_ota.cpp、BLE.md 完全一致。
 */
object GattContract {
    /** 韌體廣播名稱（ble.cpp 的 NimBLEDevice::init） */
    const val DEVICE_NAME = "env-monitor"

    val SERVICE_UUID: BleUuid = BleUuid.of("8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01")

    /** 感測讀值 JSON，Read + Notify（約 1Hz） */
    val READINGS_UUID: BleUuid = BleUuid.of("8f2a0002-b8c3-4e6a-9f1d-2a7c9e5b1a01")

    /** WiFi / 上傳狀態 JSON，Read + Notify */
    val STATUS_UUID: BleUuid = BleUuid.of("8f2a0003-b8c3-4e6a-9f1d-2a7c9e5b1a01")

    /** OTA 下指令 / 收回報，Write + Notify */
    val OTA_CONTROL_UUID: BleUuid = BleUuid.of("8f2a0004-b8c3-4e6a-9f1d-2a7c9e5b1a01")

    /** OTA 灌韌體位元組，Write With Response（回應本身就是流量控制） */
    val OTA_DATA_UUID: BleUuid = BleUuid.of("8f2a0005-b8c3-4e6a-9f1d-2a7c9e5b1a01")

    /** 韌體版本資訊，Read + Notify。舊韌體沒有這個 characteristic，讀不到屬正常 */
    val DEVICE_INFO_UUID: BleUuid = BleUuid.of("8f2a0006-b8c3-4e6a-9f1d-2a7c9e5b1a01")

    /** Client Characteristic Configuration Descriptor（開啟 notify 用，藍牙標準值） */
    val CCCD_UUID: BleUuid = BleUuid.of("00002902-0000-1000-8000-00805f9b34fb")

    /** 想協商的 MTU；韌體端 NimBLEDevice::setMTU(517) */
    const val PREFERRED_MTU = 517

    /** 單筆 OTA data 寫入上限（韌體 Update.write 可接受任意長度，這只是上限） */
    const val MAX_OTA_CHUNK = 512

    /** ATT 標頭佔用位元組數：實際可寫 payload = mtu - 3 */
    const val ATT_HEADER_BYTES = 3

    /**
     * 依協商到的 MTU 算出安全的分塊大小。
     *
     * 手機不一定給到 517（常見 23 / 185 / 247），硬寫 512 會被協定層默默截斷，
     * 設備收到殘缺位元組卻照樣寫進 flash，最後才在 END 驗證失敗——白傳好幾分鐘。
     * 下限取 1 是為了讓極端 / 不合理的 mtu 值也不會產生 0 或負數而卡死迴圈。
     *
     * @return max(1, min([MAX_OTA_CHUNK], mtu - [ATT_HEADER_BYTES]))
     */
    fun chunkSizeForMtu(mtu: Int): Int =
        maxOf(1, minOf(MAX_OTA_CHUNK, mtu - ATT_HEADER_BYTES))
}
