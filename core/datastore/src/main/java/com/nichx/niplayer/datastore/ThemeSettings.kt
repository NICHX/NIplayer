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
 * - [themeSchemeKey]：配色方案稳定 key，默认 [DEFAULT_SCHEME_KEY]（雾蓝）
 *
 * **A1 架构修复（2026-09-21）**：本模块不持有 `:core:designsystem` 的 `NiScheme` 类型
 * （避免数据层依赖 UI 层的依赖倒置），只持久化 / 暴露原始值；UI 侧在边界处用
 * `NiScheme.fromKey(key)` 还原为枚举。
 *
 * **存储格式变更（2026-09-22）**：原先存 `NiScheme` 的 ordinal。配色从 18 套精简到 12 套后
 * 序号会整体前移，若继续存序号会让老用户主题静默错位，故改为存**稳定 key 字符串**；
 * MMKV 里遗留的旧 ordinal 在首次读取时经 [ThemeSchemeMigration] 一次性换算为 key
 * 并清除旧键，用户原来的选择保持不变。
 *
 * 使用方式：
 * - 写入端：:feature:home 的 ThemeScreen 调用 [setThemeMode] / [setThemeScheme]
 * - 读取端：MainActivity / PlayerActivity collectAsState [themeFlow]，
 *   经 `NiScheme.fromKey` 还原后传入 designsystem 的 NiTheme 应用主题
 */
object ThemeSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    /** 默认配色方案 key，与 `:core:designsystem` 的 `NiScheme.DEFAULT.key` 一致。 */
    const val DEFAULT_SCHEME_KEY = "MISTY"

    private const val KEY_THEME_MODE = "theme_mode"

    /** 当前存储键：配色方案稳定 key（String）。 */
    private const val KEY_THEME_SCHEME = "theme_scheme_key"

    /** 旧版存储键：配色方案 ordinal（Int）。只在迁移时读取，迁完即删除。 */
    private const val KEY_THEME_SCHEME_LEGACY = "theme_scheme"

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

    /** 当前配色方案稳定 key（存储原始值，外部模块无需依赖 NiScheme）。 */
    val themeSchemeKey: String
        get() = _themeFlow.value.schemeKey

    /** 设置主题模式，立即持久化并通知 StateFlow。 */
    fun setThemeMode(mode: Mode) {
        mmkv.encode(KEY_THEME_MODE, mode.value)
        _themeFlow.value = _themeFlow.value.copy(mode = mode)
    }

    /** 设置配色方案 key，立即持久化并通知 StateFlow。空白值按默认方案处理。 */
    fun setThemeScheme(schemeKey: String) {
        val safe = schemeKey.ifBlank { DEFAULT_SCHEME_KEY }
        mmkv.encode(KEY_THEME_SCHEME, safe)
        _themeFlow.value = _themeFlow.value.copy(schemeKey = safe)
    }

    /** 从备份快照恢复主题模式（接收存储原始值，外部模块无需依赖 Mode 枚举）。 */
    fun restoreMode(modeValue: Int) {
        setThemeMode(Mode.fromValue(modeValue))
    }

    /** 从备份快照恢复配色方案（接收稳定 key）。 */
    fun restoreScheme(schemeKey: String) {
        setThemeScheme(schemeKey)
    }

    /**
     * 从**旧版备份快照**恢复配色方案。
     *
     * 旧备份（本改动之前导出）里 `themeScheme` 存的是 ordinal，需先换算为 key。
     */
    fun restoreLegacyScheme(schemeOrdinal: Int) {
        setThemeScheme(ThemeSchemeMigration.keyFromLegacyOrdinal(schemeOrdinal))
    }

    private fun loadThemeConfig(): ThemeConfig {
        val mode = Mode.fromValue(mmkv.decodeInt(KEY_THEME_MODE, Mode.SYSTEM.value))
        return ThemeConfig(mode, loadSchemeKey())
    }

    /**
     * 读取配色方案 key，并在必要时执行一次 ordinal → key 迁移。
     *
     * 三条路径由 [ThemeSchemeMigration.resolveKey] 判定：新 key 已存在则直接用；
     * 只有旧 ordinal 则换算后写回新键、删除旧键（仅此一次）；两者都无（全新安装）
     * 返回默认值且**不写盘**，避免凭空创建记录。
     */
    private fun loadSchemeKey(): String {
        val legacyOrdinal = if (mmkv.containsKey(KEY_THEME_SCHEME_LEGACY)) {
            mmkv.decodeInt(KEY_THEME_SCHEME_LEGACY, 0)
        } else {
            null
        }
        val resolved = ThemeSchemeMigration.resolveKey(
            storedKey = mmkv.decodeString(KEY_THEME_SCHEME),
            legacyOrdinal = legacyOrdinal,
        )
        if (legacyOrdinal != null) {
            mmkv.encode(KEY_THEME_SCHEME, resolved)
            mmkv.removeValueForKey(KEY_THEME_SCHEME_LEGACY)
        }
        return resolved
    }
}

/** 主题配置快照。 */
data class ThemeConfig(
    val mode: ThemeSettings.Mode,
    /** 配色方案稳定 key；UI 侧用 `NiScheme.fromKey` 还原为枚举。 */
    val schemeKey: String = ThemeSettings.DEFAULT_SCHEME_KEY,
)
