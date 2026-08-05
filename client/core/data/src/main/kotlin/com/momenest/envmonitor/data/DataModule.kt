// DataModule.kt — :core:data 的 Hilt 綁定。
//
// 為什麼用 @Binds 而不是 @Provides：實作類別已經有 @Inject constructor，
// @Binds 只是告訴 Dagger「有人要 CalibrationRepository 時就給 DataStoreCalibrationRepository」，
// 編譯期直接改接線，不會像 @Provides 那樣多生一個 factory。

package com.momenest.envmonitor.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 [CalibrationRepository] 綁到 DataStore 實作。
 *
 * 裝在 [SingletonComponent]：校準值是全 App 共用的設定，
 * 沒有理由跟著 Activity / ViewModel 的生命週期重建。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // 標 @Singleton 的理由：底層 DataStore 本來就是程序內單例，
    // 綁定也跟著單例，可省掉每次注入都重建一次 Flow 轉換鏈。
    @Binds
    @Singleton
    abstract fun bindCalibrationRepository(
        impl: DataStoreCalibrationRepository,
    ): CalibrationRepository
}
