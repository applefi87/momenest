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

// 數值不符時印出 want/got 方便定位 (例如 LE 位元組拼錯)
static void checkHex(const char* name, uint32_t got, uint32_t want) {
    if (got == want) {
        g_pass++;
        printf("  [PASS] %s\n", name);
    } else {
        g_fail++;
        printf("  [FAIL] %s\n", name);
        printf("         want: 0x%08X\n", (unsigned)want);
        printf("         got : 0x%08X\n", (unsigned)got);
    }
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
    // BEGIN 不受 CRC 改動影響 (回歸)：hasCrc 應維持 false
    {
        uint8_t p[] = { 0x01, 0xE8, 0x03, 0x00, 0x00 };
        check("BEGIN hasCrc=false", !parseOtaControl(p, sizeof(p)).hasCrc);
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

    // --- END 的選配 CRC32 (向後相容是硬性需求) ---
    // 舊版 App 只送 1 byte END：必須照樣 valid 且 hasCrc=false (不去驗校驗碼)
    {
        uint8_t e[] = { 0x02 };
        OtaControl c = parseOtaControl(e, sizeof(e));
        check("END 1 byte → valid",       c.valid && c.cmd == OTA_CMD_END);
        check("END 1 byte → hasCrc=false", !c.hasCrc);
        check("END 1 byte → crc32=0",     c.crc32 == 0);
    }
    // 新版 App 送 [0x02][crc LE32]：0xCBF43926 → LE 位元組 26 39 F4 CB
    {
        uint8_t e[] = { 0x02, 0x26, 0x39, 0xF4, 0xCB };
        OtaControl c = parseOtaControl(e, sizeof(e));
        check("END+CRC → cmd/valid", c.valid && c.cmd == OTA_CMD_END);
        check("END+CRC → hasCrc=true", c.hasCrc);
        checkHex("END+CRC 解析 crc32 (LE)", c.crc32, 0xCBF43926u);
    }
    // CRC 最高位為 1 (0xFFFFFFFF) 不可被誤判成負數
    {
        uint8_t e[] = { 0x02, 0xFF, 0xFF, 0xFF, 0xFF };
        checkHex("END+CRC 全 1 不溢位", parseOtaControl(e, sizeof(e)).crc32, 0xFFFFFFFFu);
    }
    // CRC 為 0 時仍算「有帶」(0 是合法校驗值，不能用 crc32==0 判斷有無)
    {
        uint8_t e[] = { 0x02, 0x00, 0x00, 0x00, 0x00 };
        OtaControl c = parseOtaControl(e, sizeof(e));
        check("END+CRC=0 仍 hasCrc=true", c.hasCrc && c.valid);
    }
    // 長度介於 2~4 的畸形 END：容忍成「不帶 CRC」而非拒絕，避免相容性意外
    {
        uint8_t e[] = { 0x02, 0x26, 0x39 };
        OtaControl c = parseOtaControl(e, sizeof(e));
        check("END 長度 3 → valid 但不帶 CRC", c.valid && !c.hasCrc);
    }
    // 尾端多餘位元組不影響前 4 bytes 的 CRC 解析
    {
        uint8_t e[] = { 0x02, 0x26, 0x39, 0xF4, 0xCB, 0xAA, 0xBB };
        checkHex("END 有多餘尾巴仍取前 4 bytes", parseOtaControl(e, sizeof(e)).crc32,
                 0xCBF43926u);
    }
    // ABORT 不帶 CRC 欄位語意
    {
        uint8_t a[] = { 0x03 };
        check("ABORT hasCrc=false", !parseOtaControl(a, sizeof(a)).hasCrc);
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
