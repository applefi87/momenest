/**********************************************************************
 * ui_edit.cpp — 土壤/水位校準編輯畫面
 * MIN 對應 0%、MAX 對應 100% (原始 ADC 值)
 * -100/-10/+10/+100 微調；「取目前值」一鍵帶入即時讀值；
 * 儲存寫入 NVS (calibration.cpp)，取消不生效
 **********************************************************************/
#include "ui.h"
#include "theme.h"
#include "i18n.h"
#include "calibration.h"
#include "sensors.h"

static bool editIsSoil = true;
static int  editMin = 0, editMax = 0;   // 暫存值，按儲存才生效

// ==========================================
// 繪製
// ==========================================
static void drawEditValues() {
    char buf[16];
    tft.setFont(&fonts::Font4);
    tft.setTextColor(COL_TEXT, COL_BG);
    tft.setTextPadding(110);
    snprintf(buf, sizeof(buf), "%d", editMin);
    tft.drawString(buf, EDT_VAL_X, EDT_ROW_MIN_Y + 8);
    snprintf(buf, sizeof(buf), "%d", editMax);
    tft.drawString(buf, EDT_VAL_X, EDT_ROW_MAX_Y + 8);
    tft.setTextPadding(0);
}

void uiDrawEditRaw() {                   // 即時原始讀值 (每量測週期更新)
    char buf[16];
    bool valid = editIsSoil ? soilValid : waterValid;
    int  raw   = editIsSoil ? soilRaw   : waterRaw;
    tft.setFont(&fonts::Font4);
    tft.setTextColor(valid ? COL_TEMP : COL_ERR, COL_BG);
    tft.setTextPadding(110);
    if (valid) snprintf(buf, sizeof(buf), "%d", raw);
    else       snprintf(buf, sizeof(buf), "--");
    tft.drawString(buf, 130, 50);
    tft.setTextPadding(0);
}

static void drawEditScreen() {
    tft.fillScreen(COL_BG);
    tft.setFont(fontTitle());
    tft.setTextColor(editIsSoil ? COL_SOIL : COL_WATER, COL_BG);
    tft.drawString(L(editIsSoil ? "calib_soil" : "calib_water"), 12, langAscii() ? 8 : 12);
    tft.drawFastHLine(0, HDR_H - 1, 480, COL_EDGE);

    tft.setFont(fontLabel());
    tft.setTextColor(COL_MUTED, COL_BG);
    tft.drawString(L("raw"),    12,        54);
    tft.drawString(L("min0"),   EDT_VAL_X, EDT_ROW_MIN_Y - 22);
    tft.drawString(L("max100"), EDT_VAL_X, EDT_ROW_MAX_Y - 22);

    for (int row = 0; row < 2; row++) {
        int y = row ? EDT_ROW_MAX_Y : EDT_ROW_MIN_Y;
        drawBtn(EDT_B1_X,   y, EDT_BTN_W,  EDT_ROW_H, "-100", COL_TEXT, &fonts::Font2);
        drawBtn(EDT_B2_X,   y, EDT_BTN_W,  EDT_ROW_H, "-10",  COL_TEXT, &fonts::Font2);
        drawBtn(EDT_B3_X,   y, EDT_BTN_W,  EDT_ROW_H, "+10",  COL_TEXT, &fonts::Font2);
        drawBtn(EDT_B4_X,   y, EDT_BTN_W,  EDT_ROW_H, "+100", COL_TEXT, &fonts::Font2);
        drawBtn(EDT_BRAW_X, y, EDT_BRAW_W, EDT_ROW_H, L("use_raw"), COL_TEMP, fontLabel());
    }
    drawBtn(EDT_SAVE_X,   EDT_ACT_Y, EDT_ACT_W, EDT_ACT_H, L("save"),   COL_OK,  fontLabel());
    drawBtn(EDT_CANCEL_X, EDT_ACT_Y, EDT_ACT_W, EDT_ACT_H, L("cancel"), COL_ERR, fontLabel());

    drawEditValues();
    uiDrawEditRaw();
}

// ==========================================
// 開啟 / 儲存 / 關閉
// ==========================================
void uiOpenEditor(bool isSoil) {
    editIsSoil = isSoil;
    editMin = isSoil ? soilMin : waterMin;
    editMax = isSoil ? soilMax : waterMax;
    uiMode = UI_EDIT;
    drawEditScreen();
}

static void closeEditor() {
    uiMode = UI_MAIN;
    uiDrawMain();
}

static void saveEditor() {
    if (editMin == editMax) {            // 防呆：min=max 換算會除以零
        tft.setFont(fontLabel());
        tft.setTextColor(COL_ERR, COL_BG);
        tft.drawString(L("err_minmax"), EDT_VAL_X, EDT_ACT_Y - 24);
        return;
    }
    calibSave(editIsSoil, editMin, editMax);
    closeEditor();
}

// ==========================================
// 觸控
// ==========================================
void uiEditTouch(int x, int y) {
    int *v = nullptr;
    int rowY = 0;
    if (y >= EDT_ROW_MIN_Y && y < EDT_ROW_MIN_Y + EDT_ROW_H) { v = &editMin; rowY = EDT_ROW_MIN_Y; }
    if (y >= EDT_ROW_MAX_Y && y < EDT_ROW_MAX_Y + EDT_ROW_H) { v = &editMax; rowY = EDT_ROW_MAX_Y; }

    if (v) {
        if      (inBox(x, y, EDT_B1_X,   rowY, EDT_BTN_W,  EDT_ROW_H)) *v -= 100;
        else if (inBox(x, y, EDT_B2_X,   rowY, EDT_BTN_W,  EDT_ROW_H)) *v -= 10;
        else if (inBox(x, y, EDT_B3_X,   rowY, EDT_BTN_W,  EDT_ROW_H)) *v += 10;
        else if (inBox(x, y, EDT_B4_X,   rowY, EDT_BTN_W,  EDT_ROW_H)) *v += 100;
        else if (inBox(x, y, EDT_BRAW_X, rowY, EDT_BRAW_W, EDT_ROW_H)) *v = editIsSoil ? soilRaw : waterRaw;
        else return;
        *v = constrain(*v, 0, 4095);     // ESP32 ADC 12-bit 範圍
        drawEditValues();
        return;
    }

    if      (inBox(x, y, EDT_SAVE_X,   EDT_ACT_Y, EDT_ACT_W, EDT_ACT_H)) saveEditor();
    else if (inBox(x, y, EDT_CANCEL_X, EDT_ACT_Y, EDT_ACT_W, EDT_ACT_H)) closeEditor();
}
