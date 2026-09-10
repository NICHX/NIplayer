package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 音频处理偏好设置（F-02 音频均衡器）。
 *
 * - [equalizerEnabled]：均衡器总开关，默认 false（关闭，避免无意识改变音色）
 * - [equalizerPresetIndex]：预设索引，`>= 0` 使用系统预设，`-1` 使用自定义频段增益
 * - 自定义频段增益通过 [getBandLevel] / [setBandLevel] 按 band 索引存储（单位 mB）
 *
 * 频段数量在运行时由 [android.media.audiofx.Equalizer.numberOfBands] 决定（通常 5 频段），
 * 存储时按 band 索引读写，未设置的 band 默认 0 mB（不平坦）。
 */
object AudioSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_EQ_ENABLED = "audio_eq_enabled"
    private const val KEY_EQ_PRESET = "audio_eq_preset"
    private const val KEY_EQ_BAND_PREFIX = "audio_eq_band_"

    /** 均衡器频段数量（v2 UI 固定 5 频段，与 EqualizerSettingsScreen EQ_BANDS 一致）。 */
    const val BAND_COUNT = 5

    /** 均衡器总开关。默认 false。 */
    var equalizerEnabled: Boolean
        get() = mmkv.decodeBool(KEY_EQ_ENABLED, false)
        set(value) { mmkv.encode(KEY_EQ_ENABLED, value) }

    /**
     * 预设索引。`>= 0` 使用系统预设（对应 Equalizer.presetNames 索引），
     * `-1` 使用自定义频段增益。默认 0。
     */
    var equalizerPresetIndex: Int
        get() = mmkv.decodeInt(KEY_EQ_PRESET, 0)
        set(value) { mmkv.encode(KEY_EQ_PRESET, value) }

    /**
     * 获取指定频段的自定义增益（mB，毫贝）。
     *
     * @param band 频段索引（0-based）
     * @return 增益值（mB），默认 0
     */
    fun getBandLevel(band: Int): Int =
        mmkv.decodeInt(KEY_EQ_BAND_PREFIX + band, 0)

    /**
     * 设置指定频段的自定义增益（mB）。
     *
     * 设置自定义增益时自动将 [equalizerPresetIndex] 置为 -1（自定义模式）。
     *
     * @param band 频段索引（0-based）
     * @param level 增益值（mB）
     */
    fun setBandLevel(band: Int, level: Int) {
        mmkv.encode(KEY_EQ_BAND_PREFIX + band, level)
        equalizerPresetIndex = -1
    }

    /**
     * 导出当前自定义频段增益快照（仅包含非 0 频段，供备份）。
     *
     * @return band 索引 -> 增益（mB）
     */
    fun snapshotBandLevels(): Map<Int, Int> = buildMap {
        for (band in 0 until BAND_COUNT) {
            val level = getBandLevel(band)
            if (level != 0) put(band, level)
        }
    }
}
