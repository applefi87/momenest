# momenest — 環境監測系統

> **⚠ 動工前必讀 `DEV_NOTES.md`**：記錄此環境的 git index 損壞、掛載同步延遲等
> 實際踩過的坑與標準對策。先讀完再開始開發。

ESP32 + 3.5" 觸控螢幕的環境監測器（空氣溫濕度/水溫/土壤濕度/水位），
每 10 秒上傳 Cloudflare Worker (D1)，附網頁儀表板。

## 目錄導覽（改東西前先看這張表，不用通讀全部檔案）

| 想做的事 | 看這裡 |
|---|---|
| 改感測器腳位 / 量測時序 | `EnvMonitor/config.h` |
| 改螢幕硬體 (SPI/觸控校正/顏色反相) | `EnvMonitor/display_hw.h` |
| 改配色 / 版面座標 | `EnvMonitor/theme.h` |
| 改介面文字 / 新增語言 | `EnvMonitor/lang.h`（內嵌 JSON，直接編輯） |
| 主畫面繪製 / 觸控入口 | `EnvMonitor/ui_main.cpp` |
| 校準編輯畫面 | `EnvMonitor/ui_edit.cpp` |
| 感測器讀取邏輯 | `EnvMonitor/sensors.cpp` |
| 校準值預設與 NVS 儲存 | `EnvMonitor/calibration.cpp` |
| WiFi / 雲端上傳 | `EnvMonitor/net.cpp`（密鑰在 `secrets.h`，不進版控） |
| 雲端 API + 網頁儀表板 | `cloud/src/index.js`（單一 Worker 檔） |
| 接線 | `EnvMonitor/WIRING.md` |

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
- 依賴函式庫：LovyanGFX、OneWire、DallasTemperature、ArduinoJson
- flash 不足時：Arduino IDE → Tools → Partition Scheme → Huge APP

## cloud（Cloudflare Worker）

- 單檔包辦：`POST /api/ingest`（寫入需 X-API-Key）、`GET /api/data`、
  `GET /api/latest`、`GET /` 內嵌 HTML 儀表板
- D1 schema 見 `cloud/schema.sql`；部署 `wrangler deploy`

## 慣例

- ESP32 上傳的 soil / water_level 一律是「原始 ADC 值」；0~100% 換