package com.momenest.envmonitor.feature.monitor

import com.google.common.truth.Truth.assertThat
import com.momenest.envmonitor.protocol.Calibration
import com.momenest.envmonitor.protocol.SensorReading
import org.junit.Test

class ReadingTileMapperTest {

    private val noCalibration = Calibration()
    private val fullCalibration = Calibration(
        soilMin = 3000, soilMax = 1200,   // 電容式探針：越濕 ADC 越低，刻意用反向刻度
        waterMin = 0, waterMax = 4000,
    )

    private val fullReading = SensorReading(
        airTemp = 24.58f,
        airHum = 58.07f,
        waterTemp = 31.0f,
        soilRaw = 2100,
        waterLevelRaw = 1000,
    )

    private fun List<ReadingTile>.byKey(key: String) = first { it.key == key }

    @Test
    fun `永遠回傳五張卡且順序固定`() {
        // 順序與網頁版 ble-app.html 的 FIELDS 一致，使用者換裝置不會看到不同排列
        val keys = ReadingTileMapper.toTiles(fullReading, noCalibration).map { it.key }
        assertThat(keys)
            .containsExactly("air_temp", "water_temp", "air_hum", "soil", "water_level")
            .inOrder()
    }

    @Test
    fun `沒有讀值時仍回傳五張佔位卡而不是空清單`() {
        // 回空清單會讓畫面在連線瞬間整片跳動
        val tiles = ReadingTileMapper.toTiles(null, noCalibration)
        assertThat(tiles).hasSize(5)
        assertThat(tiles.map { it.value }).containsExactly("--", "--", "--", "--", "--")
    }

    @Test
    fun `溫濕度固定顯示一位小數`() {
        val tiles = ReadingTileMapper.toTiles(fullReading, noCalibration)
        assertThat(tiles.byKey("air_temp").value).isEqualTo("24.6")
        assertThat(tiles.byKey("water_temp").value).isEqualTo("31.0")
        assertThat(tiles.byKey("air_hum").value).isEqualTo("58.1")
    }

    @Test
    fun `溫度單位是攝氏濕度單位是百分比`() {
        val tiles = ReadingTileMapper.toTiles(fullReading, noCalibration)
        assertThat(tiles.byKey("air_temp").unit).isEqualTo("°C")
        assertThat(tiles.byKey("water_temp").unit).isEqualTo("°C")
        assertThat(tiles.byKey("air_hum").unit).isEqualTo("%")
    }

    @Test
    fun `負溫度可以正確顯示`() {
        val tiles = ReadingTileMapper.toTiles(SensorReading(airTemp = -3.25f), noCalibration)
        assertThat(tiles.byKey("air_temp").value).isEqualTo("-3.2")
    }

    @Test
    fun `未校準時土壤與水位顯示原始 ADC 且沒有單位`() {
        // 硬套猜測的預設校準會給出看似精準卻錯誤的百分比，不如老實顯示原始值
        val tiles = ReadingTileMapper.toTiles(fullReading, noCalibration)
        assertThat(tiles.byKey("soil").value).isEqualTo("2100")
        assertThat(tiles.byKey("soil").unit).isEmpty()
        assertThat(tiles.byKey("water_level").value).isEqualTo("1000")
        assertThat(tiles.byKey("water_level").unit).isEmpty()
    }

    @Test
    fun `已校準時土壤與水位顯示百分比`() {
        val tiles = ReadingTileMapper.toTiles(fullReading, fullCalibration)
        // 反向刻度 3000→0%、1200→100%，2100 落在正中間
        assertThat(tiles.byKey("soil").value).isEqualTo("50")
        assertThat(tiles.byKey("soil").unit).isEqualTo("%")
        assertThat(tiles.byKey("water_level").value).isEqualTo("25")
        assertThat(tiles.byKey("water_level").unit).isEqualTo("%")
    }

    @Test
    fun `只校準土壤時水位仍顯示原始值`() {
        val tiles = ReadingTileMapper.toTiles(
            fullReading,
            Calibration(soilMin = 3000, soilMax = 1200),
        )
        assertThat(tiles.byKey("soil").unit).isEqualTo("%")
        assertThat(tiles.byKey("water_level").unit).isEmpty()
    }

    @Test
    fun `百分比會被夾在 0 到 100`() {
        val tiles = ReadingTileMapper.toTiles(
            SensorReading(soilRaw = 9999, waterLevelRaw = -50),
            fullCalibration,
        )
        assertThat(tiles.byKey("soil").value).isEqualTo("0")
        assertThat(tiles.byKey("water_level").value).isEqualTo("0")
    }

    @Test
    fun `部分感測器故障時其餘欄位照常顯示`() {
        val tiles = ReadingTileMapper.toTiles(
            SensorReading(airTemp = 24.5f, waterTemp = null, soilRaw = null),
            noCalibration,
        )
        assertThat(tiles.byKey("air_temp").value).isEqualTo("24.5")
        assertThat(tiles.byKey("water_temp").value).isEqualTo("--")
        assertThat(tiles.byKey("soil").value).isEqualTo("--")
    }

    @Test
    fun `沒有數值時不顯示單位`() {
        // 「-- °C」看起來像壞掉，單位要一起省略
        val tiles = ReadingTileMapper.toTiles(SensorReading(), noCalibration)
        assertThat(tiles.map { it.unit }.toSet()).containsExactly("")
    }

    @Test
    fun `每張卡有各自的強調色`() {
        val colors = ReadingTileMapper.toTiles(fullReading, noCalibration).map { it.accentArgb }
        assertThat(colors.toSet()).hasSize(5)
        // ARGB 的 alpha 必須是 FF，否則卡片小圓點會透明看不見
        assertThat(colors.all { (it shr 24) and 0xFF == 0xFFL }).isTrue()
    }
}
