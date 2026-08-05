# momenest — 環境監測系統

ESP32 + 3.5" 觸控螢幕的居家環境監測器：即時量測空氣溫濕度、水溫、
土壤濕度、水位，每 10 秒上傳雲端，並提供網頁儀表板與手機 App。

## 系統架構

```
                          WiFi / HTTPS POST
[ESP32 + 感測器] ──────────────────────────► [Cloudflare Worker] ──► [D1 資料庫]
        │                                              │
        │ BLE（就近直連，不經網路）                      │ GET /
        ▼                                              ▼
[Android App / 網頁版]                          網頁儀表板（歷史曲線）
   即時讀值 + 韌體 OTA 更新
```

- **韌體**（[`EnvMonitor/`](EnvMonitor/README.md)）：ESP32 讀取感測器、本地螢幕顯示與觸控校準，
  每 10 秒 POST 一筆資料到雲端；同時開 BLE 服務供手機就近讀值與更新韌體
- **雲端**（[`cloud/`](cloud/README.md)）：單一 Cloudflare Worker 接收資料寫入 D1、提供查詢 API，
  並內嵌網頁儀表板與備援版 BLE 網頁 App
- **客戶端**（[`client/`](client/README.md)）：Kotlin Multiplatform + Android App，
  藍牙直連看即時數值、選 `.bin` 推送韌體更新（BLE OTA）

## 目錄導覽

| 資料夾 | 內容 |
|---|---|
| [`EnvMonitor/`](EnvMonitor/README.md) | ESP32 韌體：接線表、Arduino IDE 設定、程式架構、校準方式 |
| [`cloud/`](cloud/README.md) | Cloudflare Worker + D1：API 說明、部署步驟、更新流程 |
| [`client/`](client/README.md) | Kotlin/Android App：建置、測試、安裝、使用方式 |

### 為什麼叫 `client/` 而不是 `android/`

協定層（`client/core/protocol`）是 Kotlin Multiplatform 模組，
已宣告 iOS target，未來也可編到桌面 / Web。目前只有 Android 有完整實作，
但資料夾名稱對應的是它在系統中的角色（**客戶端**，相對於設備端與伺服端），
而不是單一平台。設計細節見 [`client/ARCHITECTURE.md`](client/ARCHITECTURE.md)。

## 新手上手順序

1. 讀 [`EnvMonitor/README.md`](EnvMonitor/README.md)，依接線表接好硬體並燒錄韌體
2. 讀 [`cloud/README.md`](cloud/README.md)，部署雲端 Worker（Cloudflare 免費額度即可）
3. 複製 `EnvMonitor/secrets.h.example` 為 `secrets.h`，填入 WiFi 帳密、Worker 網址與 API 密鑰
4. 手機開 Worker 網址即可看到即時儀表板
5. 想用藍牙直連 / 更新韌體：讀 [`client/README.md`](client/README.md) 建置並安裝 App
   （或直接開 Worker 的 `/ble` 網頁備援版）

## 藍牙相關

BLE 的 GATT 契約（UUID、JSON 格式、OTA 協定）以
[`EnvMonitor/BLE.md`](EnvMonitor/BLE.md) 為**單一事實來源**，
韌體、原生 App、網頁版三邊都要跟著它走。

## 給貢獻者 / AI 協作者

- [`README_ai.md`](README_ai.md)：程式架構導覽，改哪個功能該看哪個檔案，動工前先查這張表
- [`DEV_NOTES.md`](DEV_NOTES.md)：**動工前必讀**，記錄此開發環境實際踩過的坑（git 損壞、掛載同步延遲等）

## License

[MIT](LICENSE)
