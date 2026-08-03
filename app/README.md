# app — 手機端 Web Bluetooth App

用 **Web Bluetooth** 讓 Android 手機以藍牙就近讀取設備感測值的網頁 App。
沿用雲端儀表板的蘋果風格；資料經 BLE 直傳、不經網路（設備仍照常上傳雲端）。

## 為什麼是 Web Bluetooth 而非原生 APK

- Android Chrome **原生支援 Web Bluetooth**，免 Android SDK/Gradle 建置、免上架
- 可「加入主畫面」像原生 App；改版即時，無需重編譯
- 與既有網頁技能一致；解析的 JSON 與設備端 `EnvMonitor/BLE.md` 契約一致

> iOS Safari 不支援 Web Bluetooth；本 App 針對 Android Chrome。若日後需要 iOS，
> 再評估原生或其他方案。

## 使用

Web Bluetooth 需**安全內容（HTTPS 或 localhost）**，兩種方式：

1. **本機測試**：用能提供 HTTPS/localhost 的方式開啟（純 `file://` 在 Android 上不便）
2. **正式**：把 `index.html` 放到你的 Cloudflare Worker（HTTPS）當一個路由，
   手機開該網址 → 可「加入主畫面」

步驟：Android Chrome 開頁 → 按「連接設備」→ 選 `env-monitor` → 即時顯示。
需開啟手機藍牙（Android 首次可能要求定位權限以掃描 BLE）。

## 與雲端儀表板的一致性

- 感測欄位、顏色、卡片版面沿用儀表板
- 土壤 / 水位若在儀表板設過校準（`localStorage` 的 `cal`），這裡也會顯示 %

## GATT 契約

UUID 與 JSON 格式見 `../EnvMonitor/BLE.md`——**改任一邊都要同步**。

## 現況

- 讀值（readings / status）：已實作，UI 與解析管線已於瀏覽器驗證
- BLE OTA（韌體更新）：尚未做，屬後續（見對話規劃）
