/**********************************************************************
 * ui_main.cpp — 主畫面 (深色卡片式) 與觸控分發
 * 版面：頂部標題列 (標題/狀態圓點/語言鈕)
 *       上排三小卡 (空氣溫度/空氣濕度/水溫)
 *       下兩張全寬卡 (土壤濕度/水位)：% 大字 + 橫條 + 原始值 + 校準鈕
 **********************************************************************/
#include "ui.h"
#include "theme.h"
#include "i18n.h"
#include "calibration.h"
#include "sensors.h"
#include "net.h"
#include <WiFi.h>
#include "font_ui.h"
#include "font_big.h"

UiMode uiMode = UI_MAIN;

// ==========================================
// 字體切換工具
// ==========================================
// VLW 字體不應使用 setTextSize() 縮放，而是載入對應大小的字體
static void useFont_UI()  { tft.loadFont(font_ui);  }
static void useFont_Big() { tft.loadFont(font_big); }

// ==========================================
// 共用小工具
// ==========================================
bool inBox(int x, int y, int bx, int by, int bw, int bh) {
    return x >= bx && x < bx + bw && y >= by && y < by + bh;
}

void drawBtn(int x, int y, int w, int h, const char *label,
             uint16_t color) {
    tft.fillRoundRect(x, y, w, h, 8, COL_BG);
    tft.drawRoundRect(x, y, w, h, 8, color);
    tft.setTextColor(color, COL_BG);
    tft.setTextDatum(textdatum_t::middle_center);
    tft.drawString(label, x + w / 2, y + h / 2);
    tft.setTextDatum(textdatum_t::top_left);
}

static void drawCard(int x, int y, int w, int h) {
    tft.fillRoundRect(x, y, w, h, CARD_R, COL_CARD);
    tft.drawRoundRect(x, y, w, h, CARD_R, COL_EDGE);
}

// ==========================================
// 頂部標題列：標題 + WiFi/上傳狀態圓點 + 語言切換鈕
// ==========================================
static void drawStatusDots() {
    tft.fillCircle(DOT_WIFI_X,  HDR_H / 2, 5,
                   WiFi.status() == WL_CONNECTED ? COL_OK : COL_ERR);
    uint16_t c = uploadState == 1 ? COL_OK : (uploadState == 2 ? COL_ERR : COL_MUTED);
    tft.fillCircle(DOT_CLOUD_X, HDR_H / 2, 5, c);
}

static void drawHeader() {
    useFont_UI();
    tft.setTextColor(COL_ACCENT, COL_BG);
    tft.drawString(L("title"), 12, 10);
    tft.drawFastHLine(0, HDR_H - 1, 480, COL_EDGE);

    // 繪製主題與語言切換按鈕
    bool isLight = (currentTheme == &ThemeLight);
    drawBtn(THEME_X, THEME_Y, THEME_W, THEME_H, L(isLight ? "theme_light" : "theme_dark"), COL_MUTED);
    drawBtn(LANG_X, LANG_Y, LANG_W, LANG_H, L("btn"), COL_MUTED);
    drawStatusDots();
}

// ==========================================
// 主畫面
// ==========================================
static const char    *TC_KEYS[3] = { "air_temp", "air_hum", "water_t" };

void uiInit() {
    themeInit();             // 讀取並載入預設主題
    tft.init();
    tft.setRotation(1);      // 橫向 480x320
    tft.setTextSize(1);       // VLW 字體不縮放，固定 1:1

    // 面板殘影 (image retention) 屬物理現象，短暫黑白閃無法消除，
    // 診斷與恢復用獨立工具：test tool/螢幕殘影測試/
    uiDrawMain();
}

void uiDrawMain() {
    tft.fillScreen(COL_BG);
    drawHeader();

    // 上排三小卡
    useFont_UI();
    for (int i = 0; i < 3; i++) {
        int x = 8 + i * (TC_W + 8);
        drawCard(x, TC_Y, TC_W, TC_H);
        tft.setTextColor(COL_MUTED, COL_CARD);
        tft.drawString(L(TC_KEYS[i]), x + 12, TC_Y + 10);
    }

    // 土壤濕度 / 水位 全寬卡
    const char    *bkeys[2] = { "soil", "w_level" };
    const int      ys[2]    = { BC_SOIL_Y, BC_WATER_Y };
    const uint16_t bcols[2] = { COL_SOIL, COL_WATER };
    for (int i = 0; i < 2; i++) {
        drawCard(BC_X, ys[i], BC_W, BC_H);
        tft.setTextColor(COL_MUTED, COL_CARD);
        tft.drawString(L(bkeys[i]), BC_X + 12, ys[i] + 10);
        drawBtn(BTN_EDIT_X, ys[i] + 22, BTN_EDIT_W, BTN_EDIT_H,
                L("edit"), bcols[i]);
    }

    uiDrawValues();
}

