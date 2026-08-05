/**********************************************************************
 * JsonSupport.kt — 三個 characteristic 解析器共用的 JSON 取值工具（模組內部）
 *
 * 為什麼手動取值而不是宣告 @Serializable data class 直接反序列化：
 * BLE 封包來自另一顆晶片上的韌體，版本可能比 App 舊或新。用嚴格反序列化時，
 * 任何一個欄位缺漏或型別不符就整包丟出例外，畫面直接空白；手動取值則能做到
 * 「認得的欄位就用，不認得就當 null」，讓新舊韌體互相相容。
 **********************************************************************/
package com.momenest.envmonitor.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal val protocolJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** 把 UTF-8 位元組當成 JSON 物件解析；不是合法 JSON 物件時回傳 null */
internal fun parseJsonObject(text: String): JsonObject? =
    runCatching { protocolJson.parseToJsonElement(text) as? JsonObject }.getOrNull()

internal fun parseJsonObject(bytes: ByteArray): JsonObject? =
    runCatching { parseJsonObject(bytes.decodeToString()) }.getOrNull()

/**
 * 取出「數值型」的原始值。
 *
 * JSON null、字串、以及不存在的 key 一律回 null——韌體對讀取失敗的感測器
 * 送的就是 JSON null（見 EnvMonitor/reading_format.cpp），這裡把它跟
 * 「欄位根本沒出現」視為同一件事，因為對 UI 來說都是「沒有資料」。
 */
private fun JsonObject.numeric(key: String): JsonPrimitive? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    // isString 為 true 代表原文有引號，例如 "24.5"；韌體不會這樣送，視為異常資料
    return if (primitive.isString) null else primitive
}

internal fun JsonObject.floatOrNull(key: String): Float? = numeric(key)?.floatOrNull

internal fun JsonObject.intOrNull(key: String): Int? = numeric(key)?.intOrNull

internal fun JsonObject.longOrNull(key: String): Long? = numeric(key)?.longOrNull

/** 取字串欄位；JSON null 或非字串回 null */
internal fun JsonObject.stringOrNull(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.contentOrNull
}
