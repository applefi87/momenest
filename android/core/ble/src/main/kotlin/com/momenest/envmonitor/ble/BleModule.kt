package com.momenest.envmonitor.ble

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BleModule {

    @Provides
    @Singleton
    fun provideGattOperationQueue(): GattOperationQueue = GattOperationQueue()

    @Provides
    @Singleton
    fun provideEnvMonitorClient(
        @ApplicationContext context: Context,
        queue: GattOperationQueue
    ): EnvMonitorClient = AndroidEnvMonitorClient(context, queue)
}
