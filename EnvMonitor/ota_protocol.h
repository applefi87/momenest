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
 *   [0x02]                   END   — 結束 (舊版，不帶 CRC)
 *   [0x02][crc32:uint32 LE]  END   — 結束 + 完整性校驗 (新版 App)
 *   [0x03]                   ABORT — 中止
 *
 * END 兩種長度都合法：舊版網頁 App (cloud/src/ble-app.html) 只送 1 byte，
 * 韌體升級後仍要能用它更新，所以 CRC 是「有就驗、沒有就跳過」的選配欄位。
 **********************************************************************/
#pragma once
#include <stddef.h>
#include <stdint.h>

enum OtaCmd {
    OTA_CMD_UNKNOWN = 0,
    OTA_CMD_BEGIN   = 1,   // + 4 bytes little-endian 總大小
    OTA_CMD_END     = 2,   // + 選配 4 bytes little-endian CRC32
    OTA_CMD_ABORT   = 3,
};

struct OtaControl {
    OtaCmd   cmd;
    uint32_t size;    // 僅 BEGIN 有意義
    uint32_t crc32;   // 僅 END 且 hasCrc 時有意義
    bool     hasCrc;  // END 是否帶 CRC (舊版 App 只送 1 byte → false，不校驗)
    bool     valid;   // 格式與內容合法
};

// 解析 control 封包；格式不符 (太短 / 未知 opcode / BEGIN size=0) 時 valid=false
OtaControl parseOtaControl(const uint8_t* data, size_t len);

// 進度百分比 (0..100)：total=0 回 0；received>=total 回 100
int otaPercent(uint32_t received, uint32_t total);

/* ---- CRC-32 (IEEE 802.3) ----------------------------------------------
 * BLE 傳輸雖有 CRC，但「手機讀檔→分塊→GATT→Update.write」整條路徑上仍可能
 * 少送/重送一塊，而 Update.end() 只驗 ESP 映像頭與長度，驗不出中間位元組錯。
 * 故設備邊收邊累積 CRC，END 時與手機算的比對 (見 ble_ota.cpp)。
 *
 * 反射多項式 0xEDB88320、初值 0xFFFFFFFF、輸出取反 —— 與 Kotlin 端的
 * java.util.zip.CRC32 完全一致。刻意用逐位元運算而非 256 項查表：
 * ESP32 flash 很緊，1KB 表換來的速度對 BLE 這種慢速傳輸沒有意義。
 *
 * 用法：uint32_t c = crc32Init(); c = crc32Update(c, buf, n); ... crc32Final(c)
 */
uint32_t crc32Init();
uint32_t crc32Update(uint32_t crc, const uint8_t* data, size_t len);
uint32_t crc32Final(uint32_t crc);
