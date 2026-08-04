package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 播放器偏好设置（MMKV）。
 *
 * - [longPressSpeed]：长按画面时临时切换到的倍速（松手恢复），默认 2.0x。
 *   在播放器设置面板中可调整，供需要快速回看/速看的场景使用。
 * - [lastBrightness]：上次退出时的画面亮度（0.0~1.0），下次进入自动恢复。
 *   -1f 表示未设置（使用系统默认亮度）。
 * - [autoDetectBlackBars]：智能黑边检测，默认 true。首帧后抓图分析有效画面区域，
 *   在 Fit 模式下用真实内容宽高比替代容器宽高比，避免"四周都有黑边"。
 */
object PlayerSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_LONG_PRESS_SPEED = "player_long_press_speed"
    private const val KEY_LAST_BRIGHTNESS = "player_last_brightness"
    private const val KEY_AUTO_DETECT_BLACK_BARS = "player_auto_detect_black_bars"
    private const val KEY_LAST_SPEED_INDEX = "player_last_speed_index"
    private const val KEY_PITCH_PRESERVATION = "player_pitch_preservation"
    private const val KEY_LONG_PRESS_TIMEOUT_MS = "player_long_press_timeout_ms"
    private const val KEY_SEEK_SENSITIVITY = "player_seek_sensitivity"
    private const val KEY_DOUBLE_TAP_STEP_SECONDS = "player_double_tap_step_seconds"
    private const val KEY_AUDIO_PLAY_MODE_INDEX = "player_audio_play_mode_index"

    /** 允许的长按倍速候选值（UI 选择用）。 */
    val LONG_PRESS_SPEED_OPTIONS: List<Float> = listOf(1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    /** 长按画面时临时切换到的倍速。默认 2.0x。 */
    var longPressSpeed: Float
        get() = mmkv.decodeFloat(KEY_LONG_PRESS_SPEED, 2.0f)
        set(value) { mmkv.encode(KEY_LONG_PRESS_SPEED, value) }

    /** 上次退出时的画面亮度（0.0~1.0），-1f 表示未设置（使用系统默认）。 */
    var lastBrightness: Float
        get() = mmkv.decodeFloat(KEY_LAST_BRIGHTNESS, -1f)
        set(value) { mmkv.encode(KEY_LAST_BRIGHTNESS, value) }

    /** 智能黑边检测开关。默认 true。仅在 Fit 模式下生效。 */
    var autoDetectBlackBars: Boolean
        get() = mmkv.decodeBool(KEY_AUTO_DETECT_BLACK_BARS, true)
        set(value) { mmkv.encode(KEY_AUTO_DETECT_BLACK_BARS, value) }

    /** 上次退出时的常规倍速索引（SPEED_VALUES 索引），默认 1（1.0x）。 */
    var lastSpeedIndex: Int
        get() = mmkv.decodeInt(KEY_LAST_SPEED_INDEX, 1)
        set(value) { mmkv.encode(KEY_LAST_SPEED_INDEX, value) }

    /**
     * 倍速音调保持开关（F-01）。默认 true。
     *
     * - true：倍速时保持原音调（pitch=1.0，media3 Sonic 算法 time-stretching），适合正常观影
     * - false：变速变调（pitch=speed，类似磁带快进），适合快速浏览/回看
     */
    var pitchPreservationEnabled: Boolean
        get() = mmkv.decodeBool(KEY_PITCH_PRESERVATION, true)
        set(value) { mmkv.encode(KEY_PITCH_PRESERVATION, value) }

    /** 长按画面触发临时倍速的时长（ms）。默认 300ms（系统默认约 400ms）。 */
    var longPressTimeoutMs: Int
        get() = mmkv.decodeInt(KEY_LONG_PRESS_TIMEOUT_MS, 300)
        set(value) { mmkv.encode(KEY_LONG_PRESS_TIMEOUT_MS, value) }

    /**
     * 横滑快进灵敏度：滑动多少倍屏宽滑满整片时长。
     *
     * - 1.0：满屏 = 整片时长（最灵敏，轻微滑动进度变化大）
     * - 1.5：1.5 屏 = 整片时长
     * - 2.0：2 屏 = 整片时长（最不灵敏，适合长视频精细定位）
     *
     * 默认 1.5。
     */
    var seekSensitivity: Float
        get() = mmkv.decodeFloat(KEY_SEEK_SENSITIVITY, 1.5f)
        set(value) { mmkv.encode(KEY_SEEK_SENSITIVITY, value) }

    /** 双击左/右半屏快退/快进的步长（秒）。0 表示关闭双击手势。默认 10 秒。 */
    var doubleTapStepSeconds: Int
        get() = mmkv.decodeInt(KEY_DOUBLE_TAP_STEP_SECONDS, 10)
        set(value) { mmkv.encode(KEY_DOUBLE_TAP_STEP_SECONDS, value) }

    /**
     * 音频播放模式索引（0=顺序循环 / 1=随机 / 2=单曲循环）。
     *
     * 由 AudioPlaybackManager 维护并持久化，进入音频播放页时自动恢复上次选择。
     */
    var audioPlayModeIndex: Int
        get() = mmkv.decodeInt(KEY_AUDIO_PLAY_MODE_INDEX, 0)
        set(value) { mmkv.encode(KEY_AUDIO_PLAY_MODE_INDEX, value) }

    // region 黑边检测结果缓存

    private const val KEY_BLACK_BAR_CACHE_PREFIX = "blackbar_cache_"

    /**
     * 加载指定视频的黑边检测缓存。
     *
     * BUG-5 修复：缓存归一化比例（width/height，Float）而非像素值。
     * 原实现缓存 PixelCopy bitmap 的像素宽高，但 bitmap 尺寸 = SurfaceView 尺寸，
     * 受屏幕分辨率、横竖屏、沉浸式 bar 等影响，跨设备/横竖屏切换时缓存值不适用。
     * 比例是视频固有属性，与 surface 像素尺寸无关，跨场景稳定。
     *
     * @return 有效画面的宽高比（width/height），> 0；无缓存返回 null
     */
    fun loadBlackBarCache(uniqueKey: String): Float? {
        return mmkv.decodeFloat(KEY_BLACK_BAR_CACHE_PREFIX + uniqueKey, 0f)
            .takeIf { it > 0f }
    }

    /**
     * 保存黑边检测结果到缓存。
     *
     * @param aspectRatio 有效画面宽高比（width/height），必须 > 0
     */
    fun saveBlackBarCache(uniqueKey: String, aspectRatio: Float) {
        if (aspectRatio > 0f) {
            mmkv.encode(KEY_BLACK_BAR_CACHE_PREFIX + uniqueKey, aspectRatio)
        }
    }

    /** 清除指定视频的黑边检测缓存。 */
    fun clearBlackBarCache(uniqueKey: String) {
        mmkv.removeValueForKey(KEY_BLACK_BAR_CACHE_PREFIX + uniqueKey)
    }

    // endregion
}
