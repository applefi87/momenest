/**********************************************************************
 * ble_ota.cpp — 透過 BLE 傳封包更新韌體 (見 ble_ota.h、BLE.md)
 *
 * 依賴：NimBLE-Arduino 2.x + ESP32 內建 Update 庫。協定解析用 ota_protocol
 * (有 host 單元測試)。data characteristic 用 Write With Response，由 BLE 層
 * 天然流控 (手機送一塊、設備寫完 flash 才回應、手機再送下一塊)，不會爆緩衝。
 *
 * 安全：任何錯誤 / 中途斷線 → Update.abort()，啟動分區不切換，舊韌體照跑。
 **********************************************************************/
#include "config.h"

#if ENABLE_BLE && ENABLE_BLE_OTA

#include "ble_ota.h"
#include "ota_protocol.h"
#include <NimBLEDevice.h>
#include <Update.h>
#include <Arduino.h>

// OTA characteristics UUID (單一事實來源見 BLE.md，手機端須一致)
static const char* OTA_CTRL_UUID = "8f2a0004-b8c3-4e6a-9f1d-2a7c9e5b1a01"; // 下指令/回結果
static const char* OTA_DATA_UUID = "8f2a0005-b8c3-4e6a-9f1d-2a7c9e5b1a01"; // 灌韌體位元組

static NimBLECharacteristic* ctrlChar = nullptr;
static volatile bool    otaActive   = false;
static uint32_t         otaExpected = 0;
static uint32_t         otaReceived = 0;
static volatile uint8_t otaPct      = 0;

// control notify 回報：[status][detail]
enum {
    OTA_ST_BEGIN_OK = 0x01,
    OTA_ST_END_OK   = 0x02,
    OTA_ST_ABORTED  = 0x03,
    OTA_ST_PROGRESS = 0x10,
    OTA_ST_ERROR    = 0xEE,
};

static void notifyStatus(uint8_t st, uint8_t detail = 0) {
    if (!ctrlChar) return;
    uint8_t msg[2] = { st, detail };
    ctrlChar->setValue(msg, 2);
    ctrlChar->notify();
}

static void otaReset() {
    otaActive = false; otaExpected = 0; otaReceived = 0; otaPct = 0;
}

static void handleControl(const uint8_t* data, size_t len) {
    OtaControl c = parseOtaControl(data, len);
    if (!c.valid) { notifyStatus(OTA_ST_ERROR, 1); return; }

    switch (c.cmd) {
        case OTA_CMD_BEGIN:
            if (otaActive) Update.abort();
            otaReset();
            if (!Update.begin(c.size)) { notifyStatus(OTA_ST_ERROR, 2); return; }
            otaActive = true; otaExpected = c.size;
            Serial.printf("BLE OTA begin, size=%u\n", (unsigned)c.size);
            notifyStatus(OTA_ST_BEGIN_OK);
            break;

        case OTA_CMD_END:
            if (!otaActive) { notifyStatus(OTA_ST_ERROR, 3); return; }
            if (otaReceived == otaExpected && Update.end(true)) {
                Serial.println("BLE OTA success, rebooting");
                notifyStatus(OTA_ST_END_OK);
                delay(300);              // 讓 notify 送出再重開
                ESP.restart();
            } else {
                Serial.printf("BLE OTA end fail (recv=%u/%u err=%d)\n",
                              (unsigned)otaReceived, (unsigned)otaExpected, Update.getError());
                Update.abort(); otaReset();
                notifyStatus(OTA_ST_ERROR, 4);
            }
            break;

        case OTA_CMD_ABORT:
            if (otaActive) { Update.abort(); otaReset(); }
            notifyStatus(OTA_ST_ABORTED);
            break;

        default: break;
    }
}

static void handleData(const uint8_t* data, size_t len) {
    if (!otaActive || len == 0) return;
    if (Update.write((uint8_t*)data, len) != len) {
        Serial.println("BLE OTA write fail");
        Update.abort(); otaReset();
        notifyStatus(OTA_ST_ERROR, 5);
        return;
    }
    otaReceived += len;
    uint8_t pct = (uint8_t)otaPercent(otaReceived, otaExpected);
    if (pct != otaPct) {           // 每 1% 回報一次進度
        otaPct = pct;
        notifyStatus(OTA_ST_PROGRESS, pct);
    }
}

// NimBLE 2.x characteristic callback 簽章 (含 NimBLEConnInfo&)
class CtrlCB : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c, NimBLEConnInfo&) override {
        NimBLEAttValue v = c->getValue();
        handleControl(v.data(), v.length());
    }
};
class DataCB : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c, NimBLEConnInfo&) override {
        NimBLEAttValue v = c->getValue();
        handleData(v.data(), v.length());
    }
};

void otaSetup(NimBLEService* svc) {
    ctrlChar = svc->createCharacteristic(
        OTA_CTRL_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY);
    ctrlChar->setCallbacks(new CtrlCB());

    NimBLECharacteristic* dataChar = svc->createCharacteristic(
        OTA_DATA_UUID, NIMBLE_PROPERTY::WRITE);   // Write With Response → 天然流控
    dataChar->setCallbacks(new DataCB());
}

bool    otaIsActive()   { return otaActive; }
uint8_t otaPercentNow() { return otaPct; }
void    otaHandleDisconnect() {
    if (otaActive) {
        Update.abort(); otaReset();
        Serial.println("BLE OTA aborted (disconnect)");
    }
}

#else  // ---- 停用時的 stub (供 .ino / ble.cpp 參照仍可連結) ----
#include "ble_ota.h"
void    otaSetup(NimBLEService*) {}
bool    otaIsActive()   { return false; }
uint8_t otaPercentNow() { return 0; }
void    otaHandleDisconnect() {}
#endif
