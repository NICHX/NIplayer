package com.nichx.niplayer.subtitle.renderer

import com.nichx.niplayer.subtitle.info.Caption
import com.nichx.niplayer.subtitle.info.Style
import com.nichx.niplayer.subtitle.info.TimedTextObject

/**
 * ASS override tags 解析结果（一条 Dialogue 的所有 override 指令）。
 *
 * 由 [AssOverrideParser.parse] 生成，[SubtitleEngine] 在每帧查询时根据当前时间
 * 应用动画（fad/move/transform）生成最终的 [RenderableCaption]。
 *
 * @property rawSpans 已拆分的带样式 span（不含时间动画）
 * @property align 屏幕对齐（来自 \a 或 Style）
 * @property pos 屏幕位置（来自 \pos，归一化 0..1，null 表示按 align 自动布局）
 * @property fade 渐入渐出（来自 \fad，null 表示无淡入淡出）
 * @property move 移动动画（来自 \move，null 表示无移动）
 * @property transforms \t 时间插值动画列表（来自 \t，按出现顺序）
 * @property rotationZ Z 轴旋转角度（度，来自 \frz）
 * @property style 所属 Style（提供默认值）
 * @property startMs 字幕开始时间（ms）
 * @property endMs 字幕结束时间（ms）
 */
data class ParsedCaption(
    val rawSpans: List<StyledSpan>,
    val align: SubtitleAlign,
    val pos: Pair<Float, Float>?,
    val fade: FadeAnimation?,
    val move: MoveAnimation?,
    val transforms: List<TransformAnimation>,
    val rotationZ: Float,
    val style: Style,
    val startMs: Long,
    val endMs: Long,
)

/** \fad(inMs, outMs) 淡入淡出。 */
data class FadeAnimation(
    val inMs: Long,
    val outMs: Long,
)

/** \move(x1, y1, x2, y2, t1, t2) 移动动画（归一化 0..1 坐标，t1/t2 相对字幕开始 ms）。 */
data class MoveAnimation(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val t1: Long,
    val t2: Long,
)

/**
 * ASS override tags 解析器。
 *
 * 从 [Caption.rawContent]（保留所有 `{...}` tag 的原始文本）解析出 [ParsedCaption]。
 *
 * 解析策略：遍历 raw 字符串，遇到 `{` 时调用 [scanTagsInBlock] 扫描块内所有 `\tag`，
 * 遇到 `{}` 外的 `\N`/`\n` 时输出换行 span，其他字符累积到文本缓冲并在样式变化时 flush。
 *
 * Phase 1 支持的 override tags：
 * - `\b` `\i` `\u` `\s` — 粗体/斜体/下划线/删除线
 * - `\fs<N>` — 字体大小
 * - `\fn<name>` — 字体名
 * - `\c` `\1c` — 主色（AABBGGRR 或 BBGGRR）
 * - `\3c` — 边框色
 * - `\4c` — 阴影色
 * - `\1a` `\3a` `\4a` — 透明度（&HAA&）
 * - `\pos(x,y)` — 屏幕定位（按 PlayResX/Y 归一化）
 * - `\fad(in,out)` — 淡入淡出
 * - `\move(x1,y1,x2,y2,t1,t2)` — 移动动画
 * - `\a<N>` — 对齐覆盖
 * - `\N` `\n` — 硬/软换行
 *
 * 不支持（Phase 3+）：`\t` 动画、`\frz` 旋转、`\p` 矢量绘制、`\clip` 裁剪。
 */
object AssOverrideParser {

    /** 换行 span 的文本标记（与普通文本区分）。 */
    const val NEWLINE = "\n"

