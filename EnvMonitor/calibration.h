/**********************************************************************
 * calibration.h — 土壤/水位校準值 (原始 ADC -> 0~100%)
 * 預設值寫死在 calibration.cpp；螢幕校準畫面修改後存 NVS，斷電保留
 **********************************************************************/
#pragma once

extern int soilMin,  soilMax;    // 土壤：min=0% (乾), max=100% (濕)
extern int waterMin, waterMax;   // 水位：min=0%,      max=100%

void calibInit();                              // 從 NVS 載入 (沒存過用預設)
void calibSave(bool isSoil, int mn, int mx);   // 套用並寫入 NVS
