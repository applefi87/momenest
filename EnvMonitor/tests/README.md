# tests — host 單元測試

**在 PC 上跑的自動化測試**，不需要 ESP32。與硬體無關的純邏輯抽出來後，
在這裡用 g++ 編譯執行，快速驗證正確性 (TDD)。

> 注意：這裡是「單元測試」，跟 `EnvMonitor/test tool/`（需燒錄到板子上的
> 硬體診斷 sketch）不同，用途與執行方式都不一樣，刻意分開放。

## 怎麼跑

需求：`g++`（C++11 以上）。在本資料夾執行：

```bash
./run_tests.sh
```

或手動：

```bash
g++ -std=c++11 -Wall -Wextra test_reading_format.cpp ../reading_format.cpp -o test_reading_format
./test_reading_format
```

離開碼 0 = 全過；非 0 = 有失敗（會列出 want / got 方便定位）。

## 目前涵蓋

| 測試檔 | 受測對象 | 重點案例 |
|---|---|---|
| `test_reading_format.cpp` | `../reading_format.cpp`（感測值→JSON） | 正常值、浮點 2 位小數、負溫、NaN→null、未接→null、全無效、緩衝截斷語意 |

## 設計原則

- **純邏輯與硬體隔離**：可測試的邏輯放進不依賴 Arduino API 的 `.cpp`
  （如 `reading_format.cpp`），韌體與測試共用同一份原始碼。
- **韌體 (net.cpp / ble.cpp) 只做「取全域變數 → 呼叫純函式 → 送出」**，
  真正的格式化正確性由這裡的測試保證。
- 新增可測邏輯時：先在此加測試（TDD），再寫實作，`run_tests.sh` 全綠才整合。

## 產物

`test_reading_format`（編譯出的執行檔）不進版控，已在 `.gitignore` 排除。
