package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * VR（全景）播放偏好设置（MMKV）。
 *
 * 作用于播放器内以陀螺仪 / 手指拖拽环视等距柱面全景画面的功能：
 * - [formatIndex]：画面格式索引，对应「左右 / 上下 / 整幅(mono)」×「180° / 360°」六种组合。
 *   枚举顺序见 VrFormat（feature:player），本模块不反向依赖它，故此处只存索引。默认 0。
 * - [fovDegrees]：垂直视场角（FOV，度）。越大看到范围越广、画面越"推远"。默认 85。
 * - [zoom]：视距倍率，与 FOV 相乘（有效 FOV = fov / zoom）。用于在不改变 FOV 档位的前提下微调远近。
 * - [gyroSensitivity]：陀螺仪灵敏度（0.05~0.5）。越大视角跟随越快（越灵敏）。默认 0.12。
 * - [invertYaw]：水平转向反向开关。设备/ROM 不同导致陀螺仪 yaw 方向与直觉相反时可翻转。默认 false。
 * - [helpShown]：是否已展示过「VR 环视仅适用于全景片源」的一次性提示。默认 false。
 *
 * 设置类均为独立持久化（不依赖其它 settings），由播放器 VR 模式读取并实时生效。
 */
object VrSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_FORMAT = "vr_format"
    private const val KEY_FOV = "vr_fov"
    private const val KEY_GYRO_SENSITIVITY = "vr_gyro_sensitivity"
    private const val KEY_ZOOM = "vr_zoom"
    private const val KEY_INVERT_YAW = "vr_invert_yaw"
    private const val KEY_HELP_SHOWN = "vr_help_shown"

    /** 画面格式索引（VrFormat 枚举顺序：0=左右·360 … 5=整幅·180）。 */
    var formatIndex: Int
        get() = mmkv.decodeInt(KEY_FORMAT, 0)
        set(value) { mmkv.encode(KEY_FORMAT, value.coerceIn(0, MAX_FORMAT_INDEX)) }

    /** 垂直视场角（度）。clamp 到 [MIN_FOV_DEGREES, MAX_FOV_DEGREES]。 */
    var fovDegrees: Int
        get() = mmkv.decodeInt(KEY_FOV, DEFAULT_FOV_DEGREES)
        set(value) { mmkv.encode(KEY_FOV, value.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)) }

    /** 陀螺仪灵敏度（slerp 权重），越大视角跟随越快。clamp 到 [0.05, 0.5]。 */
    var gyroSensitivity: Float
        get() = mmkv.decodeFloat(KEY_GYRO_SENSITIVITY, DEFAULT_GYRO_SENSITIVITY)
        set(value) { mmkv.encode(KEY_GYRO_SENSITIVITY, value.coerceIn(MIN_GYRO_SENSITIVITY, MAX_GYRO_SENSITIVITY)) }

    /**
     * 视距（Zoom）倍率，[MIN_ZOOM]~[MAX_ZOOM]。>1 越推近（视野变窄），<1 越拉远（视野变宽）。
     *
     * 与 [fovDegrees] 相乘生效（有效 FOV = fov / zoom），因此为 1.0 时等价于只用 FOV。
     */
    var zoom: Float
        get() = mmkv.decodeFloat(KEY_ZOOM, 1f)
        set(value) { mmkv.encode(KEY_ZOOM, value.coerceIn(MIN_ZOOM, MAX_ZOOM)) }

    /** 水平转向反向开关。 */
    var invertYaw: Boolean
        get() = mmkv.decodeBool(KEY_INVERT_YAW, false)
        set(value) { mmkv.encode(KEY_INVERT_YAW, value) }

    /** 是否已展示过「VR 环视仅适用于全景片源」的一次性提示。 */
    var helpShown: Boolean
        get() = mmkv.decodeBool(KEY_HELP_SHOWN, false)
        set(value) { mmkv.encode(KEY_HELP_SHOWN, value) }

    /** FOV 越大畸变越明显；默认取中值，兼顾观感与视野。 */
    const val MIN_FOV_DEGREES = 30
    const val MAX_FOV_DEGREES = 120
    const val DEFAULT_FOV_DEGREES = 85

    const val MIN_GYRO_SENSITIVITY = 0.05f
    const val MAX_GYRO_SENSITIVITY = 0.5f
    const val DEFAULT_GYRO_SENSITIVITY = 0.12f

    /** 视距（Zoom）范围：<1 拉远（视野变宽），>1 推近（视野变窄）。 */
    const val MIN_ZOOM = 0.4f
    const val MAX_ZOOM = 3f

    /**
     * [formatIndex] 上界。VrFormat 共 6 种（左右/上下/整幅 × 180°/360°），
     * 这里不能引用它（依赖方向相反），故用常量；新增格式时必须同步上调。
     */
    private const val MAX_FORMAT_INDEX = 5
}
