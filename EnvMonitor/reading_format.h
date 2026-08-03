/**********************************************************************
 * reading_format.h — 感測讀值序列化 (與硬體無關的純邏輯)
 *
 * 把一筆感測值序列化成雲端 / BLE 共用的 JSON。抽成純函式的目的：
 *   1. net.cpp (WiFi 上傳) 與 ble.cpp (BLE Notify) 共用同一份格式，不重複
 *   2. 不依賴任何 Arduino / 硬體 API，可在 PC 上用 g++ 編譯執行單元測試
 *      (見 tests/，TDD)
 *
 * 慣例：浮點欄位為 NaN、或整數欄位 *_valid = false 時，序列化成 JSON null
 *       (與 cloud/schema.sql「讀取失敗存 NULL」一致)
 **********************************************************************/
#pragma once
#include <stddef.h>

// 一筆感測讀值 (純資料，無硬體相依)
struct SensorReading {
    float air_temp;      // NaN = 讀取失敗
    float air_hum;       // NaN = 讀取失敗
    float water_temp;    // NaN = 讀取失敗
    int   soil;          // 原始 ADC
    int   water_level;   // 原始 ADC
    bool  soil_valid;    // false = 讀到 0，視為未接
    bool  water_valid;   // false = 讀到 0，視為未接
};

// 序列化成:
//   {"air_temp":24.58,"air_hum":58.07,"water_temp":31.00,"soil":2100,"water_level":1500}
// 無效值 (NaN / *_valid=false) 輸出 null。浮點固定 2 位小數。
// 回傳值語意同 snprintf：實際「應寫入」的字元數 (不含結尾 \0)；
// 若 >= buflen 表示被截斷 (buf 仍為合法的 null-terminated 字串)。
int formatReadingJson(char* buf, size_t buflen, const SensorReading& r);
