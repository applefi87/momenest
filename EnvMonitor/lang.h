/**********************************************************************
 * lang.h — UI 多語系文字表 (JSON)
 *
 * 直接編輯下方 JSON 即可修改文字或新增語言：
 *   1. 複製一個語言區塊 (如 "en")，把 key 改成新語言代碼
 *   2. "btn"   : 右上角語言切換鈕顯示的文字
 *   3. "ascii" : true = 該語言只用英數字 (用內建字型，較省)
 *                中文等 CJK 語言必須設 false (改用 efontTW 字型)
 *   4. 右上角按鈕會依此檔案的語言順序循環切換
 *
 * 注意：R"json( 與 )json" 之間必須是合法 JSON (逗號、引號要正確)
 **********************************************************************/
#pragma once

static const char LANG_JSON[] = R"json(
{
  "en": {
    "ascii": true,
    "btn": "EN",
    "title": "Momenest Env Monitor",
    "air_temp": "Air Temp",
    "air_hum": "Air Humidity",
    "water_t": "Water Temp",
    "soil": "Soil Moisture",
    "w_level": "Water Level",
    "edit": "EDIT",
    "raw": "RAW",
    "use_raw": "USE RAW",
    "min0": "MIN (= 0%)",
    "max100": "MAX (= 100%)",
    "save": "SAVE",
    "cancel": "CANCEL",
    "err_minmax": "MIN must not equal MAX!",
    "calib_soil": "Soil Calibration",
    "calib_water": "Water Level Calibration",
    "theme_light": "Light",
    "theme_dark": "Dark"
  },
  "zh": {
    "ascii": false,
    "btn": "中",
    "title": "漫嶼環境監測",
    "air_temp": "空氣溫度",
    "air_hum": "空氣濕度",
    "water_t": "水溫",
    "soil": "土壤濕度",
    "w_level": "水位",
    "edit": "校準",
    "raw": "原始值",
    "use_raw": "取目前值",
    "min0": "最低 (= 0%)",
    "max100": "最高 (= 100%)",
    "save": "儲存",
    "cancel": "取消",
    "err_minmax": "最低值不可等於最高值！",
    "calib_soil": "土壤濕度校準",
    "calib_water": "水位校準",
    "theme_light": "淺色",
    "theme_dark": "深色"
  }
}
)json";
