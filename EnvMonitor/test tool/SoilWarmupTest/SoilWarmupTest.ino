// ==========================================
// 手搓高階版土壤感測器「最佳暖機時間」分析測試工具
// ==========================================
// 基本上結論是，由於是電阻，時間越短電阻越高，反而給1MS 確保系統供電3.3v，然後最快的取值結束。]


// [ESP32 微控制器]
//                              |
// [GPIO 26 (脈衝供電)] --------+
//                              |
//                         (探針線 A)
//                         [黑色探針]  <-- 插在土裡
//                         (探針線 B)
//                              |
//                              +-------- [GPIO 35 (類比讀取)]
//                              |
//                        [23.8k 電阻]
//                              |
// [GND (接地)] ----------------+


#define PIN_SOIL_PWR 26   // 供電腳位 (接探針線A)
#define PIN_SOIL_LVL 35   // 類比讀取腳位 (接探針線B與分壓電阻)

// 定義我們要測試的一系列暖機時間 (單位：毫秒)
// 因為已經拔掉藍色板子，我們預期延遲會極低，所以集中測試短時間
const int testDelays[] = {10000};
const int numTests = sizeof(testDelays) / sizeof(testDelays[0]);

void setup() {
    Serial.begin(115200);
    delay(1000); // 等待 Serial Monitor 開啟
    
    Serial.println("\n\n--- 手搓土壤感測器 (純電阻) 暖機分析開始 ---");
    Serial.println("👉 實驗前請確認：已移除藍色板子，並接好 23.8k 分壓電阻。");
    Serial.println("👉 請將探針插在微濕的土壤中，測試期間請勿移動！");
    
    // 初始化供電腳位，預設斷電防鏽
    pinMode(PIN_SOIL_PWR, OUTPUT);
    digitalWrite(PIN_SOIL_PWR, LOW); 
    

    
    delay(3000); // 給土壤離子 3 秒鐘的初始平靜時間
}

void loop() {
    Serial.println("\n>>> 開始新一輪測試循環 >>>");
    
    for (int i = 0; i < numTests; i++) {
        int currentWarmUp = testDelays[i];
        
        // 1. 給予感測器瞬間供電
        digitalWrite(PIN_SOIL_PWR, HIGH);
        
        // 2. 暖機等待 (實驗變數)
        if (currentWarmUp > 0) {
            delay(currentWarmUp); 
        }
        
        // 3. 瞬間讀取數值 (取 3 次平均值過濾 ADC 雜訊)
        long sum = 0;
        // 設定 ADC 衰減為 11dB，擴展量測範圍至 0~3.1V
        analogSetPinAttenuation(PIN_SOIL_LVL, ADC_6db);
        for(int j = 0; j < 3; j++) {
            sum += analogRead(PIN_SOIL_LVL);
        }
        int avgValue = sum / 3;
        
        // 4. 讀完立刻斷電，防止電解反應！
        digitalWrite(PIN_SOIL_PWR, LOW);
        
        // 5. 印出日誌
        Serial.printf("暖機 %3d ms  =>  讀取數值: %d\n", currentWarmUp, avgValue);
        
        // 6. 【極度重要】讓土壤休息 3 秒，消除上一輪的微小電解干擾
        delay(1000); 
    }
    
    Serial.println("==================================");
    Serial.println("循環結束，等待 5 秒後進行下一輪...");
    delay(1000);
}