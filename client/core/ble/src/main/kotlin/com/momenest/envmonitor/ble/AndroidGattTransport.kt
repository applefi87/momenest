/**********************************************************************
 * AndroidGattTransport.kt — 把 Android 的回呼式 BluetoothGatt 包成協程 API
 *
 * 這是協定層 GattTransport 這個「埠」的 Android 轉接器。所有 Android BLE 的
 * 麻煩事都關在這一個檔案裡：回呼轉協程、操作序列化、API 33 前後的 API 差異、
 * CCCD 描述元。核心邏輯（OtaUploader）因此完全看不到 android.*，
 * 也才能在純 JVM 上測完整流程。
 *
 * 刻意不寫單元測試：這裡幾乎每一行都在跟真實的 Android framework 互動，
 * 用 mock 測只會測到「我以為 framework 這樣運作」，沒有意義。
 * 真正該被嚴格測試的邏輯已經被抽到 :core:protocol 了。
 **********************************************************************/
package com.momenest.envmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import com.momenest.envmonitor.protocol.BleUuid
import com.momenest.envmonitor.protocol.GattContract
import com.momenest.envmonitor.protocol.GattTransport
import com.momenest.envmonitor.protocol.GattTransportException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

private const val TAG = "GattTransport"

/** 藍牙規格的預設 ATT MTU；協商成功前只能假設這個值 */
private const val DEFAULT_MTU = 23

/**
 * 一條 GATT 連線。生命週期 = 一次連線，斷線後要建新的。
 *
 * 權限：所有標了 [SuppressLint] 的呼叫都需要 BLUETOOTH_CONNECT（API 31+），
 * 由 UI 層的 BluetoothPermissionGate 在進入畫面前就擋掉未授權的情況。
 */
