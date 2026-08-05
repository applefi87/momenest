/**********************************************************************
 * Previews.kt — Components.kt 各元件的 Android Studio 預覽
 *
 * 每個元件都給淺色與深色兩份：這個 App 的配色是手寫的兩套色票（不是 Material
 * You 動態取色），深色若沒人看就很容易改壞（例如次要灰在黑底上對比不足）。
 * 預覽全部包一層 background 色的 Surface，才能看到卡片與頁面底色的層次。
 **********************************************************************/
package com.momenest.envmonitor.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 預覽共用外框：套主題 + 頁面底色 + 邊距，避免每個預覽重複同一段樣板 */
@Composable
private fun PreviewCanvas(
    darkTheme: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    EnvMonitorTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

// ---- ReadingCard ----

@Preview(name = "ReadingCard · 淺色", showBackground = true)
@Composable
private fun ReadingCardLightPreview() {
    PreviewCanvas(darkTheme = false) {
        ReadingCard(
            label = "氣溫",
            value = "24.6",
            unit = "°C",
            accentColor = SensorAirTemp,
            modifier = Modifier.fillMaxWidth(),
        )
        // 無資料狀態也要看：破折號不該讓卡片高度跳動
        ReadingCard(
            label = "土壤",
            value = "--",
            unit = "",
            accentColor = SensorSoil,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(
    name = "ReadingCard · 深色",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ReadingCardDarkPreview() {
    PreviewCanvas(darkTheme = true) {
        ReadingCard(
            label = "水溫",
            value = "31.0",
            unit = "°C",
            accentColor = SensorWaterTemp,
            modifier = Modifier.fillMaxWidth(),
        )
        ReadingCard(
            label = "水位",
            value = "1500",
            unit = "",
            accentColor = SensorWaterLevel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- StatusDot ----

@Preview(name = "StatusDot · 淺色", showBackground = true)
@Composable
private fun StatusDotLightPreview() {
    PreviewCanvas(darkTheme = false) {
        StatusDot(connected = true)
        StatusDot(connected = false)
        StatusDot(connected = null)
    }
}

@Preview(
    name = "StatusDot · 深色",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun StatusDotDarkPreview() {
    PreviewCanvas(darkTheme = true) {
        StatusDot(connected = true)
        StatusDot(connected = false)
        StatusDot(connected = null)
    }
}

// ---- SectionCard ----

@Preview(name = "SectionCard · 淺色", showBackground = true)
@Composable
private fun SectionCardLightPreview() {
    PreviewCanvas(darkTheme = false) {
        SectionCard(title = "韌體更新 (BLE OTA)", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "firmware.bin · 1.2 MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "SectionCard · 深色",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SectionCardDarkPreview() {
    PreviewCanvas(darkTheme = true) {
        SectionCard(title = "韌體更新 (BLE OTA)", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "尚未選擇檔案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- OtaProgressBar ----

@Preview(name = "OtaProgressBar · 淺色", showBackground = true)
@Composable
private fun OtaProgressBarLightPreview() {
    PreviewCanvas(darkTheme = false) {
        // 0 / 低 / 過門檻 / 滿：四種都要看，白字轉色的門檻最容易改壞
        OtaProgressBar(percent = 0)
        OtaProgressBar(percent = 30)
        OtaProgressBar(percent = 72)
        OtaProgressBar(percent = 100)
    }
}

@Preview(
    name = "OtaProgressBar · 深色",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OtaProgressBarDarkPreview() {
    PreviewCanvas(darkTheme = true) {
        OtaProgressBar(percent = 0)
        OtaProgressBar(percent = 30)
        OtaProgressBar(percent = 72)
        OtaProgressBar(percent = 100)
    }
}
