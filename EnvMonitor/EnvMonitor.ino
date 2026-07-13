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
#include <Preferences.h>   // NVS 儲存校準值 (斷電保留)
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
        bcfg.pin_mosi   = 23;         // MOSI (螢幕 SDI 與 觸控 T_DIN 合併)
        bcfg.pin_miso   = 19;         // MISO：只接觸控 T_DO！
                                      // 螢幕 SDO 千萬不要接——ILI9488 硬體缺陷：
                                      // CS 拉高後 SDO 不會放開 MISO 線，會把觸控訊號壓死
        bcfg.pin_dc     = 27;          // TFT_DC
        _bus.config(bcfg);
        _panel.setBus(&_bus);

        // --- ILI9488 面板 ---
        auto pcfg = _panel.config();
        pcfg.pin_cs   = 14;           // TFT_CS
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
#define PIN_WATER_LVL 33
#define PIN_WATER_PWR 32
#define PIN_SOIL_PWR  26
#define PIN_SOIL      35
#define PIN_SDA       21
#define PIN_SCL       22
#define SHT45_ADDR    0x44
#define SHT45_CMD_HIGH 0xFD          // 高精度量測 (~8.3ms)

const unsigned long CYCLE_PERIOD  = 1000;  // 1Hz 週期
const unsigned long MEASURE_DELAY = 800;   // T=800ms 讀取
const uint8_t UPLOAD_EVERY_N_CYCLES = 10;  // 每 10 個週期 (10 秒) 上傳一次雲端

const unsigned long WATER_PWR_LEAD = 1;  // 讀取前 Xms 上電，目前測試不需要提前通電，因此只留 1ms 確保偵測瞬間確實到3.3V，且因為是水中導電所以通電越短耗損越少
const unsigned long SOIL_PWR_LEAD  = 1;  // 通電越短耗損越少，且測試只用 1ms 足夠

bool waterPwrOn = false, soilPwrOn = false;

// ==========================================
// 校準值 (原始 ADC -> 0~100%)，預設寫死於此，
// 可由螢幕 EDIT 按鈕修改並存入 NVS (斷電保留)
// ==========================================
int soilMin  = 1300, soilMax  = 4000;   // 土壤：min=0% (乾), max=100% (濕)
int waterMin = 1100,  waterMax = 3000;   // 水位：min=0%, max=100% (依實測校正)
Preferences prefs;

// ---- 觸控 UI 狀態 ----
enum UiMode { UI_MAIN, UI_EDIT };
UiMode uiMode = UI_MAIN;
bool editIsSoil = true;                 // 編輯中：true=土壤, false=水位
int  editMin = 0, editMax = 0;          // 編輯畫面暫存值 (按儲存才生效)
bool touchHeld = false;                 // 觸控防彈跳 (放開才能再按)

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
// 螢幕繪製 — 主畫面
// ==========================================
// 主畫面 EDIT 按鈕位置 (Soil 與 W.Level 欄位右方)
#define BTN_EDIT_X 400
#define BTN_EDIT_W 70
#define BTN_EDIT_H 32
#define BTN_SOIL_Y  170
#define BTN_WATER_Y 210

bool inBox(int x, int y, int bx, int by, int bw, int bh) {
    return x >= bx && x < bx + bw && y >= by && y < by + bh;
}

