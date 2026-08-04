package com.nichx.niplayer.datastore

import android.content.Context
import android.content.res.Configuration
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 应用语言设置持久化（MMKV + StateFlow）。
 *
 * 配置项：
 * - [languageMode]：语言模式（跟随系统 / 简体中文 / 英文），默认跟随系统
 *
 * 使用方式：
 * - 写入端：[com.nichx.niplayer.feature.home.settings.LanguageScreen] 调用 [setLanguageMode]
 * - 应用端：[com.nichx.niplayer.MainActivity.attachBaseContext] 调用 [wrap] 包裹 Context，
 *   切换语言后由调用方触发 Activity recreate() 即可全量刷新 UI 文案。
 */
object LanguageSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_LANGUAGE = "app_language"

    /** 语言模式枚举。 */
    enum class Mode(val value: Int) {
        SYSTEM(0),
        SIMPLIFIED_CHINESE(1),
        ENGLISH(2);

        companion object {
            fun fromValue(v: Int): Mode = entries.find { it.value == v } ?: SYSTEM
        }
    }

    private val _languageFlow = MutableStateFlow(loadLanguageMode())
    /** 语言配置 StateFlow，写入时自动更新。 */
    val languageFlow: StateFlow<Mode> = _languageFlow.asStateFlow()

    /** 当前语言模式。 */
    val languageMode: Mode
        get() = _languageFlow.value

    /** 设置语言模式，立即持久化并通知 StateFlow。 */
    fun setLanguageMode(mode: Mode) {
        mmkv.encode(KEY_LANGUAGE, mode.value)
        _languageFlow.value = mode
    }

    /**
     * 按当前语言配置包裹 Context，供 Activity.attachBaseContext 调用。
     *
     * [Mode.SYSTEM] 时透传原 Context，跟随系统 Locale。
     */
    fun wrap(context: Context): Context {
        val mode = _languageFlow.value
        if (mode == Mode.SYSTEM) return context
        val locale = when (mode) {
            Mode.SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            Mode.ENGLISH -> Locale.ENGLISH
            else -> return context
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    private fun loadLanguageMode(): Mode {
        return Mode.fromValue(mmkv.decodeInt(KEY_LANGUAGE, Mode.SYSTEM.value))
    }
}
