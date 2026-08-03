/**********************************************************************
 * test_ota_protocol.cpp — ota_protocol 的 host 單元測試 (TDD)
 * g++ 編譯執行，不需 ESP32。見 tests/README.md 或 ./run_tests.sh
 **********************************************************************/
#include "../ota_protocol.h"
#include <cstdio>

static int g_pass = 0, g_fail = 0;
static void check(const char* name, bool ok) {
    if (ok) { g_pass++; printf("  [PASS] %s\n", name); }
    else    { g_fail++; printf("  [FAIL] %s\n", name); }
}

int main() {
    printf("ota_protocol 單元測試\n");

    // --- parseOtaControl ---
    // BEGIN + size=1000 (0x000003E8 → LE 位元組 E8 03 00 00)
    {
        uint8_t p[] = { 0x01, 0xE8, 0x03, 0x00, 0x00 };
        OtaControl c = parseOtaControl(p, sizeof(p));
        check("BEGIN 解析 cmd", c.cmd == OTA_CMD_BEGIN);
        check("BEGIN 解析 size (LE)", c.size == 1000);
        check("BEGIN valid", c.valid);
    }
    // BEGIN size=0 → 非法
    {
        uint8_t p[] = { 0x01, 0x00, 0x00, 0x00, 0x00 };
        OtaControl c = parseOtaControl(p, sizeof(p));
        check("BEGIN size=0 → invalid", !c.valid);
    }
    // BEGIN 太短 (缺 size) → 非法
    {
        uint8_t p[] = { 0x01, 0x10 };
        OtaControl c = parseOtaControl(p, sizeof(p));
        check("BEGIN 太短 → invalid", !c.valid);
    }
    // END / ABORT
    {
        uint8_t e[] = { 0x02 };
        uint8_t a[] = { 0x03 };
        check("END",   parseOtaControl(e, 1).cmd == OTA_CMD_END   && parseOtaControl(e,1).valid);
        check("ABORT", parseOtaControl(a, 1).cmd == OTA_CMD_ABORT && parseOtaControl(a,1).valid);
    }
    // 未知 opcode / 空 buffer
    {
        uint8_t u[] = { 0x7F };
        check("未知 opcode → invalid", !parseOtaControl(u, 1).valid);
        check("空 buffer → invalid",   !parseOtaControl(nullptr, 0).valid);
    }
    // 大 size (>16bit) 確認 32-bit LE 正確：0x00123456 → 56 34 12 00
    {
        uint8_t p[] = { 0x01, 0x56, 0x34, 0x12, 0x00 };
        check("BEGIN 大 size 32-bit LE", parseOtaControl(p, sizeof(p)).size == 0x00123456u);
    }

    // --- otaPercent ---
    check("percent total=0 → 0",   otaPercent(0, 0) == 0);
    check("percent 0/1000 → 0",    otaPercent(0, 1000) == 0);
    check("percent 500/1000 → 50", otaPercent(500, 1000) == 50);
    check("percent 999/1000 → 99", otaPercent(999, 1000) == 99);
    check("percent full → 100",    otaPercent(1000, 1000) == 100);
    check("percent over → 100",    otaPercent(2000, 1000) == 100);
    // 大數不溢位 (1.5MB)
    check("percent 大數不溢位",     otaPercent(786432, 1572864) == 50);

    printf("\n結果: %d passed, %d failed\n", g_pass, g_fail);
    return g_fail == 0 ? 0 : 1;
}
