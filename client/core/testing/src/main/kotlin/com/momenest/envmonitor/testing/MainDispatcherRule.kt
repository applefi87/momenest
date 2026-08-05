/**
 * MainDispatcherRule.kt — 把 Dispatchers.Main 換成測試用 dispatcher 的 JUnit rule。
 *
 * 為什麼需要：ViewModel 內部的 viewModelScope 綁在 Dispatchers.Main 上，
 * 而 Main 在純 JVM 單元測試環境沒有 Android Looper 可用，直接跑會擲
 * IllegalStateException（"Module with the Main dispatcher had failed to initialize"）。
 * 用 rule 統一在每個測試前後 setMain / resetMain，比每個測試自己記得做可靠。
 *
 * 注意本檔位於 src/main 而非 src/test：:core:testing 整個模組就是「給別人用的
 * 測試工具」，放 main 才能被其他模組以 testImplementation(project(":core:testing"))
 * 取用（src/test 不會產出可被依賴的 artifact）。
 */
package com.momenest.envmonitor.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 測試期間把 [Dispatchers.Main] 換成 [dispatcher]。
 *
 * 用法：
 * ```
 * @get:Rule val mainDispatcherRule = MainDispatcherRule()
 * ```
 *
 * 預設用 [UnconfinedTestDispatcher]：ViewModel 測試多半只想「launch 後立刻看到結果」，
 * 不想為了推進協程而到處插 advanceUntilIdle()。若某個測試要精確控制時間軸，
 * 傳入 StandardTestDispatcher（例如 `MainDispatcherRule(StandardTestDispatcher())`）即可。
 *
 * @param dispatcher 測試期間要掛在 Main 上的 dispatcher
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        // 一定要還原：Main 是行程層級的全域狀態，不還原會污染同一個 JVM 內的後續測試
        Dispatchers.resetMain()
    }
}
