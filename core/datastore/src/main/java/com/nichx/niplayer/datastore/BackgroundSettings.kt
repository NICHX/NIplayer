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
 * - [recentImagesFlow]：**最近使用**过的背景图片路径列表（最近在前），用于在选择界面
 *   快速切换，无需再次选图 / 裁剪。换图时旧图不再删除，而是保留在最近列表中；
 *   超出 [MAX_RECENT_IMAGES] 时淘汰最旧的一张并删除其文件。
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
    private const val KEY_RECENT_IMAGES = "background_recent_images"

    /** 最近使用图片列表的持久化分隔符（本地路径不含换行，安全）。 */
    private const val RECENT_SEPARATOR = "\n"

    /** 最近使用图片保留上限；超出后淘汰最旧的一张并删除其文件。 */
    const val MAX_RECENT_IMAGES = 8

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
    private val _recentImagesFlow = MutableStateFlow(loadRecentImages())

    /** 自定义背景图片路径 StateFlow（null 表示未设置），写入时自动更新。 */
    val imagePathFlow: StateFlow<String?> = _imagePathFlow.asStateFlow()

    /** 背景图片不透明度 StateFlow，写入时自动更新。 */
    val opacityFlow: StateFlow<Float> = _opacityFlow.asStateFlow()

    /** 卡片表面不透明度 StateFlow，写入时自动更新。 */
    val cardOpacityFlow: StateFlow<Float> = _cardOpacityFlow.asStateFlow()

    /** 最近使用过的背景图片路径列表 StateFlow（最近在前），写入时自动更新。 */
    val recentImagesFlow: StateFlow<List<String>> = _recentImagesFlow.asStateFlow()

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
     * 应用一张背景图：设为当前背景，并把路径提到「最近使用」列表最前（去重）。
     * 超出 [MAX_RECENT_IMAGES] 时淘汰最旧的一张并删除其文件；旧背景文件保留以便回选。
     */
    fun applyImage(path: String) {
        val safe = path.takeIf { it.isNotBlank() && File(it).exists() } ?: return
        imagePath = safe
        val merged = buildList {
            add(safe)
            _recentImagesFlow.value.forEach { if (it != safe) add(it) }
        }
        merged.drop(MAX_RECENT_IMAGES).forEach { runCatching { File(it).delete() } }
        persistRecent(merged.take(MAX_RECENT_IMAGES))
    }

    /**
     * 从「最近使用」中移除一张图片并删除其文件；若它正是当前背景，则一并清除当前背景
     * （界面回退主题背景色）。
     */
    fun removeRecent(path: String) {
        persistRecent(_recentImagesFlow.value.filterNot { it == path })
        runCatching { File(path).delete() }
        if (imagePath == path) imagePath = null
    }

    /**
     * 重置背景设置：清除当前背景与全部「最近使用」记录，删除相关图片文件，
     * 并把背景 / 卡片不透明度恢复为默认值。
     */
    fun reset() {
        val files = (_recentImagesFlow.value + listOfNotNull(imagePath)).distinct()
        persistRecent(emptyList())
        imagePath = null
        files.forEach { runCatching { File(it).delete() } }
        opacity = DEFAULT_OPACITY
        cardOpacity = DEFAULT_CARD_OPACITY
    }

    /** 持久化最近列表（过滤已不存在的文件）并同步 StateFlow。 */
    private fun persistRecent(list: List<String>) {
        val safe = list.filter { it.isNotBlank() && File(it).exists() }.distinct()
        if (safe.isEmpty()) {
            mmkv.removeValueForKey(KEY_RECENT_IMAGES)
        } else {
            mmkv.encode(KEY_RECENT_IMAGES, safe.joinToString(RECENT_SEPARATOR))
        }
        _recentImagesFlow.value = safe
    }

    /**
     * 读取背景图片路径。若文件已不存在（被清理/迁移丢失），返回 null 使界面回退主题背景色。
     */
    private fun loadImagePath(): String? =
        mmkv.decodeString(KEY_IMAGE_PATH)?.takeIf { it.isNotBlank() && File(it).exists() }

    /**
     * 读取最近使用图片列表（过滤已不存在的文件、去重、限制上限）。
     * 兼容旧版本：仅设置了当前背景、尚无最近列表时，把当前背景补到最前。
     */
    private fun loadRecentImages(): List<String> {
        val stored = mmkv.decodeString(KEY_RECENT_IMAGES).orEmpty()
            .split(RECENT_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() && File(it).exists() }
            .distinct()
            .take(MAX_RECENT_IMAGES)
        val current = loadImagePath()
        return if (current != null && current !in stored) listOf(current) + stored else stored
    }

    private fun loadOpacity(): Float =
        mmkv.decodeFloat(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)

    private fun loadCardOpacity(): Float =
        mmkv.decodeFloat(KEY_CARD_OPACITY, DEFAULT_CARD_OPACITY).coerceIn(MIN_CARD_OPACITY, MAX_CARD_OPACITY)
}
