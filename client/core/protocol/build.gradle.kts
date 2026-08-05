// :core:protocol — Kotlin Multiplatform (KMP) 模組。
//
// 為什麼要 KMP：BLE 協定解析、OTA 編排全是與平台無關的邏輯。放在 commonMain
// 之後，未來要做 iOS 版時這一層可以 100% 共用，「設備通訊怎麼講話」永遠只有
// 一份實作、一份測試，不會 Android 修了 bug 而 iOS 忘了修。
//
// 這也和韌體端把 reading_format / ota_protocol 抽成不依賴 Arduino API 的純 C、
// 好在 PC 上用 g++ 測試，是同一個設計思路（見 EnvMonitor/tests/README.md）。
//
// 硬性規則：commonMain **永遠不准**出現 java.* 或 android.*。
// 一旦出現，iOS target 就會編譯失敗——多平台會退化成「宣告了卻編不過」的假象。
// UUID 因此用自訂的 BleUuid（純字串），CRC32 也自己實作而不是用 java.util.zip。
//
// 註：Android 端是透過 jvm() target 消費這個模組（協定層沒有任何 Android 專屬
// 需求，所以不需要 androidTarget()）。日後若真的需要 Android 專屬實作，
// 再改成 androidTarget() 並套用 com.android.library 外掛即可。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    // Android / Desktop 走這個 target
    jvm()

    // iOS 三個 target：模擬器 (x64 / arm64) 與實機 (arm64)
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // api 而非 implementation：GattTransport 等公開介面的簽章用到 Flow，
            // 消費端必須看得到協程型別才編得過
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            // kotlin("test") 會依 target 自動選對應實作（JVM 上就是 JUnit）。
            // 刻意不用 JUnit4 / Truth——它們只有 JVM 版，會讓 commonTest 編不到 iOS。
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
