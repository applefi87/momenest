/**********************************************************************
 * reading_format.cpp — 感測讀值序列化實作 (見 reading_format.h)
 * 純標準 C，可在 PC 上編譯測試，也被 Arduino 韌體 (net/ble) 直接使用。
 **********************************************************************/
#include "reading_format.h"
#include <math.h>
#include <stdio.h>

// 浮點：NaN → "null"，否則固定 2 位小數
static void fmtFloat(char* out, size_t n, float v) {
    if (isnan(v)) snprintf(out, n, "null");
    else          snprintf(out, n, "%.2f", v);
}

// 整數：未接 (valid=false) → "null"，否則原始 ADC 值
static void fmtInt(char* out, size_t n, int v, bool valid) {
    if (!valid) snprintf(out, n, "null");
    else        snprintf(out, n, "%d", v);
}

int formatReadingJson(char* buf, size_t buflen, const SensorReading& r) {
    char at[16], ah[16], wt[16], s[16], wl[16];
    fmtFloat(at, sizeof(at), r.air_temp);
    fmtFloat(ah, sizeof(ah), r.air_hum);
    fmtFloat(wt, sizeof(wt), r.water_temp);
    fmtInt(s,  sizeof(s),  r.soil,        r.soil_valid);
    fmtInt(wl, sizeof(wl), r.water_level, r.water_valid);

    return snprintf(buf, buflen,
        "{\"air_temp\":%s,\"air_hum\":%s,\"water_temp\":%s,\"soil\":%s,\"water_level\":%s}",
        at, ah, wt, s, wl);
}
