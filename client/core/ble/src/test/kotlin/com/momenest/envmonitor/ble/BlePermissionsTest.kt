/*
 * BlePermissionsTest.kt
 *
 * 權限清單給錯的症狀特別惡劣：App 不會崩潰、不會報錯，就只是「掃描永遠掃不到東西」。
 * 而且要在多個 Android 版本的實機上才驗得出來——所以把它做成純函式在這裡一次驗完。
 */
package com.momenest.envmonitor.ble

import android.Manifest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlePermissionsTest {

    private val scan = Manifest.permission.BLUETOOTH_SCAN
    private val connect = Manifest.permission.BLUETOOTH_CONNECT
    private val location = Manifest.permission.ACCESS_FINE_LOCATION

    @Test
    fun `Android 12 起要的是兩個藍牙權限`() {
        listOf(31, 33, 34, 35).forEach { sdk ->
            assertThat(BlePermissions.runtimePermissions(sdk))
                .containsExactly(scan, connect)
        }
    }

    @Test
    fun `Android 12 起不再要求定位權限`() {
        // manifest 已宣告 neverForLocation；還去要定位會讓使用者疑慮而拒絕授權
        assertThat(BlePermissions.runtimePermissions(33)).doesNotContain(location)
    }

    @Test
    fun `Android 11 以下要的是定位權限`() {
        listOf(26, 29, 30).forEach { sdk ->
            assertThat(BlePermissions.runtimePermissions(sdk)).containsExactly(location)
        }
    }

    @Test
    fun `Android 11 以下不要求新版藍牙權限`() {
        // BLUETOOTH_SCAN / CONNECT 在 API 30 以下根本不存在，請求會直接被拒
        assertThat(BlePermissions.runtimePermissions(30)).containsNoneOf(scan, connect)
    }

    @Test
    fun `全部授權後沒有缺少的權限`() {
        assertThat(BlePermissions.missing(33, setOf(scan, connect))).isEmpty()
        assertThat(BlePermissions.missing(30, setOf(location))).isEmpty()
    }

    @Test
    fun `只授權一半時列出缺少的那個`() {
        assertThat(BlePermissions.missing(33, setOf(scan))).containsExactly(connect)
        assertThat(BlePermissions.missing(33, setOf(connect))).containsExactly(scan)
    }

    @Test
    fun `完全沒授權時列出全部`() {
        assertThat(BlePermissions.missing(33, emptySet())).containsExactly(scan, connect)
        assertThat(BlePermissions.missing(30, emptySet())).containsExactly(location)
    }

    @Test
    fun `不相干的權限不會被當成已授權`() {
        assertThat(BlePermissions.missing(33, setOf(Manifest.permission.CAMERA)))
            .containsExactly(scan, connect)
    }
}
