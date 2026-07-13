#include "sensors.h"
#include "config.h"
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>

float airTemp = NAN, airHum = NAN, waterTemp = NAN;
int   soilRaw = 0, waterRaw = 0;
bool  soilValid = false, waterValid = false;

static OneWire oneWire(PIN_ONEWIRE);
static DallasTemperature ds18b20(&oneWire);

static unsigned long lastCycleStartTime = 0;
static bool isMeasuring = false;
static bool waterPwrOn = false, soilPwrOn = false;

// ==========================================
// SHT45 觸發 / CRC / 讀取 (觸發與讀取分離，非阻塞)
// ==========================================
static void sht45Trigger() {
    Wire.beginTransmission(SHT45_ADDR);
    Wire.write(SHT45_CMD_HIGH);
    Wire.endTransmission();
}

static uint8_t calcCRC8(uint8_t *ptr, uint8_t len) {  // 多項式 0x31、初始 0xFF
    uint8_t crc = 0xFF;
    for (uint8_t i = 0; i < len; i++) {
        crc ^= ptr[i];
        for (uint8_t j = 0; j < 8; j++)
            crc = (crc & 0x80) ? (crc << 1) ^ 0x31 : (crc << 1);
    }
    return crc;
}

static bool sht45Read(float &temp, float &hum) {
    Wire.requestFrom((uint8_t)SHT45_ADDR, (uint8_t)6);
    if (Wire.available() >= 6) {
        uint8_t d[6];
        for (int i = 0; i < 6; i++) d[i] = Wire.read();
        if (calcCRC8(d, 2) == d[2] && calcCRC8(d + 3, 2) == d[5]) {
            temp = -45.0f + 175.0f * (((uint16_t)d[0] << 8) | d[1]) / 65535.0f;
            hum  = constrain(-6.0f + 125.0f * (((uint16_t)d[3] << 8) | d[4]) / 65535.0f, 0.0f, 100.0f);
            return true;
        }
    }
    while (Wire.available()) Wire.read();
    return false;
}

// ==========================================
// 類比讀取：取 5 次中位數，抑制偶發跳值 (土壤曾間歇讀到 0)
// ==========================================
static int readAnalogMedian5(int pin) {
    int v[5];
    for (int i = 0; i < 5; i++) v[i] = analogRead(pin);
    for (int i = 1; i < 5; i++) {              // 插入排序
        int k = v[i], j = i - 1;
        while (j >= 0 && v[j] > k) { v[j + 1] = v[j]; j--; }
        v[j + 1] = k;
    }
    return v[2];
}

// ==========================================
// 初始化
// ==========================================
void sensorsInit() {
    Wire.begin(PIN_SDA, PIN_SCL);
    Wire.setTimeOut(3);                    // I2C 逾時，防卡死

    pinMode(PIN_WATER_PWR, OUTPUT);
    digitalWrite(PIN_WATER_PWR, LOW);
    pinMode(PIN_SOIL_PWR, OUTPUT);
    digitalWrite(PIN_SOIL_PWR, LOW);

    ds18b20.begin();
    ds18b20.setResolution(12);
    ds18b20.setWaitForConversion(false);   // 非阻塞轉換

    // ADC 量程只需設定一次 (修正：原本誤放在 loop 每圈重設)
    analogSetPinAttenuation(PIN_SOIL,      ADC_6db);     // 針對感測器全通/否的優化
    analogSetPinAttenuation(PIN_WATER_LVL, ADC_2_5db);

    lastCycleStartTime = millis();
}

// ==========================================
// 非阻塞量測時序；回傳 true = 剛完成一輪讀取
// ==========================================
bool sensorsLoop(unsigned long now) {
    // ---- T=0：觸發量測 (立即返回) ----
    if (!isMeasuring && (now - lastCycleStartTime >= CYCLE_PERIOD)) {
        lastCycleStartTime += CYCLE_PERIOD;
        isMeasuring = true;
        sht45Trigger();                    // ~8.3ms
        ds18b20.requestTemperatures();     // ~750ms，非阻塞
    }

    // ---- 讀取前 Xms：探針上電暖機 ----
    if (isMeasuring && !waterPwrOn &&
        (now - lastCycleStartTime >= MEASURE_DELAY - WATER_PWR_LEAD)) {
        digitalWrite(PIN_WATER_PWR, HIGH);
        waterPwrOn = true;
    }
    if (isMeasuring && !soilPwrOn &&
        (now - lastCycleStartTime >= MEASURE_DELAY - SOIL_PWR_LEAD)) {
        digitalWrite(PIN_SOIL_PWR, HIGH);
        soilPwrOn = true;
    }

    // ---- T=800ms：統一讀取 ----
    if (isMeasuring && (now - lastCycleStartTime >= MEASURE_DELAY)) {
        isMeasuring = false;

        if (!sht45Read(airTemp, airHum)) { airTemp = NAN; airHum = NAN; }

        float wt = ds18b20.getTempCByIndex(0);
        waterTemp = (wt <= DEVICE_DISCONNECTED_C + 1) ? NAN : wt;

        soilRaw  = readAnalogMedian5(PIN_SOIL);
        waterRaw = readAnalogMedian5(PIN_WATER_LVL);
        soilValid  = soilRaw  > 0;         // 0 = 未接/接觸不良，顯示 -- 並上傳 null
        waterValid = waterRaw > 0;

        digitalWrite(PIN_WATER_PWR, LOW);
        waterPwrOn = false;
        digitalWrite(PIN_SOIL_PWR, LOW);
        soilPwrOn = false;
        return true;
    }
    return false;
}