@SuppressLint("MissingPermission")
internal class AndroidGattTransport(
    private val context: Context,
    private val queue: GattOperationQueue,
) : GattTransport {

    private var gatt: BluetoothGatt? = null

    @Volatile
    private var mtu: Int = DEFAULT_MTU

    // 每個 characteristic 一條 notify 流，避免訂閱者互相干擾。
    // extraBufferCapacity 讓回呼執行緒能用 tryEmit 非阻塞地送出（回呼裡不能 suspend）。
    private val notifyFlows = ConcurrentHashMap<BleUuid, MutableSharedFlow<ByteArray>>()

    /** 連線狀態變化：true = 已連上、false = 已斷線 */
    val connectionEvents = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 8)

    // 一次性操作的等待點。所有操作都經過 queue 序列化，所以同一時間每種最多一個。
    private var connectCont: CancellableContinuation<Boolean>? = null
    private var servicesCont: CancellableContinuation<Boolean>? = null
    private var mtuCont: CancellableContinuation<Int>? = null
    private var writeCont: CancellableContinuation<Boolean>? = null
    private var readCont: CancellableContinuation<ByteArray?>? = null
    private var descriptorCont: CancellableContinuation<Boolean>? = null

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val connected = status == BluetoothGatt.GATT_SUCCESS &&
                newState == BluetoothProfile.STATE_CONNECTED
            Log.d(TAG, "連線狀態變化 status=$status newState=$newState")

            connectCont?.let { cont ->
                connectCont = null
                if (cont.isActive) cont.resume(connected)
            }
            connectionEvents.tryEmit(connected)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            servicesCont?.let { cont ->
                servicesCont = null
                if (cont.isActive) cont.resume(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // 協商失敗就維持預設值——寧可分塊小一點慢慢傳，也不要送出會被截斷的封包
            if (status == BluetoothGatt.GATT_SUCCESS) this@AndroidGattTransport.mtu = mtu
            Log.d(TAG, "MTU 協商結果 mtu=$mtu status=$status")
            mtuCont?.let { cont ->
                mtuCont = null
                if (cont.isActive) cont.resume(this@AndroidGattTransport.mtu)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            writeCont?.let { cont ->
                writeCont = null
                if (cont.isActive) cont.resume(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            descriptorCont?.let { cont ->
                descriptorCont = null
                if (cont.isActive) cont.resume(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        // API 33 起改用帶 value 參數的版本；33 以下只會呼叫舊的那個，兩個都要接
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            finishRead(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }

        @Deprecated("API 33 起由帶 value 的多載取代，但 minSdk 26 仍需要它")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            finishRead(if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            dispatchNotification(characteristic.uuid, value)
        }

        @Deprecated("API 33 起由帶 value 的多載取代，但 minSdk 26 仍需要它")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            characteristic.value?.let { dispatchNotification(characteristic.uuid, it) }
        }
    }

    private fun finishRead(value: ByteArray?) {
        readCont?.let { cont ->
            readCont = null
            if (cont.isActive) cont.resume(value)
        }
    }

    private fun dispatchNotification(uuid: UUID, value: ByteArray) {
        // 存副本：framework 會重用同一個 buffer，不複製的話下一筆 notify 會蓋掉這筆
        notifyFlows[uuid.toBleUuid()]?.tryEmit(value.copyOf())
    }

    // ------------------------------------------------------------ 連線生命週期

    /** 建立 GATT 連線並等 onConnectionStateChange */
    suspend fun connect(device: BluetoothDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            connectCont = cont
            // autoConnect = false：直連比較快，且 autoConnect 在部分機型上行為詭異
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            cont.invokeOnCancellation { closeQuietly() }
        }

    /**
     * 協商更大的 MTU。
     *
     * 預設 MTU 23 表示每筆只能寫 20 bytes——1MB 韌體要送五萬多筆，
     * 傳輸時間差好幾倍。這是 OTA 體感速度最關鍵的一步。
     */
    suspend fun requestMtu(preferred: Int = GattContract.PREFERRED_MTU): Int =
        queue.execute {
            suspendCancellableCoroutine { cont ->
                val g = gatt
                if (g == null || !g.requestMtu(preferred)) {
                    mtuCont = null
                    if (cont.isActive) cont.resume(mtu)
                } else {
                    mtuCont = cont
                }
            }
        }

    suspend fun discoverServices(): Boolean = queue.execute {
        suspendCancellableCoroutine { cont ->
            val g = gatt
            if (g == null || !g.discoverServices()) {
                servicesCont = null
                if (cont.isActive) cont.resume(false)
            } else {
                servicesCont = cont
            }
        }
    }

    /** 設備上是否有這個 characteristic（舊韌體可能沒有 device_info / OTA） */
    fun hasCharacteristic(uuid: BleUuid): Boolean = findCharacteristic(uuid) != null

    suspend fun read(uuid: BleUuid): ByteArray? = queue.execute {
        suspendCancellableCoroutine { cont ->
            val g = gatt
            val ch = findCharacteristic(uuid)
            if (g == null || ch == null || !g.readCharacteristic(ch)) {
                readCont = null
                if (cont.isActive) cont.resume(null)
            } else {
                readCont = cont
            }
        }
    }

    /**
     * 開啟 notify。
     *
     * 兩步驟缺一不可：setCharacteristicNotification 只設定本機端要不要收，
     * **還要寫 CCCD 描述元**告訴設備開始推送。少寫 CCCD 是新手最常見的錯誤，
     * 症狀就是「連得上、讀得到、但永遠收不到自動更新」。
     */
    suspend fun enableNotifications(uuid: BleUuid): Boolean = queue.execute {
        val g = gatt ?: return@execute false
        val ch = findCharacteristic(uuid) ?: return@execute false
        if (!g.setCharacteristicNotification(ch, true)) return@execute false

        val cccd = ch.getDescriptor(GattContract.CCCD_UUID.toJavaUuid()) ?: return@execute false
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        suspendCancellableCoroutine { cont ->
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, enable) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = enable
                    g.writeDescriptor(cccd)
                }
            }
            if (!started) {
                descriptorCont = null
                if (cont.isActive) cont.resume(false)
            } else {
                descriptorCont = cont
            }
        }
    }

    fun close() = closeQuietly()

    private fun closeQuietly() {
        // 一定要 close() 而不是只 disconnect()：不 close 的話底層的 GATT client
        // 不會被釋放，連線幾次之後就會因為達到上限而再也連不上
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        mtu = DEFAULT_MTU
    }

    // ------------------------------------------------------------ GattTransport

    override suspend fun write(characteristic: BleUuid, value: ByteArray) {
        val ok = queue.execute {
            suspendCancellableCoroutine { cont ->
                val g = gatt
                val ch = findCharacteristic(characteristic)
                if (g == null || ch == null) {
                    writeCont = null
                    if (cont.isActive) cont.resume(false)
                    return@suspendCancellableCoroutine
                }

                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(
                        ch,
                        value,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        ch.value = value
                        g.writeCharacteristic(ch)
                    }
                }

                if (!started) {
                    writeCont = null
                    if (cont.isActive) cont.resume(false)
                } else {
                    writeCont = cont
                }
            }
        }
        if (!ok) throw GattTransportException("寫入 $characteristic 失敗（連線可能已中斷）")
    }

    override fun notifications(characteristic: BleUuid): Flow<ByteArray> =
        notifyFlows.getOrPut(characteristic) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }.asSharedFlow()

    override fun negotiatedMtu(): Int = mtu

    private fun findCharacteristic(uuid: BleUuid): BluetoothGattCharacteristic? =
        gatt?.getService(GattContract.SERVICE_UUID.toJavaUuid())
            ?.getCharacteristic(uuid.toJavaUuid())
}
