/**********************************************************************
 * 環境監測系統 (Environment Monitor) — 入口檔
 * 硬體：ESP32 (NodeMCU-32) + 3.5" ILI9488 觸控螢幕
 *       SHT45 (空氣溫濕度) / DS18B20 (水溫) / 土壤濕度 / 水位
 *
 * ▶ 程式導覽請看專案根目錄 CLAUDE.md，不需通讀全部檔案
 *   config.h      腳位/時序      display_hw.h/.cpp 螢幕硬體
 *   theme.h       色票/版面      lang.h + i18n.*   多語系
 *   sensors.*     感測讀取       calibration.*     校準值(NVS)
 *   net.*         WiFi/上傳      ui.h + ui_*.cpp   畫面與觸控
 *
 * 需安裝函式庫：LovyanGFX、OneWire、DallasTemperature、ArduinoJson
 * flash 不足時：Arduino IDE -> Partition Scheme -> Huge APP
 **********************************************************************/
#include "config.h"
#include "display_hw.h"
#include "sensors.h"
#include "calibration.h"
#include "i18n.h"
#include "net.h"
#include "ui.h"

static uint8_t cycleCount = 0;      // 量測週期計數 (供上傳節流)
static bool touchHeld = false;      // 觸控防彈跳 (放開才能再按)
static bool screenOn = true;        // 螢幕保護狀態
static unsigned long lastTouchMs = 0;

// 熄屏：黑畫面 + 停止重繪 (量測與上傳照常)；編輯畫面自動退回主畫面
static void screenSleep() {
    screenOn = false;
    if (uiMode != UI_MAIN) uiMode = UI_MAIN;
    tft.fillScreen(TFT_BLACK);
    if (PIN_TFT_BL >= 0) digitalWrite(PIN_TFT_BL, LOW);
}
static void screenWake() {
    screenOn = true;
    if (PIN_TFT_BL >= 0) digitalWrite(PIN_TFT_BL, HIGH);
    uiDrawMain();
    uiDrawValues();
}

void setup() {
    Serial.begin(115200);
    if (PIN_TFT_BL >= 0) { pinMode(PIN_TFT_BL, OUTPUT); digitalWrite(PIN_TFT_BL, HIGH); }
    lastTouchMs = millis();
    calibInit();       // NVS 校準值
    i18nInit();        // 語言表 + 上次選的語言
    sensorsInit();     // I2C / 1-Wire / ADC
    uiInit();          // 螢幕 + 主畫面
    netInit();         // WiFi (非阻塞)
    Serial.println("Env Monitor ready.");
}

void loop() {
    unsigned long now = millis();

    // ---- 觸控 (單次觸發：放開後才能再按)；熄屏時第一下只喚醒 ----
    uint16_t tx, ty;
    bool touched = tft.getTouch(&tx, &ty);
    if (touched) lastTouchMs = now;
    if (touched && !touchHeld) {
        touchHeld = true;
        if (!screenOn) screenWake();
        else           uiHandleTouch(tx, ty);
    }
    if (!touched) touchHeld = false;

    // ---- 螢幕保護：無觸控逾時熄屏 ----
    if (screenOn && now - lastTouchMs >= SCREEN_TIMEOUT_MS) screenSleep();

    // ---- 1Hz 非阻塞量測 (時序由 sensors 模組管理) ----
    if (sensorsLoop(now)) {
        if (!screenOn)              ;                 // 熄屏時不重繪
        else if (uiMode == UI_MAIN) uiDrawValues();
        else                        uiDrawEditRaw();  // 編輯畫面只更新即時原始讀值

        Serial.printf("AirT=%.2f AirH=%.2f WaterT=%.2f Soil=%d Level=%d\n",
                      airTemp, airHum, waterTemp, soilRaw, waterRaw);

        if (++cycleCount >= UPLOAD_EVERY_N_CYCLES) {   // 每 10 秒上傳一次
            cycleCount = 0;
            netUpload();
        }
    }

    // ---- WiFi 斷線監測與重連 (非阻塞) + OTA ----
    netEnsure();
    netLoop();
}
