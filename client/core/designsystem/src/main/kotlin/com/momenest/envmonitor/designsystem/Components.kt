/**********************************************************************
 * Components.kt — 兩個 feature 共用的 Compose 元件
 *
 * 版面尺寸（圓角 16、內距 16、小圓點 8/6、進度條高 22）逐一對照網頁版
 * cloud/src/ble-app.html 的 CSS，讓 App 與網頁看起來是同一個產品。
 *
 * 每個元件都掛 testTag：UI 測試靠它定位，而不是靠會被翻譯／改字的顯示文字。
 **********************************************************************/
package com.momenest.envmonitor.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 進度條高度與圓角：網頁版 `.progress{height:22px;border-radius:11px}` */
private val ProgressBarHeight = 22.dp
private val ProgressBarRadius = 11.dp

/**
 * 進度條中央文字改用白色的門檻。
 *
 * 網頁版把百分比放在填色塊裡（低百分比時被裁掉看不到）；App 改成固定置中，
 * 所以要在「填色已經蓋過中心」時才轉白字，否則白字會落在淺灰軌道上看不見。
 */
private const val PROGRESS_LABEL_WHITE_THRESHOLD = 55

/**
 * 單一感測讀值卡：左上彩色小圓點 + 標籤，下方大字數值與次要色單位。
 *
 * @param label       顯示名稱（例如「氣溫」），同時決定 testTag `reading_card_<label>`
 * @param value       已格式化好的數值字串；無資料請傳 `"--"`（換算與格式化屬 feature 層職責）
 * @param unit        單位（`"°C"` / `"%"`）；原始 ADC 值無單位就傳空字串
 * @param accentColor 感測項目色，取自 [SensorAccents]
 */
@Composable
fun ReadingCard(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 數值與單位合成一段 AnnotatedString，而不是排兩個 Text：
    // 這樣單位會自動貼齊數值的基線，字級不同也不會上下浮動。
    val valueLine = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = onSurface,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.96).sp,
            )
        ) {
            append(value)
        }
        if (unit.isNotEmpty()) {
            withStyle(
                SpanStyle(
                    color = onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                )
            ) {
                append(" ")
                append(unit)
            }
        }
    }

    Surface(
        modifier = modifier.testTag("reading_card_$label"),
        shape = RoundedCornerShape(CardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = CardShadowElevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                // 網頁版此處有 text-transform:uppercase，但標籤是中文、無大小寫可轉，故略過
                Text(
                    text = label,
                    color = onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = valueLine,
                style = TabularFiguresStyle,
                maxLines = 1,
            )
        }
    }
}

/**
 * 連線狀態小圓點（標題列副標題用）。
 *
 * @param connected `true` 綠＝已連線、`false` 紅＝斷線、`null` 灰＝尚未嘗試／未知。
 *                  刻意用可空布林而非兩個布林參數，避免呼叫端組出「未知但已連線」的矛盾狀態。
 */
@Composable
fun StatusDot(connected: Boolean?, modifier: Modifier = Modifier) {
    val color = when (connected) {
        true -> SuccessGreen
        false -> ErrorRed
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .testTag("status_dot")
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * 帶小標題的區塊卡片（例如「韌體更新」區）。
 *
 * @param title   區塊標題，以次要色小字呈現；同時決定 testTag `section_card_<title>`
 * @param content 卡片內容，以 [ColumnScope] 為 receiver，呼叫端可直接用 `Modifier.align` 等
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.testTag("section_card_$title"),
        shape = RoundedCornerShape(CardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = CardShadowElevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * OTA 傳輸進度條。
 *
 * @param percent 0..100；超出範圍會被夾住，因為進度來源是設備回報的位元組數，
 *                韌體回報異常時不該讓畫面畫出超出邊界的色塊
 */
@Composable
fun OtaProgressBar(percent: Int, modifier: Modifier = Modifier) {
    val clamped = percent.coerceIn(0, 100)
    val labelColor = if (clamped >= PROGRESS_LABEL_WHITE_THRESHOLD) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .testTag("ota_progress")
            .fillMaxWidth()
            .height(ProgressBarHeight)
            .clip(RoundedCornerShape(ProgressBarRadius))
            .background(MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        // 0% 時整段不畫：fillMaxWidth(0f) 在部分版本會被視為無效比例，
        // 而且畫一條 0 寬的圓角色塊本來就沒有意義。
        if (clamped > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(clamped / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(ProgressBarRadius))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = "$clamped%",
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = TabularFiguresStyle,
        )
    }
}
