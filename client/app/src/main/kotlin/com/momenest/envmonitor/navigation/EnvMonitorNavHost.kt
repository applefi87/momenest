// EnvMonitorNavHost.kt — 全 App 的導覽圖。
//
// 目的地只有兩個（讀值、韌體更新），所以用最單純的字串路由即可，
// 不引入額外的型別安全導覽方案，避免為了兩條路線扛一整套樣板。
// feature 模組彼此不互相認識，「誰能去誰」只由這一個檔案決定，
// 之後要加畫面或改起點都只動這裡。

package com.momenest.envmonitor.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.momenest.envmonitor.feature.monitor.MonitorRoute
import com.momenest.envmonitor.feature.ota.OtaRoute

/** 導覽路由字串。集中成常數，避免兩處手寫字串不一致造成執行期才發現的崩潰。 */
object EnvMonitorDestinations {
    /** 即時讀值畫面（起點） */
    const val MONITOR = "monitor"

    /** 韌體更新畫面 */
    const val OTA = "ota"
}

/**
 * App 的導覽圖。
 *
 * @param modifier 外層版面修飾子
 * @param navController 導覽控制器；預設自行 remember，測試時可傳入自備的實例
 */
@Composable
fun EnvMonitorNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = EnvMonitorDestinations.MONITOR,
        modifier = modifier,
    ) {
        composable(EnvMonitorDestinations.MONITOR) {
            MonitorRoute(
                onNavigateToOta = {
                    // launchSingleTop：連點兩下按鈕不會疊出兩層一模一樣的畫面
                    navController.navigate(EnvMonitorDestinations.OTA) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(EnvMonitorDestinations.OTA) {
            // popBackStack() 回傳 Boolean，這裡的 lambda 型別是 () -> Unit，
            // Kotlin 會自動忽略回傳值
            OtaRoute(onBack = { navController.popBackStack() })
        }
    }
}
