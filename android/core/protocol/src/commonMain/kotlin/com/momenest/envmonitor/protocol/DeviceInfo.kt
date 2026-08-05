package com.momenest.envmonitor.protocol

import kotlinx.serialization.Serializable

/**
 * 設備硬體資訊。
 */
@Serializable
data class DeviceInfo(
    val firmwareVersion: String = "",
    val buildDate: String = "",
    val chip: String = "",
    val freeHeapBytes: Long? = null
)
