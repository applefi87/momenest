// ==========================================
// 水位感測器「最佳暖機時間」分析測試工具
// ==========================================
// 如果值都一樣，可以設為只需要0/1ms 暖機



const int PIN_WATER_PWR = 32;   // 供電腳位 (接感測器 VCC) 另外記得接地
const int PIN_WATER_LVL = 33;   // 類比讀取腳位 (接感測器 S)

// 定義我們要測試的一系列暖機時間 (單位：毫秒)
// 從完全不暖機 (0) 到暖機 100ms
const int testDelays[] = {10000};
const int numTests = sizeof(testDelays) / sizeof(testDelays[0]);

void setup() {
    Serial.begin(115200);
    delay(1000); // 等待 Serial Monitor 開啟
    
    Serial.println("\n\n--- 水位感測器暖機分析開始 ---");
    Serial.println("請確保感測器穩定泡在水中，測試期間請勿移動！");
    
    pinMode(PIN_WATER_PWR, OUTPUT);
    digitalWrite(PIN_WATER_PWR, LOW); // 預設斷電
    
    // 必須與主程式相同的 ADC 衰減設定，確保刻度一致
    analogSetPinAttenuation(PIN_WATER_LVL, ADC_11db);
    
    delay(3000); // 給水體 3 秒鐘的初始平靜時間
}

void loop() {
    Serial.println("\n>>> 開始新一輪測試循環 >>>");
    
    for (int i = 0; i < numTests; i++) {
        int currentWarmUp = testDelays[i];
        
        // 1. 給予感測器供電
        digitalWrite(PIN_WATER_PWR, HIGH);
        
        // 2. 暖機等待 (實驗變數)
        if (currentWarmUp > 0) {
            delay(currentWarmUp); 
        }
        
        // 3. 瞬間讀取數值 (取平均值過濾雜訊)
        long sum = 0;
        for(int j = 0; j < 3; j++) {
            sum += analogRead(PIN_WATER_LVL);
        }
        int avgValue = sum / 3;
        
        // 4. 讀完立刻斷電！
        digitalWrite(PIN_WATER_PWR, LOW);
        
        // 5. 印出日誌
        Serial.printf("暖機 %3d ms  =>  讀取數值: %d\n", currentWarmUp, avgValue);
        
        // 6. 【極度重要】讓水體休息 3 秒，消除上一輪的電解干擾
        delay(1000); 
    }
    
    Serial.println("==================================");
    Serial.println("循環結束，等待 5 秒後進行下一輪...");
    delay(1000);
}

