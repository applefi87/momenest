# momenest 客戶端 App — Kotlin Multiplatform + Android

用藍牙直接連上環境監測器：看即時感測數值、推 `.bin` 更新韌體。
不經網路，設備仍照常上傳雲端。

> 網頁版（`cloud/src/ble-app.html`，路由 `/ble`）仍可用，定位為**備援方案**。
> 兩者差異見文末「與網頁版的差異」。

---

## 1. 環境需求

| 需要 | 版本 | 備註 |
|---|---|---|
| JDK | **17** | Android Studio 內建的 JBR 就可以 |
| Android Studio | 最新穩定版 | Ladybug 以上（AGP 8.7 需要） |
| Android SDK | **API 35** | 在 SDK Manager 勾選 |
| 手機 | Android 8.0 (API 26) 以上 | 需支援 BLE |

> ⚠️ 本專案的所有 Kotlin 程式碼**尚未在任何機器上編譯過**（開發機沒有裝 JDK/SDK）。
> 第一次用 Android Studio 開啟時若出現紅字，屬預期範圍，回報即可修。

### `gradle-wrapper.jar` 不在版控

二進位檔刻意排除（見 `.gitignore`）。取得方式二選一：

- **Android Studio**：直接開啟本資料夾，IDE 會自動補上並同步
- **已裝 Gradle**：在本資料夾執行 `gradle wrapper`

---

## 2. 建置與執行

```bash
./gradlew assembleDebug
```

安裝到已連接的手機：

```bash
./gradlew installDebug
```

或在 Android Studio 直接按 Run。

---

## 3. 跑測試

### JVM 單元測試（不需要手機或模擬器，秒級完成）

```bash
./gradlew test
```

只跑協定層（最核心、最密集的那一組）：

```bash
./gradlew :core:protocol:jvmTest
```

> `:core:protocol` 是 KMP 模組，測試 task 名稱是 `jvmTest` 而不是 `test`。

### UI 測試（需要實機或模擬器）

```bash
./gradlew connectedAndroidTest
```

---

## 4. 怎麼使用

1. 手機開啟**藍牙**（Android 12 以下還要開定位服務才掃得到 BLE）
2. 開啟 App → 允許藍牙權限
3. 按「連接設備」→ 自動掃描廣播 `env-monitor` 服務的設備並連線
4. 連上後五張卡開始每秒更新；標題下方顯示設備的 WiFi 狀態與 IP
5. 要更新韌體 → 按「透過藍牙更新韌體」→ 選 `.bin` → 開始更新

### `.bin` 從哪來

在**電腦**用 Arduino IDE 開 `EnvMonitor/`，選
**Sketch → Export Compiled Binary**，把產出的 `.bin` 傳到手機
（雲端硬碟、USB、傳訊軟體都可以）。手機不編譯韌體。

### 更新韌體要注意

- 藍牙傳輸慢，1MB 韌體約需數分鐘，**過程中請勿鎖屏或離開畫面**
  （App 會自動讓螢幕常亮，但仍請勿切到別的 App）
- 更新失敗**不會影響**設備上正在跑的舊韌體（ESP32 A/B 雙分區，
  驗證通過才切換啟動分區）
- 更新成功後設備會自動重開機並斷線 —— 這是正常的。重新連線可在畫面上
  確認韌體版本已經改變
- 設備上必須**先有含 OTA 接收功能的韌體**才能被藍牙更新，第一次仍需 USB 燒錄

---

## 5. 模組結構

```
core/protocol      純 Kotlin (KMP)  協定解析、OTA 編排、CRC32、校準換算
core/ble           Android          BluetoothGatt 包裝、掃描、連線
core/data          Android          DataStore 存校準值
core/designsystem  Android          Compose 主題與共用元件
core/testing       Android          共用測試替身（手寫 fake，不用 mock 框架）
feature/monitor    Android          即時讀值畫面
feature/ota        Android          韌體更新畫面
app                Android          組裝：導覽、權限、DI
```

設計理由與資料流見 [ARCHITECTURE.md](ARCHITECTURE.md)。

---

## 6. 測試涵蓋

共 **217** 個測試（190 個 JVM 單元測試 + 27 個 UI 測試）。

