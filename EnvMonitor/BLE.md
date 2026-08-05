# BLE 讀值服務 — GATT 契約

手機以 BLE 就近即時取得感測值；設備仍照常 WiFi 上傳雲端（WiFi + BLE 共存）。
本檔是**設備端（`ble.cpp`）與手機端（原生 App `../client/`、備援網頁版
`../cloud/src/ble-app.html`）的單一事實來源**——改 UUID 或 JSON 格式時，
所有端都要跟著改（Android 端的契約在
`client/core/protocol/.../GattContract.kt`，須與本表逐字一致）。

## 依賴與設定

- **函式庫**：NimBLE-Arduino（Library Manager 安裝；比內建 Bluedroid 省 flash/RAM）
- **開關**：`config.h` 的 `ENABLE_BLE`（0 = 關閉整個 BLE 子系統）
- **Partition**：需有 OTA 槽者，建議 `Minimal SPIFFS (1.9MB APP with OTA)`
  （WiFi + BLE + 字型同時佔空間，Default 可能不夠）

## GATT 結構

| 項目 | UUID | 屬性 |
|---|---|---|
| Service | `8f2a0001-b8c3-4e6a-9f1d-2a7c9e5b1a01` | — |
| readings | `8f2a0002-b8c3-4e6a-9f1d-2a7c9e5b1a01` | Read + Notify |
| status | `8f2a0003-b8c3-4e6a-9f1d-2a7c9e5b1a01` | Read + Notify |
| ota_control | `8f2a0004-b8c3-4e6a-9f1d-2a7c9e5b1a01` | Write + Notify |
| ota_data | `8f2a0005-b8c3-4e6a-9f1d-2a7c9e5b1a01` | Write (with response) |
| device_info | `8f2a0006-b8c3-4e6a-9f1d-2a7c9e5b1a01` | Read + Notify |

- 廣播名稱：`env-monitor`
- 推播時機：每量測週期（約 1Hz）由 `bleNotify()` 更新；手機連著才 Notify
- **舊韌體沒有 `device_info`**（1.0.x）；App 找不到時要當「版本未知」處理，
  不可視為連線失敗

## 資料格式（UTF-8 JSON 字串）

**readings**（與 WiFi 上傳同格式，由 `reading_format` 產生、受單元測試保護）：
```json
{"air_temp":24.58,"air_hum":58.07,"water_temp":31.00,"soil":2100,"water_level":1500}
```
- 浮點固定 2 位小數；讀取失敗 / 未接 → `null`
- `soil` / `water_level` 為**原始 ADC**；換算 % 在顯示端做（App 用共用的校準值）

**status**：
```json
{"wifi":1,"upload":1,"ip":"192.168.31.158"}
```
- `wifi` 1/0；`upload` 0=尚未 1=成功 2=失敗；`ip` 未連線時為空字串

**device_info**：
```json
{"fw":"1.1.0","built":"Aug  4 2026","chip":"esp32","heap":123456}
```
- `fw` = `config.h` 的 `FIRMWARE_VERSION`（改韌體行為時手動 bump）
- `built` = 編譯器的 `__DATE__`，格式固定為 `"Mmm  d yyyy"`（日期個位數時是**兩個空格**）
- `heap` = `ESP.getFreeHeap()`，隨每次 `bleNotify()` 更新
- **用途**：OTA 更新後手機沒有其他方法確認灌進去的是哪一版；重新連線讀這支
  比對 `fw` 才算真正驗證更新成功

## 驗證（不必寫 App，先確認設備端）

1. Library Manager 裝 **NimBLE-Arduino**，Partition 選 Minimal SPIFFS，USB 燒錄
2. 手機裝 **nRF Connect**（免費）→ 掃描 → 連 `env-monitor`
3. 找到 Service `8f2a0001…`，對 `readings` 開 Notify → 應每秒收到感測 JSON
4. 設備端正常後，用手機 Android Chrome 開 `<worker>/ble`（Web Bluetooth）連

## BLE OTA（藍牙傳封包更新韌體）

由 `config.h` 的 `ENABLE_BLE_OTA` 控制。設備端 `ble_ota.cpp`（協定解析
`ota_protocol.*`，有 host 單元測試），手機端在 `/ble` 頁面選 `.bin` 後推送。

**流程**：手機 → `ota_control` 寫 `[0x01][size LE32]`(BEGIN) → 分塊寫
`ota_data`(每塊 ≤512B，Write With Response 天然流控) → `ota_control` 寫
`[0x02]`(END)。設備用 `Update` 庫寫入另一個 OTA 分區，全收齊且驗證通過才
切換啟動分區並重開機。

**control notify 回報碼**（`[status][detail]`）：
`0x01` BEGIN_OK、`0x02` END_OK、`0x03` ABORTED、`0x10` PROGRESS(+百分比)、
`0xEE` ERROR(+detail)。

**進度 %**：手機依已送位元組即時算(進度條)；設備依已收位元組算，
顯示在螢幕(`uiDrawOta`)。

**WiFi 影響與對策**：單天線 WiFi/BLE 共用，且每 10 秒 HTTPS 上傳的 TLS
握手瞬吃 ~40KB heap——會搶天線並增加 OOM 風險。故 **OTA 期間主程式
(`EnvMonitor.ino`) 以 `otaIsActive()` 暫停量測/UI/WiFi 上傳/BLE 推播/重連**，
把資源全讓給傳輸；OTA 收發在 NimBLE 任務進行。傳完/中止後自動恢復。

**失敗可復原**：任何錯誤、驗證失敗、或中途斷線 → `Update.abort()`，啟動
分區不切換，舊韌體照跑（A/B 雙槽）。斷線由 `otaHandleDisconnect()` 處理。

**重要前提**：
- 設備上要**先有含 OTA 接收的韌體**才能被 BLE 更新（第一次仍需 USB 燒）
- `.bin` 需在**電腦編譯**(Sketch → Export Compiled Binary)，手機不編譯
- BLE 慢，1MB 韌體約數分鐘，傳輸中手機別鎖屏/離開

## 相關檔案

- 設備端讀值：`ble.cpp` / `ble.h`
- 設備端 OTA：`ble_ota.cpp` / `ble_ota.h`、協定 `ota_protocol.*`（+ `tests/`）
- 序列化：`reading_format.*`（+ `tests/`）
- 手機 App：`../cloud/src/ble-app.html`（Worker 路由 `/ble` 託管）
