/**********************************************************************
 * test_reading_format.cpp — reading_format 的 host 單元測試 (TDD)
 *
 * 這是「在 PC 上跑」的測試，不需要 ESP32。用 g++ 編譯執行：
 *   見 tests/README.md，或直接 ./run_tests.sh
 *
 * 只依賴標準 C/C++，刻意與韌體/硬體隔離。
 **********************************************************************/
#include "../reading_format.h"
#include <cstdio>
#include <cstring>
#include <cmath>

static int g_pass = 0, g_fail = 0;

// 比對序列化結果是否等於預期字串
static void expectJson(const char* name, const SensorReading& r, const char* want) {
    char buf[256];
    int n = formatReadingJson(buf, sizeof(buf), r);
    bool ok = (strcmp(buf, want) == 0) && (n == (int)strlen(want));
    if (ok) {
        g_pass++;
        printf("  [PASS] %s\n", name);
    } else {
        g_fail++;
        printf("  [FAIL] %s\n", name);
        printf("         want: %s\n", want);
        printf("         got : %s  (return=%d, len=%lu)\n", buf, n, (unsigned long)strlen(want));
    }
}

static void expectTrue(const char* name, bool cond) {
    if (cond) { g_pass++; printf("  [PASS] %s\n", name); }
    else      { g_fail++; printf("  [FAIL] %s\n", name); }
}

int main() {
    printf("reading_format 單元測試\n");

    const float NANF = NAN;

    // 1. 全部有效的正常值
    expectJson("normal values",
        { 24.58f, 58.07f, 31.0f, 2100, 1500, true, true },
        "{\"air_temp\":24.58,\"air_hum\":58.07,\"water_temp\":31.00,\"soil\":2100,\"water_level\":1500}");

    // 2. 浮點固定 2 位小數 (整數溫度補 .00、四捨五入)
    expectJson("float 2 decimals",
        { 31.0f, 5.5f, 24.581f, 0, 0, true, true },
        "{\"air_temp\":31.00,\"air_hum\":5.50,\"water_temp\":24.58,\"soil\":0,\"water_level\":0}");

    // 3. 負溫度
    expectJson("negative temp",
        { -5.5f, 40.0f, -0.25f, 100, 200, true, true },
        "{\"air_temp\":-5.50,\"air_hum\":40.00,\"water_temp\":-0.25,\"soil\":100,\"water_level\":200}");

    // 4. 空氣溫度讀取失敗 (NaN) → null
    expectJson("air_temp NaN -> null",
        { NANF, 58.07f, 31.0f, 2100, 1500, true, true },
        "{\"air_temp\":null,\"air_hum\":58.07,\"water_temp\":31.00,\"soil\":2100,\"water_level\":1500}");

    // 5. 土壤未接 (soil_valid=false) → null，即使 soil 有數字
    expectJson("soil invalid -> null",
        { 24.58f, 58.07f, 31.0f, 9999, 1500, false, true },
        "{\"air_temp\":24.58,\"air_hum\":58.07,\"water_temp\":31.00,\"soil\":null,\"water_level\":1500}");

    // 6. 水位未接 → null
    expectJson("water_level invalid -> null",
        { 24.58f, 58.07f, 31.0f, 2100, 9999, true, false },
        "{\"air_temp\":24.58,\"air_hum\":58.07,\"water_temp\":31.00,\"soil\":2100,\"water_level\":null}");

    // 7. 全部無效
    expectJson("all invalid -> all null",
        { NANF, NANF, NANF, 0, 0, false, false },
        "{\"air_temp\":null,\"air_hum\":null,\"water_temp\":null,\"soil\":null,\"water_level\":null}");

    // 8. 截斷語意同 snprintf：回傳「應寫入長度」，buf 為合法字串且不溢位
    {
        SensorReading r = { 24.58f, 58.07f, 31.0f, 2100, 1500, true, true };
        char small[10];
        int n = formatReadingJson(small, sizeof(small), r);
        expectTrue("truncation: return is full length", n > (int)sizeof(small));
        expectTrue("truncation: buf null-terminated within bounds",
                   strlen(small) < sizeof(small));
    }

    printf("\n結果: %d passed, %d failed\n", g_pass, g_fail);
    return g_fail == 0 ? 0 : 1;
}
