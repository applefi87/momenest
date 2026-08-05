/**********************************************************************
 * AndroidEnvMonitorClient.kt — EnvMonitorClient 的 Android 實作
 *
 * 一次完整連線的流程（順序不能亂，少一步就是「連得上卻沒資料」）：
 *   掃描(過濾 SERVICE_UUID) → connectGatt → 協商 MTU → 探索服務
 *   → 開啟 readings/status 的 notify（含 CCCD）→ 各讀一次初值
 *   → 讀 device_info（舊韌體沒有，讀不到屬正常）→ 判斷是否支援 OTA
 *
 * 上層（ViewModel）只看得到 StateFlow 與 suspend 函式，所有 GATT 細節
 * 都被關在 AndroidGattTransport 裡。
 **********************************************************************/
package com.momenest.envmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.momenest.envmonitor.protocol.DeviceInfo
import com.momenest.envmonitor.protocol.DeviceInfoParser
import com.momenest.envmonitor.protocol.DeviceStatus
import com.momenest.envmonitor.protocol.GattContract
import com.momenest.envmonitor.protocol.OtaEvent
import com.momenest.envmonitor.protocol.OtaFailure
import com.momenest.envmonitor.protocol.OtaUploader
import com.momenest.envmonitor.protocol.ReadingParser
import com.momenest.envmonitor.protocol.SensorReading
import com.momenest.envmonitor.protocol.StatusParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "EnvMonitorClient"

/** 掃描逾時。設備廣播間隔通常在數百毫秒內，15 秒找不到就是真的不在附近 */
private const val SCAN_TIMEOUT_MS = 15_000L

