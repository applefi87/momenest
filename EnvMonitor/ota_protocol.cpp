/**********************************************************************
 * ota_protocol.cpp — BLE OTA 控制封包協定實作 (見 ota_protocol.h)
 * 純標準 C，可在 PC 上編譯測試，也被韌體 ble_ota.cpp 使用。
 **********************************************************************/
#include "ota_protocol.h"

// 從 data[off] 起取 4 bytes little-endian
static uint32_t readLe32(const uint8_t* data, size_t off) {
    return (uint32_t)data[off]
         | ((uint32_t)data[off + 1] << 8)
         | ((uint32_t)data[off + 2] << 16)
         | ((uint32_t)data[off + 3] << 24);
}

OtaControl parseOtaControl(const uint8_t* data, size_t len) {
    OtaControl c = { OTA_CMD_UNKNOWN, 0, 0, false, false };
    if (!data || len < 1) return c;

    switch (data[0]) {
        case OTA_CMD_BEGIN:
            if (len >= 5) {                      // opcode + 4-byte size
                c.cmd   = OTA_CMD_BEGIN;
                c.size  = readLe32(data, 1);
                c.valid = (c.size > 0);          // size=0 視為非法
            }
            break;
        case OTA_CMD_END:
            c.cmd = OTA_CMD_END;
            if (len >= 5) {                      // opcode + 4-byte CRC32 (新版 App)
                c.crc32  = readLe32(data, 1);
                c.hasCrc = true;
            }
            // 長度不足 5 → 舊版 App 的 1-byte END，維持 hasCrc=false 照樣 valid，
            // 否則升級韌體後舊網頁版會直接壞掉
            c.valid = true;
            break;
        case OTA_CMD_ABORT: c.cmd = OTA_CMD_ABORT; c.valid = true; break;
        default: break;                          // 未知 opcode → valid=false
    }
    return c;
}

int otaPercent(uint32_t received, uint32_t total) {
    if (total == 0)        return 0;
    if (received >= total) return 100;
    return (int)((uint64_t)received * 100 / total);
}

// ---- CRC-32 (IEEE 802.3，反射式) ----
// 逐位元版本：每個位元組低位先進，crc 最低位為 1 時才 XOR 反射多項式。
// 不建查表以省 flash (見 ota_protocol.h 的說明)。

uint32_t crc32Init() { return 0xFFFFFFFFu; }

uint32_t crc32Update(uint32_t crc, const uint8_t* data, size_t len) {
    if (!data) return crc;                   // 防呆：空指標視為沒有新資料
    for (size_t i = 0; i < len; i++) {
        crc ^= (uint32_t)data[i];
        for (int bit = 0; bit < 8; bit++) {
            crc = (crc & 1u) ? ((crc >> 1) ^ 0xEDB88320u) : (crc >> 1);
        }
    }
    return crc;
}

uint32_t crc32Final(uint32_t crc) { return crc ^ 0xFFFFFFFFu; }
