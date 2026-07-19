// ==========================================
// 手搓高階版土壤感測器 (動態校準版) 測試程式
// ==========================================

// 腳位定義
#define PIN_SOIL_PWR 26   // 探針供電腳位
#define PIN_SOIL_LVL 32   // 類比訊號讀取腳位

// 將 const 改為一般變數，並賦予預設值
int dryValue = 1300; // 土壤偏乾時的數值 (對應 0%)
int wetValue = 2800; // 剛澆完飽和水的數值 (對應 100%)

void setup() {
    Serial.begin(115200);
    delay(1000); 
    
    Serial.println("\n==================================");
    Serial.println("--- 手搓土壤感測器 動態校準工具 ---");
    Serial.println("目前預設邊界 -> DRY: 1300, WET: 2800");
    Serial.println("👉 想要更改邊界，請在上方輸入框輸入： [DRY數值],[WET數值]");
    Serial.println("👉 例如輸入： 1000,4000 (然後按 Enter)");
    Serial.println("==================================\n");
    
    pinMode(PIN_SOIL_PWR, OUTPUT);
    digitalWrite(PIN_SOIL_PWR, LOW);
    
    analogSetPinAttenuation(PIN_SOIL_LVL, ADC_11db);
}

void loop() {
    // ==========================================
    // 1. 檢查是否有來自 Serial Monitor 的新輸入
    // ==========================================
    if (Serial.available() > 0) {
        // 讀取整行字串直到遇到換行符號
        String input = Serial.readStringUntil('\n');
        input.trim(); // 清除字串前後的多餘空白或隱藏換行符
        
        // 尋找逗號的位置
        int commaIndex = input.indexOf(',');
        
        // 如果有找到逗號，就進行字串切割與更新
        if (commaIndex > 0) {
            // 切割出前後兩段，並轉成整數 (toInt)
            int newDry = input.substring(0, commaIndex).toInt();
            int newWet = input.substring(commaIndex + 1).toInt();
            
            // 簡易防呆：確保轉換出來的數字不是 0 (除非你真的輸入0)
            dryValue = newDry;
            wetValue = newWet;
            
            Serial.println("\n✅ ==================================");
            Serial.printf("✅ 更新成功！新邊界值 -> DRY: %d, WET: %d\n", dryValue, wetValue);
            Serial.println("✅ ==================================\n");
        } else if (input.length() > 0) {
            Serial.println("❌ 格式錯誤！請記得加上逗號，例如: 1000,4000");
        }
    }

    // ==========================================
    // 2. 瞬間供電與讀取感測器
    // ==========================================
    digitalWrite(PIN_SOIL_PWR, HIGH);       
    delay(1);                               
    int soilRaw = analogRead(PIN_SOIL_LVL); 
    digitalWrite(PIN_SOIL_PWR, LOW);  
  
    
    // ==========================================
    // 3. 數值轉換與印出結果
    // ==========================================
    int soilPercent = map(constrain(soilRaw, dryValue, wetValue), dryValue, wetValue, 0, 100);
    
    Serial.printf("原始值 (Raw): %4d  |  換算濕度: %3d%%  (範圍: %d ~ %d)\n", 
                  soilRaw, soilPercent, dryValue, wetValue);
    
    delay(1000); // 休息 1 秒鐘後進行下一次測量
}