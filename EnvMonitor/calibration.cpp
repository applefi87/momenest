#include "calibration.h"
#include <Arduino.h>
#include <Preferences.h>

// 預設校準值 (寫死；可由螢幕校準畫面修改)
int soilMin  = 1300, soilMax  = 2800;
int waterMin = 500,  waterMax = 1770;

static Preferences prefs;

void calibInit() {
    prefs.begin("calib", false);
    soilMin  = prefs.getInt("sMin", soilMin);
    soilMax  = prefs.getInt("sMax", soilMax);
    waterMin = prefs.getInt("wMin", waterMin);
    waterMax = prefs.getInt("wMax", waterMax);
    Serial.printf("Calib loaded: soil %d~%d, water %d~%d\n",
                  soilMin, soilMax, waterMin, waterMax);
}

void calibSave(bool isSoil, int mn, int mx) {
    if (isSoil) { soilMin = mn;  soilMax = mx; }
    else        { waterMin = mn; waterMax = mx; }
    prefs.putInt(isSoil ? "sMin" : "wMin", mn);
    prefs.putInt(isSoil ? "sMax" : "wMax", mx);
    Serial.printf("Calib saved: %s MIN=%d MAX=%d\n", isSoil ? "soil" : "water", mn, mx);
}
