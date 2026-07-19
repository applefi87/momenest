#include "theme.h"
#include <Preferences.h>

static Preferences themePrefs;
const Theme* currentTheme;

// ==========================================
// 深色主題 (原有設定)
// ==========================================
const Theme ThemeDark = {
    .bg      = lgfx::color565(16, 20, 26),     // 全域背景
    .card    = lgfx::color565(28, 34, 43),     // 卡片底
    .edge    = lgfx::color565(42, 51, 64),     // 卡片邊框/分隔線
    .text    = lgfx::color565(232, 236, 241),  // 主要文字
    .muted   = lgfx::color565(138, 148, 166),  // 次要文字
    .accent  = lgfx::color565(79, 195, 247),   // 標題
    
    .temp    = lgfx::color565(255, 201, 77),   // 空氣溫度
    .hum     = lgfx::color565(100, 181, 246),  // 空氣濕度
    .wtemp   = lgfx::color565(129, 199, 132),  // 水溫
    .soil    = lgfx::color565(255, 162, 77),   // 土壤濕度
    .water   = lgfx::color565(111, 141, 255),  // 水位
    
    .ok      = lgfx::color565(76, 175, 80),    // 正常/成功
    .err     = lgfx::color565(239, 83, 80),    // 錯誤/失敗
    .bar_bg  = lgfx::color565(42, 51, 64)      // 橫條軌道 (沿用 edge)
};

// ==========================================
// 蘋果白色系主題 (Apple Light)
// ==========================================
const Theme ThemeLight = {
    .bg      = lgfx::color565(242, 242, 247),  // iOS 群組背景 (非常淺的灰白)
    .card    = lgfx::color565(255, 255, 255),  // 純白卡片，產生層次感
    .edge    = lgfx::color565(229, 229, 234),  // 淡灰邊框
    .text    = lgfx::color565(28, 28, 30),     // 深灰黑 (非純黑)，護眼
    .muted   = lgfx::color565(142, 142, 147),  // 中度灰次要文字
    .accent  = lgfx::color565(0, 122, 255),    // 經典 Apple Blue
    
    .temp    = lgfx::color565(255, 149, 0),    // 橘 (Apple 亮色版)
    .hum     = lgfx::color565(90, 200, 250),   // 淺藍
    .wtemp   = lgfx::color565(52, 199, 89),    // 綠
    .soil    = lgfx::color565(210, 122, 56),   // 泥土褐橘 (加深增加白底對比)
    .water   = lgfx::color565(175, 82, 222),   // 紫
    
    .ok      = lgfx::color565(52, 199, 89),    // 綠
    .err     = lgfx::color565(255, 59, 48),    // 紅
    .bar_bg  = lgfx::color565(229, 229, 234)   // 橫條軌道 (沿用 edge)
};

// ==========================================
// 初始化與切換邏輯
// ==========================================
void themeInit() {
    themePrefs.begin("ui", false);
    // 預設 true (Light Theme)
    bool isLight = themePrefs.getBool("isLight", true);
    themePrefs.end();
    currentTheme = isLight ? &ThemeLight : &ThemeDark;
}

void themeToggle() {
    bool isLight = (currentTheme == &ThemeLight);
    isLight = !isLight;
    themePrefs.begin("ui", false);
    themePrefs.putBool("isLight", isLight);
    themePrefs.end();
    currentTheme = isLight ? &ThemeLight : &ThemeDark;
}
