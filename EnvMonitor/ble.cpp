/**********************************************************************
 * ble.cpp — BLE 讀值服務實作 (見 ble.h、BLE.md)
 *
 * 依賴函式庫：NimBLE-Arduino 2.x (Library Manager 安裝)。
 *   註：2.x 的 ServerCallbacks 多了 NimBLEConnInfo& 參數、廣播改用
 *   setName/enableScanResponse，與 1.x 不相容。
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

// 斷線後自動重新廣播，讓手機能再次連上 (NimBLE 2.x callback 簽章)
class ServerCB : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer*, NimBLEConnInfo&) override {
        clientConnected = true;
    }
    void onDisconnect(NimBLEServer*, NimBLEConnInfo&, int) override {
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

    // NimBLE 2.x：名稱與 128-bit UUID 同時放不進 31 bytes 廣播封包，
    // 開 scan response 讓名稱放進回應封包 (App 以 service UUID 掃描)
    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    adv->setName("env-monitor");
    adv->addServiceUUID(SVC_UUID);
    adv->enableScanResponse(true);
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
