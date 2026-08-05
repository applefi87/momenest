package com.momenest.envmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.momenest.envmonitor.protocol.DeviceInfo
import com.momenest.envmonitor.protocol.DeviceStatus
import com.momenest.envmonitor.protocol.GattContract
import com.momenest.envmonitor.protocol.OtaEvent
import com.momenest.envmonitor.protocol.SensorReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "EnvMonitorClient"

@SuppressLint("MissingPermission")
class AndroidEnvMonitorClient(
    private val context: Context,
    private val queue: GattOperationQueue
) : EnvMonitorClient {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        context.getSystemService(BluetoothManager::class.java).adapter
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var bluetoothGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableStateFlow<SensorReading?>(null)
    override val readings: StateFlow<SensorReading?> = _readings.asStateFlow()

    private val _status = MutableStateFlow<DeviceStatus?>(null)
    override val status: StateFlow<DeviceStatus?> = _status.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    override val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _otaSupported = MutableStateFlow(false)
    override val otaSupported: StateFlow<Boolean> = _otaSupported.asStateFlow()

    override suspend fun connect() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionState.value = ConnectionState.Failed("藍牙未開啟")
            return
        }

        _connectionState.value = ConnectionState.Scanning
        
        val device = scanForDevice()
        if (device == null) {
            _connectionState.value = ConnectionState.Failed("找不到設備，請確認電源已開啟")
            return
        }

        _connectionState.value = ConnectionState.Connecting
        
        connectToDevice(device)
    }

    private suspend fun scanForDevice(): BluetoothDevice? = suspendCancellableCoroutine { continuation ->
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.d(TAG, "掃描到設備: ${result.device.name} (${result.device.address})")
                // 優先比對 SERVICE_UUID，次之比對名稱
                val name = result.device.name ?: ""
                if (name.contains(GattContract.DEVICE_NAME, ignoreCase = true)) {
                    scanner.stopScan(this)
                    if (continuation.isActive) continuation.resume(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "掃描失敗: $errorCode")
                if (continuation.isActive) continuation.resume(null)
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GattContract.SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, callback)

        continuation.invokeOnCancellation {
            scanner.stopScan(callback)
        }

        // 10 秒逾時
        scope.launch {
            delay(10000)
            if (continuation.isActive) {
                scanner.stopScan(callback)
                continuation.resume(null)
            }
        }
    }

    private suspend fun connectToDevice(device: BluetoothDevice) {
        suspendCancellableCoroutine { continuation ->
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "連線狀態錯誤: $status")
                        _connectionState.value = ConnectionState.Failed("連線失敗 (status=$status)")
                        gatt.close()
                        bluetoothGatt = null
                        if (continuation.isActive) continuation.resume(Unit)
                        return
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "已連線，開始探索服務...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "連線中斷")
                        _connectionState.value = ConnectionState.Disconnected
                        gatt.close()
                        bluetoothGatt = null
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "服務探索完成")
                        _connectionState.value = ConnectionState.Connected(device.name, device.address)
                        
                        // 這裡應該啟動通知與讀取資訊，暫略以求編譯通過
                        
                        if (continuation.isActive) continuation.resume(Unit)
                    } else {
                        _connectionState.value = ConnectionState.Failed("服務探索失敗")
                        gatt.disconnect()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }

            bluetoothGatt = device.connectGatt(context, false, callback)
            continuation.invokeOnCancellation {
                bluetoothGatt?.disconnect()
            }
        }
    }

    override fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    override fun uploadFirmware(bytes: ByteArray): Flow<OtaEvent> {
        TODO("Not yet implemented")
    }
}
