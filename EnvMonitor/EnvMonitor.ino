/**********************************************************************
 * 環境監測系統 (Environment Monitor) — LovyanGFX 版
 * 硬體：ESP32 (NodeMCU-32)
 *   - 3.5" SPI TFT ILI9488 + XPT2046 觸控 (VSPI 共用匯流排)
 *   - SHT45   : I2C  空氣溫濕度 (SDA=21, SCL=22)
 *   - DS18B20 : 1-Wire 水溫      (DATA=25)
 *   - 土壤濕度: 類比 (GPIO32)
 *   - 水位    : 類比 (GPIO33)
 *-
 * 優點：所有腳位設定都在本檔內，不需修改任何 library 檔案。
 *
 * 需安裝函式庫：LovyanGFX、OneWire、DallasTemperature
 *
 * 時序 (仿 OMNI-TEC 非阻塞做法)：
 *   T=0    觸發 SHT45 + DS18B20 (只下指令不等待)
 *   T=800ms 統一讀取 (>750ms 確保 DS18B20 12-bit 轉換完成) 並更新螢幕
 **********************************************************************/

#define LGFX_USE_V1
#include <LovyanGFX.hpp>
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include "secrets.h"   // WiFi 帳密 + API 網址/密鑰 (複製 secrets.h.example 為 secrets.h 填入)

// ==========================================
// LovyanGFX 硬體設定 (腳位全在這裡，不動 library)
// ==========================================
class LGFX : public lgfx::LGFX_Device {
    lgfx::Panel_ILI9488     _panel;
    lgfx::Bus_SPI           _bus;
    lgfx::Touch_XPT2046     _touch;
public:
    LGFX() {
        // --- SPI 匯流排 (VSPI，螢幕與觸控共用) ---
        auto bcfg = _bus.config();
        bcfg.spi_host   = SPI3_HOST;  // VSPI (ESP32 Core 3.x 改名為 SPI3_HOST)
        bcfg.spi_mode   = 0;
        bcfg.freq_write = 27000000;   // 寫入 27MHz (穩定後可試 40MHz)
        bcfg.freq_read  = 16000000;
        bcfg.pin_sclk   = 18;         // SCK
        bcfg.pin_mosi   = 23;         // MOSI (SDI/TDI 合併)
        bcfg.pin_miso   = 19;         // MISO (SDO/TDO 合併)
        bcfg.pin_dc     = 2;          // TFT_DC
        _bus.config(bcfg);
        _panel.setBus(&_bus);

        // --- ILI9488 面板 ---
        auto pcfg = _panel.config();
        pcfg.pin_cs   = 15;           // TFT_CS
        pcfg.pin_rst  = 4;            // TFT_RST
        pcfg.panel_width  = 320;
        pcfg.panel_height = 480;
        pcfg.bus_shared   = true;     // 觸控共用匯流排，切換時自動處理 CS
        _panel.config(pcfg);

        // --- XPT2046 觸控 ---
        auto tcfg = _touch.config();
        tcfg.spi_host = SPI3_HOST;    // 同一組 SPI (VSPI)
        tcfg.freq     = 2500000;      // XPT2046 上限 2.5MHz
        tcfg.pin_sclk = 18;
        tcfg.pin_mosi = 23;
        tcfg.pin_miso = 19;
        tcfg.pin_cs   = 5;            // TOUCH_CS
        tcfg.pin_int  = 16;           // TOUCH_IRQ (PEN)，LovyanGFX 直接支援
        tcfg.x_min = 300;  tcfg.x_max = 3600;   // 校正值，可跑校正後修正
        tcfg.y_min = 300;  tcfg.y_max = 3600;
        _touch.config(tcfg);
        _panel.setTouch(&_touch);

        setPanel(&_panel);
    }
};

LGFX tft;

// ==========================================
// 感測器腳位與參數
// ==========================================
#define PIN_ONEWIRE   25
#define PIN_SOIL      32
#define PIN_WATER_LVL 33
#define PIN_SDA       21
#define PIN_SCL       22
#define SHT45_ADDR    0x44
#define SHT45_CMD_HIGH 0xFD          // 高精度量測 (~8.3ms)

const unsigned long CYCLE_PERIOD  = 1000;  // 1Hz 週期
const unsigned long MEASURE_DELAY = 800;   // T=800ms 讀取
const uint8_t UPLOAD_EVERY_N_CYCLES = 10;  // 每 10 個週期 (10 秒) 上傳一次雲端

OneWire oneWire(PIN_ONEWIRE);
DallasTemperature ds18b20(&oneWire);

unsigned long lastCycleStartTime = 0;
bool isMeasuring = false;
unsigned long lastTouchDraw = 0;

float airTemp = NAN, airHum = NAN, waterTemp = NAN;
int soilRaw = 0, waterRaw = 0;

uint8_t cycleCount = 0;                    // 量測週期計數 (供上傳節流)
unsigned long lastWifiRetry = 0;           // WiFi 重連節流

