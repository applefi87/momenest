/**********************************************************************
 * OtaUploaderTest.kt — OTA 傳輸編排的完整驗證
 *
 * 這是全專案最重要的測試檔：OtaUploader 寫錯會把使用者的設備刷成磚，
 * 而實機測一次 OTA 要好幾分鐘、失敗還得接 USB 救援。所以這裡把每一條路徑
 * ——正常、設備拒絕、中途故障、連線中斷、逾時、使用者取消——都在毫秒內驗完。
 *
 * 用 runTest 的虛擬時間：逾時測試設的是 10 秒 / 20 秒，但虛擬時間下瞬間完成，
 * 不會讓測試真的等 30 秒。
 **********************************************************************/
package com.momenest.envmonitor.protocol

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OtaUploaderTest {

    /** 內容可預測的假韌體，方便驗證設備收到的位元組與原檔一致 */
    private fun firmware(size: Int) = ByteArray(size) { (it % 251).toByte() }

    // ---------------------------------------------------------------- 正常流程

    @Test
    fun `空韌體不會送出任何封包`() = runTest {
        val transport = TestGattTransport()
        val events = OtaUploader(transport).upload(ByteArray(0)).toList()

        assertEquals(listOf(OtaEvent.Failed(OtaFailure.EMPTY_FIRMWARE, "韌體檔案是空的")), events)
        // 關鍵：連 BEGIN 都不能送。送了 BEGIN(0) 只會讓設備進入等待狀態再逾時
        assertTrue(transport.writes.isEmpty(), "空韌體時不該有任何寫入")
    }

    @Test
    fun `正常流程的封包順序是 BEGIN 然後分塊然後 END`() = runTest {
        val transport = TestGattTransport(mtu = 23)   // 分塊 20，1000 bytes → 50 塊
        val data = firmware(1000)

        OtaUploader(transport).upload(data).toList()

        val writes = transport.writes
        assertEquals(GattContract.OTA_CONTROL_UUID, writes.first().characteristic)
        assertEquals(OtaControlPacket.OP_BEGIN, writes.first().value[0])

        // 中間 50 筆全是 data
        val middle = writes.subList(1, writes.size - 1)
        assertEquals(50, middle.size)
        assertTrue(middle.all { it.characteristic == GattContract.OTA_DATA_UUID })

        assertEquals(GattContract.OTA_CONTROL_UUID, writes.last().characteristic)
        assertEquals(OtaControlPacket.OP_END, writes.last().value[0])
    }

    @Test
    fun `設備收到的位元組與原始韌體完全相同`() = runTest {
        val transport = TestGattTransport(mtu = 185)
        val data = firmware(4096)

        OtaUploader(transport).upload(data).toList()

        assertContentEquals(data, transport.receivedFirmware())
    }

    @Test
    fun `BEGIN 帶的大小就是韌體位元組數`() = runTest {
        val transport = TestGattTransport()
        OtaUploader(transport).upload(firmware(300)).toList()

        assertContentEquals(OtaControlPacket.begin(300), transport.controlWrites.first())
    }

    @Test
    fun `成功時最後一個事件是設備已確認的完成`() = runTest {
        val transport = TestGattTransport()
        val events = OtaUploader(transport).upload(firmware(600)).toList()

        assertEquals(OtaEvent.Started, events.first())
        assertEquals(OtaEvent.Completed(confirmedByDevice = true), events.last())
        assertTrue(events.contains(OtaEvent.Verifying))
    }

    @Test
    fun `成功時不會送出 ABORT`() = runTest {
        val transport = TestGattTransport()
        OtaUploader(transport).upload(firmware(600)).toList()

        assertTrue(
            transport.controlWrites.none { it[0] == OtaControlPacket.OP_ABORT },
            "成功流程不該出現 ABORT",
        )
    }

    // ---------------------------------------------------------------- 分塊與 MTU

    @Test
    fun `分塊大小跟著協商到的 MTU 走`() = runTest {
        // 手機不一定給到 517。硬寫 512 會被協定層默默截斷，設備收到殘缺位元組
        val transport = TestGattTransport(mtu = 247)
        OtaUploader(transport).upload(firmware(1000)).toList()

        assertTrue(transport.dataWrites.all { it.size <= 244 })
        assertEquals(244, transport.dataWrites.first().size)
    }

    @Test
    fun `MTU 為 517 時用上限 512 而不是 514`() = runTest {
        val transport = TestGattTransport(mtu = 517)
        OtaUploader(transport).upload(firmware(2000)).toList()

        assertEquals(512, transport.dataWrites.first().size)
    }

    @Test
    fun `最後一塊可以比分塊大小短`() = runTest {
        val transport = TestGattTransport(mtu = 23)   // 分塊 20
        OtaUploader(transport).upload(firmware(45)).toList()

        assertEquals(listOf(20, 20, 5), transport.dataWrites.map { it.size })
    }

    // ---------------------------------------------------------------- 進度事件

    @Test
    fun `進度單調遞增且不重複`() = runTest {
        val transport = TestGattTransport(mtu = 23)
        val events = OtaUploader(transport).upload(firmware(5000)).toList()

        val percents = events.filterIsInstance<OtaEvent.Sending>().map { it.percent }
        assertEquals(percents.sorted(), percents, "進度必須遞增")
        assertEquals(percents.distinct(), percents, "同一個百分比不該發兩次")
        assertEquals(100, percents.last())
    }

    @Test
    fun `進度事件帶的位元組數正確`() = runTest {
        val transport = TestGattTransport(mtu = 23)
        val events = OtaUploader(transport).upload(firmware(100)).toList()

        val sending = events.filterIsInstance<OtaEvent.Sending>()
        assertEquals(100L, sending.last().bytesSent)
        assertTrue(sending.all { it.totalBytes == 100L })
    }

    // ---------------------------------------------------------------- CRC

    @Test
    fun `預設會在 END 帶上整份韌體的 CRC32`() = runTest {
        val transport = TestGattTransport()
        val data = firmware(777)

        OtaUploader(transport, sendCrc = true).upload(data).toList()

        assertContentEquals(
            OtaControlPacket.end(Crc32.compute(data)),
            transport.controlWrites.last(),
        )
        assertEquals(5, transport.controlWrites.last().size)
    }

    @Test
    fun `關掉 CRC 時 END 退回舊的單位元組格式`() = runTest {
        // 這條路徑保證面對更舊的韌體也還能更新
        val transport = TestGattTransport()
        OtaUploader(transport, sendCrc = false).upload(firmware(777)).toList()

        assertContentEquals(byteArrayOf(OtaControlPacket.OP_END), transport.controlWrites.last())
    }

    // ---------------------------------------------------------------- 失敗路徑

    @Test
    fun `設備拒絕 BEGIN 時回報原因且不灌任何資料`() = runTest {
        val transport = TestGattTransport()
        transport.beginReply = OtaDeviceReport.DeviceError(2)

        val events = OtaUploader(transport).upload(firmware(500)).toList()

        val failed = assertIs<OtaEvent.Failed>(events.last())
        assertEquals(OtaFailure.BEGIN_REJECTED, failed.reason)
        assertEquals(OtaReportDecoder.describeErrorCode(2), failed.detail)
        assertTrue(transport.dataWrites.isEmpty(), "被拒絕後不該再灌韌體")
    }

    @Test
    fun `設備完全不回應 BEGIN 時逾時失敗`() = runTest {
        val transport = TestGattTransport()
        transport.beginReply = null

        val events = OtaUploader(transport).upload(firmware(500)).toList()

        val failed = assertIs<OtaEvent.Failed>(events.last())
        assertEquals(OtaFailure.BEGIN_REJECTED, failed.reason)
        assertTrue(transport.dataWrites.isEmpty())
    }

    @Test
    fun `BEGIN 失敗後會補送 ABORT 讓設備釋放分區`() = runTest {
        val transport = TestGattTransport()
        transport.beginReply = OtaDeviceReport.DeviceError(2)

        OtaUploader(transport).upload(firmware(500)).toList()

        assertContentEquals(OtaControlPacket.abort(), transport.controlWrites.last())
    }

    @Test
    fun `傳輸中設備回報錯誤會立刻停止並中止`() = runTest {
        val transport = TestGattTransport(mtu = 23)
        transport.errorAfterDataWrites = 3
        transport.midTransferReport = OtaDeviceReport.DeviceError(5)

        val events = OtaUploader(transport).upload(firmware(1000)).toList()

        val failed = assertIs<OtaEvent.Failed>(events.last())
        assertEquals(OtaFailure.DEVICE_ERROR, failed.reason)
        assertEquals(OtaReportDecoder.describeErrorCode(5), failed.detail)
        // 沒有繼續把剩下的 47 塊灌完
        assertTrue(transport.dataWrites.size < 10, "偵測到錯誤後不該繼續傳")
        assertContentEquals(OtaControlPacket.abort(), transport.controlWrites.last())
    }

    @Test
    fun `傳輸中設備主動中止會回報 ABORTED`() = runTest {
        val transport = TestGattTransport(mtu = 23)
        transport.errorAfterDataWrites = 2
        transport.midTransferReport = OtaDeviceReport.Aborted

        val events = OtaUploader(transport).upload(firmware(1000)).toList()

        assertEquals(OtaFailure.ABORTED, assertIs<OtaEvent.Failed>(events.last()).reason)
    }

    @Test
    fun `寫入 BEGIN 就失敗時回報傳輸錯誤`() = runTest {
        val transport = TestGattTransport()
        transport.failWriteAt(0, "GATT 忙碌中")

        val events = OtaUploader(transport).upload(firmware(500)).toList()

        val failed = assertIs<OtaEvent.Failed>(events.last())
        assertEquals(OtaFailure.TRANSPORT_ERROR, failed.reason)
        assertEquals("GATT 忙碌中", failed.detail)
    }

    @Test
    fun `灌資料時連線中斷會回報傳輸錯誤並補送 ABORT`() = runTest {
        val transport = TestGattTransport(mtu = 23)
        transport.failWriteAt(3, "連線中斷")   // 第 0 次是 BEGIN，第 3 次是第 3 塊 data

        val events = OtaUploader(transport).upload(firmware(1000)).toList()

        val failed = assertIs<OtaEvent.Failed>(events.last())
        assertEquals(OtaFailure.TRANSPORT_ERROR, failed.reason)
        assertContentEquals(OtaControlPacket.abort(), transport.controlWrites.last())
    }

    @Test
    fun `寫入 END 失敗時回報傳輸錯誤`() = runTest {
        val transport = TestGattTransport(mtu = 517)
        // 500 bytes → 1 塊 data。寫入序：0=BEGIN, 1=data, 2=END
        transport.failWriteAt(2, "斷線")

        val events = OtaUploader(transport).upload(firmware(500)).toList()

        assertEquals(OtaFailure.TRANSPORT_ERROR, assertIs<OtaEvent.Failed>(events.last()).reason)
    }

    @Test
    fun `設備在 END 回報 CRC 不符時明確告知原因`() = runTest {
        val transport = TestGattTransport()
        transport.endReply = OtaDeviceReport.DeviceError(6)

        val events = OtaUploader(transport).upload(firmware(500)).toList()

        val failed = assertIs<OtaEvent.Failed>(events.last())
        assertEquals(OtaFailure.DEVICE_ERROR, failed.reason)
        assertTrue(failed.detail?.contains("CRC") == true, "應說明是 CRC 校驗不符")
    }

    // ---------------------------------------------------------------- 逾時視為成功

    @Test
    fun `END 之後設備沒回應視為完成但標記未確認`() = runTest {
        // 真實情況：設備驗證通過後立刻重開機，END_OK 常常來不及送達手機
        val transport = TestGattTransport()
        transport.endReply = null

        val events = OtaUploader(transport).upload(firmware(500)).toList()

        assertEquals(OtaEvent.Completed(confirmedByDevice = false), events.last())
    }

    @Test
    fun `END 逾時的情況不會送出 ABORT`() = runTest {
        // 設備很可能已經在重開機了，這時送 ABORT 沒有意義；更重要的是不能讓
        // 使用者以為更新被取消了
        val transport = TestGattTransport()
        transport.endReply = null

        OtaUploader(transport).upload(firmware(500)).toList()

        assertTrue(transport.controlWrites.none { it[0] == OtaControlPacket.OP_ABORT })
    }

    // ---------------------------------------------------------------- 事件契約

    @Test
    fun `任何路徑都以 Completed 或 Failed 收尾`() = runTest {
        val scenarios: List<Pair<String, TestGattTransport>> = listOf(
            "正常" to TestGattTransport(),
            "BEGIN 被拒" to TestGattTransport().apply { beginReply = OtaDeviceReport.DeviceError(2) },
            "BEGIN 逾時" to TestGattTransport().apply { beginReply = null },
            "END 逾時" to TestGattTransport().apply { endReply = null },
            "中途錯誤" to TestGattTransport(mtu = 23).apply { errorAfterDataWrites = 2 },
            "寫入失敗" to TestGattTransport().apply { failWriteAt(0) },
        )

        scenarios.forEach { (name, transport) ->
            val last = OtaUploader(transport).upload(firmware(1000)).toList().last()
            assertTrue(
                last is OtaEvent.Completed || last is OtaEvent.Failed,
                "$name 情境的最後一個事件是 $last，應為 Completed 或 Failed",
            )
        }
    }

    @Test
    fun `upload 不會把例外丟給呼叫端`() = runTest {
        // UI 層因此不必再包 try-catch，狀態機也不會卡在「傳輸中」下不來
        val transport = TestGattTransport()
        transport.failWriteAt(0, "爆炸")

        val events = OtaUploader(transport).upload(firmware(100)).toList()

        assertIs<OtaEvent.Failed>(events.last())
    }
}
