/**********************************************************************
 * ota_protocol.cpp — BLE OTA 控制封包協定實作 (見 ota_protocol.h)
 * 純標準 C，可在 PC 上編譯測試，也被韌體 ble_ota.cpp 使用。
 **********************************************************************/
#include "ota_protocol.h"

OtaControl parseOtaControl(const uint8_t* data, size_t len) {
    OtaControl c = { OTA_CMD_UNKNOWN, 0, false };
    if (!data || len < 1) return c;

    switch (data[0]) {
        case OTA_CMD_BEGIN:
            if (len >= 5) {                      // opcode + 4-byte size
                c.cmd  = OTA_CMD_BEGIN;
                c.size = (uint32_t)data[1]
                       | ((uint32_t)data[2] << 8)
                       | ((uint32_t)data[3] << 16)
                       | ((uint32_t)data[4] << 24);
                c.valid = (c.size > 0);          // size=0 視為非法
            }
            break;
        case OTA_CMD_END:   c.cmd = OTA_CMD_END;   c.valid = true; break;
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
