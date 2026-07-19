# momenest — 環境監測系統

ESP32 + 3.5" 觸控螢幕的居家環境監測器：即時量測空氣溫濕度、水溫、
土壤濕度、水位，每 10 秒上傳雲端，並提供手機可看的網頁儀表板。

## 系統架構

```
[ESP32 + 感測器] --WiFi / HTTPS POST--> [Cloudflare Worker] --> [D1 資料庫]
        |                                       |
   本地螢幕即時顯示                    GET / 內嵌網頁儀表板（手機可看）
```

- **韌體**（[`EnvMonitor/`](EnvMonitor/README.md)）：ESP32 讀取感測器、本地螢幕顯示與觸控校準，每 10 秒 POST 一筆資料到雲端
- **雲端**（[`cloud/`](cloud/README.md)）：單一 Cloudflare Worker 接收資料寫入 D1、提供查詢 API，並內嵌一頁可看即時數值與歷史曲線的網頁儀表板

## 目錄導覽

| 資料夾 | 內容 |
|---|---|
| [`EnvMonitor/`](EnvMonitor/README.md) | ESP32 韌體：接線表、Arduino IDE 設定、程式架構、校準方式 |
| [`cloud/`](cloud/README.md) | Cloudflare Worker + D1：API 說明、部署步驟、更新流程 |

## 新手上手順序

1. 讀 [`EnvMonitor/README.md`](EnvMonitor/README.md)，依接線表接好硬體並燒錄韌體
2. 讀 [`cloud/README.md`](cloud/README.md)，部署雲端 Worker（Cloudflare 免費額度即可）
3. 複製 `EnvMonitor/secrets.h.example` 為 `secrets.h`，填入 WiFi 帳密、Worker 網址與 API 密鑰
4. 手機開 Worker 網址即可看到即時儀表板

## 給貢獻者 / AI 協作者

- [`CLAUDE.md`](CLAUDE.md)：程式架構導覽，改哪個功能該看哪個檔案，動工前先查這張表
- [`DEV_NOTES.md`](DEV_NOTES.md)：**動工前必讀**，記錄此開發環境實際踩過的坑（git 損壞、掛載同步延遲等）

## License

[MIT](LICENSE)
