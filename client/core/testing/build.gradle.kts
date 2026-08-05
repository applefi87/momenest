// :core:testing — 共用測試替身（fakes）與 JUnit rule。
//
// 注意程式碼放在 src/main 而不是 src/test：這樣其他模組才能用
// testImplementation(project(":core:testing")) 取用這些 fake。
// 這是 Android 官方 Now in Android 範例採用的作法。
//
// 用手寫 fake 而非 mock 框架（MockK/Mockito）的理由：fake 是真的能跑的實作，
// 行為看得見、除錯容易，而且不會因為 mock 設定寫錯而測到假的東西。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.momenest.envmonitor.testing"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // api：使用這些 fake 的模組要看得到它們實作的介面
    api(project(":core:protocol"))
    api(project(":core:ble"))
    api(project(":core:data"))

    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.truth)
    api(libs.turbine)
}