    /**
     * 解析一条 [Caption] 的 [Caption.rawContent]。
     *
     * @param caption 待解析的字幕条目
     * @param tto 所属 TimedTextObject（提供 PlayResX/PlayResY 用于 \pos/\move 归一化）
     * @return 解析结果；若 rawContent 为空返回仅含空文本的 ParsedCaption
     */
    fun parse(caption: Caption, tto: TimedTextObject): ParsedCaption {
        val style = caption.style ?: Style("Default")
        val initialAlign = parseAlignFromStyle(style)
        val playResX = tto.playResX.takeIf { it > 0f } ?: 384f
        val playResY = tto.playResY.takeIf { it > 0f } ?: 288f

        var currentAlign = initialAlign
        var currentPos: Pair<Float, Float>? = null
        var fade: FadeAnimation? = null
        var move: MoveAnimation? = null
        val transforms = mutableListOf<TransformAnimation>()
        var currentRotationZ: Float = 0f

        // 当前 span 累积的样式覆盖（null 表示继承 Style）
        var sBold: Boolean? = parseBool(style.bold)
        var sItalic: Boolean? = parseBool(style.italic)
        var sUnderline: Boolean? = parseBool(style.underline)
        var sStrikeout: Boolean? = null
        var sFontSize: Float? = style.fontSize?.toFloatOrNull()
        var sFontName: String? = style.font?.takeIf { it.isNotBlank() }
        var sPrimary: SubtitleColor? = parseStyleColor(style.color)
        var sOutline: SubtitleColor? = parseStyleColor(style.backgroundColor)
        var sBack: SubtitleColor? = null
        var sOutlineWidth: Float? = null
        var sShadowDepth: Float? = null

        val spans = mutableListOf<StyledSpan>()
        val textBuffer = StringBuilder()

        fun flushText() {
            if (textBuffer.isNotEmpty()) {
                spans.add(
                    StyledSpan(
                        text = textBuffer.toString(),
                        bold = sBold,
                        italic = sItalic,
                        underline = sUnderline,
                        strikeout = sStrikeout,
                        fontSize = sFontSize,
                        fontName = sFontName,
                        primaryColor = sPrimary,
                        outlineColor = sOutline,
                        backColor = sBack,
                        outlineWidth = sOutlineWidth,
                        shadowDepth = sShadowDepth,
                        rotationZ = currentRotationZ.takeIf { it != 0f },
                    )
                )
                textBuffer.clear()
            }
        }

        val raw = caption.rawContent ?: ""
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when {
                ch == '{' -> {
                    val end = raw.indexOf('}', i + 1)
                    if (end < 0) {
                        // 未闭合的 {，当普通字符处理
                        textBuffer.append(ch)
                        i += 1
                        continue
                    }
                    val block = raw.substring(i + 1, end)
                    val r = scanTagsInBlock(block, playResX, playResY)
                    // 应用样式覆盖（非 null 字段覆盖当前值）
                    r.bold?.let { sBold = it }
                    r.italic?.let { sItalic = it }
                    r.underline?.let { sUnderline = it }
                    r.strikeout?.let { sStrikeout = it }
                    r.fontSize?.let { sFontSize = it }
                    r.fontName?.let { sFontName = it }
                    r.primary?.let { sPrimary = it }
                    r.outline?.let { sOutline = it }
                    r.back?.let { sBack = it }
                    r.outlineWidth?.let { sOutlineWidth = it }
                    r.shadowDepth?.let { sShadowDepth = it }
                    r.rotationZ?.let { currentRotationZ = it }
                    r.pos?.let { currentPos = it }
                    r.align?.let { currentAlign = it }
                    r.fade?.let { fade = it }
                    r.move?.let { move = it }
                    if (r.transform != null) transforms.add(r.transform)
                    i = end + 1
                }
                ch == '\\' && i + 1 < raw.length -> {
                    // {} 外的 \N / \n 换行（罕见但兼容）
                    val next = raw[i + 1]
                    if (next == 'N' || next == 'n') {
                        flushText()
                        spans.add(StyledSpan(text = NEWLINE))
                        i += 2
                    } else {
                        textBuffer.append(ch)
                        i += 1
                    }
                }
                else -> {
                    textBuffer.append(ch)
                    i += 1
                }
            }
        }
        flushText()

