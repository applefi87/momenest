/**********************************************************************
 * ble_ota.h — 透過 BLE 傳封包更新韌體 (見 ble_ota.cpp、BLE.md)
 *
 * 在既有 BLE service 上加兩個 characteristic：control (下指令/回結果) 與
 * data (灌韌體位元組)。收到的位元組用 ESP32 內建 Update 庫寫進另一個 OTA
 * 分區，全部收完並驗證通過才切換啟動分區、重開機。
 *
 * 安全：任何失敗 / 中途斷線 → Update.abort()，舊韌體不受影響 (A/B 雙槽)。
 * 效能：OTA 期間主程式應暫停 WiFi 上傳 (見 EnvMonitor.ino 用 otaIsActive())。
 *
 * 由 config.h 的 ENABLE_BLE_OTA 控制；關閉時以下皆為 no-op / 回 false。
 **********************************************************************/
#pragma once
#include <stdint.h>

class NimBLEService;   // 前置宣告，避免在標頭引入整包 NimBLE

void    otaSetup(NimBLEService* svc);  // 在既有 service 上掛 OTA characteristics
bool    otaIsActive();                 // OTA 進行中？(供主程式暫停 WiFi/BLE 推播)
uint8_t otaPercentNow();               // 目前進度 0..100 (供螢幕顯示)
void    otaHandleDisconnect();         // 連線中斷時呼叫：進行中則安全 abort
