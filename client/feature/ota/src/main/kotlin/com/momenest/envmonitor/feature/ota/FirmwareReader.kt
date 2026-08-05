// FirmwareReader.kt — 從系統選檔器拿到的位置讀出韌體位元組。
//
// 介面化的理由有兩個：
//   1. ViewModel 因此不必碰 ContentResolver，可以在純 JVM 單元測試裡跑
//   2. 參數刻意用 String 而不是 android.net.Uri——Uri 是 Android 型別，
//      在 JVM 測試裡是空殼（Uri.parse 會回 null），讓 ViewModel 的介面
//      保持平台中立，測試就不需要任何 Android 模擬。
package com.momenest.envmonitor.feature.ota

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirmwareReader"

/**
 * 韌體檔大小上限。
 *
 * ESP32 的 OTA 分區通常在 1.9 MB 左右，8 MB 已經是非常寬鬆的上限；
 * 設這道門檻主要是防止使用者誤選一個幾百 MB 的檔案而讓 App 直接 OOM。
 */
internal const val MAX_FIRMWARE_BYTES = 8L * 1024 * 1024

fun interface FirmwareReader {
    /**
     * @param uriString 系統選檔器回傳的位置（`Uri.toString()`）
     * @return 讀取失敗、檔案為空、或超過 [MAX_FIRMWARE_BYTES] 時回 null
     */
    suspend fun read(uriString: String): SelectedFirmware?
}

@Singleton
class AndroidFirmwareReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : FirmwareReader {

    override suspend fun read(uriString: String): SelectedFirmware? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val resolver = context.contentResolver

            // 先問大小再決定要不要讀，避免對超大檔先讀進記憶體才發現不該讀
            var displayName = "firmware.bin"
            var declaredSize = -1L
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        declaredSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (declaredSize > MAX_FIRMWARE_BYTES) {
                Log.w(TAG, "檔案過大 declaredSize=$declaredSize")
                return@runCatching null
            }

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null

            // provider 不一定有回報大小，所以讀完還要再檢查一次
            if (bytes.isEmpty() || bytes.size > MAX_FIRMWARE_BYTES) {
                Log.w(TAG, "檔案大小不合法 size=${bytes.size}")
                return@runCatching null
            }

            SelectedFirmware(
                fileName = displayName,
                sizeBytes = bytes.size.toLong(),
                bytes = bytes,
            )
        }.onFailure { Log.e(TAG, "讀取韌體失敗", it) }.getOrNull()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FirmwareReaderModule {

    @Binds
    @Singleton
    abstract fun bindFirmwareReader(impl: AndroidFirmwareReader): FirmwareReader
}
