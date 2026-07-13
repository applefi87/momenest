/**********************************************************************
 * config.h — 腳位定義與量測時序參數 (全專案唯一改腳位的地方)
 **********************************************************************/
#pragma once
#include <Arduino.h>

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
