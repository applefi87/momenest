/**********************************************************************
 * 螢幕殘影 (image retention) 診斷 + 恢復工具 — 獨立測試程式
 *
 * 用法：燒錄後螢幕顯示全屏 50% 灰 → 觸控任意處切換模式
 *
 * 判讀：
 *   - 在「灰階」畫面上仍隱約看到舊介面文字 → 面板殘影 (物理極化)，
 *     與韌體無關；殘影會隨顯示變動內容或關機放置而淡化
 *   - 灰階畫面完全均勻乾淨 → 殘影已消 / 或問題出在韌體繪製
 *
 * 恢復：切到最後的「恢復模式」(黑白每 500ms 交替) 掛著跑 1~2 小時，
 *       加速去極化；或直接關機放置一晚
 *
 * 模式順序：灰50% → 灰25% → 灰75% → 白 → 黑 → 紅 → 綠 → 藍 → 恢復模式
 * (模式名稱會在左上角顯示 1 秒後消失，避免文字本身造成殘留)
 **********************************************************************/
#define LGFX_USE_V1
#include <LovyanGFX.hpp>

// ---- 螢幕/觸控硬體設定 (與主程式 display_hw.h 相同) ----
class LGFX : public lgfx::LGFX_Device {
    lgfx::Panel_ILI9488 _panel;
    lgfx::Bus_SPI       _bus;
    lgfx::Touch_XPT2046 _touch;
public:
    LGFX() {
        auto bcfg = _bus.config();
        bcfg.spi_host = SPI3_HOST;
        bcfg.spi_mode = 0;
        bcfg.freq_write = 27000000;
        bcfg.freq_read  = 16000000;
        bcfg.pin_sclk = 18;
        bcfg.pin_mosi = 23;
        bcfg.pin_miso = 19;
        bcfg.pin_dc   = 27;
        _bus.config(bcfg);
        _panel.setBus(&_bus);

        auto pcfg = _panel.config();
        pcfg.pin_cs  = 14;
        pcfg.pin_rst = 4;
        pcfg.panel_width  = 320;
        pcfg.panel_height = 480;
        pcfg.invert = true;          // 與主程式一致
        pcfg.bus_shared = true;
        _panel.config(pcfg);

        auto tcfg = _touch.config();
        tcfg.spi_host = SPI3_HOST;
        tcfg.freq = 2500000;
        tcfg.pin_sclk = 18;
        tcfg.pin_mosi = 23;
        tcfg.pin_miso = 19;
        tcfg.pin_cs   = 5;
        tcfg.pin_int  = 16;
        tcfg.x_min = 300; tcfg.x_max = 3600;
        tcfg.y_min = 300; tcfg.y_max = 3600;
        _touch.config(tcfg);
        _panel.setTouch(&_touch);

        setPanel(&_panel);
    }
};

LGFX tft;

// ---- 測試模式 ----
struct Mode { const char *name; uint8_t r, g, b; };
const Mode MODES[] = {
    { "GRAY 50%",  128, 128, 128 },   // 殘影最容易現形
    { "GRAY 25%",   64,  64,  64 },
    { "GRAY 75%",  192, 192, 192 },
    { "WHITE",     255, 255, 255 },
    { "BLACK",       0,   0,   0 },
    { "RED",       255,   0,   0 },   // 順便檢查色彩均勻度
    { "GREEN",       0, 255,   0 },
    { "BLUE",        0,   0, 255 },
    { "RECOVERY",    0,   0,   0 },   // 黑白交替去極化
};
const int N_MODES = sizeof(MODES) / sizeof(MODES[0]);
const int RECOVERY = N_MODES - 1;

int  mode = 0;
bool touchHeld = false;
bool recoveryPhase = false;
unsigned long labelUntil = 0, lastFlip = 0;

void showMode() {
    const Mode &m = MODES[mode];
    tft.fillScreen(tft.color565(m.r, m.g, m.b));
    // 模式名稱顯示 1 秒後抹掉，避免文字本身造成殘留
    tft.setFont(&fonts::Font4);
    tft.setTextColor(m.r + m.g + m.b > 380 ? TFT_BLACK : TFT_WHITE);
    tft.drawString(m.name, 10, 10);
    labelUntil = millis() + 1000;
    Serial.printf("Mode %d/%d: %s\n", mode + 1, N_MODES, m.name);
}

void setup() {
    Serial.begin(115200);
    tft.init();
    tft.setRotation(1);
    Serial.println("殘影測試：觸控切換模式；灰階畫面上看得到舊介面 = 面板殘影");
    showMode();
}

void loop() {
    unsigned long now = millis();

    // 模式名稱 1 秒後抹掉，還原純色畫面
    if (labelUntil && now > labelUntil) {
        labelUntil = 0;
        const Mode &m = MODES[mode];
        if (mode != RECOVERY) tft.fillScreen(tft.color565(m.r, m.g, m.b));
    }

    // 恢復模式：黑白每 500ms 交替
    if (mode == RECOVERY && !labelUntil && now - lastFlip >= 500) {
        lastFlip = now;
        recoveryPhase = !recoveryPhase;
        tft.fillScreen(recoveryPhase ? TFT_WHITE : TFT_BLACK);
    }

    // 觸控切換 (單次觸發)
    uint16_t tx, ty;
    bool touched = tft.getTouch(&tx, &ty);
    if (touched && !touchHeld) {
        touchHeld = true;
        mode = (mode + 1) % N_MODES;
        showMode();
    }
    if (!touched) touchHeld = false;
}
