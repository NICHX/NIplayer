package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 播放器偏好设置（MMKV）。
 *
 * - [longPressSpeed]：长按画面时临时切换到的倍速（松手恢复），默认 2.0x。
 *   在播放器设置面板中可调整，供需要快速回看/速看的场景使用。
 * - [autoDetectBlackBars]：智能黑边检测，默认 false。开启后首帧起抓图分析有效画面区域，
 *   在 Fit 模式下用真实内容宽高比替代容器宽高比，避免"四周都有黑边"。
 */
object PlayerSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_LONG_PRESS_SPEED = "player_long_press_speed"
    private const val KEY_AUTO_DETECT_BLACK_BARS = "player_auto_detect_black_bars"
    private const val KEY_LAST_SPEED_INDEX = "player_last_speed_index"
    private const val KEY_PITCH_PRESERVATION = "player_pitch_preservation"
    private const val KEY_LONG_PRESS_TIMEOUT_MS = "player_long_press_timeout_ms"
    private const val KEY_SEEK_SENSITIVITY = "player_seek_sensitivity"
    private const val KEY_DOUBLE_TAP_STEP_SECONDS = "player_double_tap_step_seconds"
    private const val KEY_AUDIO_PLAY_MODE_INDEX = "player_audio_play_mode_index"
    private const val KEY_AUDIO_SPEED_INDEX = "player_audio_speed_index"
    private const val KEY_ORIENTATION_MODE = "player_orientation_mode"
    private const val KEY_SCALE_MODE_INDEX = "player_scale_mode_index"

    /** 缩放模式索引：适应（Contain）。 */
    const val SCALE_MODE_CONTAIN = 0

    /** 缩放模式索引：填满（Cover）。 */
    const val SCALE_MODE_COVER = 1

    /** 缩放模式索引：拉伸（Fill）。 */
    const val SCALE_MODE_FILL = 2

    /** 允许的长按倍速候选值（UI 选择用）。 */
    val LONG_PRESS_SPEED_OPTIONS: List<Float> = listOf(1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    /** 长按画面时临时切换到的倍速。默认 2.0x。 */
    var longPressSpeed: Float
        get() = mmkv.decodeFloat(KEY_LONG_PRESS_SPEED, 2.0f)
        set(value) { mmkv.encode(KEY_LONG_PRESS_SPEED, value) }

    /** 智能黑边检测开关。默认 false。 */
    var autoDetectBlackBars: Boolean
        get() = mmkv.decodeBool(KEY_AUTO_DETECT_BLACK_BARS, false)
        set(value) { mmkv.encode(KEY_AUTO_DETECT_BLACK_BARS, value) }

    /** 缩放模式索引（[SCALE_MODE_CONTAIN] / [SCALE_MODE_COVER] / [SCALE_MODE_FILL]）。默认适应。 */
    var scaleModeIndex: Int
        get() = mmkv.decodeInt(KEY_SCALE_MODE_INDEX, SCALE_MODE_CONTAIN)
        set(value) { mmkv.encode(KEY_SCALE_MODE_INDEX, value.coerceIn(SCALE_MODE_CONTAIN, SCALE_MODE_FILL)) }

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

    /**
     * 音频播放倍速索引（AudioPlaybackManager.AudioPlaybackSpeedValues 索引）。
     *
     * 音频倍速档位（0.5/1/1.5/2 四档）与视频（8 档）不同，故独立持久化，
     * 不与视频共享 [lastSpeedIndex]，避免索引语义错位。
     */
    var audioSpeedIndex: Int
        get() = mmkv.decodeInt(KEY_AUDIO_SPEED_INDEX, 1)
        set(value) { mmkv.encode(KEY_AUDIO_SPEED_INDEX, value) }

    /**
     * 进入播放器时的方向模式。默认 0（横屏，保持既有行为）。
     *
     * - 0：横屏（[android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE]）
     * - 1：竖屏（[android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT]）
     * - 2：自动（读取视频分辨率，宽>高横屏、高>宽竖屏；首帧尺寸就绪前先按横屏）
     *
     * 播放中仍可通过方向按钮手动切换，本设置只决定进入时刻的默认方向。
     */
    var orientationMode: Int
        get() = mmkv.decodeInt(KEY_ORIENTATION_MODE, 0)
        set(value) { mmkv.encode(KEY_ORIENTATION_MODE, value) }

    // region 去黑边判决缓存

    private const val KEY_BLACK_BAR_CACHE_PREFIX = "blackbar_cache_"

    /** 判决未知：无缓存（或已清除）。 */
    const val BLACK_BAR_VERDICT_UNKNOWN = 0f

    /**
     * 判决「不适用」：该视频是可变画幅（既出现满幅帧、又出现带黑边帧），
     * 整片禁用去黑边，避免把 IMAX 扩展画幅段落的真实画面裁掉。
     */
    const val BLACK_BAR_VERDICT_NOT_APPLICABLE = -1f

    /**
     * 读取指定视频的去黑边判决。
     *
     * 只存一个 Float：`0` = 无缓存，负数 = 不适用，正数 = 检测到的内容宽高比。
     * 内容比例是视频固有属性，与 surface 像素尺寸无关，跨设备/横竖屏稳定
     * （BUG-5：原实现缓存像素宽高，随屏幕分辨率与横竖屏变化而失效）。
     *
     * BUG-51：判决一旦为「不适用」就不再回头——结果单调，不会在裁剪比例与
     * 原始比例之间来回跳变。
     *
     * @return [BLACK_BAR_VERDICT_UNKNOWN] / [BLACK_BAR_VERDICT_NOT_APPLICABLE] / 内容宽高比(>0)
     */
    fun loadBlackBarVerdict(uniqueKey: String): Float =
        mmkv.decodeFloat(KEY_BLACK_BAR_CACHE_PREFIX + uniqueKey, BLACK_BAR_VERDICT_UNKNOWN)

    /**
     * 保存去黑边判决。
     *
     * @param verdict 内容宽高比（> 0）或 [BLACK_BAR_VERDICT_NOT_APPLICABLE]；
     *   其他值（含 [BLACK_BAR_VERDICT_UNKNOWN]）不写入
     */
    fun saveBlackBarVerdict(uniqueKey: String, verdict: Float) {
        if (verdict > 0f || verdict == BLACK_BAR_VERDICT_NOT_APPLICABLE) {
            mmkv.encode(KEY_BLACK_BAR_CACHE_PREFIX + uniqueKey, verdict)
        }
    }

    /** 清除指定视频的去黑边判决缓存。 */
    fun clearBlackBarVerdict(uniqueKey: String) {
        mmkv.removeValueForKey(KEY_BLACK_BAR_CACHE_PREFIX + uniqueKey)
    }

    // endregion
}
