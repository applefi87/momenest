/**********************************************************************
 * config.h — 腳位定義與量測時序參數 (全專案唯一改腳位的地方)
 **********************************************************************/
#pragma once
#include <Arduino.h>

// ---- 韌體版本 ----
// OTA 更新後手機無法從畫面確認灌進去的到底是哪一版，所以設備要主動報版本：
// 這兩個值會塞進 BLE 的 device_info characteristic (見 ble.cpp、BLE.md)。
// 改動韌體行為時手動 bump FIRMWARE_VERSION；建置日期由編譯器填 (如 "Aug  4 2026")。
#define FIRMWARE_VERSION    "1.1.0"
#define FIRMWARE_BUILD_DATE __DATE__

// ---- 感測器腳位 ----
#define PIN_ONEWIRE   25    // DS18B20 水溫
#define PIN_WATER_LVL 33    // 水位 類比讀取
#define PIN_WATER_PWR 32    // 水位 探針供電
#define PIN_SOIL      35    // 土壤濕度 類比讀取
#define PIN_SOIL_PWR  26    // 土壤濕度 探針供電
#define PIN_SDA       21    // I2C (SHT45)
#define PIN_SCL       22

// ---- SHT45 ----
#define SHT45_ADDR     0x44
#define SHT45_CMD_HIGH 0xFD   // 高精度量測 (~8.3ms)

// ---- 量測時序 (仿 OMNI-TEC 非阻塞做法) ----
const unsigned long CYCLE_PERIOD  = 1000;  // 1Hz 週期
const unsigned long MEASURE_DELAY = 800;   // T=800ms 統一讀取 (>750ms 確保 DS18B20 12-bit 完成)
const uint8_t UPLOAD_EVERY_N_CYCLES = 10;  // 每 10 個週期 (10 秒) 上傳一次雲端

// 讀取前 Xms 探針上電；水中導電通電越短耗損越少，實測 1ms 足夠
const unsigned long WATER_PWR_LEAD = 1;
const unsigned long SOIL_PWR_LEAD  = 1;

// ---- BLE 讀值服務 ----
// 手機以 BLE 連線即時取得感測值 (設備仍照常 WiFi 上傳)。用 NimBLE-Arduino。
// flash 不足時設為 0 關閉整個 BLE 子系統 (ble.cpp 會編成空、.ino 不呼叫)。
// 啟用需 Partition Scheme 選有 OTA 槽者 (Minimal SPIFFS 1.9MB APP with OTA)。
#define ENABLE_BLE 1

// BLE OTA：手機透過藍牙傳韌體更新 (需 ENABLE_BLE)。失敗自動 abort、
// 舊韌體不變 (A/B 雙槽)；OTA 期間暫停 WiFi 上傳避免搶天線/RAM。
#define ENABLE_BLE_OTA 1

// ---- 螢幕保護 ----
// 無觸控超過此時間熄屏（黑畫面停止重繪，防殘影），觸控喚醒
const unsigned long SCREEN_TIMEOUT_MS = 5UL * 60 * 1000;
// 背光腳位：-1 = BL 硬接 3.3V（目前接法，熄屏時背光仍微亮）；
// 若把 BL 改接 GPIO 並填腳位，熄屏會真正切斷背光
#define PIN_TFT_BL -1
