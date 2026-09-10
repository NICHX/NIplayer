package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * VR（全景/左右·上下格式）播放偏好设置（MMKV）。
 *
 * 作用于播放器内以陀螺仪视角环视单眼等距柱面画面的功能：
 * - [formatIndex]：画面格式索引，对应左右/上下 × 180°/360° 的四种组合
 *   （0=左右·360 / 1=左右·180 / 2=上下·360 / 3=上下·180）。默认 0。
 * - [fovDegrees]：垂直视场角（FOV，度）。越大看到范围越广、画面越"推远"。
 *   默认 85。
 * - [gyroSensitivity]：陀螺仪灵敏度（0.05~0.5）。越大视角跟随越快（越灵敏）。
 *   默认 0.12。
 * - [invertYaw]：水平转向反向开关。设备/ROM 不同导致陀螺仪 yaw 方向与直觉相反时可翻转。
 *   默认 false。
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

    /** 画面格式索引：0=左右·360 / 1=左右·180 / 2=上下·360 / 3=上下·180。 */
    var formatIndex: Int
        get() = mmkv.decodeInt(KEY_FORMAT, 0)
        set(value) { mmkv.encode(KEY_FORMAT, value.coerceIn(0, 3)) }

    /** 垂直视场角（度）。clamp 到 [MIN_FOV_DEGREES, MAX_FOV_DEGREES]。 */
    var fovDegrees: Int
        get() = mmkv.decodeInt(KEY_FOV, DEFAULT_FOV_DEGREES)
        set(value) { mmkv.encode(KEY_FOV, value.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)) }

    /** 陀螺仪灵敏度（slerp 权重），越大视角跟随越快。clamp 到 [0.05, 0.5]。 */
    var gyroSensitivity: Float
        get() = mmkv.decodeFloat(KEY_GYRO_SENSITIVITY, DEFAULT_GYRO_SENSITIVITY)
        set(value) { mmkv.encode(KEY_GYRO_SENSITIVITY, value.coerceIn(MIN_GYRO_SENSITIVITY, MAX_GYRO_SENSITIVITY)) }

    /** 视距（Zoom）倍率，MIN_ZOOM~MAX_ZOOM。>1 越推近（拉近视野），<1 越拉远（视野变宽）。 */
    var zoom: Float
        get() = mmkv.decodeFloat(KEY_ZOOM, 1f)
        set(value) { mmkv.encode(KEY_ZOOM, value.coerceIn(MIN_ZOOM, MAX_ZOOM)) }

    /** 水平转向反向开关。 */
    var invertYaw: Boolean
        get() = mmkv.decodeBool(KEY_INVERT_YAW, false)
        set(value) { mmkv.encode(KEY_INVERT_YAW, value) }

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
}