        return ParsedCaption(
            rawSpans = spans,
            align = currentAlign,
            pos = currentPos,
            fade = fade,
            move = move,
            transforms = transforms,
            rotationZ = currentRotationZ,
            style = style,
            startMs = caption.start?.mseconds ?: 0L,
            endMs = caption.end?.mseconds ?: 0L,
        )
    }

    /** 单个 `{...}` 块的扫描结果。null 字段表示该块未指定此样式。 */
    private data class BlockScan(
        val bold: Boolean?,
        val italic: Boolean?,
        val underline: Boolean?,
        val strikeout: Boolean?,
        val fontSize: Float?,
        val fontName: String?,
        val primary: SubtitleColor?,
        val outline: SubtitleColor?,
        val back: SubtitleColor?,
        val outlineWidth: Float?,
        val shadowDepth: Float?,
        val rotationZ: Float?,
        val pos: Pair<Float, Float>?,
        val align: SubtitleAlign?,
        val fade: FadeAnimation?,
        val move: MoveAnimation?,
        val transform: TransformAnimation?,
    )

    /** 扫描 `{...}` 块内容（不含大括号），返回所有 tag 的覆盖结果。 */
    private fun scanTagsInBlock(
        block: String,
        playResX: Float,
        playResY: Float,
    ): BlockScan {
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline: Boolean? = null
        var strikeout: Boolean? = null
        var fontSize: Float? = null
        var fontName: String? = null
        var primary: SubtitleColor? = null
        var outline: SubtitleColor? = null
        var back: SubtitleColor? = null
        var outlineWidth: Float? = null
        var shadowDepth: Float? = null
        var rotationZ: Float? = null
        var pos: Pair<Float, Float>? = null
        var align: SubtitleAlign? = null
        var fade: FadeAnimation? = null
        var move: MoveAnimation? = null
        var transform: TransformAnimation? = null

        // 拆分 override 块，但 \t(...) 内的嵌套 \ tag 不能被拆开
        // （朴素 split('\\') 会把 \t(0,1000,\fs40) 拆成 "t(0,1000," 与 "fs40)"，丢失 transform 目标）
        // 第一个元素是块前缀文本（通常为空），跳过
        val tags = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < block.length) {
            val ch = block[i]
            if (ch == '\\') {
                // 提交前一个普通片段
                if (sb.isNotEmpty()) {
                    tags.add(sb.toString())
                    sb.clear()
                }
                // 读取 tag 名直到 ( 或下一个 \ 或末尾
                val tagStart = i + 1
                var j = tagStart
                while (j < block.length && block[j] != '\\' && block[j] != '(') {
                    j++
                }
                if (j < block.length && block[j] == '(') {
                    // 带括号的 tag（如 t(...)、pos(...)、fad(...)），整体提取到闭合 )
                    var parenDepth = 1
                    val tagFull = StringBuilder()
                    tagFull.append(block, tagStart, j) // tag 名（不含 (）
                    tagFull.append('(')
                    j++ // 跳过 (
                    while (j < block.length && parenDepth > 0) {
                        val c = block[j]
                        when (c) {
                            '(' -> parenDepth++
                            ')' -> parenDepth--
                        }
                        tagFull.append(c)
                        j++
                    }
                    tags.add(tagFull.toString())
                    i = j
                } else {
                    // 普通 tag（无括号），追加 tagStart..j 的内容
                    sb.append(block, tagStart, j)
                    i = j
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        if (sb.isNotEmpty()) tags.add(sb.toString())
        for (tagRaw in tags) {
            if (tagRaw.isEmpty()) continue
            // tag 形如 "b1" "fs24" "c&H00FFFFFF&" "pos(320,460)" "t(0,1000,\fs40)"
            val tagBody = tagRaw.trim()
            when {
                tagBody.startsWith("bord") -> {
                    outlineWidth = tagBody.substring(4).toFloatOrNull()
                }
                tagBody.startsWith("shad") -> {
                    shadowDepth = tagBody.substring(4).toFloatOrNull()
                }
                tagBody.startsWith("frz") -> {
                    rotationZ = tagBody.substring(3).toFloatOrNull() ?: 0f
                }
                tagBody.startsWith("fr") && tagBody.length > 2 &&
                    !tagBody.startsWith("frx") && !tagBody.startsWith("fry") -> {
                    // \fr<angle> 等价于 \frz
                    rotationZ = tagBody.substring(2).toFloatOrNull() ?: 0f
                }
                tagBody.startsWith("b") && tagBody.length > 1 -> {
                    bold = parseBoolTag(tagBody.substring(1))
                }
                tagBody.startsWith("i") && tagBody.length > 1 -> {
                    italic = parseBoolTag(tagBody.substring(1))
                }
                tagBody.startsWith("u") && tagBody.length > 1 -> {
                    underline = parseBoolTag(tagBody.substring(1))
                }
                tagBody.startsWith("s") && tagBody.length > 1 -> {
                    strikeout = parseBoolTag(tagBody.substring(1))
                }
                tagBody.startsWith("fs") -> {
                    fontSize = tagBody.substring(2).toFloatOrNull()
                }
                tagBody.startsWith("fn") -> {
                    val name = tagBody.substring(2)
                    if (name.isNotEmpty()) fontName = name
                }
                tagBody.startsWith("1c") -> {
                    primary = parseAssColor(tagBody.substring(2))
                }
                tagBody.startsWith("1a") -> {
                    val a = parseAssAlpha(tagBody.substring(2))
                    primary = SubtitleColor(primary?.r ?: 1f, primary?.g ?: 1f, primary?.b ?: 1f, a)
                }
                tagBody.startsWith("3c") -> {
                    outline = parseAssColor(tagBody.substring(2))
                }
                tagBody.startsWith("3a") -> {
                    val a = parseAssAlpha(tagBody.substring(2))
                    outline = SubtitleColor(outline?.r ?: 0f, outline?.g ?: 0f, outline?.b ?: 0f, a)
                }
                tagBody.startsWith("4c") -> {
                    back = parseAssColor(tagBody.substring(2))
                }
                tagBody.startsWith("4a") -> {
                    val a = parseAssAlpha(tagBody.substring(2))
                    back = SubtitleColor(back?.r ?: 0f, back?.g ?: 0f, back?.b ?: 0f, a)
                }
                tagBody == "c" || tagBody.startsWith("c&") || tagBody.startsWith("c") -> {
                    val value = tagBody.substring(1)
                    if (value.isNotEmpty()) {
                        parseAssColor(value)?.let { primary = it }
                    }
                }
                tagBody.startsWith("pos(") -> {
                    val params = extractParenParams(tagBody, 4)
                    if (params.size >= 2) {
                        val x = params[0].toFloatOrNull() ?: 0f
                        val y = params[1].toFloatOrNull() ?: 0f
                        pos = (x / playResX) to (y / playResY)
                    }
                }
                tagBody.startsWith("move(") -> {
                    val params = extractParenParams(tagBody, 5)
                    if (params.size >= 6) {
                        move = MoveAnimation(
                            x1 = (params[0].toFloatOrNull() ?: 0f) / playResX,
                            y1 = (params[1].toFloatOrNull() ?: 0f) / playResY,
                            x2 = (params[2].toFloatOrNull() ?: 0f) / playResX,
                            y2 = (params[3].toFloatOrNull() ?: 0f) / playResY,
                            t1 = params[4].toLongOrNull() ?: 0L,
                            t2 = params[5].toLongOrNull() ?: 0L,
                        )
                    }
                }
                tagBody.startsWith("fad(") -> {
                    val params = extractParenParams(tagBody, 4)
                    if (params.size >= 2) {
                        fade = FadeAnimation(
                            inMs = params[0].toLongOrNull() ?: 0L,
                            outMs = params[1].toLongOrNull() ?: 0L,
                        )
                    }
                }
                tagBody.startsWith("t(") -> {
                    transform = parseTransformTag(tagBody.substring(2))
                }
                tagBody.startsWith("a") && tagBody.length > 1 -> {
                    tagBody.substring(1).toIntOrNull()?.let { align = SubtitleAlign.fromAss(it) }
                }
                tagBody == "N" || tagBody == "n" -> Unit
                else -> Unit
            }
        }

        return BlockScan(
            bold, italic, underline, strikeout, fontSize, fontName,
            primary, outline, back, outlineWidth, shadowDepth, rotationZ,
            pos, align, fade, move, transform,
        )
    }

    /**
     * 解析 \t(t1,t2,\tags) 或 \t(\tags) 形式的动画 tag。
     *
     * - \t(t1,t2,\tag1\tag2...) — t1..t2 时间内插值
     * - \t(t,\tags) — 等价于 \t(0,t,\tags)
     * - \t(\tags) — 等价于 \t(0,0,\tags)（瞬时切换，不插值）
     *
     * 支持的插值 tag：\fs \c/\1c \3c \1a \frz \bord
     * 不支持的插值 tag（瞬时切换）：\b \i \u \s \fn
     */
    private fun parseTransformTag(content: String): TransformAnimation? {
        // content 形如 "0,1000,\fs40\c&H000000FF&" 或 "0,1000,\fs40"
        // 括号感知拆分会保留闭合 )，这里剥去末尾多余的 ) 防止污染最后一个 \tag
        val content = content.trimEnd(')')
        // 先按逗号分割，前 0-2 个数值是时间参数，剩余是 \tags
        val parts = splitTransformContent(content)
        if (parts.isEmpty()) return null

        var t1 = 0L
        var t2 = 0L
        var tagsStr: String

        when {
            parts.size >= 3 && parts[0].toLongOrNull() != null && parts[1].toLongOrNull() != null -> {
                // t1, t2, tags
                t1 = parts[0].toLongOrNull() ?: 0L
                t2 = parts[1].toLongOrNull() ?: 0L
                tagsStr = parts.subList(2, parts.size).joinToString(",")
            }
            parts.size >= 2 && parts[0].toLongOrNull() != null -> {
                // t, tags（等价于 0,t,tags）
                t2 = parts[0].toLongOrNull() ?: 0L
                tagsStr = parts.subList(1, parts.size).joinToString(",")
            }
            else -> {
                tagsStr = parts.joinToString(",")
            }
        }

        // 解析 tagsStr 中的 \tag
        var targetFontSize: Float? = null
        var targetPrimary: SubtitleColor? = null
        var targetOutline: SubtitleColor? = null
        var targetPrimaryAlpha: Float? = null
        var targetRotationZ: Float? = null
        var targetOutlineWidth: Float? = null

        val tags = tagsStr.split('\\')
        for (tag in tags) {
            if (tag.isEmpty()) continue
            when {
                tag.startsWith("fs") -> {
                    targetFontSize = tag.substring(2).toFloatOrNull()
                }
                tag.startsWith("1c") -> {
                    targetPrimary = parseAssColor(tag.substring(2))
                }
                tag.startsWith("c&") || tag.startsWith("c") -> {
                    parseAssColor(tag.substring(1))?.let { targetPrimary = it }
                }
                tag.startsWith("1a") -> {
                    targetPrimaryAlpha = parseAssAlpha(tag.substring(2))
                }
                tag.startsWith("3c") -> {
                    targetOutline = parseAssColor(tag.substring(2))
                }
                tag.startsWith("frz") -> {
                    targetRotationZ = tag.substring(3).toFloatOrNull()
                }
                tag.startsWith("fr") && !tag.startsWith("frx") && !tag.startsWith("fry") -> {
                    targetRotationZ = tag.substring(2).toFloatOrNull()
                }
                tag.startsWith("bord") -> {
                    targetOutlineWidth = tag.substring(4).toFloatOrNull()
                }
            }
        }

        return TransformAnimation(
            t1 = t1,
            t2 = t2,
            targetFontSize = targetFontSize,
            targetPrimary = targetPrimary,
            targetOutline = targetOutline,
            targetPrimaryAlpha = targetPrimaryAlpha,
            targetRotationZ = targetRotationZ,
            targetOutlineWidth = targetOutlineWidth,
        )
    }

    /**
     * 拆分 \t(...) 内容为 parts（按逗号，但保留 \tags 部分完整）。
     *
     * 例如 "0,1000,\fs40\c&H000000FF&" → ["0", "1000", "\\fs40\\c&H000000FF&"]
     */
    private fun splitTransformContent(content: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inTag = false
        for (ch in content) {
            when {
                ch == '\\' -> {
                    inTag = true
                    sb.append(ch)
                }
                ch == ',' && !inTag -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString().trim())
        return result
    }

    /** 从 `tag(start...` 中提取括号内的参数列表。返回参数字符串列表。 */
    private fun extractParenParams(tagBody: String, start: Int): List<String> {
        val openIdx = tagBody.indexOf('(', start - 1)
        if (openIdx < 0) return emptyList()
        val closeIdx = tagBody.indexOf(')', openIdx + 1)
        if (closeIdx < 0) return emptyList()
        val content = tagBody.substring(openIdx + 1, closeIdx)
        return content.split(',').map { it.trim() }
    }

    private fun parseBoolTag(value: String): Boolean? = when (value) {
        "1", "-1" -> true
        "0" -> false
        else -> null
    }

    private fun parseAssColor(value: String): SubtitleColor? {
        val v = value.trim().removePrefix("&H").removePrefix("&h").removeSuffix("&")
        if (v.isEmpty()) return null
        return try {
            SubtitleColor.fromAss(v)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAssAlpha(value: String?): Float {
        if (value.isNullOrBlank()) return 1f
        val v = value.trim().removePrefix("&H").removePrefix("&h").removeSuffix("&")
        val aAss = v.toIntOrNull(16) ?: 0
        return (1f - (aAss / 255f)).coerceIn(0f, 1f)
    }

    private fun parseAlignFromStyle(style: Style): SubtitleAlign {
        return when (style.textAlign?.lowercase()) {
            "bottom-left" -> SubtitleAlign.BOTTOM_LEFT
            "bottom-center" -> SubtitleAlign.BOTTOM_CENTER
            "bottom-right" -> SubtitleAlign.BOTTOM_RIGHT
            "mid-left" -> SubtitleAlign.MIDDLE_LEFT
            "mid-center" -> SubtitleAlign.MIDDLE_CENTER
            "mid-right" -> SubtitleAlign.MIDDLE_RIGHT
            "top-left" -> SubtitleAlign.TOP_LEFT
            "top-center" -> SubtitleAlign.TOP_CENTER
            "top-right" -> SubtitleAlign.TOP_RIGHT
            else -> SubtitleAlign.BOTTOM_CENTER
        }
    }

    private fun parseBool(value: Boolean?): Boolean? = value

    private fun parseStyleColor(color: String?): SubtitleColor? {
        if (color.isNullOrBlank()) return null
        // info.Style.color 是 RRGGBBAA 格式（Style.getRGBValue 输出）
        return try {
            if (color.length == 8) {
                val r = color.substring(0, 2).toInt(16)
                val g = color.substring(2, 4).toInt(16)
                val b = color.substring(4, 6).toInt(16)
                val a = color.substring(6, 8).toInt(16)
                SubtitleColor(r / 255f, g / 255f, b / 255f, a / 255f)
            } else if (color.length == 6) {
                // RRGGBB（SSA Style.getRGBValue 输出格式），alpha 默认 FF
                val r = color.substring(0, 2).toInt(16)
                val g = color.substring(2, 4).toInt(16)
                val b = color.substring(4, 6).toInt(16)
                SubtitleColor(r / 255f, g / 255f, b / 255f, 1f)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
