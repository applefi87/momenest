/**********************************************************************
 * display_hw.h — 螢幕/觸控硬體設定 (LovyanGFX)
 * 3.5" SPI TFT ILI9488 + XPT2046 觸控，VSPI 共用匯流排
 * 所有螢幕相關腳位都在這裡，不需修改任何 library 檔案
 **********************************************************************/
#pragma once

#define LGFX_USE_V1
#include <LovyanGFX.hpp>

class LGFX : public lgfx::LGFX_Device {
    lgfx::Panel_ILI9488     _panel;
    lgfx::Bus_SPI           _bus;
    lgfx::Touch_XPT2046     _touch;
public:
    LGFX() {
        // --- SPI 匯流排 (VSPI，螢幕與觸控共用) ---
        auto bcfg = _bus.config();
        bcfg.spi_host   = SPI3_HOST;  // VSPI (ESP32 Core 3.x 改名為 SPI3_HOST)
        bcfg.spi_mode   = 0;
        bcfg.freq_write = 27000000;   // 寫入 27MHz (穩定後可試 40MHz)
        bcfg.freq_read  = 16000000;
        bcfg.pin_sclk   = 18;         // SCK
        bcfg.pin_mosi   = 23;         // MOSI (螢幕 SDI 與 觸控 T_DIN 合併)
        bcfg.pin_miso   = 19;         // MISO：只接觸控 T_DO！
                                      // 螢幕 SDO 千萬不要接——ILI9488 硬體缺陷：
                                      // CS 拉高後 SDO 不會放開 MISO 線，會把觸控訊號壓死
        bcfg.pin_dc     = 27;          // TFT_DC
        _bus.config(bcfg);
        _panel.setBus(&_bus);

        // --- ILI9488 面板 ---
        auto pcfg = _panel.config();
        pcfg.pin_cs   = 14;           // TFT_CS
        pcfg.pin_rst  = 4;            // TFT_RST
        pcfg.panel_width  = 320;
        pcfg.panel_height = 480;
        pcfg.invert = true;           // 此面板顏色反相 (實測黑白顛倒)；若換面板顏色相反改 false
        pcfg.bus_shared   = true;     // 觸控共用匯流排，切換時自動處理 CS
        _panel.config(pcfg);

        // --- XPT2046 觸控 ---
        auto tcfg = _touch.config();
        tcfg.spi_host = SPI3_HOST;    // 同一組 SPI (VSPI)
        tcfg.freq     = 2500000;      // XPT2046 上限 2.5MHz
        tcfg.pin_sclk = 18;
        tcfg.pin_mosi = 23;
        tcfg.pin_miso = 19;
        tcfg.pin_cs   = 5;            // TOUCH_CS
        tcfg.pin_int  = 16;           // TOUCH_IRQ (PEN)，LovyanGFX 直接支援
        tcfg.x_min = 300;  tcfg.x_max = 3600;   // 校正值，可跑校正後修正
        tcfg.y_min = 300;  tcfg.y_max = 3600;
        _touch.config(tcfg);
        _panel.setTouch(&_touch);

        setPanel(&_panel);
    }
};

extern LGFX tft;   // 實體定義在 display_hw.cpp
