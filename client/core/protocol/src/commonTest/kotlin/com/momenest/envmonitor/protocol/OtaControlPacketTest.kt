package com.momenest.envmonitor.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 這些是**逐位元組**的驗證，因為對手是另一顆晶片上的 C 程式碼
 * （EnvMonitor/ota_protocol.cpp 的 parseOtaControl）。位元組序寫反的話，
 * 設備會拿到一個完全不同的韌體大小——編譯器不會抱怨，實機才會炸。
 */
class OtaControlPacketTest {

    @Test
    fun `BEGIN 是 opcode 加上 4 個位元組的 little-endian 大小`() {
        // 0x01020304 → 低位在前：04 03 02 01
        assertContentEquals(
            byteArrayOf(0x01, 0x04, 0x03, 0x02, 0x01),
            OtaControlPacket.begin(0x01020304L),
        )
    }

    @Test
    fun `BEGIN 大小為 1 時只有最低位元組是 1`() {
        assertContentEquals(
            byteArrayOf(0x01, 0x01, 0x00, 0x00, 0x00),
            OtaControlPacket.begin(1L),
        )
    }

    @Test
    fun `BEGIN 對典型韌體大小的編碼正確`() {
        // 1 MB = 0x00100000 → 00 00 10 00
        assertContentEquals(
            byteArrayOf(0x01, 0x00, 0x00, 0x10, 0x00),
            OtaControlPacket.begin(1024L * 1024L),
        )
    }

    @Test
    fun `BEGIN 的最高位元組會正確落在第 5 個位置`() {
        // 0xFF000000：只有最高位元組是 FF，驗證沒有因為 Byte 有號而錯位
        assertContentEquals(
            byteArrayOf(0x01, 0x00, 0x00, 0x00, 0xFF.toByte()),
            OtaControlPacket.begin(0xFF000000L),
        )
    }

    @Test
    fun `BEGIN 大小為 0 或負數會擲例外`() {
        // 韌體把 size=0 視為非法封包（ota_protocol.cpp: c.valid = size > 0）
        assertFailsWith<IllegalArgumentException> { OtaControlPacket.begin(0L) }
        assertFailsWith<IllegalArgumentException> { OtaControlPacket.begin(-1L) }
    }

    @Test
    fun `BEGIN 大小超出 uint32 會擲例外`() {
        assertFailsWith<IllegalArgumentException> { OtaControlPacket.begin(0x1_0000_0000L) }
    }

    @Test
    fun `不帶 CRC 的 END 只有 1 個位元組`() {
        // 這是舊格式，網頁版 ble-app.html 送的就是這個，韌體必須繼續接受
        assertContentEquals(byteArrayOf(0x02), OtaControlPacket.end())
        assertContentEquals(byteArrayOf(0x02), OtaControlPacket.end(null))
    }

    @Test
    fun `帶 CRC 的 END 是 5 個位元組且 CRC 為 little-endian`() {
        assertContentEquals(
            byteArrayOf(0x02, 0x26, 0x39, 0xF4.toByte(), 0xCB.toByte()),
            OtaControlPacket.end(0xCBF43926L),
        )
    }

    @Test
    fun `END 的 CRC 為 0 時仍送出完整 5 個位元組`() {
        // CRC 剛好是 0 不代表「沒有 CRC」，長度必須維持 5 讓韌體走校驗路徑
        assertContentEquals(
            byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00),
            OtaControlPacket.end(0L),
        )
    }

    @Test
    fun `END 的 CRC 超出 uint32 或為負數會擲例外`() {
        assertFailsWith<IllegalArgumentException> { OtaControlPacket.end(0x1_0000_0000L) }
        assertFailsWith<IllegalArgumentException> { OtaControlPacket.end(-1L) }
    }

    @Test
    fun `ABORT 只有 1 個位元組`() {
        assertContentEquals(byteArrayOf(0x03), OtaControlPacket.abort())
    }

    @Test
    fun `opcode 常數與韌體定義一致`() {
        assertEquals(1, OtaControlPacket.OP_BEGIN.toInt())
        assertEquals(2, OtaControlPacket.OP_END.toInt())
        assertEquals(3, OtaControlPacket.OP_ABORT.toInt())
    }
}
