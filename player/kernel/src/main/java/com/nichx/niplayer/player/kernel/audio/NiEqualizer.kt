package com.nichx.niplayer.player.kernel.audio

import android.media.audiofx.Equalizer
import com.nichx.niplayer.datastore.AudioSettings

/**
 * 均衡器封装（F-02）。
 *
 * 包装 [android.media.audiofx.Equalizer]，管理其生命周期与配置应用：
 * - [attach]：在获取到有效 audioSessionId 后创建 Equalizer 并应用 [AudioSettings] 配置
 * - [release]：释放 Equalizer 资源（播放器销毁 / audioSessionId 变化时调用）
 * - [applySettings]：从 [AudioSettings] 重新读取配置并应用（UI 修改设置后调用）
 *
 * 频段信息（[bandInfos] / [presetNames]）在 [attach] 后才可用，供 UI 读取渲染控件。
 *
 * 线程安全：Equalizer 的方法在主线程调用（ExoPlayer 的 onAudioSessionIdChanged 回调在主线程），
 * UI 层的 applySettings 也应在主线程调用，避免并发。
 */
class NiEqualizer {

    private var equalizer: Equalizer? = null

    /** 是否已成功 attach（有有效的 Equalizer 实例）。 */
    val isAttached: Boolean get() = equalizer != null

    /** 频段信息列表，[attach] 后可用。 */
    val bandInfos: List<BandInfo>
        get() {
            val eq = equalizer ?: return emptyList()
            val range = eq.bandLevelRange // [min, max] mB
            return (0 until eq.numberOfBands.toInt()).map { band ->
                BandInfo(
                    index = band,
                    centerFreqHz = eq.getCenterFreq(band.toShort()) / 1000, // mHz → Hz
                    minLevelMb = range[0].toInt(),
                    maxLevelMb = range[1].toInt(),
                    currentLevelMb = eq.getBandLevel(band.toShort()).toInt(),
                )
            }
        }

    /** 系统预设名称列表，[attach] 后可用。 */
    val presetNames: List<String>
        get() {
            val eq = equalizer ?: return emptyList()
            val count = eq.numberOfPresets.toInt()
            if (count <= 0) return emptyList()
            return (0 until count).map { eq.getPresetName(it.toShort()) }
        }

    /**
     * 绑定到指定 audioSessionId，创建 Equalizer 并应用 [AudioSettings] 配置。
     *
     * @param audioSessionId ExoPlayer 的 audioSessionId，必须 > 0
     */
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        release()
        runCatching {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            applySettings()
        }
    }

    /**
     * 从 [AudioSettings] 重新读取配置并应用到当前 Equalizer。
     *
     * 调用前必须已 [attach]。UI 修改设置后调用此方法实时生效。
     */
    fun applySettings() {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = AudioSettings.equalizerEnabled
            val presetIndex = AudioSettings.equalizerPresetIndex
            if (presetIndex >= 0 && presetIndex < eq.numberOfPresets) {
                eq.usePreset(presetIndex.toShort())
            } else {
                // 自定义模式：逐 band 设置增益
                for (band in 0 until eq.numberOfBands.toInt()) {
                    val level = AudioSettings.getBandLevel(band)
                    eq.setBandLevel(band.toShort(), level.toShort())
                }
            }
            // usePreset 后 enabled 状态可能被重置，重新应用
            eq.enabled = AudioSettings.equalizerEnabled
        }
    }

    /** 释放 Equalizer 资源。 */
    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }

    /** 频段信息。 */
    data class BandInfo(
        val index: Int,
        /** 中心频率（Hz）。 */
        val centerFreqHz: Int,
        /** 最小增益（mB）。 */
        val minLevelMb: Int,
        /** 最大增益（mB）。 */
        val maxLevelMb: Int,
        /** 当前增益（mB）。 */
        val currentLevelMb: Int,
    )
}
