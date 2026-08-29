package com.nichx.niplayer.datastore

import com.nichx.niplayer.designsystem.theme.NiScheme
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题设置持久化（MMKV + StateFlow）。
 *
 * 配置项：
 * - [themeMode]：主题模式（浅色 / 暗色 / 跟随系统），默认跟随系统
 * - [themeScheme]：配色方案（蓝色 / 紫色 / 青绿），默认蓝色
 *
 * 使用方式：
 * - 写入端：[ThemeScreen] 调用 [setThemeMode] / [setThemeScheme] 修改配置
 * - 读取端：[com.nichx.niplayer.MainActivity] collectAsState [themeFlow]，
 *   传入 [com.nichx.niplayer.designsystem.theme.NiTheme] 应用主题
 */
object ThemeSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_SCHEME = "theme_scheme"

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

    /** 当前配色方案。 */
    val themeScheme: NiScheme
        get() = _themeFlow.value.scheme

    /** 当前配色方案序号（存储原始值，供备份导出，外部模块无需依赖 NiScheme）。 */
    val themeSchemeOrdinal: Int
        get() = _themeFlow.value.scheme.ordinal

    /** 设置主题模式，立即持久化并通知 StateFlow。 */
    fun setThemeMode(mode: Mode) {
        mmkv.encode(KEY_THEME_MODE, mode.value)
        _themeFlow.value = _themeFlow.value.copy(mode = mode)
    }

    /** 设置配色方案，立即持久化并通知 StateFlow。 */
    fun setThemeScheme(scheme: NiScheme) {
        mmkv.encode(KEY_THEME_SCHEME, scheme.ordinal)
        _themeFlow.value = _themeFlow.value.copy(scheme = scheme)
    }

    /** 从备份快照恢复主题模式（接收存储原始值，外部模块无需依赖 Mode 枚举）。 */
    fun restoreMode(modeValue: Int) {
        setThemeMode(Mode.fromValue(modeValue))
    }

    /** 从备份快照恢复配色方案（接收存储原始值，外部模块无需依赖 NiScheme）。 */
    fun restoreScheme(schemeOrdinal: Int) {
        setThemeScheme(NiScheme.entries.getOrElse(schemeOrdinal) { NiScheme.MISTY })
    }

    private fun loadThemeConfig(): ThemeConfig {
        val mode = Mode.fromValue(mmkv.decodeInt(KEY_THEME_MODE, Mode.SYSTEM.value))
        val scheme = NiScheme.entries.getOrElse(
            mmkv.decodeInt(KEY_THEME_SCHEME, 0),
        ) { NiScheme.MISTY }
        return ThemeConfig(mode, scheme)
    }
}

/** 主题配置快照。 */
data class ThemeConfig(
    val mode: ThemeSettings.Mode,
    val scheme: NiScheme = NiScheme.MISTY,
)
