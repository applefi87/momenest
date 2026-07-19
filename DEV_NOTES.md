# DEV_NOTES — 開發前必讀（環境陷阱與對策）

此檔記錄在此 repo 開發時實際踩過的坑。**任何 AI/協作者動工前必須先讀完這份**，
程式架構導覽則看 `CLAUDE.md`。

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

## 3. 其他此專案的既有事實

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
