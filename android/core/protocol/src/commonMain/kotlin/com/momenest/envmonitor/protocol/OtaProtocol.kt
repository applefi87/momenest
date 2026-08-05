package com.momenest.envmonitor.protocol

/**
 * OTA 控制封包代碼。
 */
object OtaControlPacket {
    const val OP_BEGIN: Byte = 0x01
    const val OP_END: Byte = 0x02
    const val OP_ABORT: Byte = 0x03
}

/**
 * 設備回報的 OTA 狀態。
 */
sealed interface OtaDeviceReport {
    data object BeginOk : OtaDeviceReport
    data object EndOk : OtaDeviceReport
    data object Aborted : OtaDeviceReport
    data class Progress(val percent: Int) : OtaDeviceReport
    data class DeviceError(val code: Int) : OtaDeviceReport
    data class Unrecognized(val status: Int) : OtaDeviceReport
}