| 模組 | 測試檔 | 個數 | 涵蓋重點 |
|---|---|---|---|
| `core:protocol` | `OtaUploaderTest` | 26 | **全專案最重要**：封包順序與內容、分塊隨 MTU 變動、設備拒絕/中途錯誤/斷線/逾時、失敗必補送 ABORT |
| | `ChunkerAndMathTest` | 23 | 分塊切割與還原、進度百分比（與韌體同演算法）、校準換算 |
| | `ParserTest` | 20 | readings / status / device_info 的 JSON 解析與容錯 |
| | `OtaReportDecoderTest` | 13 | 設備回報碼解碼、錯誤碼說明 |
| | `GattContractTest` | 12 | UUID 逐字比對韌體、MTU→分塊大小 |
| | `OtaControlPacketTest` | 12 | BEGIN/END/ABORT 的**逐位元組** little-endian 驗證 |
| | `Crc32Test` | 9 | 已知向量（與韌體 `test_crc32.cpp` 同一組） |
| `core:ble` | `BlePermissionsTest` | 8 | Android 12 前後的權限清單差異 |
| | `GattOperationQueueTest` | 6 | GATT 操作序列化、例外與取消後不卡死 |
| `feature:monitor` | `MonitorScreenTest` 🔌 | 13 | 按鈕狀態切換、卡片渲染、OTA 入口顯示條件 |
| | `MonitorViewModelTest` | 12 | 讀值推播、校準變更重算、錯誤處理 |
| | `ReadingTileMapperTest` | 12 | 小數位數、無資料佔位、有無校準的顯示差異 |
| | `StatusLineFormatterTest` | 8 | 各連線狀態的文案、IP 空字串處理 |
| `feature:ota` | `OtaViewModelTest` | 15 | 選檔、開始/取消、重複按不併發、斷線後鎖住 |
| | `OtaScreenTest` 🔌 | 14 | 進度條、按鈕啟用條件、失敗訊息 |
| | `OtaMessagesTest` | 14 | 每個 OTA 事件的狀態轉移與文案 |

🔌 = instrumented 測試，需要實機或模擬器。

**刻意不測**的部分：`AndroidGattTransport` 與 `AndroidEnvMonitorClient`。
它們幾乎每一行都在跟 Android framework 互動，用 mock 測只會測到
「我以為 framework 這樣運作」。真正該嚴格驗證的邏輯已經被抽到
`:core:protocol`，那裡不需要任何模擬就能測完整個 OTA 流程。

---

## 7. 已知限制

- **App 圖示**用系統內建的 `@android:drawable/ic_dialog_info` 暫代，尚未做自製圖示
- **release 未開啟程式碼縮減**（`isMinifyEnabled = false`），要上架時需開啟並補 ProGuard 規則
- **`:core:data` 沒有單元測試**：真實 DataStore 需要 Android Context，
  改由 `:core:testing` 的 `FakeCalibrationRepository` 支撐 feature 層測試
- **校準值目前沒有編輯介面**：`CalibrationRepository` 已就緒，但畫面尚未做，
  所以土壤/水位目前一律顯示原始 ADC。設備本機螢幕可以校準（見 `EnvMonitor/`）
- **iOS 尚未實作**：協定層已可編到 iOS target，但缺 CoreBluetooth 的
  `GattTransport` 實作（見 ARCHITECTURE.md 的「加一個平台要做什麼」）

---

## 8. 與網頁版的差異

| | 原生 App | 網頁版 `/ble` |
|---|---|---|
| 平台 | Android 8.0+ | Chrome/Edge（Android、桌面）；iOS Safari 不支援 |
| MTU 協商 | ✅ 主動要求 517，分塊依實際協商值計算 | ❌ Web Bluetooth 不提供 MTU，硬編 512 |
| OTA 完整性 | ✅ END 附 CRC32，設備比對後才切換分區 | ❌ 只靠 ESP32 映像自身的檢查 |
| 韌體版本顯示 | ✅ 讀 `device_info` | ❌ 無 |
| 安裝 | 要裝 APK | 開網址就能用 |

網頁版仍然保留且可用 —— 臨時借別人的手機、或想快速給同事看時很方便。

---

## 9. 相關文件

- [ARCHITECTURE.md](ARCHITECTURE.md) — 模組設計、資料流、OTA 流程、測試策略
- [`../EnvMonitor/BLE.md`](../EnvMonitor/BLE.md) — **GATT 契約的單一事實來源**（UUID、JSON 格式、OTA 協定）
- [`../README_ai.md`](../README_ai.md) — 全專案的程式架構導覽
- [`../DEV_NOTES.md`](../DEV_NOTES.md) — 開發環境的已知陷阱，動工前必讀
