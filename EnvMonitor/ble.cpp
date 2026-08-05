/**********************************************************************
 * ble.cpp — BLE 讀值服務實作 (見 ble.h、BLE.md)
 *
 * 依賴函式庫：NimBLE-Arduino 2.x (Library Manager 安裝)。
 *   註：2.x 的 ServerCallbacks 多了 NimBLEConnInfo& 參數、廣播改用
 *   setName/enableScanResponse，與 1.x 不相容。
 * GATT 契約 (UUID / JSON 格式) 見 BLE.md，與手機端 (../android/ 原生 App、
 * 備援的 ../cloud/src/ble-app.html 網頁版) 一致。
 **********************************************************************/
#include "config.h"

#if ENABLE_BLE

#include "ble.h"
#include "ble_ota.h"          // OTA characteristics (掛在同一個 service 上)
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
static const char* INFO_UUID     = "8f2a0006-b8c3-4e6a-9f1d-2a7c9e5b1a01"; // 韌體版本/heap

static NimBLECharacteristic* readingsChar = nullptr;
static NimBLECharacteristic* statusChar   = nullptr;
static NimBLECharacteristic* infoChar     = nullptr;
static bool clientConnected = false;

// 設備自報資訊。fw/built/chip 是編譯期常數，heap 每次更新 —— OTA 後手機讀
// 這支就能確認實際跑的是哪一版韌體 (更新前後版本號應該不同)，heap 則方便
// 遠端判斷記憶體是否吃緊。
static void buildInfoJson(char* buf, size_t cap) {
    snprintf(buf, cap,
             "{\"fw\":\"%s\",\"built\":\"%s\",\"chip\":\"esp32\",\"heap\":%u}",
             FIRMWARE_VERSION, FIRMWARE_BUILD_DATE, (unsigned)ESP.getFreeHeap());
}

static void updateInfoChar() {
    if (!infoChar) return;
    char info[128];
    buildInfoJson(info, sizeof(info));
    infoChar->setValue((const uint8_t*)info, strlen(info));
}

// 斷線後自動重新廣播，讓手機能再次連上 (NimBLE 2.x callback 簽章)
class ServerCB : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* server, NimBLEConnInfo& connInfo) override {
        clientConnected = true;
        // 主動請求高速連線參數 (7.5ms ~ 15ms)，大幅提升 BLE OTA 傳輸吞吐量
        server->updateConnParams(connInfo.getConnHandle(), 6, 12, 0, 400);
    }
    void onDisconnect(NimBLEServer*, NimBLEConnInfo&, int) override {
        clientConnected = false;
        otaHandleDisconnect();          // OTA 進行中斷線 → 安全 abort，舊韌體不變
        NimBLEDevice::startAdvertising();
    }
};

void bleInit() {
    NimBLEDevice::init("env-monitor");
    NimBLEDevice::setMTU(517);       // 大 MTU 讓 OTA 每筆封包更大、傳更快

    NimBLEServer* server = NimBLEDevice::createServer();
    server->setCallbacks(new ServerCB());

    NimBLEService* svc = server->createService(SVC_UUID);
    readingsChar = svc->createCharacteristic(
        READINGS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    statusChar = svc->createCharacteristic(
        STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    infoChar = svc->createCharacteristic(
        INFO_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    updateInfoChar();                // 先填好初值，手機一連上 Read 就有東西
#if ENABLE_BLE_OTA
    otaSetup(svc);                   // 在同一 service 上加 OTA control/data characteristics
#endif
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

    // 設備資訊：只有 heap 會變，但整包重建比較單純 (每秒一次成本可忽略)
    updateInfoChar();

    // 只有手機連著才推播 (省射頻)；未連線時上面已更新值，供下次 Read
    if (clientConnected) {
        readingsChar->notify();
        statusChar->notify();
        if (infoChar) infoChar->notify();
    }
}

#endif // ENABLE_BLE
