/**********************************************************************
 * sensors.h — 感測器讀取 (SHT45 / DS18B20 / 土壤 / 水位)
 * 1Hz 非阻塞量測時序由 sensorsLoop() 內部管理：
 *   T=0      觸發 SHT45 + DS18B20 (只下指令不等待)
 *   T=800ms  探針上電 -> 統一讀取 -> 斷電
 **********************************************************************/
#pragma once
#include <Arduino.h>

extern float airTemp, airHum, waterTemp;   // NAN = 該次讀取失敗
extern int   soilRaw, waterRaw;            // 原始 ADC 值 (中位數取樣)
extern bool  soilValid, waterValid;        // false = 讀到 0，視為未接/接觸不良

void sensorsInit();
bool sensorsLoop(unsigned long now);       // 每圈呼叫；回傳 true = 本次完成一輪讀取
