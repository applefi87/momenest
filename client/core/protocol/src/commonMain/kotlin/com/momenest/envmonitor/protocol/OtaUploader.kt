/**********************************************************************
 * OtaUploader.kt — BLE OTA 傳輸編排
 *
 * 流程：BEGIN(size) → 等 BEGIN_OK → 依序灌分塊 → END(crc32) → 等 END_OK。
 *
 * 這是整個 App 風險最高的一段邏輯——寫錯會把使用者的設備刷成磚。所以它被刻意
 * 設計成不依賴任何平台 API（只透過 GattTransport 這個埠），好讓每一條路徑都能
 * 在純 JVM 上用單元測試驗證：封包順序、位元組內容、分塊大小、各種失敗時是否
 * 有補送 ABORT。
 *
 * 安全底線：任何錯誤、逾時、或使用者中途取消，都會送出 ABORT 讓設備
 * Update.abort()，啟動分區不切換，設備繼續跑舊韌體（A/B 雙分區）。
 **********************************************************************/
package com.momenest.envmonitor.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * @param transport          GATT 傳輸埠
 * @param sendCrc            END 是否附帶 CRC32。設備韌體較舊時仍安全——
 *                           韌體只在封包長度 >= 5 才讀 CRC，舊版會忽略多出來的位元組
 * @param beginAckTimeoutMs  等 BEGIN_OK 的逾時
 * @param endAckTimeoutMs    等 END_OK 的逾時；逾時視為 Completed(confirmedByDevice=false)
 */