void drawBtn(int x, int y, int w, int h, const char *label, uint16_t color) {
    tft.drawRoundRect(x, y, w, h, 6, color);
    tft.setTextColor(color, TFT_BLACK);
    tft.setTextDatum(textdatum_t::middle_center);
    tft.drawString(label, x + w / 2, y + h / 2);
    tft.setTextDatum(textdatum_t::top_left);
}

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

    // Soil / W.Level 右方的 EDIT 按鈕 (點擊進入校準畫面)
    tft.setFont(&fonts::Font2);
    drawBtn(BTN_EDIT_X, BTN_SOIL_Y,  BTN_EDIT_W, BTN_EDIT_H, "EDIT", TFT_ORANGE);
    drawBtn(BTN_EDIT_X, BTN_WATER_Y, BTN_EDIT_W, BTN_EDIT_H, "EDIT", TFT_SKYBLUE);

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

    tft.setTextPadding(190);               // 縮小覆蓋範圍，避免蓋到右方 EDIT 按鈕

    tft.setTextColor(TFT_ORANGE, TFT_BLACK);
    int soilPct = constrain(map(soilRaw, soilMin, soilMax, 0, 100), 0, 100);
    snprintf(buf, sizeof(buf), "%d (%d%%)", soilRaw, soilPct);
    tft.drawString(buf, X, 175);

    tft.setTextColor(TFT_SKYBLUE, TFT_BLACK);
    int lvlPct = constrain(map(waterRaw, waterMin, waterMax, 0, 100), 0, 100);
    snprintf(buf, sizeof(buf), "%d (%d%%)", waterRaw, lvlPct);
    tft.drawString(buf, X, 215);

    tft.setTextPadding(0);
}

// ==========================================
// 螢幕繪製 — 校準編輯畫面
//   MIN 對應 0%、MAX 對應 100%
//   -100/-10/+10/+100 微調、RAW 直接取目前讀值
// ==========================================
// 編輯畫面版面配置
#define EDT_ROW_MIN_Y 105     // MIN 按鈕列 y
#define EDT_ROW_MAX_Y 175     // MAX 按鈕列 y
#define EDT_ROW_H     40
#define EDT_BTN_W     60
#define EDT_VAL_X     10      // 數值顯示 x
#define EDT_B1_X      130     // -100
#define EDT_B2_X      195     // -10
#define EDT_B3_X      260     // +10
#define EDT_B4_X      325     // +100
#define EDT_BRAW_X    390     // RAW (取目前讀值)
#define EDT_BRAW_W    80
#define EDT_SAVE_X    100
#define EDT_CANCEL_X  260
#define EDT_ACT_Y     258
#define EDT_ACT_W     120
#define EDT_ACT_H     50

void drawEditRaw() {           // 目前原始讀值 (量測週期即時更新)
    char buf[24];
    int raw = editIsSoil ? soilRaw : waterRaw;
    tft.setFont(&fonts::Font4);
    tft.setTextColor(TFT_YELLOW, TFT_BLACK);
    tft.setTextPadding(120);
    snprintf(buf, sizeof(buf), "%d", raw);
    tft.drawString(buf, 190, 48);
    tft.setTextPadding(0);
}

void drawEditValues() {        // MIN/MAX 目前設定值
    char buf[16];
    tft.setFont(&fonts::Font4);
    tft.setTextColor(TFT_WHITE, TFT_BLACK);
    tft.setTextPadding(110);
    snprintf(buf, sizeof(buf), "%d", editMin);
    tft.drawString(buf, EDT_VAL_X, EDT_ROW_MIN_Y + 7);
    snprintf(buf, sizeof(buf), "%d", editMax);
    tft.drawString(buf, EDT_VAL_X, EDT_ROW_MAX_Y + 7);
    tft.setTextPadding(0);
}

