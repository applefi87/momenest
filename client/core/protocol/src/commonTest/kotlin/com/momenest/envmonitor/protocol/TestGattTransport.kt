/**********************************************************************
 * TestGattTransport.kt — 本模組測試專用的 GattTransport 替身
 *
 * 為什麼不用 :core:testing 的 FakeGattTransport：那個模組依賴本模組，
 * 反過來依賴會造成循環相依。而且本模組是 KMP，替身也必須是純 common 程式碼
 * （不能用 java.util.concurrent 那些）。
 *
 * 用手寫替身而非 mock 框架：OTA 是有狀態的來回對話，「收到 BEGIN 才回 BEGIN_OK」
 * 這種因果關係用 fake 寫最短也最清楚；而且 fake 是真的能跑的假設備，
 * 不會發生「mock 設定寫錯，測試綠燈但實機爛掉」。
 **********************************************************************/
package com.momenest.envmonitor.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.yield

/** 設備回報的 status 位元組，與韌體 ble_ota.cpp 的 OTA_ST_* 一致 */
private const val ST_BEGIN_OK: Byte = 0x01
private const val ST_END_OK: Byte = 0x02
private const val ST_ABORTED: Byte = 0x03
private const val ST_PROGRESS: Byte = 0x10

// 0xEE 超出 Byte 的正值範圍，寫不了字面值，故轉型（也因此不能宣告成 const）
private val ST_ERROR: Byte = 0xEE.toByte()

/**
 * SharedFlow(replay = 0) 在沒有訂閱者時 emit 會直接丟棄，而 OtaUploader 是
 * 「先 launch 訂閱協程、再寫 BEGIN」——訂閱協程可能還沒被排程執行，
 * 這時自動回應會石沉大海，測試就卡到逾時。故先 yield 幾次讓訂閱者跑起來。
 * 設上限是為了在「刻意不訂閱」的測試裡不會無限空轉。
 */
private const val SUBSCRIBER_WAIT_SPINS = 64

/**
 * 一台可程式化的假設備。
 *
 * 預設行為是「一切正常的設備」；要演各種故障時改下面那幾個欄位即可。
 */
class TestGattTransport(private var mtu: Int = GattContract.PREFERRED_MTU) : GattTransport {

