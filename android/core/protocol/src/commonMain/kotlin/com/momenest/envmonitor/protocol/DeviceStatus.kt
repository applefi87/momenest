package com.momenest.envmonitor.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class UploadState {
    IDLE, UPLOADING, SUCCESS, FAILED
}

/**
 * 設備運作狀態。
 */
@Serializable
data class DeviceStatus(
    val wifiConnected: Boolean = false,
    val uploadState: UploadState = UploadState.IDLE,
    val ipAddress: String = ""
)