class OtaUploader(
    private val transport: GattTransport,
    private val sendCrc: Boolean = true,
    private val beginAckTimeoutMs: Long = 10_000,
    private val endAckTimeoutMs: Long = 20_000,
) {

    /**
     * 上傳韌體。回傳冷流，collect 才開始傳。
     *
     * 保證：一定以 [OtaEvent.Completed] 或 [OtaEvent.Failed] 作為最後一個事件收尾，
     * 而且不會把例外丟給呼叫端（所有錯誤都轉成 Failed 事件）——UI 層因此不需要
     * 再包一層 try/catch，狀態機也不會卡在「傳輸中」下不來。
     */
    fun upload(firmware: ByteArray): Flow<OtaEvent> = channelFlow {
        if (firmware.isEmpty()) {
            // 提早返回：一個封包都不送。送了 BEGIN(0) 只會讓設備進入等待狀態再逾時
            send(OtaEvent.Failed(OtaFailure.EMPTY_FIRMWARE, "韌體檔案是空的"))
            return@channelFlow
        }

        val total = firmware.size.toLong()
        val chunkSize = GattContract.chunkSizeForMtu(transport.negotiatedMtu())
        val reports = Channel<OtaDeviceReport>(Channel.BUFFERED)

        // 必須在寫 BEGIN **之前**就開始訂閱，否則設備回得太快會漏掉 BEGIN_OK
        val listener = launch {
            runCatching {
                transport.notifications(GattContract.OTA_CONTROL_UUID).collect { bytes ->
                    OtaReportDecoder.decode(bytes)?.let { reports.trySend(it) }
                }
            }
        }
        // 讓上面的訂閱協程有機會真的跑起來再往下走
        yield()

        // 只有「確認完成」才不補 ABORT；其餘所有離開路徑（失敗、例外、被取消）都要補
        var abortOnExit = true

        try {
            // ---------- BEGIN ----------
            writeErrorOrNull(GattContract.OTA_CONTROL_UUID, OtaControlPacket.begin(total))?.let {
                send(OtaEvent.Failed(OtaFailure.TRANSPORT_ERROR, it))
                return@channelFlow
            }
            when (val ack = awaitReport(reports, beginAckTimeoutMs)) {
                null -> {
                    send(OtaEvent.Failed(OtaFailure.BEGIN_REJECTED, "設備沒有回應開始指令"))
                    return@channelFlow
                }

                OtaDeviceReport.BeginOk -> send(OtaEvent.Started)

                is OtaDeviceReport.DeviceError -> {
                    val why = OtaReportDecoder.describeErrorCode(ack.code)
                    send(OtaEvent.Failed(OtaFailure.BEGIN_REJECTED, why))
                    return@channelFlow
                }

                else -> {
                    send(OtaEvent.Failed(OtaFailure.BEGIN_REJECTED, "設備回報非預期的狀態"))
                    return@channelFlow
                }
            }

            // ---------- 灌位元組 ----------
            var sent = 0L
            var lastPercent = -1
            for (chunk in FirmwareChunker.chunks(firmware, chunkSize)) {
                // 每塊之前檢查設備有沒有中途喊停，避免對著已經放棄的設備繼續灌好幾分鐘
                pendingProblem(reports)?.let {
                    send(it)
                    return@channelFlow
                }

                writeErrorOrNull(GattContract.OTA_DATA_UUID, chunk)?.let {
                    send(OtaEvent.Failed(OtaFailure.TRANSPORT_ERROR, it))
                    return@channelFlow
                }

                sent += chunk.size
                val percent = otaPercent(sent, total)
                // 百分比沒變就不重複發，否則 2048 塊會灌爆 UI；但最後一塊一定要發
                if (percent != lastPercent || sent == total) {
                    lastPercent = percent
                    send(OtaEvent.Sending(percent, sent, total))
                }
            }

            // ---------- END ----------
            send(OtaEvent.Verifying)
            val crc = if (sendCrc) Crc32.compute(firmware) else null
            writeErrorOrNull(GattContract.OTA_CONTROL_UUID, OtaControlPacket.end(crc))?.let {
                send(OtaEvent.Failed(OtaFailure.TRANSPORT_ERROR, it))
                return@channelFlow
            }

            when (val ack = awaitReport(reports, endAckTimeoutMs)) {
                // 逾時多半代表設備驗證通過後立刻重開機，notify 來不及送達——算成功但標記未確認
                null -> {
                    abortOnExit = false
                    send(OtaEvent.Completed(confirmedByDevice = false))
                }

                OtaDeviceReport.EndOk -> {
                    abortOnExit = false
                    send(OtaEvent.Completed(confirmedByDevice = true))
                }

                is OtaDeviceReport.DeviceError -> {
                    val why = OtaReportDecoder.describeErrorCode(ack.code)
                    send(OtaEvent.Failed(OtaFailure.DEVICE_ERROR, why))
                }

                OtaDeviceReport.Aborted -> {
                    send(OtaEvent.Failed(OtaFailure.ABORTED, "設備中止了更新"))
                }

                else -> {
                    abortOnExit = false
                    send(OtaEvent.Completed(confirmedByDevice = false))
                }
            }
        } finally {
            listener.cancel()
            reports.close()
            if (abortOnExit) {
                // NonCancellable：使用者取消時這段仍要跑完，否則設備會一直停在等待狀態
                withContext(NonCancellable) {
                    runCatching {
                        transport.write(GattContract.OTA_CONTROL_UUID, OtaControlPacket.abort())
                    }
                }
            }
        }
    }

    /**
     * 寫入並把失敗轉成訊息字串（成功回 null）。
     *
     * 刻意不讓例外往外傳：OTA 的每一步失敗都要能轉成 Failed 事件給 UI，
     * 但 [CancellationException] 必須原樣往上拋，否則協程取消會被吞掉。
     */
    private suspend fun writeErrorOrNull(characteristic: BleUuid, value: ByteArray): String? =
        try {
            transport.write(characteristic, value)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.message ?: e.toString()
        }

    /**
     * 等一筆「有意義的」回報。
     *
     * 進度回報（0x10）在等待 ack 時直接略過——它會在傳輸過程中不斷湧入，
     * 若不濾掉就會被誤當成 BEGIN_OK / END_OK。
     *
     * @return 逾時回 null
     */
    private suspend fun awaitReport(
        reports: Channel<OtaDeviceReport>,
        timeoutMs: Long,
    ): OtaDeviceReport? = withTimeoutOrNull(timeoutMs) {
        var report: OtaDeviceReport
        do {
            report = reports.receive()
        } while (report is OtaDeviceReport.Progress)
        report
    }

    /** 非阻塞地清空回報佇列，只回報「需要中止傳輸」的那些；沒問題回 null */
    private fun pendingProblem(reports: Channel<OtaDeviceReport>): OtaEvent.Failed? {
        while (true) {
            val report = reports.tryReceive().getOrNull() ?: return null
            when (report) {
                is OtaDeviceReport.DeviceError -> return OtaEvent.Failed(
                    OtaFailure.DEVICE_ERROR,
                    OtaReportDecoder.describeErrorCode(report.code),
                )

                OtaDeviceReport.Aborted -> return OtaEvent.Failed(
                    OtaFailure.ABORTED,
                    "設備中止了更新",
                )

                // 進度與其他狀態在傳輸中不影響流程，丟掉即可
                else -> Unit
            }
        }
    }
}