@SuppressLint("MissingPermission")
class AndroidEnvMonitorClient(
    private val context: Context,
    private val queue: GattOperationQueue,
) : EnvMonitorClient {

    private val adapter: BluetoothAdapter? by lazy {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }

    /** 與 client 同壽命的 scope，用來監看斷線事件 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 只活在單次連線期間；斷線時整個取消，順帶收掉所有 notify 收集器 */
    private var sessionScope: CoroutineScope? = null
    private var watcherJob: Job? = null
    private var transport: AndroidGattTransport? = null

    /** 避免使用者連點造成同時跑兩條連線流程（會互相搶 GATT） */
    private val connectLock = Mutex()

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

    override suspend fun connect() = connectLock.withLock {
        if (_connectionState.value is ConnectionState.Connected) return@withLock

        val adapter = this.adapter
        when {
            adapter == null -> return@withLock fail("此裝置不支援藍牙")
            !adapter.isEnabled -> return@withLock fail("請先開啟藍牙")
        }

        _connectionState.value = ConnectionState.Scanning
        val device = withTimeoutOrNull(SCAN_TIMEOUT_MS) { scanForDevice(requireNotNull(adapter)) }
            ?: return@withLock fail("找不到設備（請確認監測器電源已開啟且在附近）")

        _connectionState.value = ConnectionState.Connecting

        val gattTransport = AndroidGattTransport(context, queue)
        transport = gattTransport

        if (!gattTransport.connect(device)) {
            return@withLock fail("連線失敗，請再試一次")
        }

        // 設備 OTA 完成後會自己重開機 → 這裡會收到斷線事件，把狀態收乾淨
        watcherJob = scope.launch {
            gattTransport.connectionEvents.collect { connected ->
                if (!connected) handleDisconnected()
            }
        }

        // 請求 2M PHY、設定最高傳輸優先權（最小連線間隔 11.25~15ms）並協商大 MTU
        gattTransport.requestPhy2M()
        gattTransport.requestHighPriority()
        gattTransport.requestMtu()

        if (!gattTransport.discoverServices()) {
            return@withLock fail("服務探索失敗，請重新連線")
        }

        subscribeAll(gattTransport)

        _connectionState.value = ConnectionState.Connected(device.name, device.address)
        Log.d(TAG, "連線完成 mtu=${gattTransport.negotiatedMtu()} ota=${_otaSupported.value}")
    }

    override fun disconnect() {
        handleDisconnected()
    }

    override fun uploadFirmware(bytes: ByteArray): Flow<OtaEvent> {
        val gattTransport = transport
        if (gattTransport == null || _connectionState.value !is ConnectionState.Connected) {
            return flowOf(OtaEvent.Failed(OtaFailure.TRANSPORT_ERROR, "尚未連線到設備"))
        }
        if (!_otaSupported.value) {
            return flowOf(
                OtaEvent.Failed(OtaFailure.TRANSPORT_ERROR, "此設備的韌體不支援 BLE OTA"),
            )
        }
        gattTransport.requestHighPriority()
        gattTransport.requestPhy2M()
        return OtaUploader(gattTransport).upload(bytes)
    }

    // ------------------------------------------------------------ 內部流程

    /**
     * 只用 SERVICE_UUID 過濾，**不比對設備名稱**。
     *
     * 名稱比對是個陷阱：Android 12 起若沒有定位權限，ScanResult 的 device.name
     * 常常是 null，於是「掃到了卻因為名字對不上而被忽略」，症狀是永遠連不上。
     * 韌體廣播的 service UUID 本來就是唯一識別，比名稱可靠得多。
     */
    private suspend fun scanForDevice(adapter: BluetoothAdapter): BluetoothDevice? =
        suspendCancellableCoroutine { cont ->
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner.stopScan(this)
                    if (cont.isActive) cont.resume(result.device)
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "掃描失敗 errorCode=$errorCode")
                    if (cont.isActive) cont.resume(null)
                }
            }

            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(GattContract.SERVICE_UUID.toJavaUuid()))
                    .build(),
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(filters, settings, callback)
            cont.invokeOnCancellation { runCatching { scanner.stopScan(callback) } }
        }

    /**
     * 開啟兩個資料 characteristic 的 notify 並各讀一次初值。
     *
     * 為什麼還要讀一次：notify 要等設備下一次量測週期（約 1 秒）才會推第一筆，
     * 少了這一步，畫面會有一秒的空白。
     */
    private suspend fun subscribeAll(gattTransport: AndroidGattTransport) {
        val session = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sessionScope = session

        session.launch {
            gattTransport.notifications(GattContract.READINGS_UUID).collect { bytes ->
                ReadingParser.parse(bytes)?.let { _readings.value = it }
            }
        }
        session.launch {
            gattTransport.notifications(GattContract.STATUS_UUID).collect { bytes ->
                StatusParser.parse(bytes)?.let { _status.value = it }
            }
        }

        gattTransport.enableNotifications(GattContract.READINGS_UUID)
        gattTransport.enableNotifications(GattContract.STATUS_UUID)
        if (gattTransport.hasCharacteristic(GattContract.OTA_CONTROL_UUID)) {
            gattTransport.enableNotifications(GattContract.OTA_CONTROL_UUID)
        }

        gattTransport.read(GattContract.READINGS_UUID)
            ?.let { ReadingParser.parse(it) }
            ?.let { _readings.value = it }
        gattTransport.read(GattContract.STATUS_UUID)
            ?.let { StatusParser.parse(it) }
            ?.let { _status.value = it }

        // 舊韌體沒有 device_info，讀不到只是版本顯示為「未知」，不影響其他功能
        if (gattTransport.hasCharacteristic(GattContract.DEVICE_INFO_UUID)) {
            gattTransport.read(GattContract.DEVICE_INFO_UUID)
                ?.let { DeviceInfoParser.parse(it) }
                ?.let { _deviceInfo.value = it }
        }

        _otaSupported.value = gattTransport.hasCharacteristic(GattContract.OTA_CONTROL_UUID) &&
            gattTransport.hasCharacteristic(GattContract.OTA_DATA_UUID)
    }

    /** 設定失敗狀態並把資源收乾淨。回傳 Unit 方便在 connect() 裡直接 return */
    private fun fail(reason: String) {
        cleanUp()
        _connectionState.value = ConnectionState.Failed(reason)
    }

    private fun handleDisconnected() {
        cleanUp()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun cleanUp() {
        // watcherJob 可能就是呼叫端所在的協程；cancel() 只是標記，
        // 這個非 suspend 函式仍會完整跑完，不會半途中斷
        watcherJob?.cancel()
        watcherJob = null
        sessionScope?.cancel()
        sessionScope = null
        transport?.close()
        transport = null

        _readings.value = null
        _status.value = null
        _deviceInfo.value = null
        _otaSupported.value = false
    }
}
