package com.nichx.niplayer.datastore

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用图标设置（MMKV + StateFlow）。
 *
 * 通过 activity-alias 在运行时切换桌面图标，无需重装：
 * 每个预设图标对应 Manifest 中一个 `MainActivity.<Suffix>` 的 launcher alias，
 * 切换时用 [PackageManager.setComponentEnabledSetting] 启用目标 alias 并禁用其余 alias。
 *
 * [suffix] 必须与 Manifest 中 activity-alias 的 `android:name` 后缀一致。
 */
object IconSettings {

    private const val ALIAS_PREFIX = "MainActivity."
    private const val KEY_ICON = "app_icon"

    /** 应用图标预设，[suffix] 与 Manifest 中 activity-alias 同名后缀对应。 */
    enum class AppIcon(val value: Int, val suffix: String) {
        DEFAULT(0, "Default"),
        CREAM(1, "Cream"),
        SAGE(2, "Sage"),
        STEEL(3, "Steel"),
        TEXT(4, "Text"),
        TEXT2(5, "Text2"),
        PLAY(6, "Play"),
        EMOJI(7, "Emoji");

        companion object {
            fun fromValue(v: Int): AppIcon = entries.find { it.value == v } ?: DEFAULT
        }
    }

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private val _iconFlow = MutableStateFlow(currentIcon)
    /** 图标 StateFlow，写入时自动更新。 */
    val iconFlow: StateFlow<AppIcon> = _iconFlow.asStateFlow()

    /** 当前所选图标。 */
    val icon: AppIcon
        get() = _iconFlow.value

    /** 写入并持久化当前图标（不切换组件，供备份恢复等场景）。 */
    fun store(icon: AppIcon) {
        mmkv.encode(KEY_ICON, icon.value)
        _iconFlow.value = icon
    }

    /**
     * 运行时切换桌面图标：启用 [icon] 对应 alias 并禁用其余，立即生效并持久化。
     */
    fun setIcon(context: Context, icon: AppIcon) {
        applyComponent(context, icon)
        store(icon)
    }

    /**
     * 将组件开关状态同步为当前设置（用于应用启动时校正，保证桌面图标一致）。
     * 不落库、不更新 StateFlow。
     */
    fun apply(context: Context) {
        applyComponent(context, _iconFlow.value)
    }

    private fun applyComponent(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        AppIcon.entries.forEach { candidate ->
            val target = if (candidate == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val component = ComponentName(context, context.packageName + ".$ALIAS_PREFIX${candidate.suffix}")
            if (pm.getComponentEnabledSetting(component) != target) {
                pm.setComponentEnabledSetting(component, target, PackageManager.DONT_KILL_APP)
            }
        }
    }

    private val currentIcon: AppIcon
        get() = AppIcon.fromValue(mmkv.decodeInt(KEY_ICON, AppIcon.DEFAULT.value))
}