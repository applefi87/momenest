/**********************************************************************
 * ble.h — BLE 讀值服務 (手機就近即時取得感測值)
 *
 * 設備開一個 GATT server，手機 (原生 App 見 ../android/，備援網頁版見
 * ../cloud/src/ble-app.html) 連線後即時收到感測值 Notify；
 * 設備仍照常 WiFi 上傳雲端 (WiFi+BLE 共存)。
 *
 * 用 NimBLE-Arduino (比內建 Bluedroid 省 flash/RAM)。GATT 契約 (UUID/格式)
 * 見 BLE.md，是設備端與手機 App 的單一事實來源。
 *
 * 由 config.h 的 ENABLE_BLE 控制；關閉時以下皆為 no-op。
 **********************************************************************/
#pragma once

void bleInit();     // 啟動 GATT server 與 advertising
void bleNotify();   // 每量測週期呼叫：更新 characteristic 並 Notify 已連線的手機
