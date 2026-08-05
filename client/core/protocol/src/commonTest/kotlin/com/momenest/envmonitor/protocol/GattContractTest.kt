package com.momenest.envmonitor.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UUID 打錯一個字元的症狀是「連得上設備但找不到 service」，而 BLE 不會給任何
 * 錯誤訊息——所以這裡用字面值再比對一次，等於把 EnvMonitor/BLE.md 的表格
 * 抄進測試裡當作驗收條件。
 */
class GattContractTest {

    @Test
    fun `UUID 與韌體 BLE_md 的定義逐字相同`() {
        assertEquals("8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01", GattContract.SERVICE_UUID.text)
        assertEquals("8f2a0002-b8c3-4e6a-9f1d-2a7c9e5b1a01", GattContract.READINGS_UUID.text)
        assertEquals("8f2a0003-b8c3-4e6a-9f1d-2a7c9e5b1a01", GattContract.STATUS_UUID.text)
        assertEquals("8f2a0004-b8c3-4e6a-9f1d-2a7c9e5b1a01", GattContract.OTA_CONTROL_UUID.text)
        assertEquals("8f2a0005-b8c3-4e6a-9f1d-2a7c9e5b1a01", GattContract.OTA_DATA_UUID.text)
        assertEquals("8f2a0006-b8c3-4e6a-9f1d-2a7c9e5b1a01", GattContract.DEVICE_INFO_UUID.text)
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", GattContract.CCCD_UUID.text)
    }

    @Test
    fun `廣播名稱與韌體一致`() {
        assertEquals("env-monitor", GattContract.DEVICE_NAME)
    }

    @Test
    fun `所有 characteristic 的 UUID 互不相同`() {
        val all = listOf(
            GattContract.SERVICE_UUID,
            GattContract.READINGS_UUID,
            GattContract.STATUS_UUID,
            GattContract.OTA_CONTROL_UUID,
            GattContract.OTA_DATA_UUID,
            GattContract.DEVICE_INFO_UUID,
        )
        assertEquals(all.size, all.toSet().size, "有重複的 UUID")
    }

    @Test
    fun `分塊大小在典型 MTU 下等於 MTU 減 3`() {
        assertEquals(20, GattContract.chunkSizeForMtu(23))    // BLE 預設，最糟情況
        assertEquals(182, GattContract.chunkSizeForMtu(185))  // 常見於 iOS
        assertEquals(244, GattContract.chunkSizeForMtu(247))  // 常見於 Android
    }

    @Test
    fun `分塊大小不會超過韌體端的單筆上限`() {
        // MTU 給再大也不能超過 512：韌體 NimBLE 的單筆屬性寫入有上限
        assertEquals(512, GattContract.chunkSizeForMtu(517))
        assertEquals(512, GattContract.chunkSizeForMtu(1000))
        assertEquals(512, GattContract.chunkSizeForMtu(Int.MAX_VALUE))
    }

    @Test
    fun `不合理的 MTU 也不會產生 0 或負數`() {
        // 產生 0 會讓傳輸迴圈永遠前進不了，卡死在 while (sent < total)
        assertTrue(GattContract.chunkSizeForMtu(0) >= 1)
        assertTrue(GattContract.chunkSizeForMtu(3) >= 1)
        assertTrue(GattContract.chunkSizeForMtu(-100) >= 1)
    }
}

class BleUuidTest {

    @Test
    fun `大寫會被正規化成小寫`() {
        // BLE 世界大小寫混用很常見；不正規化就會出現「兩個看起來一樣卻不相等」的 UUID
        assertEquals(
            BleUuid.of("8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01"),
            BleUuid.of("8F2A0001-B8C3-4E6A-9F1D-2A7C9E5B1A01"),
        )
    }

    @Test
    fun `前後空白會被去掉`() {
        assertEquals(
            "8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01",
            BleUuid.of("  8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01  ").text,
        )
    }

    @Test
    fun `toString 回傳正規形式`() {
        val uuid = BleUuid.of("8F2A0001-B8C3-4E6A-9F1D-2A7C9E5B1A01")
        assertEquals("8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01", uuid.toString())
    }

    @Test
    fun `相同 UUID 的 hashCode 相同`() {
        val a = BleUuid.of("8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01")
        val b = BleUuid.of("8F2A0001-B8C3-4E6A-9F1D-2A7C9E5B1A01")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `格式錯誤時 ofOrNull 回 null`() {
        assertEquals(null, BleUuid.ofOrNull(""))
        assertEquals(null, BleUuid.ofOrNull("8f2a0001"))                                  // 太短
        assertEquals(null, BleUuid.ofOrNull("8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a0"))       // 少一碼
        assertEquals(null, BleUuid.ofOrNull("8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a011"))     // 多一碼
        assertEquals(null, BleUuid.ofOrNull("8f2a0001+b8c3-4e6a-9f1d-2a7c9e5b1a01"))      // 連字號位置錯
        assertEquals(null, BleUuid.ofOrNull("gggg0001-b8c3-4e6a-9f1d-2a7c9e5b1a01"))      // 非十六進位
    }

    @Test
    fun `格式錯誤時 of 會擲例外`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { BleUuid.of("not-a-uuid") }
    }
}
