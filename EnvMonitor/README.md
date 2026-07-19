# EnvMonitor — ESP32 環境監測系統

ESP32 + 3.5" TFT 觸控螢幕的環境監測系統，每秒更新空氣溫濕度、水溫、土壤濕度與水位。

## 硬體

| 模組 | 介面 | 腳位 |
|------|------|------|
| ESP32 NodeMCU-32 (ESP32-WROOM-E, 38pin) | — | — |
| 3.5" TFT ILI9488 | VSPI | MOSI=23, SCK=18, CS=15, DC=2, RST=4, **SDO 不接**(見下) |
| XPT2046 觸控 | VSPI (共用) | T_DIN=23, T_DO=19, T_CLK=18, CS=5, IRQ(PEN)=16 |

> **重要：螢幕的 SDO/MISO 腳不要接。** ILI9488 模組有已知硬體缺陷：CS 拉高後 SDO 不會進入高阻抗，會持續佔住 MISO 線，導致共用匯流排的 XPT2046 觸控完全讀不到資料。螢幕只寫不讀，SDO 不接沒有任何影響；MISO (GPIO19) 只接觸控的 T_DO。
| SHT45 空氣溫濕度 | I2C | SDA=21, SCL=22 |
| DS18B20 水溫 | 1-Wire | DATA=25 |
| 土壤濕度 | Analog | AO=32 |
| 水位感測 | Analog | S=33 |

所有模組 VCC 接 3.3V、GND 共地。

## Arduino IDE 設定

**Board 選擇（重要，選錯無法編譯）：**

- Tools → Board → esp32 → **ESP32 Dev Module**
  - 不要選 ESP32**S3** / S2 / C3 Dev Module——本板是 ESP32-WROOM-E（Xtensa LX6 雙核），選 S3 會編譯失敗
- Flash Size: 4MB（預設）
- Partition Scheme: Default（含中文介面字型，flash 不足時改 **Huge APP**）
- Upload Speed: 921600（上傳不穩改 115200）
- USB 驅動：板載 CP2102，需安裝 Silicon Labs CP210x 驅動

**ESP32 Core 版本注意：** 本程式使用 `SPI3_HOST`（Core 3.x 寫法）。Core 3.x 已移除舊名 `VSPI_HOST`。

## 需安裝函式庫（Library Manager）

- **LovyanGFX**（螢幕+觸控，腳位設定全在 `display_hw.h`，不需修改 library 檔案）
- **OneWire**
- **DallasTemperature**
- **ArduinoJson**（解析 `lang.h` 的多語系 JSON 文字表）

SHT45 使用內建 Wire 以原始 I2C 指令操作（觸發/讀取分離、CRC-8 校驗），不需額外函式庫。
校準值與語言選擇用內建 Preferences (NVS) 儲存，也不需額外函式庫。

## 程式架構

程式已模組化拆分（腳位 `config.h`、螢幕 `display_hw.*`、感測 `sensors.*`、
UI `ui_*.cpp`…），完整導覽見根目錄 `CLAUDE.md`，動工前先讀 `../DEV_NOTES.md`。

非阻塞 1Hz 嚴格時序（參考 OMNI-TEC 專案做法，實作在 `sensors.cpp`）：

- T=0ms：觸發 SHT45 量測與 DS18B20 溫度轉換（只下指令，立即返回）
- T=800ms：探針上電 → 統一讀取所有結果（>750ms 確保 DS18B20 12-bit 轉換完成）→ 斷電，更新螢幕
- 觸控隨時輪詢（LovyanGFX 用 IRQ 腳快速判斷），單次觸發防彈跳

## 雲端上傳（Cloudflare Worker + D1）

- 每 10 秒 HTTPS POST 一筆到 Worker（部署方式見 `../cloud/README.md`）
- **設定**：複製 `secrets.h.example` 為 `secrets.h`，填入 WiFi 帳密、Worker 網址、API 密鑰（此檔已被 .gitignore 排除）
- WiFi 非阻塞連線 + 每 15 秒自動重連；斷網時本地螢幕照常運作
- 時間戳由伺服器補上，ESP32 不需 NTP

## 校準

- 土壤濕度 / 水位的 0~100% 端點：**直接在螢幕上按各列右側「校準」鈕設定**
  （可微調或一鍵取目前讀值），儲存於 NVS 斷電保留；預設值在 `calibration.cpp`
- 觸控校正值 `x_min/x_max/y_min/y_max`（`display_hw.h`）：預設 300~3600，可再精調
