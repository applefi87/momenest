/**********************************************************************
 * ota_protocol.h — BLE OTA 控制封包協定 (與硬體無關的純邏輯)
 *
 * 手機透過 BLE 傳韌體時，用一個 control characteristic 下指令、一個 data
 * characteristic 灌位元組。這裡只負責「解析 control 封包」與「算進度%」，
 * 抽成純函式方便在 PC 上單元測試 (見 tests/)；實際 flash 寫入 (Update 庫)
 * 與 NimBLE 收發在 ble_ota.cpp。
 *
 * control 封包格式：
 *   [0x01][size:uint32 LE]   BEGIN — 開始，帶韌體總位元組數
 *   [0x02]                   END   — 結束，驗證並切換啟動分區
 *   [0x03]                   ABORT — 中止
 **********************************************************************/
#pragma once
#include <stddef.h>
#include <stdint.h>

enum OtaCmd {
    OTA_CMD_UNKNOWN = 0,
    OTA_CMD_BEGIN   = 1,   // + 4 bytes little-endian 總大小
    OTA_CMD_END     = 2,
    OTA_CMD_ABORT   = 3,
};

struct OtaControl {
    OtaCmd   cmd;
    uint32_t size;    // 僅 BEGIN 有意義
    bool     valid;   // 格式與內容合法
};

// 解析 control 封包；格式不符 (太短 / 未知 opcode / BEGIN size=0) 時 valid=false
OtaControl parseOtaControl(const uint8_t* data, size_t len);

// 進度百分比 (0..100)：total=0 回 0；received>=total 回 100
int otaPercent(uint32_t received, uint32_t total);
