// BluetoothPermissionGate.kt — BLE 執行期權限的閘門。
//
// 為什麼要獨立成一層：Android 12 前後的藍牙權限模型完全不同，
//   * API 31 (Android 12) 起：BLUETOOTH_SCAN + BLUETOOTH_CONNECT，屬「附近的裝置」，
//     且 scan 宣告了 neverForLocation 就不必再要定位權限。
//   * API 30 以下：BLUETOOTH / BLUETOOTH_ADMIN 是安裝時授予不需請求，
//     但系統認為 BLE 掃描可以反推使用者位置，所以「掃描」必須有 ACCESS_FINE_LOCATION。
// 若把版本判斷散在各畫面，很容易出現「新機器可以、舊機器掃不到」這種只在特定
// Android 版本才重現的問題。這裡只保留 UI，版本差異全部交給 :core:ble 的
// BlePermissions（純函式、有單元測試涵蓋各 sdkInt）。

package com.momenest.envmonitor.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.momenest.envmonitor.R
import com.momenest.envmonitor.ble.BlePermissions

private const val TAG = "PermissionGate"

/**
 * 權限閘門：所有需要的 BLE 執行期權限都拿到才顯示 [content]，
 * 否則顯示說明畫面與「授予權限」／「前往系統設定」兩個動作。
 *
 * 之所以要有「前往系統設定」：使用者一旦在系統對話框選了「不要再詢問」，
 * 之後呼叫 launcher 會直接被系統靜默拒絕、連對話框都不會跳，
 * 此時唯一的出路就是自己去設定頁開啟。
 *
 * @param content 權限齊全時要顯示的內容
 */
@Composable
fun BluetoothPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current

    // 需要哪些權限只跟 SDK 版本有關，重組時不需要重算
    val required = remember { BlePermissions.runtimePermissions(Build.VERSION.SDK_INT) }

    var granted by remember { mutableStateOf(hasAllPermissions(context, required)) }
    // 用 rememberSaveable：轉螢幕重建 Activity 後不要又自動彈一次對話框
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // 不看回傳的 map，改為重讀系統實際狀態：使用者可能是在系統設定頁
        // 而非對話框裡完成授權，以系統為準才不會誤判
        granted = hasAllPermissions(context, required)
    }

    // 從系統設定頁返回時要重新確認一次，否則畫面會卡在說明頁不更新
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, required) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                granted = hasAllPermissions(context, required)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 首次進入自動請求一次，省掉一次多餘的點擊；之後只由按鈕觸發，
    // 避免被「不再詢問」靜默拒絕時陷入無限請求迴圈
    LaunchedEffect(granted, askedOnce) {
        if (!granted && !askedOnce && required.isNotEmpty()) {
            askedOnce = true
            launcher.launch(required.toTypedArray())
        }
    }

    if (granted) {
        content()
    } else {
        PermissionRationale(
            onGrantClick = {
                askedOnce = true
                launcher.launch(required.toTypedArray())
            },
            onSettingsClick = { openAppSettings(context) },
        )
    }
}

/** 權限不足時的說明畫面（純顯示，狀態由 [BluetoothPermissionGate] 持有）。 */
@Composable
private fun PermissionRationale(
    onGrantClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("permission_gate"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(rationaleStringRes()),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_denied_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permission_grant"),
            ) {
                Text(stringResource(R.string.permission_grant))
            }
            TextButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permission_settings"),
            ) {
                Text(stringResource(R.string.permission_open_settings))
            }
        }
    }
}

/**
 * 說明文字要依 Android 版本換一套講法：Android 12 以上使用者看到的是
 * 「附近的裝置」，12 以下看到的卻是「位置」，講錯會讓人在設定頁找不到開關。
 */
private fun rationaleStringRes(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        R.string.permission_rationale_nearby
    } else {
        R.string.permission_rationale_location
    }

/** 逐一比對系統目前的授權狀態；空清單（理論上不會發生）視為已授權。 */
private fun hasAllPermissions(context: Context, permissions: List<String>): Boolean =
    permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

/** 開啟本 App 的系統設定頁，讓使用者手動補上被「不再詢問」擋掉的權限。 */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
        // LocalContext 不保證是 Activity（可能被 Compose 包了一層），
        // 缺這個 flag 在非 Activity context 下會直接擲例外
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // 少數改機 ROM 沒有這個設定頁；開不起來只記 log，不要讓 App 崩潰
    runCatching { context.startActivity(intent) }
        .onFailure { Log.w(TAG, "無法開啟系統設定頁", it) }
}
