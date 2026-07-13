/**********************************************************************
 * i18n.h — 多語系 (文字表在 lang.h 的 JSON，用 ArduinoJson 解析)
 * 語言選擇存 NVS，斷電保留；右上角按鈕循環切換
 **********************************************************************/
#pragma once
#include "display_hw.h"

void i18nInit();                    // 解析 lang.h + 載入上次選的語言
const char* L(const char* key);     // 取目前語言的文字，查不到回傳 key 本身
bool langAscii();                   // 目前語言是否純英數 (決定字型)
void nextLang();                    // 切換到下一個語言並存 NVS

// 目前語言適用的字型 (英數用內建字型；中文用 efontTW)
const lgfx::IFont* fontLabel();     // 標籤/按鈕小字
const lgfx::IFont* fontTitle();     // 標題
