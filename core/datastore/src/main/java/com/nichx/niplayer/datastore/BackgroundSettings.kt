package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 自定义背景设置持久化（MMKV + StateFlow）。
 *
 * 配置项：
 * - [imagePath]：自定义背景图片的**本地文件绝对路径**（选取后复制到应用私有目录，避免 SAF
 *   临时授权失效）；为空表示未设置自定义背景，界面回退主题背景色。
 * - [opacity]：背景图片**不透明度**（0..1），越高越清晰、越低越淡。数值语义与
 *   [GlassSettings] 保持一致。
 *
 * 使用方式：
 * - 写入端：主题设置页 [com.nichx.niplayer.feature.home.settings.ThemeScreen] 选图 / 移除 / 滑条
 * - 读取端：MainActivity 根布局收集 [imagePathFlow] / [opacityFlow]，绘制全局背景图，
 *   并经 [com.nichx.niplayer.designsystem.components.LocalNiCustomBackground] 下发，
 *   供页面骨架透出背景图与预览组件渲染
 */
object BackgroundSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_IMAGE_PATH = "background_image_path"
    private const val KEY_OPACITY = "background_opacity"
    private const val KEY_CARD_OPACITY = "background_card_opacity"

    /** 背景图片默认不透明度（50%）。 */
    const val DEFAULT_OPACITY = 0.50f

    /** 可调下限（完全透明，等同于不显示背景）。 */
    const val MIN_OPACITY = 0.00f

    /** 可调上限（完全不透明）。 */
    const val MAX_OPACITY = 1.00f

    /** 卡片表面默认不透明度（66%，与开启自定义背景后的默认观感一致）。 */
    const val DEFAULT_CARD_OPACITY = 0.66f

    /** 卡片表面可调下限（越透明背景透出越明显）。 */
    const val MIN_CARD_OPACITY = 0.30f

    /** 卡片表面可调上限（1.0 = 完全不透明，等同于回到不透明卡片）。 */
    const val MAX_CARD_OPACITY = 1.00f

    private val _imagePathFlow = MutableStateFlow(loadImagePath())
    private val _opacityFlow = MutableStateFlow(loadOpacity())
    private val _cardOpacityFlow = MutableStateFlow(loadCardOpacity())

    /** 自定义背景图片路径 StateFlow（null 表示未设置），写入时自动更新。 */
    val imagePathFlow: StateFlow<String?> = _imagePathFlow.asStateFlow()

    /** 背景图片不透明度 StateFlow，写入时自动更新。 */
    val opacityFlow: StateFlow<Float> = _opacityFlow.asStateFlow()

    /** 卡片表面不透明度 StateFlow，写入时自动更新。 */
    val cardOpacityFlow: StateFlow<Float> = _cardOpacityFlow.asStateFlow()

    /** 当前自定义背景图片路径；为空表示未设置。 */
    var imagePath: String?
        get() = _imagePathFlow.value
        set(value) {
            val safe = value?.takeIf { it.isNotBlank() }
            if (safe == null) {
                mmkv.removeValueForKey(KEY_IMAGE_PATH)
            } else {
                mmkv.encode(KEY_IMAGE_PATH, safe)
            }
            _imagePathFlow.value = safe
        }

    /** 当前背景图片不透明度。 */
    var opacity: Float
        get() = _opacityFlow.value
        set(value) {
            val v = value.coerceIn(MIN_OPACITY, MAX_OPACITY)
            mmkv.encode(KEY_OPACITY, v)
            _opacityFlow.value = v
        }

    /** 当前卡片表面不透明度。 */
    var cardOpacity: Float
        get() = _cardOpacityFlow.value
        set(value) {
            val v = value.coerceIn(MIN_CARD_OPACITY, MAX_CARD_OPACITY)
            mmkv.encode(KEY_CARD_OPACITY, v)
            _cardOpacityFlow.value = v
        }

    /**
     * 读取背景图片路径。若文件已不存在（被清理/迁移丢失），返回 null 使界面回退主题背景色。
     */
    private fun loadImagePath(): String? =
        mmkv.decodeString(KEY_IMAGE_PATH)?.takeIf { it.isNotBlank() && File(it).exists() }

    private fun loadOpacity(): Float =
        mmkv.decodeFloat(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)

    private fun loadCardOpacity(): Float =
        mmkv.decodeFloat(KEY_CARD_OPACITY, DEFAULT_CARD_OPACITY).coerceIn(MIN_CARD_OPACITY, MAX_CARD_OPACITY)
}
