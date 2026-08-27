package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 液态玻璃浮层不透明度设置持久化（MMKV + StateFlow）。
 *
 * 分为两类语义，分别持久化，避免共用同一透明度导致某些浮层可读性失衡：
 * - [opacity]：**导航栏/顶栏/多选操作栏**等薄浮层的底色不透明度（偏透、强调整体玻璃感）。
 * - [panelOpacity]：**对话框/菜单**等面板的底色不透明度（偏实、保证内容可读）。
 *
 * 使用方式：
 * - 写入端：设置页"玻璃不透明度"滑条 [com.nichx.niplayer.feature.home.settings.GlassSettingsScreen]
 * - 读取端：MainActivity 根布局收集 [opacityFlow]/[panelOpacityFlow]，
 *   经 [com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity] 与
 *   [com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity] 下发给各玻璃组件
 */
object GlassSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_OPACITY = "glass_opacity"
    private const val KEY_PANEL_OPACITY = "glass_panel_opacity"

    /** 薄浮层默认不透明度。 */
    const val DEFAULT_OPACITY = 0.62f

    /** 薄浮层可调下限。 */
    const val MIN_OPACITY = 0.20f

    /** 薄浮层可调上限。 */
    const val MAX_OPACITY = 0.85f

    /** 面板默认不透明度（对话框/菜单，偏实以保证可读）。 */
    const val DEFAULT_PANEL_OPACITY = 0.82f

    /** 面板可调下限。 */
    const val MIN_PANEL_OPACITY = 0.50f

    /** 面板可调上限。 */
    const val MAX_PANEL_OPACITY = 0.95f

    private val _opacityFlow = MutableStateFlow(loadOpacity())
    private val _panelOpacityFlow = MutableStateFlow(loadPanelOpacity())

    /** 薄浮层不透明度 StateFlow，写入时自动更新，供根布局 collect 下发。 */
    val opacityFlow: StateFlow<Float> = _opacityFlow.asStateFlow()

    /** 面板不透明度 StateFlow，写入时自动更新，供根布局 collect 下发。 */
    val panelOpacityFlow: StateFlow<Float> = _panelOpacityFlow.asStateFlow()

    /** 薄浮层当前不透明度。 */
    var opacity: Float
        get() = _opacityFlow.value
        set(value) {
            val v = value.coerceIn(MIN_OPACITY, MAX_OPACITY)
            mmkv.encode(KEY_OPACITY, v)
            _opacityFlow.value = v
        }

    /** 面板当前不透明度。 */
    var panelOpacity: Float
        get() = _panelOpacityFlow.value
        set(value) {
            val v = value.coerceIn(MIN_PANEL_OPACITY, MAX_PANEL_OPACITY)
            mmkv.encode(KEY_PANEL_OPACITY, v)
            _panelOpacityFlow.value = v
        }

    private fun loadOpacity(): Float =
        mmkv.decodeFloat(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)

    private fun loadPanelOpacity(): Float =
        mmkv.decodeFloat(KEY_PANEL_OPACITY, DEFAULT_PANEL_OPACITY)
            .coerceIn(MIN_PANEL_OPACITY, MAX_PANEL_OPACITY)
}