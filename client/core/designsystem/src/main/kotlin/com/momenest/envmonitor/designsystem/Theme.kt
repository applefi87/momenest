/**********************************************************************
 * Theme.kt — Material3 主題包裝（配色 / 字級 / 圓角）
 *
 * 刻意不用 Material You 動態取色：這台設備的視覺語言是 iOS 風格，讓系統桌布
 * 決定強調色會讓 App 與網頁版長得不一樣，違背「兩端同一個產品」的目標。
 *
 * Material3 沒有「成功」色槽，故 SuccessGreen 直接用 Color.kt 的常數，不塞進
 * ColorScheme（塞進 tertiary 之類的槽只會讓語意更難讀）。
 **********************************************************************/
package com.momenest.envmonitor.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 卡片圓角，對應網頁版 `--radius:16px` */
val CardCornerRadius = 16.dp

/** 按鈕圓角，對應網頁版 `.btn { border-radius:14px }` */
val ButtonCornerRadius = 14.dp

/**
 * 卡片陰影。網頁版是 `0 2px 16px rgba(0,0,0,.04)` 的極淡陰影；Compose 的
 * shadowElevation 觀感較重，故只給 2dp，深色底上本來就幾乎看不見。
 */
val CardShadowElevation = 2.dp

/**
 * 數字專用字型設定：`tnum` 開啟等寬數字（tabular figures）。
 *
 * 讀值每秒刷新，若用比例數字，1 和 8 寬度不同會讓整張卡片左右抖動，
 * 這是網頁版 `font-variant-numeric: tabular-nums` 想解決的同一個問題。
 */
val TabularFiguresStyle = TextStyle(fontFeatureSettings = "tnum")

private val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    secondary = LightAccent,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    // surfaceVariant 當「卡片內的次級區塊」底色，用頁面灰才不會與卡片白撞色
    surfaceVariant = LightBackground,
    onSurfaceVariant = LightTextSecondary,
    outline = LightTextSecondary,
    outlineVariant = LightCardBorder,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color.White,
    secondary = DarkAccent,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkTextSecondary,
    outlineVariant = DarkCardBorder,
    error = ErrorRed,
    onError = Color.White,
)

/**
 * 字級表對照網頁版 CSS：
 *   h1 2rem/700/-0.02em、.card .value 2rem/500/-0.03em、
 *   .card .label 0.72rem/600、.ota-status 0.82rem。
 * 1rem 視為 16sp，負字距換算成 sp 後直接寫死（Compose 沒有 em 單位）。
 */
private val EnvMonitorTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.64).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.24.sp,
    ),
)

private val EnvMonitorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(CardCornerRadius),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * App 的主題入口，所有畫面都要包在裡面。
 *
 * @param darkTheme 預設跟隨系統；`@Preview` 與測試可強制指定，方便一次看兩種配色
 * @param content   受主題影響的畫面內容
 */
@Composable
fun EnvMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EnvMonitorTypography,
        shapes = EnvMonitorShapes,
        content = content,
    )
}
