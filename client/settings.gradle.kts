// 多模組組成。新增模組時記得在最下面 include，否則 Gradle 看不到它。
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 禁止各模組自己宣告 repository，來源集中在這裡，避免相依來路不明
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "momenest-android"

include(":app")

// core：可被任何 feature 重用的基礎能力
include(":core:protocol")      // 純 Kotlin，無 Android 相依（協定/解析/OTA 編排）
include(":core:ble")           // Android BLE 實作
include(":core:data")          // 偏好設定持久化
include(":core:designsystem")  // Compose 主題與共用元件
include(":core:testing")       // 共用測試替身

// feature：一個畫面一個模組
include(":feature:monitor")
include(":feature:ota")