void drawEditScreen() {
    tft.fillScreen(TFT_BLACK);
    tft.setFont(&fonts::Font4);
    tft.setTextColor(editIsSoil ? TFT_ORANGE : TFT_SKYBLUE, TFT_BLACK);
    tft.drawString(editIsSoil ? "Soil Calibration" : "Water Level Calibration", 10, 5);
    tft.drawFastHLine(0, 38, 480, TFT_DARKGREY);

    tft.setTextColor(TFT_WHITE, TFT_BLACK);
    tft.drawString("Raw :", 100, 48);

    tft.setFont(&fonts::Font2);
    tft.setTextColor(TFT_DARKGREY, TFT_BLACK);
    tft.drawString("MIN  (= 0%)",   EDT_VAL_X, EDT_ROW_MIN_Y - 18);
    tft.drawString("MAX  (= 100%)", EDT_VAL_X, EDT_ROW_MAX_Y - 18);

    // 兩列調整按鈕
    for (int row = 0; row < 2; row++) {
        int y = row == 0 ? EDT_ROW_MIN_Y : EDT_ROW_MAX_Y;
        drawBtn(EDT_B1_X,   y, EDT_BTN_W,  EDT_ROW_H, "-100", TFT_WHITE);
        drawBtn(EDT_B2_X,   y, EDT_BTN_W,  EDT_ROW_H, "-10",  TFT_WHITE);
        drawBtn(EDT_B3_X,   y, EDT_BTN_W,  EDT_ROW_H, "+10",  TFT_WHITE);
        drawBtn(EDT_B4_X,   y, EDT_BTN_W,  EDT_ROW_H, "+100", TFT_WHITE);
        drawBtn(EDT_BRAW_X, y, EDT_BRAW_W, EDT_ROW_H, "RAW",  TFT_YELLOW);
    }

    drawBtn(EDT_SAVE_X,   EDT_ACT_Y, EDT_ACT_W, EDT_ACT_H, "SAVE",   TFT_GREENYELLOW);
    drawBtn(EDT_CANCEL_X, EDT_ACT_Y, EDT_ACT_W, EDT_ACT_H, "CANCEL", TFT_RED);

    drawEditValues();
    drawEditRaw();
}

// ==========================================
// 校準編輯：開啟 / 儲存 / 關閉
// ==========================================
void openEditor(bool isSoil) {
    editIsSoil = isSoil;
    editMin = isSoil ? soilMin : waterMin;
    editMax = isSoil ? soilMax : waterMax;
    uiMode = UI_EDIT;
    drawEditScreen();
}

void closeEditor() {
    uiMode = UI_MAIN;
    drawStaticLabels();
    drawValues();
}

void saveEditor() {
    if (editMin == editMax) {          // 防呆：min=max 會除以零
        tft.setFont(&fonts::Font2);
        tft.setTextColor(TFT_RED, TFT_BLACK);
        tft.drawString("MIN = MAX not allowed!", 10, 235);
        return;
    }
    if (editIsSoil) { soilMin = editMin;  soilMax = editMax; }
    else            { waterMin = editMin; waterMax = editMax; }

    prefs.putInt(editIsSoil ? "sMin" : "wMin", editMin);
    prefs.putInt(editIsSoil ? "sMax" : "wMax", editMax);
    Serial.printf("Calib saved: %s MIN=%d MAX=%d\n",
                  editIsSoil ? "soil" : "water", editMin, editMax);
    closeEditor();
}

