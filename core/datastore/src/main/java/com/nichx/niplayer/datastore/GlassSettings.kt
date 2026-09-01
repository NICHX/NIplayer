package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 液态玻璃浮层不透明度设置持久化（MMKV + StateFlow）。
 *
 * 三类语义分别持久化，避免共用同一透明度导致某些浮层可读性失衡：
 * - [opacity]：**导航栏/多选操作栏**等底部薄浮层的底色不透明度（偏透、强调整体玻璃感）。
 * - [topBarOpacity]：**顶栏**的底色不透明度，与导航栏分开设置。
 * - [panelOpacity]：**对话框/菜单**等面板的底色不透明度（偏实、保证内容可读）。
 *
 * 使用方式：
 * - 写入端：主题设置页 [com.nichx.niplayer.feature.home.settings.ThemeScreen] 的玻璃不透明度滑条
 * - 读取端：MainActivity 根布局收集 [opacityFlow]/[topBarOpacityFlow]/[panelOpacityFlow]，
 *   经 [com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity]、
 *   [com.nichx.niplayer.designsystem.components.LocalNiGlassTopBarOpacity] 与
 *   [com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity] 下发给各玻璃组件
 */
object GlassSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_OPACITY = "glass_opacity"
    private const val KEY_TOP_BAR_OPACITY = "glass_top_bar_opacity"
    private const val KEY_PANEL_OPACITY = "glass_panel_opacity"

    /** 导航栏（底部薄浮层）默认不透明度（50%）。 */
    const val DEFAULT_OPACITY = 0.50f

    /** 薄浮层可调下限（10%）。 */
    const val MIN_OPACITY = 0.10f

    /** 薄浮层可调上限（100% 完全不透明）。 */
    const val MAX_OPACITY = 1.00f

    /** 顶栏默认不透明度（与导航栏默认一致）。 */
    const val DEFAULT_TOP_BAR_OPACITY = 0.50f

    /** 面板默认不透明度（对话框/菜单/下拉，50%）。 */
    const val DEFAULT_PANEL_OPACITY = 0.50f

    /** 面板可调下限（10%）。 */
    const val MIN_PANEL_OPACITY = 0.10f

    /** 面板可调上限（100% 完全不透明）。 */
    const val MAX_PANEL_OPACITY = 1.00f

    private val _opacityFlow = MutableStateFlow(loadOpacity())
    private val _topBarOpacityFlow = MutableStateFlow(loadTopBarOpacity())
    private val _panelOpacityFlow = MutableStateFlow(loadPanelOpacity())

    /** 导航栏（底部薄浮层）不透明度 StateFlow，写入时自动更新，供根布局 collect 下发。 */
    val opacityFlow: StateFlow<Float> = _opacityFlow.asStateFlow()

    /** 顶栏不透明度 StateFlow，写入时自动更新，供根布局 collect 下发。 */
    val topBarOpacityFlow: StateFlow<Float> = _topBarOpacityFlow.asStateFlow()

    /** 面板不透明度 StateFlow，写入时自动更新，供根布局 collect 下发。 */
    val panelOpacityFlow: StateFlow<Float> = _panelOpacityFlow.asStateFlow()

    /** 导航栏（底部薄浮层）当前不透明度。 */
    var opacity: Float
        get() = _opacityFlow.value
        set(value) {
            val v = value.coerceIn(MIN_OPACITY, MAX_OPACITY)
            mmkv.encode(KEY_OPACITY, v)
            _opacityFlow.value = v
        }

    /** 顶栏当前不透明度。 */
    var topBarOpacity: Float
        get() = _topBarOpacityFlow.value
        set(value) {
            val v = value.coerceIn(MIN_OPACITY, MAX_OPACITY)
            mmkv.encode(KEY_TOP_BAR_OPACITY, v)
            _topBarOpacityFlow.value = v
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

    private fun loadTopBarOpacity(): Float =
        mmkv.decodeFloat(KEY_TOP_BAR_OPACITY, DEFAULT_TOP_BAR_OPACITY)
            .coerceIn(MIN_OPACITY, MAX_OPACITY)

    private fun loadPanelOpacity(): Float =
        mmkv.decodeFloat(KEY_PANEL_OPACITY, DEFAULT_PANEL_OPACITY)
            .coerceIn(MIN_PANEL_OPACITY, MAX_PANEL_OPACITY)
}