    /**
     * 一次成功的寫入記錄。
     *
     * equals / hashCode 一定要自己覆寫：data class 產生的版本對 ByteArray 用的是
     * 參考比較，兩個內容相同的陣列永遠不相等，斷言會莫名其妙失敗。
     */
    data class Written(val characteristic: BleUuid, val value: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Written) return false
            return characteristic == other.characteristic && value.contentEquals(other.value)
        }

        override fun hashCode(): Int = 31 * characteristic.hashCode() + value.contentHashCode()
    }

    private val recorded = mutableListOf<Written>()
    private val notifyFlows = mutableMapOf<BleUuid, MutableSharedFlow<ByteArray>>()
    private val scheduledFailures = mutableMapOf<Int, String>()
    private var writeAttempts = 0
    private var dataWriteCount = 0

    /** 依序記錄每次**成功**的 write */
    val writes: List<Written> get() = recorded.toList()

    /** 只取 OTA control 的寫入內容，方便斷言指令序列 */
    val controlWrites: List<ByteArray>
        get() = recorded.filter { it.characteristic == GattContract.OTA_CONTROL_UUID }.map { it.value }

    /** 只取 OTA data 的寫入內容 */
    val dataWrites: List<ByteArray>
        get() = recorded.filter { it.characteristic == GattContract.OTA_DATA_UUID }.map { it.value }

    /** 收到 BEGIN 後要回什麼；null = 完全不回應（用來測逾時） */
    var beginReply: OtaDeviceReport? = OtaDeviceReport.BeginOk

    /** 收到 END 後要回什麼；null = 完全不回應（模擬設備驗證通過後立刻重開機） */
    var endReply: OtaDeviceReport? = OtaDeviceReport.EndOk

    /** 寫到第 N 塊 data 之後主動回報這個錯誤；null = 不模擬中途故障 */
    var errorAfterDataWrites: Int? = null
    var midTransferReport: OtaDeviceReport = OtaDeviceReport.DeviceError(5)

    /** 串接所有 OTA data 寫入，用來驗證設備收到的韌體與原始位元組完全一致 */
    fun receivedFirmware(): ByteArray {
        val chunks = recorded.filter { it.characteristic == GattContract.OTA_DATA_UUID }
        val out = ByteArray(chunks.sumOf { it.value.size })
        var offset = 0
        chunks.forEach { chunk ->
            chunk.value.copyInto(out, offset)
            offset += chunk.value.size
        }
        return out
    }

    /**
     * 讓第 [index] 次 write 擲出 [GattTransportException]（模擬連線中斷），index 從 0 起算。
     *
     * 擲例外的那次不會進 [writes]（設備根本沒收到），但仍佔用一個 index，
     * 所以後續補送的 ABORT 是第 index + 1 次寫入。
     */
    fun failWriteAt(index: Int, message: String = "連線中斷") {
        scheduledFailures[index] = message
    }

    /** 模擬 MTU 重新協商 */
    fun setMtu(value: Int) {
        mtu = value
    }

    suspend fun emitReport(report: OtaDeviceReport) = emitReport(encode(report))

    suspend fun emitReport(bytes: ByteArray) {
        val flow = flowFor(GattContract.OTA_CONTROL_UUID)
        var spins = 0
        while (flow.subscriptionCount.value == 0 && spins < SUBSCRIBER_WAIT_SPINS) {
            yield()
            spins++
        }
        flow.emit(bytes.copyOf())
    }

    override suspend fun write(characteristic: BleUuid, value: ByteArray) {
        val index = writeAttempts++
        scheduledFailures[index]?.let { throw GattTransportException(it) }

        // 存副本：呼叫端若重用緩衝區，記錄下來的內容才不會被後續寫入改掉
        recorded.add(Written(characteristic, value.copyOf()))

        if (characteristic == GattContract.OTA_DATA_UUID) {
            dataWriteCount++
            if (dataWriteCount == errorAfterDataWrites) emitReport(midTransferReport)
            return
        }

        if (characteristic != GattContract.OTA_CONTROL_UUID || value.isEmpty()) return
        when (value[0]) {
            OtaControlPacket.OP_BEGIN -> beginReply?.let { emitReport(it) }
            OtaControlPacket.OP_END -> endReply?.let { emitReport(it) }
            // ABORT 不自動回應：中止後設備怎麼反應由測試自己決定
            else -> Unit
        }
    }

    override fun notifications(characteristic: BleUuid): Flow<ByteArray> =
        flowFor(characteristic).asSharedFlow()

    override fun negotiatedMtu(): Int = mtu

    private fun flowFor(characteristic: BleUuid): MutableSharedFlow<ByteArray> =
        notifyFlows.getOrPut(characteristic) {
            // replay = 0：notify 是即時事件，重播舊值會讓後來的訂閱者收到過期回報
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }

    private fun encode(report: OtaDeviceReport): ByteArray = when (report) {
        OtaDeviceReport.BeginOk -> byteArrayOf(ST_BEGIN_OK, 0)
        OtaDeviceReport.EndOk -> byteArrayOf(ST_END_OK, 0)
        OtaDeviceReport.Aborted -> byteArrayOf(ST_ABORTED, 0)
        is OtaDeviceReport.Progress -> byteArrayOf(ST_PROGRESS, report.percent.toByte())
        is OtaDeviceReport.DeviceError -> byteArrayOf(ST_ERROR, report.code.toByte())
        is OtaDeviceReport.Unrecognized -> byteArrayOf(report.status.toByte(), 0)
    }
}
