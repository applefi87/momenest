#include "i18n.h"
#include "lang.h"
#include <ArduinoJson.h>
#include <Preferences.h>

static DynamicJsonDocument langDoc(8192);   // lang.h 全部字串都會複製進來，留餘量
static char codes[8][8];      // 語言代碼清單 (依 lang.h JSON 順序)
static int  langCount = 0;
static int  langIdx = 0;
static Preferences uiPrefs;

void i18nInit() {
    uiPrefs.begin("ui", false);

    DeserializationError e = deserializeJson(langDoc, LANG_JSON);
    if (e) Serial.printf("lang.h JSON 解析失敗: %s\n", e.c_str());

    for (JsonPair kv : langDoc.as<JsonObject>()) {
        if (langCount < 8) strlcpy(codes[langCount++], kv.key().c_str(), sizeof(codes[0]));
    }
    if (langCount == 0) { strcpy(codes[0], "en"); langCount = 1; }   // JSON 壞掉的保底

    String saved = uiPrefs.getString("lang", codes[0]);
    for (int i = 0; i < langCount; i++)
        if (saved == codes[i]) langIdx = i;
}

const char* L(const char* key) {
    const char* s = langDoc[codes[langIdx]][key] | (const char*)nullptr;
    return s ? s : key;   // 缺 key 時直接顯示 key，方便發現漏翻
}

bool langAscii() {
    return langDoc[codes[langIdx]]["ascii"] | true;
}

void nextLang() {
    langIdx = (langIdx + 1) % langCount;
    uiPrefs.putString("lang", codes[langIdx]);
}

const lgfx::IFont* fontLabel() {
    return langAscii() ? (const lgfx::IFont*)&fonts::Font2
                       : (const lgfx::IFont*)&fonts::efontTW_16;
}

const lgfx::IFont* fontTitle() {
    return langAscii() ? (const lgfx::IFont*)&fonts::Font4
                       : (const lgfx::IFont*)&fonts::efontTW_16;
}
