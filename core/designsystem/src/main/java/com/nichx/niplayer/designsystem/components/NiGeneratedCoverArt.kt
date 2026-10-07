package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 生成封面上文件名使用的字体。
 *
 * 默认系统字体；App 层用用户自选字体（见 `CoverLabelFontStore`）覆盖本值，
 * 这样设计系统本身不必依赖数据层，各处调用方也不用各自解析字体。
 */
val LocalGeneratedCoverFont = compositionLocalOf<FontFamily> { FontFamily.Default }

/** 纸封面上要写的内容：已按文件名规则拆好，UI 直接按三档字号渲染。 */
internal data class CoverLabelLines(
    val prefix: String?,
    val main: String,
    val source: String?,
)

/** 小于该宽度时只写主名：三行文字在那点面积里无法辨认（列表行/小块缩略图）。 */
private val COMPACT_WIDTH = 96.dp

/** 常见音频扩展名（只剥这种；避免把「laoqu2...」这类以点结尾的文件名误当扩展名切掉）。 */
private val FILE_EXTENSION = Regex("""\.[A-Za-z0-9]{1,5}$""")

/**
 * 开头的序号：`3.`、`03、`、`1)`、`01 - `。
 *
 * 分隔符是**必需**的——否则「1989 序曲」的年份会被当成序号删掉。
 */
private val LEADING_INDEX = Regex("""^\s*\d{1,4}\s*(?:[.、．)）\]]|[-–—]\s+)\s*""")

/** 「来源」行的起点：`（微信群…` / `(Live)` / `【…` / `[…`。 */
private val SOURCE_START = Regex("""[（(【\[]""")

/** 主名内部的切分符。 */
private val MAIN_SEPARATORS = charArrayOf(' ', '\t', '　', '·', '・', '-', '_', '—', '|', '/')

/**
 * 把音频文件名拆成「前缀 / 主名 / 来源」三行，供无封面时生成的纸封面使用。
 *
 * 规则刻意保持简单、可预测（全部是纯字符串处理，回归见 `NiGeneratedCoverArtTest`）：
 * 1. 去掉扩展名（仅识别 `\.mp3` 这类 1~5 位字母数字后缀）；
 * 2. 去掉开头的序号（必须带 `.`/`、`/`)` 或「- 」，避免误删年份）；
 * 3. 从第一个 `（`/`(`/`【`/`[` 起截为「来源」行；
 * 4. 主名按分隔符切开：**≥3 段**时最后一段放大作主名、前面合并作前缀；
 *    只有 1~2 段则整体作主名——避免把 `Song Title` 硬拆成两行。
 */
internal fun buildCoverLabelLines(fileName: String): CoverLabelLines {
    val noExtension = fileName.replace(FILE_EXTENSION, "").trim()
    val noIndex = noExtension.replaceFirst(LEADING_INDEX, "").trim()

    val sourceMatch = SOURCE_START.find(noIndex)
    val head = (sourceMatch?.let { noIndex.substring(0, it.range.first) } ?: noIndex).trim()
    val source = sourceMatch
        ?.let { noIndex.substring(it.range.first) }
        ?.trimStart('（', '(', '【', '[')
        ?.trimEnd('）', ')', '】', ']')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val tokens = head.split(*MAIN_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
    val main: String
    val prefix: String?
    if (tokens.size >= 3) {
        main = tokens.last()
        prefix = tokens.dropLast(1).joinToString(" ").takeIf { it.isNotEmpty() }
    } else {
        main = tokens.joinToString(" ").ifEmpty { noIndex }
        prefix = null
    }

    return CoverLabelLines(prefix = prefix, main = main, source = source)
}

/**
 * 无封面时**现场生成**的封面：主题色纸面 + 文件名（系统字体或用户自选字体）。
 *
 * 各处的封面位共用同一套内容，只是外形不同——播放器黑胶主题是圆形贴纸，
 * 简约/Apple 主题与列表缩略图是圆角方形。
 *
 * 调用方应把它**始终**画在真实封面图之下，而不是只在没有封面时才画：
 * 封面路径可能非空却已失效（缓存指向的文件被删/损坏），此时图片库什么都不画，
 * 若没有这层底衬就会透出下层——黑胶主题下是唱盘投影，在浅色背景上就是一块浅灰圆，
 * 也就是「无封面时是个大洞」的成因。有了底衬，「没有封面」不再是缺陷，而是现场造一张封面。
 *
 * @param fileName 要写在封面上的文件名（经 [buildCoverLabelLines] 拆成三行）。
 * @param shape 外形：圆形传 `CircleShape`，方形封面/缩略图传圆角矩形。
 * @param rotation 随碟面旋转的角度（黑胶用；其余传 0）。
 */
@Composable
fun NiGeneratedCoverArt(
    fileName: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
) {
    val primary = MaterialTheme.colorScheme.primary
    // 「淡主题色」纸面：把主题色向白/黑平移，纸面取很淡的一档，墨色取较深的一档
    val paper = lerp(primary, Color.White, 0.80f)
    val paperEdge = lerp(primary, Color.White, 0.56f)
    val ink = lerp(primary, Color.Black, 0.52f)
    val inkSoft = lerp(primary, Color.Black, 0.26f)

    val lines = remember(fileName) { buildCoverLabelLines(fileName) }
    val family = LocalGeneratedCoverFont.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clip(shape)
            .background(paper)
            // 纸边：描边落在形状内侧，做出纸张厚度
            .border(width = 2.dp, color = paperEdge, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < COMPACT_WIDTH
        val sidePx = with(density) { maxWidth.toPx() }

        // 字号随封面尺寸成比例，且**不随系统字体缩放**——它是图形的一部分，
        // 跟随系统字号会直接溢出封面（每行另有 maxLines=1 + 省略号兜底）。
        fun inkSp(ratio: Float): TextUnit = with(density) {
            (sidePx * ratio / (density.density * density.fontScale)).sp
        }

        // 压印内圈：同一形状按内边距再描一圈，圆形与圆角矩形都适用
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(maxWidth * 0.07f)
                .border(width = 1.dp, color = paperEdge, shape = shape),
        )

        if (lines.main.isNotEmpty()) {
            Column(
                // 横向限制在 0.66 倍宽度内：三行文字块在最上/最下一行处的可用弦宽仍放得下
                modifier = Modifier.width(maxWidth * 0.66f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!compact) {
                    lines.prefix?.let { CoverLine(it, family, inkSp(0.115f), inkSoft) }
                }
                CoverLine(lines.main, family, inkSp(if (compact) 0.32f else 0.20f), ink)
                if (!compact) {
                    lines.source?.let { CoverLine(it, family, inkSp(0.095f), inkSoft) }
                }
            }
        }
    }
}

/** 生成封面上的一行字：单行 + 省略号，保证不溢出。 */
@Composable
private fun CoverLine(text: String, family: FontFamily, size: TextUnit, color: Color) {
    Text(
        text = text,
        fontFamily = family,
        fontSize = size,
        lineHeight = size * 1.25f,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}
