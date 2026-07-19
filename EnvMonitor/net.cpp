#include "net.h"
#include "sensors.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoOTA.h>
#include "secrets.h"   // WiFi 帳密 + API 網址/密鑰 (複製 secrets.h.example 填入)

int uploadState = 0;
static unsigned long lastWifiRetry = 0;
static bool otaStarted = false;

// ==========================================
// OTA 無線更新 (Arduino IDE: Tools -> Port -> env-monitor at ...)
// 注意：需選有 OTA 槽的 Partition Scheme (Default 可；Huge APP 不行，
//       flash 不足改用 "Minimal SPIFFS (1.9MB APP with OTA)")
// ==========================================
void netLoop() {
    if (WiFi.status() != WL_CONNECTED) return;
    if (!otaStarted) {
        otaStarted = true;
        ArduinoOTA.setHostname("env-monitor");
        ArduinoOTA.setPassword(OTA_PASS);
        ArduinoOTA.onStart([]() { Serial.println("OTA start"); });
        ArduinoOTA.onEnd([]()   { Serial.println("OTA done, rebooting"); });
        ArduinoOTA.onError([](ota_error_t e) { Serial.printf("OTA error %u\n", e); });
        ArduinoOTA.begin();
        Serial.println("OTA ready");
    }
    ArduinoOTA.handle();
}

void netInit() {
    // 非阻塞啟動：不等連線完成，連上前僅本地顯示
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASS);
}

void netEnsure() {
    if (WiFi.status() == WL_CONNECTED) return;
    if (millis() - lastWifiRetry < 15000) return;
    lastWifiRetry = millis();
    Serial.println("WiFi reconnecting...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASS);   // begin 為非阻塞，不等待結果
}

// ==========================================
// 雲端上傳：HTTPS POST 到 Cloudflare Worker
// 注意：HTTP 請求本身是阻塞的 (~0.3-1 秒)，但每 10 秒才發生一次，
//       且設 3 秒逾時保底；期間觸控/螢幕會短暫停頓，可接受。
//       時間戳由伺服器端補上，ESP32 不需 NTP 對時。
// ==========================================
void netUpload() {
    if (WiFi.status() != WL_CONNECTED) return;   // 沒網路直接跳過，不影響本地顯示

    WiFiClientSecure client;
    client.setInsecure();          // 跳過憑證驗證 (簡化；Cloudflare 傳輸仍為 TLS 加密)
    HTTPClient http;
    http.setTimeout(3000);         // 3 秒逾時，避免網路異常卡住主迴圈太久
    http.setConnectTimeout(3000);

    if (!http.begin(client, API_URL)) return;
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-API-Key", API_KEY);        // 只有持密鑰的本機能寫入

    // 組 JSON；讀取失敗/無效以 null 送出，資料庫存 NULL
    char body[192];
    char tA[16], tH[16], tW[16], tS[12], tL[12];
    if (isnan(airTemp))   { strcpy(tA, "null"); } else { snprintf(tA, sizeof(tA), "%.2f", airTemp); }
    if (isnan(airHum))    { strcpy(tH, "null"); } else { snprintf(tH, sizeof(tH), "%.2f", airHum); }
    if (isnan(waterTemp)) { strcpy(tW, "null"); } else { snprintf(tW, sizeof(tW), "%.2f", waterTemp); }
    if (!soilValid)       { strcpy(tS, "null"); } else { snprintf(tS, sizeof(tS), "%d", soilRaw); }
    if (!waterValid)      { strcpy(tL, "null"); } else { snprintf(tL, sizeof(tL), "%d", waterRaw); }
    snprintf(body, sizeof(body),
        "{\"air_temp\":%s,\"air_hum\":%s,\"water_temp\":%s,\"soil\":%s,\"water_level\":%s}",
        tA, tH, tW, tS, tL);

    int code = http.POST(body);
    uploadState = (code == 200) ? 1 : 2;
    Serial.printf("Upload HTTP %d\n", code);
    http.end();
}