// ==========================================
// SHT45 觸發 / CRC / 讀取 (仿 OMNI-TEC 觸發讀取分離)
// ==========================================
void sht45Trigger() {
    Wire.beginTransmission(SHT45_ADDR);
    Wire.write(SHT45_CMD_HIGH);
    Wire.endTransmission();
}

uint8_t calcCRC8(uint8_t *ptr, uint8_t len) {  // 多項式 0x31、初始 0xFF
    uint8_t crc = 0xFF;
    for (uint8_t i = 0; i < len; i++) {
        crc ^= ptr[i];
        for (uint8_t j = 0; j < 8; j++)
            crc = (crc & 0x80) ? (crc << 1) ^ 0x31 : (crc << 1);
    }
    return crc;
}

bool sht45Read(float &temp, float &hum) {
    Wire.requestFrom((uint8_t)SHT45_ADDR, (uint8_t)6);
    if (Wire.available() >= 6) {
        uint8_t d[6];
        for (int i = 0; i < 6; i++) d[i] = Wire.read();
        if (calcCRC8(d, 2) == d[2] && calcCRC8(d + 3, 2) == d[5]) {
            temp = -45.0f + 175.0f * (((uint16_t)d[0] << 8) | d[1]) / 65535.0f;
            hum  = constrain(-6.0f + 125.0f * (((uint16_t)d[3] << 8) | d[4]) / 65535.0f, 0.0f, 100.0f);
            return true;
        }
    }
    while (Wire.available()) Wire.read();
    return false;
}

// ==========================================
// 螢幕繪製
// ==========================================
void drawStaticLabels() {
    tft.fillScreen(TFT_BLACK);
    tft.setTextColor(TFT_CYAN, TFT_BLACK);
    tft.setFont(&fonts::Font4);
    tft.drawString("Env Monitor", 10, 5);
    tft.drawFastHLine(0, 38, 480, TFT_DARKGREY);

    tft.setTextColor(TFT_WHITE, TFT_BLACK);
    tft.drawString("Air Temp :", 10,  55);
    tft.drawString("Air Hum  :", 10,  95);
    tft.drawString("Water T  :", 10, 135);
    tft.drawString("Soil     :", 10, 175);
    tft.drawString("W. Level :", 10, 215);

    tft.setFont(&fonts::Font2);
    tft.setTextColor(TFT_DARKGREY, TFT_BLACK);
    tft.drawString("Touch:", 10, 295);
}

void drawValues() {
    char buf[24];
    const int X = 190;
    tft.setFont(&fonts::Font4);
    tft.setTextPadding(200);               // 覆蓋舊字避免殘影

    tft.setTextColor(TFT_YELLOW, TFT_BLACK);
    isnan(airTemp) ? snprintf(buf, sizeof(buf), "ERR") : snprintf(buf, sizeof(buf), "%.2f C", airTemp);
    tft.drawString(buf, X, 55);
    isnan(airHum) ? snprintf(buf, sizeof(buf), "ERR") : snprintf(buf, sizeof(buf), "%.2f %%", airHum);
    tft.drawString(buf, X, 95);

    tft.setTextColor(TFT_GREENYELLOW, TFT_BLACK);
    isnan(waterTemp) ? snprintf(buf, sizeof(buf), "ERR") : snprintf(buf, sizeof(buf), "%.2f C", waterTemp);
    tft.drawString(buf, X, 135);

    tft.setTextColor(TFT_ORANGE, TFT_BLACK);
    int soilPct = constrain(map(soilRaw, 4095, 1050, 0, 100), 0, 100); // 依實測校正
    snprintf(buf, sizeof(buf), "%d (%d%%)", soilRaw, soilPct);
    tft.drawString(buf, X, 175);

    tft.setTextColor(TFT_SKYBLUE, TFT_BLACK);
    int lvlPct = constrain(map(waterRaw, 500, 1770, 0, 100), 0, 100);    // 依實測校正
    snprintf(buf, sizeof(buf), "%d (%d%%)", waterRaw, lvlPct);
    tft.drawString(buf, X, 215);

    tft.setTextPadding(0);
}

