/**********************************************************************
 * theme.h — 深色主題色票與版面座標 (改配色/排版只動這裡)
 * 螢幕為 480x320 橫向
 **********************************************************************/
#pragma once
#include "display_hw.h"

// ==========================================
// 色票 (深色質感主題)
// ==========================================
#define COL_BG      lgfx::color565(16, 20, 26)     // 全域背景
#define COL_CARD    lgfx::color565(28, 34, 43)     // 卡片底
#define COL_EDGE    lgfx::color565(42, 51, 64)     // 卡片邊框/分隔線
#define COL_TEXT    lgfx::color565(232, 236, 241)  // 主要文字
#define COL_MUTED   lgfx::color565(138, 148, 166)  // 次要文字
#define COL_ACCENT  lgfx::color565(79, 195, 247)   // 標題
#define COL_TEMP    lgfx::color565(255, 201, 77)   // 空氣溫度
#define COL_HUM     lgfx::color565(100, 181, 246)  // 空氣濕度
#define COL_WTEMP   lgfx::color565(129, 199, 132)  // 水溫
#define COL_SOIL    lgfx::color565(255, 162, 77)   // 土壤濕度
#define COL_WATER   lgfx::color565(111, 141, 255)  // 水位
#define COL_OK      lgfx::color565(76, 175, 80)    // 正常/成功
#define COL_ERR     lgfx::color565(239, 83, 80)    // 錯誤/失敗
#define COL_BAR_BG  COL_EDGE                        // 橫條軌道

// ==========================================
// 主畫面版面
// ==========================================
#define HDR_H       40      // 頂部標題列高
#define CARD_R      10      // 卡片圓角

// 右上角：語言切換鈕 + 狀態圓點 (WiFi / 上傳)
#define LANG_X      428
#define LANG_Y      7
#define LANG_W      44
#define LANG_H      26
#define DOT_WIFI_X  392
#define DOT_CLOUD_X 412

// 上排三張小卡 (空氣溫度/濕度/水溫)，x = 8 + i*(TC_W+8)
#define TC_Y        48
#define TC_H        88
#define TC_W        148

// 下兩張全寬卡 (土壤濕度/水位)
#define BC_X        8
#define BC_W        464
#define BC_H        80
#define BC_SOIL_Y   144
#define BC_WATER_Y  232

// 全寬卡內部：百分比橫條 / 大%字 / 校準按鈕
#define BAR_X       140
#define BAR_W       160
#define BAR_H       16
#define PCT_X       305
#define BTN_EDIT_W  62
#define BTN_EDIT_H  36
#define BTN_EDIT_X  (BC_X + BC_W - BTN_EDIT_W - 10)

// ==========================================
// 校準編輯畫面版面
// ==========================================
#define EDT_VAL_X     10     // MIN/MAX 數值顯示 x
#define EDT_ROW_MIN_Y 105    // MIN 按鈕列 y
#define EDT_ROW_MAX_Y 175    // MAX 按鈕列 y
#define EDT_ROW_H     40
#define EDT_BTN_W     60
#define EDT_B1_X      130    // -100
#define EDT_B2_X      195    // -10
#define EDT_B3_X      260    // +10
#define EDT_B4_X      325    // +100
#define EDT_BRAW_X    390    // 取目前讀值
#define EDT_BRAW_W    80
#define EDT_SAVE_X    100
#define EDT_CANCEL_X  260
#define EDT_ACT_Y     258
#define EDT_ACT_W     120
#define EDT_ACT_H     50
