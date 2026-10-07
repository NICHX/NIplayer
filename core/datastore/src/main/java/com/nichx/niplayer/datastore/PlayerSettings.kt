package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 音频播放器外观样式。
 *
 * - [VINYL]：黑胶唱片。
 * - [GLASS]：简约封面（保留模糊封面背景与方形封面卡，控件与黑胶一致，无玻璃质感）。
 * - [APPLE_MUSIC]：Apple Music 风格（封面主色渐变背景 + 极简扁平控件 + 左对齐大字歌词），默认样式。
 */
enum class AudioPlayerStyle { VINYL, GLASS, APPLE_MUSIC }

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
    private const val KEY_AUDIO_PLAYER_STYLE = "player_audio_player_style"
    private const val KEY_ORIENTATION_MODE = "player_orientation_mode"
    private const val KEY_SCALE_MODE_INDEX = "player_scale_mode_index"
    private const val KEY_REMEMBER_AUDIO_PROGRESS = "player_remember_audio_progress"
    private const val KEY_AUDIO_PROGRESS_MIN_MINUTES = "player_audio_progress_min_minutes"
    private const val KEY_COVER_LABEL_FONT_PATH = "player_cover_label_font_path"
    private const val KEY_COVER_LABEL_FONT_NAME = "player_cover_label_font_name"

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

    /**
     * 长按画面触发临时倍速的时长（ms）。默认 500ms。
     *
     * ⚠️ 2026-09-30 调整：原默认 300ms、选项 250/300/400 明显短于系统长按阈值
     * （ViewConfiguration#getLongPressTimeout，通常 400~500ms）。偏短的时长会让
     * 「按下后稍作停顿再滑动」的习惯在滑动生效前先触发长按倍速（误触）。
     * 现整体上移为 400/500/600，默认 500，与系统手势节奏对齐。
     *
     * 读取时把历史遗留的小于 [LONG_PRESS_TIMEOUT_MIN_MS] 的值（旧 250/300）统一上抬到 500，
     * 使老用户无需手动改设置即可摆脱误触。
     */
    var longPressTimeoutMs: Int
        get() = mmkv.decodeInt(KEY_LONG_PRESS_TIMEOUT_MS, DEFAULT_LONG_PRESS_TIMEOUT_MS)
            .let { if (it < LONG_PRESS_TIMEOUT_MIN_MS) DEFAULT_LONG_PRESS_TIMEOUT_MS else it }
        set(value) { mmkv.encode(KEY_LONG_PRESS_TIMEOUT_MS, value) }

    /** 长按倍速默认时长（ms）：与系统长按阈值对齐，避免抢占滑动意图。 */
    const val DEFAULT_LONG_PRESS_TIMEOUT_MS = 500

    /** 允许的最小长按时长（ms）。低于该值视为历史遗留配置，读取时上抬到默认值。 */
    const val LONG_PRESS_TIMEOUT_MIN_MS = 400

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
     * 音频播放器外观样式（[AudioPlayerStyle]）。默认 [AudioPlayerStyle.APPLE_MUSIC]。
     *
     * 由「设置 → 音频播放器设置 → 外观」写入，[com.nichx.niplayer.feature.player.AudioPlayerScreen]
     * 读取并按样式渲染竖屏/横屏布局；播放器内也可临时切换并同步写回，作为持久默认。
     */
    var audioPlayerStyle: AudioPlayerStyle
        get() = runCatching {
            AudioPlayerStyle.valueOf(
                mmkv.decodeString(KEY_AUDIO_PLAYER_STYLE) ?: AudioPlayerStyle.APPLE_MUSIC.name,
            )
        }.getOrDefault(AudioPlayerStyle.APPLE_MUSIC)
        set(value) { mmkv.encode(KEY_AUDIO_PLAYER_STYLE, value.name) }

    /**
     * 无封面「生成封面」上文件名使用的自定义字体文件路径（应用私有目录内的副本）。
     *
     * 空串表示未设置 —— 用系统默认字体。应用**不内置**字体（CJK 手写体近 3MB），
     * 由用户在「设置 → 音频播放器设置 → 外观」里自选，选中后复制到私有目录。
     */
    var coverLabelFontPath: String
        get() = mmkv.decodeString(KEY_COVER_LABEL_FONT_PATH) ?: ""
        set(value) { mmkv.encode(KEY_COVER_LABEL_FONT_PATH, value) }

    /** 自定义字体的原始文件名，仅用于设置页展示。 */
    var coverLabelFontName: String
        get() = mmkv.decodeString(KEY_COVER_LABEL_FONT_NAME) ?: ""
        set(value) { mmkv.encode(KEY_COVER_LABEL_FONT_NAME, value) }

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

    /**
     * 是否记住音频播放进度。默认 true。
     *
     * 仅对总时长达到 [audioProgressMinDurationMinutes] 的长音频（有声书/播客）生效：
     * 普通歌曲短于门槛时不记录也不续播，避免切歌时从中间开始。关闭后所有音频都不记录、不续播。
     */
    var rememberAudioProgress: Boolean
        get() = mmkv.decodeBool(KEY_REMEMBER_AUDIO_PROGRESS, true)
        set(value) { mmkv.encode(KEY_REMEMBER_AUDIO_PROGRESS, value) }

    /** 音频进度记录门槛候选值（分钟），供设置页选择。 */
    val AUDIO_PROGRESS_MIN_MINUTES_OPTIONS: List<Int> = listOf(1, 3, 5, 10, 15, 30)

    /** 音频进度记录门槛默认值（分钟）。 */
    const val DEFAULT_AUDIO_PROGRESS_MIN_MINUTES = 5

    /**
     * 音频记录/续播播放进度的最小总时长（分钟），默认 [DEFAULT_AUDIO_PROGRESS_MIN_MINUTES]。
     *
     * 仅对总时长不小于该值的音频（有声书/播客）记录并断点续播；普通歌曲从头播放。
     * 读取时若值不在 [AUDIO_PROGRESS_MIN_MINUTES_OPTIONS] 内（历史/异常数据）回退到默认值。
     */
    var audioProgressMinDurationMinutes: Int
        get() = mmkv.decodeInt(KEY_AUDIO_PROGRESS_MIN_MINUTES, DEFAULT_AUDIO_PROGRESS_MIN_MINUTES)
            .takeIf { it in AUDIO_PROGRESS_MIN_MINUTES_OPTIONS } ?: DEFAULT_AUDIO_PROGRESS_MIN_MINUTES
        set(value) { mmkv.encode(KEY_AUDIO_PROGRESS_MIN_MINUTES, value) }

    /** 音频进度记录门槛（毫秒），供续播判定与落盘使用。 */
    val audioProgressMinDurationMs: Long
        get() = audioProgressMinDurationMinutes * 60_000L

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
