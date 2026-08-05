# momenest — 給協作者 / AI 的程式架構導覽

> **⚠ 動工前必讀 [`DEV_NOTES.md`](DEV_NOTES.md)**：記錄此環境的 git index 損壞、
> 掛載同步延遲等實際踩過的坑與標準對策。先讀完再開始開發。

> 這份檔案給**任何**協作者（人類或 AI）閱讀。
> `CLAUDE.md` 只是一行轉接，內容全在這裡。

ESP32 + 3.5" 觸控螢幕的環境監測器（空氣溫濕度/水溫/土壤濕度/水位），
每 10 秒上傳 Cloudflare Worker (D1)，附網頁儀表板與 Kotlin/Android App。

## 目錄導覽（改東西前先看這張表，不用通讀全部檔案）

### 韌體（ESP32）

| 想做的事 | 看這裡 |
|---|---|
| 改感測器腳位 / 量測時序 | `EnvMonitor/config.h` |
| 改韌體版本號 | `EnvMonitor/config.h` 的 `FIRMWARE_VERSION` |
| 改螢幕硬體 (SPI/觸控校正/顏色反相) | `EnvMonitor/display_hw.h` |
| 改配色 / 版面座標 | `EnvMonitor/theme.h` |
| 改介面文字 / 新增語言 | `EnvMonitor/lang.h`（內嵌 JSON，直接編輯） |
| 主畫面繪製 / 觸控入口 | `EnvMonitor/ui_main.cpp` |
| 校準編輯畫面 | `EnvMonitor/ui_edit.cpp` |
| 感測器讀取邏輯 | `EnvMonitor/sensors.cpp` |
| 校準值預設與 NVS 儲存 | `EnvMonitor/calibration.cpp` |
| WiFi / 雲端上傳 / OTA | `EnvMonitor/net.cpp`（密鑰在 `secrets.h`，不進版控） |
| 感測值→JSON 序列化 | `EnvMonitor/reading_format.*`（net/BLE 共用，有 host 單元測試） |
| BLE 讀值服務 / device_info | `EnvMonitor/ble.cpp`（NimBLE，契約見 `EnvMonitor/BLE.md`） |
| BLE OTA 韌體更新 | `EnvMonitor/ble_ota.cpp` + 協定 `ota_protocol.*`（有 host 單元測試） |
| host 單元測試 | `EnvMonitor/tests/`（g++ 跑純邏輯，非硬體；`./run_tests.sh`） |
| 接線 | `EnvMonitor/WIRING.md` |

### 雲端（Cloudflare Worker）

| 想做的事 | 看這裡 |
|---|---|
| 雲端 API 邏輯 | `cloud/src/api.js`（D1 讀寫） |
| 雲端網頁儀表板 | `cloud/src/dashboard.html`（路由 `/`，wrangler Text 模組內嵌） |
| 雲端路由入口 | `cloud/src/index.js` |
| 手機 BLE 網頁版（**備援方案**） | `cloud/src/ble-app.html`（Web Bluetooth，Android Chrome，路由 `/ble`） |

### 客戶端 App（Kotlin Multiplatform + Android）

| 想做的事 | 看這裡 |
|---|---|
| 專案總覽 / 怎麼建置與測試 | `client/README.md` |
| 架構設計與取捨 | `client/ARCHITECTURE.md` |
| GATT UUID / MTU / 分塊大小 | `client/core/protocol/src/commonMain/.../GattContract.kt` |
| 感測值 / 狀態 / 版本的 JSON 解析 | `client/core/protocol/.../SensorReading.kt`、`DeviceStatus.kt`、`DeviceInfo.kt` |
| **OTA 傳輸編排（最高風險）** | `client/core/protocol/.../OtaUploader.kt` + `OtaUploaderTest.kt` |
| OTA 封包編解碼 / CRC32 | `client/core/protocol/.../OtaProtocol.kt`、`Crc32.kt` |
| ADC → 百分比換算 | `client/core/protocol/.../Calibration.kt` |
| Android BLE 掃描 / 連線 / notify | `client/core/ble/.../AndroidEnvMonitorClient.kt` |
| BluetoothGatt 回呼轉協程 | `client/core/ble/.../AndroidGattTransport.kt` |
| 藍牙權限（Android 12 前後差異） | `client/core/ble/.../BlePermissions.kt` |
| 配色 / Compose 元件 | `client/core/designsystem/.../Color.kt`、`Components.kt` |
| 讀值畫面 | `client/feature/monitor/` |
| 韌體更新畫面 | `client/feature/ota/` |
| 導覽 / 權限閘門 / DI 組裝 | `client/app/` |
| 共用測試替身 | `client/core/testing/` |
| 版本統一管理 | `client/gradle/libs.versions.toml` |

