# cloud — Cloudflare Worker + D1 (免費方案)

單一 Worker 包辦：ESP32 資料接收、公開查詢 API、手機儀表板網頁。

## API

| 路由 | 方法 | 權限 | 說明 |
|------|------|------|------|
| `/api/ingest` | POST | 需 `X-API-Key` header | ESP32 上傳，時間戳由伺服器補 |
| `/api/data?from=&to=&limit=` | GET | 公開 | 歷史區間 (unix 秒)，預設近 24h |
| `/api/latest` | GET | 公開 | 最新一筆 |
| `/` | GET | 公開 | 儀表板網頁 |

## 部署步驟（一次性）

```bash
npm install -g wrangler
cd cloud
wrangler login                          # 開瀏覽器登入 Cloudflare（免費帳號即可）

# 1. 建立 D1 資料庫，把回傳的 database_id 貼進 wrangler.toml
wrangler d1 create env-monitor-db

# 2. 建表（--remote = 直接對雲端資料庫執行）
wrangler d1 execute env-monitor-db --remote --file=schema.sql

# 3. 設定上傳密鑰（自己想一組長隨機字串，之後填進 ESP32 的 secrets.h）
wrangler secret put API_KEY

# 4. 部署
wrangler deploy
```

部署完成會顯示網址，如 `https://env-monitor.xxx.workers.dev`：

- 手機開這個網址 = 儀表板
- ESP32 的 `secrets.h` 中 `API_URL` 填 `https://env-monitor.xxx.workers.dev/api/ingest`

## 更新流程（改完程式碼後如何同步到雲端）

程式分三個檔案，部署時 wrangler 會自動打包，**沒有另外的靜態網頁上傳步驟**：

- 改 API 行為（查詢邏輯、驗證）→ `src/api.js`
- 改路由 → `src/index.js`
- 改儀表板畫面（版面、圖表、文字）→ `src/dashboard.html`
  （真正的 HTML 檔，直接編輯；經 wrangler.toml 的 Text 規則 import 進 Worker）

改完後重新部署一次即可生效，網址不變：

```bash
cd cloud
wrangler deploy
```

若同時改到 `schema.sql`（新增欄位等資料庫結構變更），`wrangler deploy`
**不會**自動套用 SQL，需要另外手動執行遷移：

```bash
wrangler d1 execute env-monitor-db --remote --command="ALTER TABLE readings ADD COLUMN ..."
```

## 測試（不用 ESP32 也能先驗證）

```bash
# 假資料寫入
curl -X POST https://env-monitor.xxx.workers.dev/api/ingest \
  -H "Content-Type: application/json" -H "X-API-Key: 你的密鑰" \
  -d '{"air_temp":25.5,"air_hum":60.2,"water_temp":24.1,"soil":2100,"water_level":1800}'

# 讀取
curl https://env-monitor.xxx.workers.dev/api/latest
```

## 免費額度確認（每 10 秒上傳一筆）

- Worker 請求：8,640 寫 + 儀表板讀取 ≪ 100,000/天 ✅
- D1 寫入：8,640 列/天 ≪ 100,000/天 ✅
- D1 容量：約 25MB/月，5GB 可存 15 年以上 ✅
