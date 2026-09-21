package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题设置持久化（MMKV + StateFlow）。
 *
 * 配置项：
 * - [themeMode]：主题模式（浅色 / 暗色 / 跟随系统），默认跟随系统
 * - [themeSchemeOrdinal]：配色方案序号，默认 0（雾蓝 Misty）
 *
 * **A1 架构修复（2026-09-21）**：本模块原先直接使用 :core:designsystem 的 `NiScheme` 枚举类型，
 * 造成**数据层依赖 UI 层的依赖倒置**。现改为只持久化 / 暴露 `Int` 序号 —— 与 MMKV 里的存储格式
 * 本来就是 ordinal 一致，因此**无数据迁移**。UI 侧在边界处用 `NiScheme.fromOrdinal(ordinal)`
 * 还原为枚举（越界回落默认方案），行为与修复前完全等价。
 *
 * 使用方式：
 * - 写入端：:feature:home 的 ThemeScreen 调用 [setThemeMode] / [setThemeScheme]
 * - 读取端：MainActivity / PlayerActivity collectAsState [themeFlow]，
 *   经 `NiScheme.fromOrdinal` 还原后传入 designsystem 的 NiTheme 应用主题
 */
object ThemeSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_SCHEME = "theme_scheme"

    /** 默认配色方案序号（0 = 雾蓝 Misty，与 :core:designsystem 的 `NiScheme.MISTY` 对应）。 */
    private const val DEFAULT_SCHEME_ORDINAL = 0

    /** 主题模式枚举。 */
    enum class Mode(val value: Int) {
        LIGHT(0),
        DARK(1),
        SYSTEM(2);

        companion object {
            fun fromValue(v: Int): Mode = entries.find { it.value == v } ?: SYSTEM
        }
    }

    private val _themeFlow = MutableStateFlow(loadThemeConfig())
    /** 主题配置 StateFlow，写入时自动更新。 */
    val themeFlow: StateFlow<ThemeConfig> = _themeFlow.asStateFlow()

    /** 当前主题模式。 */
    val themeMode: Mode
        get() = _themeFlow.value.mode

    /** 当前配色方案序号（存储原始值，外部模块无需依赖 NiScheme）。 */
    val themeSchemeOrdinal: Int
        get() = _themeFlow.value.schemeOrdinal

    /** 设置主题模式，立即持久化并通知 StateFlow。 */
    fun setThemeMode(mode: Mode) {
        mmkv.encode(KEY_THEME_MODE, mode.value)
        _themeFlow.value = _themeFlow.value.copy(mode = mode)
    }

    /** 设置配色方案序号，立即持久化并通知 StateFlow。负数按 0 处理。 */
    fun setThemeScheme(schemeOrdinal: Int) {
        val safe = schemeOrdinal.coerceAtLeast(0)
        mmkv.encode(KEY_THEME_SCHEME, safe)
        _themeFlow.value = _themeFlow.value.copy(schemeOrdinal = safe)
    }

    /** 从备份快照恢复主题模式（接收存储原始值，外部模块无需依赖 Mode 枚举）。 */
    fun restoreMode(modeValue: Int) {
        setThemeMode(Mode.fromValue(modeValue))
    }

    /** 从备份快照恢复配色方案（接收存储原始值，外部模块无需依赖 NiScheme）。 */
    fun restoreScheme(schemeOrdinal: Int) {
        setThemeScheme(schemeOrdinal)
    }

    private fun loadThemeConfig(): ThemeConfig {
        val mode = Mode.fromValue(mmkv.decodeInt(KEY_THEME_MODE, Mode.SYSTEM.value))
        val schemeOrdinal = mmkv.decodeInt(KEY_THEME_SCHEME, DEFAULT_SCHEME_ORDINAL)
        return ThemeConfig(mode, schemeOrdinal.coerceAtLeast(0))
    }
}

/** 主题配置快照。 */
data class ThemeConfig(
    val mode: ThemeSettings.Mode,
    /** 配色方案序号；UI 侧用 `NiScheme.fromOrdinal` 还原为枚举。 */
    val schemeOrdinal: Int = 0,
)