---

## EnvMonitor（韌體）架構

- 入口 `EnvMonitor.ino` 只有 setup/loop；1Hz 非阻塞量測時序由 `sensors.cpp` 的
  `sensorsLoop()` 內部管理（T=0 觸發、T=800ms 探針上電→讀取→斷電）
- 模組間以 extern 全域變數共享狀態：sensors 提供讀值、calibration 提供校準值、
  net 提供 `uploadState`、ui 提供 `uiMode`
- 校準值（土壤/水位 ADC 的 MIN=0% / MAX=100%）與語言選擇存 NVS（`Preferences`），
  斷電保留；預設值寫死在 `calibration.cpp`
- 多語系：`lang.h` 內嵌 JSON → `i18n.cpp` 以 ArduinoJson 解析；
  中文用 LovyanGFX 內建 `efontTW_16`，英文用內建 Font2/Font4
- 觸控為單次觸發（放開才能再按），防彈跳在 `EnvMonitor.ino` 的 loop
- BLE 讀值（`ble.cpp`，`config.h` 的 `ENABLE_BLE` 開關）與 WiFi 上傳共用
  `reading_format` 的 JSON 序列化；可測邏輯抽成純函式在 `tests/` 用 g++ 驗證（TDD）
- BLE OTA（`ble_ota.cpp`，`ENABLE_BLE_OTA`）：手機傳 `.bin` 更新韌體，
  OTA 期間主迴圈以 `otaIsActive()` 暫停 WiFi/量測讓出資源；失敗自動 abort
  不動舊韌體（A/B 雙槽）。END 封包可帶 CRC32 做整體完整性校驗（向後相容
  不帶 CRC 的舊格式）。詳見 `BLE.md`
- `device_info` characteristic 回報韌體版本與 heap，讓 App 在 OTA 後能確認版本
- 依賴函式庫：LovyanGFX、OneWire、DallasTemperature、ArduinoJson、
  NimBLE-Arduino（BLE，啟用 ENABLE_BLE 時）
- flash 不足時：Partition Scheme 改 `Minimal SPIFFS (1.9MB APP with OTA)`
  （保留 OTA 槽；用 BLE 時勿選 Huge APP，它無 OTA 槽）

## cloud（Cloudflare Worker）

- 三檔模組：`index.js` 路由入口、`api.js` D1 讀寫（`POST /api/ingest`
  寫入需 X-API-Key、`GET /api/data`、`GET /api/latest`）、
  `dashboard.html` 儀表板網頁（wrangler Text 規則 import 成字串）
- `ble-app.html`（路由 `/ble`）是 BLE 的**網頁備援版**：主力已改為
  `client/` 的原生 App，網頁版保留給「臨時借手機」「快速展示」等場合
- D1 schema 見 `cloud/schema.sql`；部署 `wrangler deploy`

## client（Kotlin Multiplatform + Android）

- 8 個模組，相依方向為 `app → feature → core`，`core:protocol` 不依賴任何東西
- **`core:protocol` 是 KMP 模組**（commonMain），已宣告 iOS target。
  硬性規則：commonMain **不准出現 `java.*` 或 `android.*`**——
  所以 UUID 用自訂的 `BleUuid`、CRC32 自己實作，而不是用 `java.util.*`
- 設計樞紐是 `GattTransport` 介面（埠）：`OtaUploader` 只認識它，
  正式跑時接 Android 實作，測試時接假設備，因此 OTA 的每條失敗路徑
  都能在毫秒內驗證，不需要真設備
- 畫面一律「無狀態 Screen + Route 接線」，狀態全在可測的 ViewModel
- 換算與文案組合抽成純函式（`ReadingTileMapper`、`StatusLineFormatter`、
  `OtaMessages`），先寫測試再寫實作
- 測試用手寫 fake 而非 mock 框架（理由見 `client/ARCHITECTURE.md` 第 6 節）
- 共 217 個測試；`./gradlew test` 跑 JVM 測試（不需裝置），
  `./gradlew connectedAndroidTest` 跑 UI 測試（需裝置）

## 慣例

- ESP32 上傳的 soil / water_level 一律是「原始 ADC 值」；0~100% 換算只在顯示端做
- 讀取失敗以 null 上傳（資料庫存 NULL）；土壤/水位讀到 0 視為感測器未接
- BLE 的 GATT 契約以 `EnvMonitor/BLE.md` 為**單一事實來源**，
  韌體 / 原生 App / 網頁版三邊改動要同步
- commit 依功能段落拆分，不要一大包
- 註解寫「為什麼」而不是「做什麼」；一律繁體中文
