# BLE 讀值服務 — GATT 契約

手機以 BLE 就近即時取得感測值；設備仍照常 WiFi 上傳雲端（WiFi + BLE 共存）。
本檔是**設備端（`ble.cpp`）與手機 App（`../app/`）的單一事實來源**——改 UUID 或
JSON 格式時，兩邊都要跟著改。

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

- 廣播名稱：`env-monitor`
- 推播時機：每量測週期（約 1Hz）由 `bleNotify()` 更新；手機連著才 Notify

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

## 驗證（不必寫 App，先確認設備端）

1. Library Manager 裝 **NimBLE-Arduino**，Partition 選 Minimal SPIFFS，USB 燒錄
2. 手機裝 **nRF Connect**（免費）→ 掃描 → 連 `env-monitor`
3. 找到 Service `8f2a0001…`，對 `readings` 開 Notify → 應每秒收到感測 JSON
4. 設備端正常後，用手機 Android Chrome 開 `<worker>/ble`（Web Bluetooth）連

## 相關檔案

- 設備端：`ble.cpp` / `ble.h`
- 序列化：`reading_format.*`（+ `tests/`）
- 手機 App：`../cloud/src/ble-app.html`（Worker 路由 `/ble` 託管）
