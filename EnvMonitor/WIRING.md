# EnvMonitor 硬體接線說明

對應程式版本：`EnvMonitor.ino`（LovyanGFX 版）。腳位若改動，以程式碼 `LGFX` class 與 `#define PIN_*` 區塊為準。

## 電源

| 項目 | 接法 | 說明 |
|------|------|------|
| 螢幕 VDD | 3.3V | 主供電，**不要接 5V** |
| 螢幕 BL | 3.3V | 背光恆亮（程式未控制背光腳） |
| 各感測器 VCC | 3.3V | 例外：水位感測器 VCC 接 GPIO32（見下） |
| GND | 全部共地 | 所有模組 GND 接 ESP32 GND |

## 3.5" TFT ILI9488（螢幕端排針）

| 螢幕腳位 | ESP32 腳位 | 程式碼對應 | 說明 |
|---------|-----------|-----------|------|
| VDD | 3.3V | — | 主供電 |
| GND | GND | — | 共地 |
| CS | GPIO14 | `pcfg.pin_cs = 14` | TFT 片選 |
| RST | GPIO4 | `pcfg.pin_rst = 4` | 硬體重置 |
| D/C | GPIO27 | `bcfg.pin_dc = 27` | 資料/命令 |
| SDI | GPIO23 | `bcfg.pin_mosi = 23` | MOSI，與觸控 TDI 並聯共用 |
| SCK | GPIO18 | `bcfg.pin_sclk = 18` | 時脈，與觸控 TCK 並聯共用 |
| BL | 3.3V | — | 背光恆亮 |
| SDO | **不接，懸空** | — | ⚠️ ILI9488 硬體缺陷：CS 拉高後 SDO 不釋放 MISO 線，接了會壓死觸控訊號 |

## XPT2046 觸控（同一塊模組的觸控排針）

| 觸控腳位 | ESP32 腳位 | 程式碼對應 | 說明 |
|---------|-----------|-----------|------|
| TCK | GPIO18 | `tcfg.pin_sclk = 18` | 與螢幕 SCK 並聯 |
| TCS | GPIO5 | `tcfg.pin_cs = 5` | 觸控片選（獨立） |
| TDI | GPIO23 | `tcfg.pin_mosi = 23` | 與螢幕 SDI 並聯 |
| TDO | GPIO19 | `tcfg.pin_miso = 19` | **整個系統唯一接 MISO 的線** |
| PEN | GPIO16 | `tcfg.pin_int = 16` | 觸控中斷（IRQ） |

## 感測器

| 感測器 | 腳位 | ESP32 腳位 | 程式碼對應 | 說明 |
|--------|------|-----------|-----------|------|
| SHT45 空氣溫濕度 | SDA | GPIO21 | `PIN_SDA` | I2C 資料 |
| | SCL | GPIO22 | `PIN_SCL` | I2C 時脈 |
| DS18B20 水溫 | DATA | GPIO25 | `PIN_ONEWIRE` | ⚠️ DATA 與 3.3V 之間需接 **4.7kΩ 上拉電阻**，沒接會讀不到（回傳 -127） |
| 土壤濕度 | AO | GPIO35 | `PIN_SOIL` | GPIO35 為純輸入腳，只能當 ADC，正好 |
| 水位感測器 | S | GPIO33 | `PIN_WATER_LVL` | 類比訊號 |
| | VCC | **GPIO32** | `PIN_WATER_PWR` | ⚠️ 不接 3.3V！量測前才由程式上電（防電解腐蝕與極化漂移），讀完立即斷電 |

## 注意事項

1. **SDO 千萬不要接**——這塊 ILI9488 的頭號陷阱，接了觸控就讀不到。
2. **DS18B20 必須有 4.7kΩ 上拉**，防水型的三條線：紅=3.3V、黑=GND、黃=DATA。
3. **strapping 腳**：GPIO5（TCS）、GPIO4 等在開機瞬間電位敏感。若開機不穩（序列埠無輸出），先拔螢幕/觸控排線測試。
4. **麵包板/杜邦線品質**是本專案已知的不穩定來源：SPI 已降速至 16MHz；若畫面破碎，檢查線長與接觸，必要時再降速。
5. ADC 類比感測器（土壤、水位）數值會隨供電與雜訊浮動，換算百分比的乾/濕邊界值需自行校正。