// ==========================================
// 雲端上傳：HTTPS POST 到 Cloudflare Worker
// 注意：HTTP 請求本身是阻塞的 (~0.3-1 秒)，但每 10 秒才發生一次，
//       且設 3 秒逾時保底；期間觸控/螢幕會短暫停頓，可接受。
//       時間戳由伺服器端補上，ESP32 不需 NTP 對時。
// ==========================================
void uploadToCloud() {
    if (WiFi.status() != WL_CONNECTED) return;   // 沒網路直接跳過，不影響本地顯示

    WiFiClientSecure client;
    client.setInsecure();          // 跳過憑證驗證 (簡化；Cloudflare 傳輸仍為 TLS 加密)
    HTTPClient http;
    http.setTimeout(3000);         // 3 秒逾時，避免網路異常卡住主迴圈太久
    http.setConnectTimeout(3000);

    if (!http.begin(client, API_URL)) return;
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-API-Key", API_KEY);        // 只有持密鑰的本機能寫入

    // 組 JSON；NAN 以 null 送出，資料庫存 NULL 代表該次讀取失敗
    char body[192];
    char tA[16], tH[16], tW[16];
    if (isnan(airTemp))   { strcpy(tA, "null"); } else { snprintf(tA, sizeof(tA), "%.2f", airTemp); }
    if (isnan(airHum))    { strcpy(tH, "null"); } else { snprintf(tH, sizeof(tH), "%.2f", airHum); }
    if (isnan(waterTemp)) { strcpy(tW, "null"); } else { snprintf(tW, sizeof(tW), "%.2f", waterTemp); }
    snprintf(body, sizeof(body),
        "{\"air_temp\":%s,\"air_hum\":%s,\"water_temp\":%s,\"soil\":%d,\"water_level\":%d}",
        tA, tH, tW, soilRaw, waterRaw);

    int code = http.POST(body);
    Serial.printf("Upload HTTP %d\n", code);
    http.end();
}

// ==========================================
// WiFi 斷線自動重連 (非阻塞，每 15 秒最多試一次)
// ==========================================
void ensureWifi() {
    if (WiFi.status() == WL_CONNECTED) return;
    if (millis() - lastWifiRetry < 15000) return;
    lastWifiRetry = millis();
    Serial.println("WiFi reconnecting...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASS);   // begin 為非阻塞，不等待結果
}

// ==========================================
// setup
// ==========================================
void setup() {
    Serial.begin(115200);

    Wire.begin(PIN_SDA, PIN_SCL);
    Wire.setTimeOut(3);                    // I2C 逾時，防卡死 (仿 OMNI-TEC F-FW-009)

    ds18b20.begin();
    ds18b20.setResolution(12);
    ds18b20.setWaitForConversion(false);   // 非阻塞轉換

    analogSetPinAttenuation(PIN_SOIL,      ADC_11db);
    analogSetPinAttenuation(PIN_WATER_LVL, ADC_11db);

    tft.init();
    tft.setRotation(1);                    // 橫向 480x320
    drawStaticLabels();

    // ---- WiFi (非阻塞啟動：不等連線完成，連上前僅本地顯示) ----
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASS);

    lastCycleStartTime = millis();
    Serial.println("Env Monitor (LovyanGFX) ready.");
}

// ==========================================
// loop：非阻塞主迴圈 (仿 OMNI-TEC 1Hz 嚴格時序)
// ==========================================
void loop() {
    unsigned long currentMillis = millis();

    // ---- 觸控：LovyanGFX 內部用 pin_int 快速判斷，無觸控時幾乎零成本 ----
    uint16_t tx, ty;
    if (tft.getTouch(&tx, &ty) && currentMillis - lastTouchDraw > 50) {
        lastTouchDraw = currentMillis;     // 50ms 節流
        char buf[24];
        snprintf(buf, sizeof(buf), "X=%3d Y=%3d", tx, ty);
        tft.setFont(&fonts::Font2);
        tft.setTextColor(TFT_MAGENTA, TFT_BLACK);
        tft.setTextPadding(140);
        tft.drawString(buf, 70, 295);
        tft.setTextPadding(0);
    }

    // ---- T=0：觸發量測 (立即返回) ----
    if (!isMeasuring && (currentMillis - lastCycleStartTime >= CYCLE_PERIOD)) {
        lastCycleStartTime += CYCLE_PERIOD;
        isMeasuring = true;
        sht45Trigger();                    // ~8.3ms
        ds18b20.requestTemperatures();     // ~750ms，非阻塞
    }

    // ---- T=800ms：統一讀取並更新螢幕 ----
    if (isMeasuring && (currentMillis - lastCycleStartTime >= MEASURE_DELAY)) {
        isMeasuring = false;

        if (!sht45Read(airTemp, airHum)) { airTemp = NAN; airHum = NAN; }

        float wt = ds18b20.getTempCByIndex(0);
        waterTemp = (wt <= DEVICE_DISCONNECTED_C + 1) ? NAN : wt;

        soilRaw  = analogRead(PIN_SOIL);
        waterRaw = analogRead(PIN_WATER_LVL);

        drawValues();
        Serial.printf("AirT=%.2f AirH=%.2f WaterT=%.2f Soil=%d Level=%d\n",
                      airTemp, airHum, waterTemp, soilRaw, waterRaw);

        // ---- 每 10 個週期 (10 秒) 上傳雲端一次 ----
        if (++cycleCount >= UPLOAD_EVERY_N_CYCLES) {
            cycleCount = 0;
            uploadToCloud();
        }
    }

    // ---- WiFi 斷線監測與重連 (非阻塞) ----
    ensureWifi();
}
