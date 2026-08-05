# DEV_NOTES — 開發前必讀（環境陷阱與對策）

此檔記錄在此 repo 開發時實際踩過的坑。**任何 AI/協作者動工前必須先讀完這份**，
程式架構導覽則看 `README_ai.md`（`CLAUDE.md` 只是一行轉接）。

## 1. Cowork/掛載環境的 git index 會被同步損壞（已踩過兩次）

症狀：`git add`/`git commit` 途中出現
`error: bad signature 0x00000000` / `fatal: index file corrupt`，
以及 `.git/*.lock`、`.git/objects/tmp_obj_*` 無法 unlink（`Operation not permitted`）。

原因：工作資料夾是雙向同步掛載（Windows ↔ Linux VM），
git 高頻寫入 `.git/index` 時會跟同步機制打架；刪除檔案預設沒有權限。

**標準對策（依序）：**

1. 刪檔前先啟用刪除權限（Cowork 的 `allow_cowork_file_delete`）
2. 清掉殘留：`rm -f .git/index .git/*.lock .git/objects/maintenance.lock`
   與 `find .git/objects -name 'tmp_obj_*' -delete`
3. **改用掛載外的 index 檔操作 git**，避免再次損壞：
   ```bash
   export GIT_INDEX_FILE=/tmp/gidx
   git read-tree HEAD        # 先從 HEAD 重建暫用 index
   git add <files> && git commit -m "..."
   ```
4. 全部 commit 完成後，寫回正式 index：
   `unset GIT_INDEX_FILE && git reset -q`，再 `git status` 確認乾淨
5. `git fsck` 出現 dangling commit 是失敗嘗試的殘留，無害可忽略

## 2. 掛載同步有延遲，檔案工具寫入 ≠ VM 立即可見

症狀：用檔案工具 (Read/Write/Edit) 改完檔案，VM 端 `grep`/`git diff` 看到的是舊內容，
甚至只同步到一半（部分編輯有、部分沒有）。

**對策：**

- 執行 git 操作或 VM 端驗證前，先 grep 一個「最新編輯才有的字串」確認同步完成，
  沒同步就 sleep 幾秒重試（可能要等數十秒）
- 驗證檔案內容以檔案工具（Windows 路徑）為準，VM 掛載路徑只當參考
- 不要用 bash 直接寫掛載內的專案檔（會跟檔案工具的寫入互相覆蓋）

## 3. client/（Kotlin/Android）的環境陷阱

### 3.1 開發機沒有 JDK / Gradle / Android SDK

`client/` 底下的 Kotlin 程式碼**曾在沒有工具鏈的機器上撰寫**，
意即那些檔案未必編譯驗證過。動它之前先確認本機有沒有：

```bash
java -version && gradle -v
```

沒有的話用 Android Studio 開 `client/` 資料夾（它自帶 JBR 與 SDK 管理），
不要嘗試在沒有工具鏈的環境「靠讀程式碼確認能編譯」——這是本專案已經
付出過代價的教訓。

### 3.2 `gradle-wrapper.jar` 不在版控

二進位檔刻意排除。取得方式：用 Android Studio 開啟資料夾（自動補），
或已裝 Gradle 時執行 `gradle wrapper`。

### 3.3 搬移 / 改名 client/ 會被 Android Studio 鎖住

症狀：`mv client xxx` 回報 `Device or resource busy`。
原因是 Android Studio 或 Gradle daemon 持有資料夾控制代碼。

**對策**：先關掉 Android Studio（或 `./gradlew --stop`），
再用 PowerShell 的 `Move-Item` 執行（有時 Git Bash 的 `mv` 仍失敗但
`Move-Item` 可以）。移動後**務必刪掉** `.gradle/`、`.kotlin/`、`build/`，
它們裡面存的是絕對路徑，留著會產生莫名其妙的建置錯誤。

### 3.4 commonMain 不准出現 java.\* 或 android.\*

`client/core/protocol` 是 KMP 模組並宣告了 iOS target。
`commonMain` 一旦 import `java.util.UUID` 這類 JVM 專屬 API，
**Android 端仍然編得過**（因為只編 jvm target），但 iOS target 會直接失敗——
多平台就退化成「宣告了卻編不過」的假象。

這個坑已經踩過一次：所以 UUID 用自訂的 `BleUuid`、CRC32 自己實作。
改這個模組時請保持這條紅線。

## 4. 其他此專案的既有事實

- `secrets.h` 不進版控（`.gitignore`），新環境要從 `secrets.h.example` 複製
- 螢幕面板顏色反相已在 `display_hw.h` 用 `pcfg.invert = true` 修正；
  換面板若顏色相反把它改回 false
- 中文介面用 LovyanGFX 內建 `efontTW_16`，flash 不夠改 Partition Scheme → Huge APP
- 編譯依賴：LovyanGFX、OneWire、DallasTemperature、ArduinoJson
- 土壤感測曾間歇讀 0 幾分鐘：軟體端已加 5 次中位數與無效值判斷，
  若仍發生優先懷疑接線/探針接觸
- 螢幕殘影：長時間顯示靜態畫面（尤其反相亮底）造成液晶暫時極化殘留，
  深色底下隱約可見舊畫面。屬面板物理現象，短暫黑白閃無法消除，
  不是 fillScreen 沒清乾淨。診斷（全屏灰階）與恢復（黑白長時間交替）
  用 `EnvMonitor/test tool/螢幕殘影測試/`；平時避免同一畫面連續亮多天
