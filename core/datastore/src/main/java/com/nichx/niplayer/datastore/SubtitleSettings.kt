package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 字幕设置持久化（MMKV）。
 *
 * 替代旧仓库 `SubtitleConfigTable`（@MMKVKotlinClass 注解生成），
 * v2 改用手动 MMKV 读写，避免编译期注解处理器的额外依赖。
 *
 * 配置项：
 * - [assrtToken]：ASSRT API token（用户在 assrt.net 注册获取，替代旧仓库误命名的 shooterSecret）
 * - [autoLoadSameNameSubtitle]：自动加载同文件夹同名字幕（默认 true）
 * - [subtitlePriority]：同名字幕加载优先级（如 "chs,cht"，逗号分隔，默认空表示按文件名排序）
 * - [textSizeFraction]：字幕字体大小（相对于视图高度的比例），默认 0.0533f
 * - [applyEmbeddedStyles]：是否应用内嵌样式（如字体颜色、斜体等），默认 true
 * - [fontFamilyKey]：字幕字体族 key（系统内置），默认 [FONT_FAMILY_KEY_DEFAULT]
 * - [fontColor]：字幕文字颜色（ARGB Int），默认白色
 * - [outlineWidth]：描边宽度（px，相对字号），默认 2f
 * - [outlineColor]：描边颜色（ARGB Int），默认黑色
 * - [bottomPaddingDp]：字幕底部边距（dp），默认 48
 *
 * 字幕样式应用规则：
 * - [applyEmbeddedStyles]=true 时，外挂字幕优先使用 ASS Style 自带的颜色/字体，但描边宽度、
 *   底部边距、字体族仍由用户设置覆盖（避免硬编码 2f/48.dp 导致不可调）
 * - [applyEmbeddedStyles]=false 时，文字颜色与描边颜色强制使用用户配置覆盖 ASS Style
 *
 * 本对象保持纯净（无 Compose 依赖），仅存原始值类型。
 * Compose Color / FontFamily 的转换由 UI 层（PlayerSettingsScreen、SubtitleOverlay）完成。
 *
 * 旧仓库命名 `shooterSecret` 让人误以为是射手网密钥，实际是 assrt.net 的 token，v2 纠正命名。
 * 旧仓库的 autoMatchSubtitle（射手网 hash 匹配）已失效，v2 不迁移。
 */
object SubtitleSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_ASSRT_TOKEN = "assrt_token"
    private const val KEY_AUTO_LOAD_SAME_NAME = "auto_load_same_name_subtitle"
    private const val KEY_SUBTITLE_PRIORITY = "subtitle_priority"
    private const val KEY_TEXT_SIZE_FRACTION = "subtitle_text_size_fraction"
    private const val KEY_APPLY_EMBEDDED_STYLES = "subtitle_apply_embedded_style"
    private const val KEY_FONT_FAMILY = "subtitle_font_family"
    private const val KEY_FONT_COLOR = "subtitle_font_color"
    private const val KEY_OUTLINE_WIDTH = "subtitle_outline_width"
    private const val KEY_OUTLINE_COLOR = "subtitle_outline_color"
    private const val KEY_BOTTOM_PADDING_DP = "subtitle_bottom_padding_dp"

    // ===== 字体族 key 常量（与 UI 层 FontFamily 映射对应） =====
    const val FONT_FAMILY_KEY_DEFAULT = "default"
    const val FONT_FAMILY_KEY_SERIF = "serif"
    const val FONT_FAMILY_KEY_MONOSPACE = "monospace"
    const val FONT_FAMILY_KEY_SANS_SERIF = "sans_serif"

    /** ASSRT API token。空字符串表示未配置，字幕搜索时会提示用户设置。 */
    var assrtToken: String
        get() = mmkv.decodeString(KEY_ASSRT_TOKEN, "") ?: ""
        set(value) { mmkv.encode(KEY_ASSRT_TOKEN, value) }

    /** 自动加载同文件夹同名字幕。默认 true。 */
    var autoLoadSameNameSubtitle: Boolean
        get() = mmkv.decodeBool(KEY_AUTO_LOAD_SAME_NAME, true)
        set(value) { mmkv.encode(KEY_AUTO_LOAD_SAME_NAME, value) }

    /** 同名字幕加载优先级（如 "chs,cht"，逗号分隔）。空表示按文件名排序。 */
    var subtitlePriority: String
        get() = mmkv.decodeString(KEY_SUBTITLE_PRIORITY, "") ?: ""
        set(value) { mmkv.encode(KEY_SUBTITLE_PRIORITY, value) }

    /** 字幕字体大小（相对于视图高度的比例）。默认 0.0533f（media3 默认值）。 */
    var textSizeFraction: Float
        get() = mmkv.decodeFloat(KEY_TEXT_SIZE_FRACTION, 0.0533f)
        set(value) { mmkv.encode(KEY_TEXT_SIZE_FRACTION, value) }

    /** 是否应用内嵌样式（字体颜色/斜体等）。默认 true。 */
    var applyEmbeddedStyles: Boolean
        get() = mmkv.decodeBool(KEY_APPLY_EMBEDDED_STYLES, true)
        set(value) { mmkv.encode(KEY_APPLY_EMBEDDED_STYLES, value) }

    /**
     * 字幕字体族 key。默认 [FONT_FAMILY_KEY_DEFAULT]。
     *
     * 取值：[FONT_FAMILY_KEY_DEFAULT]/[FONT_FAMILY_KEY_SERIF]/
     * [FONT_FAMILY_KEY_MONOSPACE]/[FONT_FAMILY_KEY_SANS_SERIF]。
     * UI 层将 key 映射为 Compose [androidx.compose.ui.text.font.FontFamily]。
     */
    var fontFamilyKey: String
        get() = mmkv.decodeString(KEY_FONT_FAMILY, FONT_FAMILY_KEY_DEFAULT) ?: FONT_FAMILY_KEY_DEFAULT
        set(value) { mmkv.encode(KEY_FONT_FAMILY, value) }

    /** 字幕文字颜色（ARGB Int）。默认白色 0xFFFFFFFF。 */
    var fontColor: Int
        get() = mmkv.decodeInt(KEY_FONT_COLOR, 0xFFFFFFFF.toInt())
        set(value) { mmkv.encode(KEY_FONT_COLOR, value) }

    /** 描边宽度（px，相对字号）。默认 2f。0 表示无描边。 */
    var outlineWidth: Float
        get() = mmkv.decodeFloat(KEY_OUTLINE_WIDTH, 2f)
        set(value) { mmkv.encode(KEY_OUTLINE_WIDTH, value) }

    /** 描边颜色（ARGB Int）。默认黑色 0xFF000000。 */
    var outlineColor: Int
        get() = mmkv.decodeInt(KEY_OUTLINE_COLOR, 0xFF000000.toInt())
        set(value) { mmkv.encode(KEY_OUTLINE_COLOR, value) }

    /** 字幕底部边距（dp）。默认 48。 */
    var bottomPaddingDp: Int
        get() = mmkv.decodeInt(KEY_BOTTOM_PADDING_DP, 48)
        set(value) { mmkv.encode(KEY_BOTTOM_PADDING_DP, value) }

    /** 字幕字体大小选项（供 UI 选择用）。 */
    val TEXT_SIZE_OPTIONS: List<Pair<String, Float>> = listOf(
        "小" to 0.04f,
        "中" to 0.0533f,
        "大" to 0.066f,
        "特大" to 0.08f,
    )

    /**
     * 字幕字体族选项（key + 显示名）。
     *
     * UI 层将 key 映射为 Compose FontFamily。仅提供系统内置字体族，无需打包字体文件。
     */
    val FONT_FAMILY_OPTIONS: List<Pair<String, String>> = listOf(
        "默认（无衬线）" to FONT_FAMILY_KEY_DEFAULT,
        "衬线" to FONT_FAMILY_KEY_SERIF,
        "等宽" to FONT_FAMILY_KEY_MONOSPACE,
        "紧凑无衬线" to FONT_FAMILY_KEY_SANS_SERIF,
    )

    /**
     * 字幕文字颜色选项（显示名 + ARGB Int）。
     *
     * 提供常用字幕色：白色（默认）、黄色（影视常用）、青色、浅灰、黑色。
     */
    val FONT_COLOR_OPTIONS: List<Pair<String, Int>> = listOf(
        "白色" to 0xFFFFFFFF.toInt(),
        "黄色" to 0xFFFFEB3B.toInt(),
        "青色" to 0xFF00E5FF.toInt(),
        "浅灰" to 0xFFE0E0E0.toInt(),
        "黑色" to 0xFF000000.toInt(),
    )

    /**
     * 描边宽度选项（px，相对字号）。
     *
     * "无"=0f（关闭描边）、"细"=1f、"中"=2f（默认）、"粗"=4f。
     */
    val OUTLINE_WIDTH_OPTIONS: List<Pair<String, Float>> = listOf(
        "无" to 0f,
        "细" to 1f,
        "中" to 2f,
        "粗" to 4f,
    )

    /**
     * 描边颜色选项（显示名 + ARGB Int）。
     *
     * 黑色（默认）、白色、深灰、红色。颜色对比强烈的描边在浅色/深色背景下都可读。
     */
    val OUTLINE_COLOR_OPTIONS: List<Pair<String, Int>> = listOf(
        "黑色" to 0xFF000000.toInt(),
        "白色" to 0xFFFFFFFF.toInt(),
        "深灰" to 0xFF424242.toInt(),
        "红色" to 0xFFD32F2F.toInt(),
    )

    /**
     * 底部边距选项（dp）。
     *
     * "近"=24、"中"=48（默认，贴近屏幕底部）、"远"=96（避开控制条与底部安全区）。
     */
    val BOTTOM_PADDING_OPTIONS: List<Pair<String, Int>> = listOf(
        "近" to 24,
        "中" to 48,
        "远" to 96,
    )
}
