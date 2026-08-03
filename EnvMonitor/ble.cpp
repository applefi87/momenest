/**********************************************************************
 * ble.cpp — BLE 讀值服務實作 (見 ble.h、BLE.md)
 *
 * 依賴函式庫：NimBLE-Arduino (Library Manager 安裝)。本檔對應 NimBLE 1.4.x API。
 * GATT 契約 (UUID / JSON 格式) 見 BLE.md，與 app/ 手機端一致。
 **********************************************************************/
#include "config.h"

#if ENABLE_BLE

#include "ble.h"
#include "sensors.h"
#include "reading_format.h"   // 感測值→JSON (與 WiFi 上傳共用，見 tests/)
#include "net.h"              // uploadState
#include <WiFi.h>
#include <NimBLEDevice.h>
#include <string.h>
#include <stdio.h>

// GATT UUID (單一事實來源見 BLE.md；手機 App 必須用相同 UUID)
static const char* SVC_UUID      = "8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01";
static const char* READINGS_UUID = "8f2a0002-b8c3-4e6a-9f1d-2a7c9e5b1a01"; // 感測值 JSON
static const char* STATUS_UUID   = "8f2a0003-b8c3-4e6a-9f1d-2a7c9e5b1a01"; // wifi/upload/ip

static NimBLECharacteristic* readingsChar = nullptr;
static NimBLECharacteristic* statusChar   = nullptr;
static bool clientConnected = false;

// 斷線後自動重新廣播，讓手機能再次連上
class ServerCB : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer*) override    { clientConnected = true; }
    void onDisconnect(NimBLEServer*) override {
        clientConnected = false;
        NimBLEDevice::startAdvertising();
    }
};

void bleInit() {
    NimBLEDevice::init("env-monitor");

    NimBLEServer* server = NimBLEDevice::createServer();
    server->setCallbacks(new ServerCB());

    NimBLEService* svc = server->createService(SVC_UUID);
    readingsChar = svc->createCharacteristic(
        READINGS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    statusChar = svc->createCharacteristic(
        STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    svc->start();

    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    adv->addServiceUUID(SVC_UUID);
    adv->setScanResponse(true);
    NimBLEDevice::startAdvertising();

    Serial.println("BLE advertising as \"env-monitor\"");
}

void bleNotify() {
    if (!readingsChar) return;   // 尚未 bleInit()

    // 感測值 (與 WiFi 上傳同格式，受 host 單元測試保護)
    char body[192];
    SensorReading r = { airTemp, airHum, waterTemp,
                        soilRaw, waterRaw, soilValid, waterValid };
    formatReadingJson(body, sizeof(body), r);
    readingsChar->setValue((const uint8_t*)body, strlen(body));

    // 連線健康度：WiFi / 上傳結果 / IP
    char st[96];
    bool wifiUp = (WiFi.status() == WL_CONNECTED);
    snprintf(st, sizeof(st), "{\"wifi\":%d,\"upload\":%d,\"ip\":\"%s\"}",
             wifiUp ? 1 : 0, uploadState,
             wifiUp ? WiFi.localIP().toString().c_str() : "");
    statusChar->setValue((const uint8_t*)st, strlen(st));

    // 只有手機連著才推播 (省射頻)；未連線時上面已更新值，供下次 Read
    if (clientConnected) {
        readingsChar->notify();
        statusChar->notify();
    }
}

#endif // ENABLE_BLE