// ==========================================
// 觸控事件處理 (單次觸發，放開才能再按)
// ==========================================
void handleTouch(int x, int y) {
    if (uiMode == UI_MAIN) {
        if (inBox(x, y, BTN_EDIT_X, BTN_SOIL_Y,  BTN_EDIT_W, BTN_EDIT_H)) openEditor(true);
        else if (inBox(x, y, BTN_EDIT_X, BTN_WATER_Y, BTN_EDIT_W, BTN_EDIT_H)) openEditor(false);
        return;
    }

    // ---- 編輯畫面 ----
    int *v = nullptr;
    int rowY = 0;
    if (y >= EDT_ROW_MIN_Y && y < EDT_ROW_MIN_Y + EDT_ROW_H) { v = &editMin; rowY = EDT_ROW_MIN_Y; }
    if (y >= EDT_ROW_MAX_Y && y < EDT_ROW_MAX_Y + EDT_ROW_H) { v = &editMax; rowY = EDT_ROW_MAX_Y; }

    if (v) {
        if      (inBox(x, y, EDT_B1_X,   rowY, EDT_BTN_W,  EDT_ROW_H)) *v -= 100;
        else if (inBox(x, y, EDT_B2_X,   rowY, EDT_BTN_W,  EDT_ROW_H)) *v -= 10;
        else if (inBox(x, y, EDT_B3_X,   rowY, EDT_BTN_W,  EDT_ROW_H)) *v += 10;
        else if (inBox(x, y, EDT_B4_X,   rowY, EDT_BTN_W,  EDT_ROW_H)) *v += 100;
        else if (inBox(x, y, EDT_BRAW_X, rowY, EDT_BRAW_W, EDT_ROW_H)) *v = editIsSoil ? soilRaw : waterRaw;
        *v = constrain(*v, 0, 4095);   // ESP32 ADC 12-bit 範圍
        drawEditValues();
        return;
    }

    if (inBox(x, y, EDT_SAVE_X,   EDT_ACT_Y, EDT_ACT_W, EDT_ACT_H)) saveEditor();
    else if (inBox(x, y, EDT_CANCEL_X, EDT_ACT_Y, EDT_ACT_W, EDT_ACT_H)) closeEditor();
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

    pinMode(PIN_WATER_PWR, OUTPUT);
    digitalWrite(PIN_WATER_PWR, LOW);
    pinMode(PIN_SOIL_PWR, OUTPUT);
    digitalWrite(PIN_SOIL_PWR, LOW);



    ds18b20.begin();
    ds18b20.setResolution(12);
    ds18b20.setWaitForConversion(false);   // 非阻塞轉換

    analogSetPinAttenuation(PIN_SOIL,      ADC_11db);
    analogSetPinAttenuation(PIN_WATER_LVL, ADC_11db);

    // ---- 從 NVS 載入校準值 (沒存過就用上方寫死的預設值) ----
    prefs.begin("calib", false);
    soilMin  = prefs.getInt("sMin", soilMin);
    soilMax  = prefs.getInt("sMax", soilMax);
    waterMin = prefs.getInt("wMin", waterMin);
    waterMax = prefs.getInt("wMax", waterMax);
    Serial.printf("Calib loaded: soil %d~%d, water %d~%d\n",
                  soilMin, soilMax, waterMin, waterMax);

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
    bool touched = tft.getTouch(&tx, &ty);
    if (touched && !touchHeld) {           // 單次觸發：放開後才能再按
        touchHeld = true;
        handleTouch(tx, ty);
    }
    if (!touched) touchHeld = false;

    // 主畫面觸控座標除錯顯示
    if (touched && uiMode == UI_MAIN && currentMillis - lastTouchDraw > 50) {
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
    
    // 設定合適的感測範圍，針對該感測器全通/否的優化
    analogSetPinAttenuation(PIN_WATER_LVL, ADC_2_5db);
    analogSetPinAttenuation(PIN_SOIL, ADC_6db);
    // ---- T= 每個周期結束前 水位檢測前Xms，暖機----
    if (isMeasuring && !waterPwrOn &&
    (currentMillis - lastCycleStartTime >= MEASURE_DELAY - WATER_PWR_LEAD)) {
    digitalWrite(PIN_WATER_PWR, HIGH);
    waterPwrOn = true;
    }
    // ---- T= 每個周期結束前 土壤溼度檢測前Xms，暖機----
    if (isMeasuring && !soilPwrOn &&
    (currentMillis - lastCycleStartTime >= MEASURE_DELAY - SOIL_PWR_LEAD)) {
    digitalWrite(PIN_SOIL_PWR, HIGH);
     soilPwrOn = true;
    }

    // ---- T=800ms：統一讀取並更新螢幕 ----
    if (isMeasuring && (currentMillis - lastCycleStartTime >= MEASURE_DELAY)) {
        isMeasuring = false;

        if (!sht45Read(airTemp, airHum)) { airTemp = NAN; airHum = NAN; }

        float wt = ds18b20.getTempCByIndex(0);
        waterTemp = (wt <= DEVICE_DISCONNECTED_C + 1) ? NAN : wt;

        soilRaw  = analogRead(PIN_SOIL);
        waterRaw = analogRead(PIN_WATER_LVL);

        // 水位
        digitalWrite(PIN_WATER_PWR, LOW);
        waterPwrOn = false;
        // 土壤
        digitalWrite(PIN_SOIL_PWR, LOW);
        soilPwrOn = false;

        if (uiMode == UI_MAIN) drawValues();
        else                   drawEditRaw();   // 編輯畫面只更新即時原始讀值
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