// 土壤/水位列：原始值小字 + 百分比橫條 + 大 % 字
static void drawAnalogRow(int y, int raw, bool valid, int mn, int mx, uint16_t color) {
    char buf[24];
    int pct = valid ? (int)constrain(map(raw, mn, mx, 0, 100), 0L, 100L) : 0;

    // 原始值小字
    useFont_UI();
    tft.setTextColor(COL_MUTED, COL_CARD);
    tft.setTextPadding(115);
    if (valid) snprintf(buf, sizeof(buf), "%s %d", L("raw"), raw);
    else       snprintf(buf, sizeof(buf), "%s --", L("raw"));
    tft.drawString(buf, BC_X + 12, y + 44);

    // 百分比橫條
    tft.fillRoundRect(BAR_X, y + 32, BAR_W, BAR_H, 8, COL_BAR_BG);
    if (valid && pct > 0) {
        int w = BAR_W * pct / 100;
        if (w < 12) w = 12;                 // 圓角所需最小寬
        tft.fillRoundRect(BAR_X, y + 32, w, BAR_H, 8, color);
    }

    // 大 % 字 (使用 28px 大字體)
    useFont_Big();
    tft.setTextColor(valid ? color : COL_ERR, COL_CARD);
    // 設定靠右對齊，並用 padding 清除背景。固定留 3 個字 (100%) 的寬度
    tft.setTextPadding(75);
    tft.setTextDatum(textdatum_t::top_right);
    if (valid) snprintf(buf, sizeof(buf), "%d%%", pct);
    else       snprintf(buf, sizeof(buf), "--");
    // 畫在編輯按鈕的左側 15px 處
    tft.drawString(buf, BTN_EDIT_X - 15, y + 20);
    tft.setTextDatum(textdatum_t::top_left); // 恢復預設
    tft.setTextPadding(0);
}

void uiDrawValues() {
    char buf[24];
    const float vals[3]  = { airTemp, airHum, waterTemp };
    const char *units[3] = { "C", "%", "C" };
    const uint16_t tc_cols[3] = { COL_TEMP, COL_HUM, COL_WTEMP };

    useFont_UI();
    for (int i = 0; i < 3; i++) {
        int x = 8 + i * (TC_W + 8);
        tft.setTextColor(isnan(vals[i]) ? COL_ERR : tc_cols[i], COL_CARD);
        if (isnan(vals[i])) snprintf(buf, sizeof(buf), "ERR");
        else                snprintf(buf, sizeof(buf), "%.1f %s", vals[i], units[i]);
        tft.setTextPadding(TC_W - 24);
        tft.drawString(buf, x + 12, TC_Y + 44);
    }
    tft.setTextPadding(0);

    drawAnalogRow(BC_SOIL_Y,  soilRaw,  soilValid,  soilMin,  soilMax,  COL_SOIL);
    drawAnalogRow(BC_WATER_Y, waterRaw, waterValid, waterMin, waterMax, COL_WATER);
    drawStatusDots();
}

// ==========================================
// 觸控分發 (loop 已做單次觸發防彈跳)
// 命中範圍比按鈕外擴數 px，較好按
// ==========================================
void uiHandleTouch(int x, int y) {
    if (uiMode == UI_MAIN) {
        // 主題切換按鈕
        if (inBox(x, y, THEME_X - 6, THEME_Y - 6, THEME_W + 12, THEME_H + 12)) {
            themeToggle();
            uiDrawMain();
            return;
        }
        // 語言切換按鈕
        if (inBox(x, y, LANG_X - 6, LANG_Y - 6, LANG_W + 12, LANG_H + 12)) {
            nextLang();
            uiDrawMain();
            return;
        }
        if (inBox(x, y, BTN_EDIT_X - 8, BC_SOIL_Y + 14,  BTN_EDIT_W + 16, BTN_EDIT_H + 16)) {
            uiOpenEditor(true);
            return;
        }
        if (inBox(x, y, BTN_EDIT_X - 8, BC_WATER_Y + 14, BTN_EDIT_W + 16, BTN_EDIT_H + 16)) {
            uiOpenEditor(false);
            return;
        }
        return;
    }
    uiEditTouch(x, y);
}
