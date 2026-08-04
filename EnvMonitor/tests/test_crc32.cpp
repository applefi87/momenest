/**********************************************************************
 * test_crc32.cpp — ota_protocol 的 CRC-32 純函式 host 單元測試 (TDD)
 *
 * 這組 CRC 必須與 Kotlin 端的 java.util.zip.CRC32 位元完全一致，否則
 * 每次 OTA 都會被誤判成資料損毀。故用公開的標準向量釘死行為，
 * 而不是「跟自己的實作比對」。
 *
 * g++ 編譯執行，不需 ESP32。見 tests/README.md 或 ./run_tests.sh
 **********************************************************************/
#include "../ota_protocol.h"
#include <cstdio>
#include <cstring>

static int g_pass = 0, g_fail = 0;

static void expectHex(const char* name, uint32_t got, uint32_t want) {
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

static void expectTrue(const char* name, bool cond) {
    if (cond) { g_pass++; printf("  [PASS] %s\n", name); }
    else      { g_fail++; printf("  [FAIL] %s\n", name); }
}

// 一次算完整段資料的便利包裝
static uint32_t crcOf(const void* data, size_t len) {
    return crc32Final(crc32Update(crc32Init(), (const uint8_t*)data, len));
}

static uint32_t crcOfStr(const char* s) { return crcOf(s, strlen(s)); }

int main() {
    printf("crc32 單元測試\n");

    // --- 標準向量 (與 java.util.zip.CRC32 相同) ---
    expectHex("空資料 → 0",              crcOf(nullptr, 0), 0x00000000u);
    expectHex("\"123456789\" → CBF43926", crcOfStr("123456789"), 0xCBF43926u);
    expectHex("\"a\" → E8B7BE43",         crcOfStr("a"), 0xE8B7BE43u);
    expectHex("\"abc\" → 352441C2",       crcOfStr("abc"), 0x352441C2u);
    expectHex("單一 0x00 位元組 → D202EF8D",
              crcOf("\0", 1), 0xD202EF8Du);

    // --- 初值/收尾語意 ---
    expectHex("crc32Init 初值 0xFFFFFFFF", crc32Init(), 0xFFFFFFFFu);
    expectHex("未餵資料就 Final → 0", crc32Final(crc32Init()), 0x00000000u);

    // --- 分段 update 與一次算完結果相同 (OTA 是邊收邊算，這點最關鍵) ---
    {
        const char* s = "123456789";
        uint32_t c = crc32Init();
        c = crc32Update(c, (const uint8_t*)s, 4);        // "1234"
        c = crc32Update(c, (const uint8_t*)s + 4, 5);    // "56789"
        expectHex("分兩段 = 一次算完", crc32Final(c), 0xCBF43926u);
    }
    {
        // 逐位元組餵入 (極端分塊，模擬 MTU 很小的手機)
        const char* s = "123456789";
        uint32_t c = crc32Init();
        for (size_t i = 0; i < strlen(s); i++) {
            c = crc32Update(c, (const uint8_t*)s + i, 1);
        }
        expectHex("逐位元組 = 一次算完", crc32Final(c), 0xCBF43926u);
    }
    {
        // 中間插入長度 0 的 update 不應改變結果 (BLE 可能收到空封包)
        const char* s = "abc";
        uint32_t c = crc32Init();
        c = crc32Update(c, (const uint8_t*)s, 1);
        c = crc32Update(c, (const uint8_t*)s, 0);
        c = crc32Update(c, (const uint8_t*)s + 1, 2);
        expectHex("len=0 的 update 不影響結果", crc32Final(c), 0x352441C2u);
    }

    // --- 防呆：空指標不應改變累積值也不應當機 ---
    {
        uint32_t c = crc32Update(crc32Init(), (const uint8_t*)"abc", 3);
        expectHex("nullptr 不改變累積值", crc32Update(c, nullptr, 10), c);
    }

    // --- 位元組順序敏感：換順序結果必須不同 (證明不是弱校驗) ---
    expectTrue("\"ab\" 與 \"ba\" 結果不同", crcOfStr("ab") != crcOfStr("ba"));

    // --- 長資料 (模擬一塊 512B 韌體) 可重現 ---
    {
        uint8_t buf[512];
        for (size_t i = 0; i < sizeof(buf); i++) buf[i] = (uint8_t)(i % 256);
        uint32_t a = crcOf(buf, sizeof(buf));
        uint32_t b = crcOf(buf, sizeof(buf));
        expectTrue("同資料兩次計算一致", a == b);
        // 改一個位元組就要變 (單點錯誤偵測)
        buf[100] ^= 0x01;
        expectTrue("翻轉 1 bit 後 CRC 改變", crcOf(buf, sizeof(buf)) != a);
    }

    printf("\n結果: %d passed, %d failed\n", g_pass, g_fail);
    return g_fail == 0 ? 0 : 1;
}
