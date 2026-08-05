/*
 * BleUuidExt.kt — 協定層的 BleUuid 與 Android 的 java.util.UUID 互轉。
 *
 * 協定層（:core:protocol）是 KMP 的 commonMain，不能碰 java.*，所以它用自訂的
 * BleUuid（純字串）。轉換只在這個平台適配層發生——這正是「埠與轉接器」
 * 分層的意義：平台差異被關在最外圈，核心邏輯保持乾淨。
 */
package com.momenest.envmonitor.ble

import com.momenest.envmonitor.protocol.BleUuid
import java.util.UUID

/** BleUuid → Android UUID */
internal fun BleUuid.toJavaUuid(): UUID = UUID.fromString(text)

/** Android UUID → BleUuid。UUID.toString() 本來就是小寫正規形式，一定解析得過 */
internal fun UUID.toBleUuid(): BleUuid = BleUuid.of(toString())
