/**********************************************************************
 * ui.h — 螢幕 UI 總入口
 *   ui_main.cpp : 主畫面 (卡片/橫條/狀態圓點/語言鈕) + 觸控分發
 *   ui_edit.cpp : 土壤/水位校準編輯畫面
 * 版面座標與色票在 theme.h；文字在 lang.h
 **********************************************************************/
#pragma once
#include "display_hw.h"

enum UiMode { UI_MAIN, UI_EDIT };
extern UiMode uiMode;

// ---- 主流程呼叫 ----
void uiInit();                       // tft 初始化 + 畫主畫面
void uiDrawMain();                   // 主畫面全部重繪 (切語言/離開編輯時)
void uiDrawValues();                 // 主畫面數值更新 (每量測週期)
void uiDrawEditRaw();                // 編輯畫面即時原始讀值更新
void uiHandleTouch(int x, int y);    // 觸控事件分發 (單次觸發)

// ---- 共用繪圖小工具 (ui_main.cpp 實作) ----
bool inBox(int x, int y, int bx, int by, int bw, int bh);
void drawBtn(int x, int y, int w, int h, const char *label,
             uint16_t color, const lgfx::IFont *font);

// ---- 校準編輯畫面 (ui_edit.cpp 實作) ----
void uiOpenEditor(bool isSoil);
void uiEditTouch(int x, int y);
