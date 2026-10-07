package com.nichx.niplayer.feature.player.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 距离 → 视觉量的映射曲线。三套主题各一条，**互不复用**。
 *
 * 用 `object` 而不是 lambda：`object` 是引用相等，放进 [LyricsTheme] 这个 `data class` 里
 * 不会破坏相等性判定。lambda 每次构造都是新实例，会让「theme 没变但参数不等」而整页无谓重组。
 */
internal interface LyricsDistanceCurve {

    /** 距离（行）+ 焦点进度 → 不透明度。 */
    fun opacity(lineDistance: Float, focus: Float): Float

    /** 距离（行）+ 焦点进度 → 模糊半径（dp）。不做距离模糊的主题恒返回 0。 */
    fun blurDp(lineDistance: Float, focus: Float): Float

    /**
     * Apple Music 口径。
     *
     * 压得最狠（近处接近 1，远处收到底噪 0.12），且是三套里**唯一**做距离模糊的。
     * 聚焦行按 [focus] 抬回 1。
     */
    object Apple : LyricsDistanceCurve {

        /** 距离 → 不透明度的整体压暗量（0=不压暗，1=完全按曲线压暗）。 */
        private const val DIM_AMOUNT = 0.85f

        /**
         * 距离 → 模糊半径（dp）的整形。
         *
         * 近处（1.35 行以内）不模糊，之后按线性增长、封顶，再乘以强度；
         * 聚焦中的行（[focus]→1）不做距离模糊。**不是**「超过 N 行就一律最大模糊」的硬截断——
         * 那会把远处一片糊成一块。
         */
        private const val BLUR_INTENSITY = 0.6f

        override fun opacity(lineDistance: Float, focus: Float): Float {
            val base = when {
                lineDistance <= 1f -> 1f - lineDistance * 0.44f
                lineDistance <= 2f -> 0.56f - (lineDistance - 1f) * 0.22f
                else -> (0.34f - (lineDistance - 2f) * 0.07f).coerceAtLeast(0.12f)
            }
            val dimmed = 1f - (1f - base) * DIM_AMOUNT
            val f = focus.coerceIn(0f, 1f)
            return dimmed + (1f - dimmed) * f
        }

        override fun blurDp(lineDistance: Float, focus: Float): Float {
            val progress = (lineDistance - 1.35f).coerceAtLeast(0f)
            val base = (progress * 3.1f).coerceAtMost(10f)
            return base * BLUR_INTENSITY * (1f - focus.coerceIn(0f, 1f))
        }
    }

    /**
     * 黑胶口径。
     *
     * 比 Apple 的口径更平缓、下限更高（0.40）：远处的歌词只是变暗、仍保持可读，
     * 层次由明暗与放大来体现，而不是「淡到几乎看不见」。聚焦行由 [focus] 抬回 1。
     */
    object Vinyl : LyricsDistanceCurve {
        override fun opacity(lineDistance: Float, focus: Float): Float {
            val base = (1f - lineDistance * 0.20f).coerceIn(0.40f, 1f)
            val f = focus.coerceIn(0f, 1f)
            return base + (1f - base) * f
        }

        override fun blurDp(lineDistance: Float, focus: Float): Float = 0f
    }

    /**
     * 简约封面口径（「聚光」）。
     *
     * 每行衰减 0.30、下限 0.28：相邻行 0.70、隔一行 0.40、再远就落到底噪。
     * 比黑胶（每行 0.20、下限 0.40）陡得多 —— 配合放大的当前句，把视线收在焦点附近；
     * 黑胶那种「远处也只是变暗、仍保持可读」的口径在这里会让整屏一样重。
     */
    object Glass : LyricsDistanceCurve {
        override fun opacity(lineDistance: Float, focus: Float): Float {
            val base = (1f - lineDistance * 0.30f).coerceIn(0.28f, 1f)
            val f = focus.coerceIn(0f, 1f)
            return base + (1f - base) * f
        }

        override fun blurDp(lineDistance: Float, focus: Float): Float = 0f
    }
}

/**
 * 已唱字符的**荧光**（字自己发光），只有简约封面用；`null` 表示该主题不发光。
 *
 * 这是「光带」的替代方案 —— 光带那套（切薄条做竖向柔化、把擦除位置从文本坐标换算到行坐标、
 * 离屏图层 + DstIn）又重又难调，而荧光只要给「已唱」那一层的文字样式加一个 shadow：
 * 它天然**只作用于已唱的部分**（那一层本来就按擦除裁切），于是荧光跟着唱词走，
 * 关掉「逐字歌词」时那一层不排，荧光也就没有了。
 *
 * 强度与半径要分深色 / 浅色两档：
 *
 * - **深色**：封面取色时强调色被重映射到亮档（感知亮度 0.44~0.62），亮字配亮晕才是「发光」，
 *   可以给足；
 * - **浅色**：强调色被重映射到暗档（0.26~0.42），此时「发光的晕」其实是一圈**暗晕** ——
 *   在浅底上给到 0.85 会读成一块脏影子、还会把字糊粗，所以强度与半径都收一档。
 *
 * ⚠️ [blurDark] / [blurLight] 最终会喂给 `Shadow.blurRadius`，而它的单位是**像素**不是 dp ——
 * 早前写 `9f` 在 3x 屏上只有 3dp，所以「荧光太弱」。这里按 dp 给、使用处再换算成 px。
 */
@Immutable
internal data class LyricsGlow(
    val alphaDark: Float,
    val alphaLight: Float,
    val blurDark: Dp,
    val blurLight: Dp,
)

/**
 * 歌词页的主题描述。三套主题各一个实例：见 [AppleLyrics] / [VinylLyrics] / [GlassLyrics]。
 *
 * 设计规则：**只放「三套主题共有的同一概念」**。歌词页的状态机 —— 滚动跟随、浏览打断、
 * 级联入场、逐字铺色 —— 与主题无关，全部在 `LyricsView` 里共用一套实现；这里只描述差异。
 *
 * 由此带来的直接收益：加一套主题 = 加一个 [LyricsTheme] 实例，不必碰 `LyricsView` 一行。
 */
@Immutable
internal data class LyricsTheme(
    // ---- 排版 ----
    /** 当前句字号倍率（相对 `titleLarge`）。 */
    val fontBoost: Float,
    /** 非当前句相对当前句的字号比例。两档共用同一 lineHeight，故行距均匀。 */
    val idleFontRatio: Float,
    /** 歌词字距。 */
    val letterSpacing: TextUnit,
    /** 句与句之间的额外留白（句内折行间距由字体 lineHeight 决定）。 */
    val sentenceGap: Dp,
    /** 当前句落在视口高度的该比例处。0.5 = 居中；Apple 取 0.34（偏上）。 */
    val focusFraction: Float,
    /** 文字对齐。Apple 左对齐，另两套居中。 */
    val textAlign: TextAlign,

    // ---- 着色 ----
    /**
     * 是否使用「统一纯白大字」的配色（Apple Music）。
     *
     * 为 true 时：当前句与普通句都是纯白，层次只由透明度与模糊给出，逐字铺色靠**明暗**
     * 区分（未唱 0.45 / 已唱 1.0）；为 false 时走「正文色 → 强调色」的层级，
     * 逐字铺色由 `accentColor` 参与。
     */
    val whiteText: Boolean,
    /** 当前句光晕强度系数（最终 alpha = focus × 该值）。0 表示不发光。 */
    val glowAlphaPerFocus: Float,
    /**
     * 当前句光晕的模糊半径（**像素**，直接喂给 `Shadow.blurRadius`）。
     *
     * Apple 14、黑胶 12：Apple 的辉光是纯白大字的附加层，可以散得更开；
     * 黑胶的光晕贴着主题色，收紧一档才不会糊住笔画。
     */
    val glowBlurRadiusPx: Float,
    /** 已唱字符的荧光（仅简约封面）；null = 该主题不用荧光。 */
    val glow: LyricsGlow?,

    // ---- 距离映射 ----
    val distanceCurve: LyricsDistanceCurve,

    // ---- 行为开关 ----
    /** 焦点行的轻微放大比例（最终 scale = 1 + focus × 该值）。黑胶 0.05，其余 0。 */
    val focusScaleAmount: Float,
    /** 是否在文字下方铺一层「主题色底衬（上下渐隐）」。只有黑胶需要（见 `LyricsView` 的说明）。 */
    val vignette: Boolean,
    /** 「逐行追赶」：换句时焦点下方的行先抵消列表滚动、再依次抬升归位。只有 Apple 做。 */
    val cascadeChase: Boolean,
    /** 逐字铺色时是否把已唱的字**抬起**（字号的 10%，夹在 1.5~6dp）。只有 Apple 做。 */
    val liftSung: Boolean,
    /** 是否在长间隔处插入间奏点缀（「点 · 点 · 点」）。只有 Apple 做。 */
    val interlude: Boolean,
    /** 点击歌词是否**一次即跳**。Apple 是；另两套保留「先预览再确认」的两次点击。 */
    val seekOnFirstTap: Boolean,
    /**
     * 点击区域是否收在**文字**上。
     *
     * Apple 是（左右各让出 28dp，空白留给外层切换控件显隐）；另两套整项可点、左右让 24dp。
     */
    val tapTargetOnText: Boolean,
)

/** Apple Music：左对齐白色大字，当前行明亮、其余发散模糊。 */
internal val AppleLyrics = LyricsTheme(
    fontBoost = 1.4f,
    // ⚠️ 下面两项沿用黑胶的档位：现状 `if (glassStyle) GLASS_x else VINYL_x` 的 else 分支
    // 同时罩住了 Apple 与黑胶，所以 Apple 拿到的就是 VINYL_ 的值。**不要**改成 Apple 自己的数。
    idleFontRatio = 0.78f,
    letterSpacing = 0.2.sp,
    sentenceGap = 20.dp,
    focusFraction = 0.34f,
    textAlign = TextAlign.Start,
    whiteText = true,
    glowAlphaPerFocus = 0.62f,
    glowBlurRadiusPx = 14f,
    glow = null,
    distanceCurve = LyricsDistanceCurve.Apple,
    focusScaleAmount = 0f,
    vignette = false,
    cascadeChase = true,
    liftSung = true,
    interlude = true,
    seekOnFirstTap = true,
    tapTargetOnText = true,
)

/** 黑胶：居中，当前句放大 + 主题色光晕 + 整行轻微放大（唱片封面的语言）。 */
internal val VinylLyrics = LyricsTheme(
    fontBoost = 1.15f,
    idleFontRatio = 0.78f,
    letterSpacing = 0.2.sp,
    sentenceGap = 18.dp,
    focusFraction = 0.5f,
    textAlign = TextAlign.Center,
    whiteText = false,
    glowAlphaPerFocus = 0.55f,
    glowBlurRadiusPx = 12f,
    glow = null,
    distanceCurve = LyricsDistanceCurve.Vinyl,
    focusScaleAmount = 0.05f,
    vignette = true,
    cascadeChase = false,
    liftSung = false,
    interlude = false,
    seekOnFirstTap = false,
    tapTargetOnText = false,
)

/** 简约封面：居中，字号几乎齐平，层次**只由颜色与透明度**给出（「聚光」口径）。 */
internal val GlassLyrics = LyricsTheme(
    fontBoost = 1.2f,
    idleFontRatio = 0.73f,
    letterSpacing = 0.6.sp,
    sentenceGap = 18.dp,
    focusFraction = 0.5f,
    textAlign = TextAlign.Center,
    whiteText = false,
    // 当前句不发光：层次交给颜色，这里恒 0（见 distanceCurve 的说明）
    glowAlphaPerFocus = 0f,
    glowBlurRadiusPx = 12f,
    glow = LyricsGlow(
        alphaDark = 0.85f,
        alphaLight = 0.50f,
        blurDark = 8.dp,
        blurLight = 6.dp,
    ),
    distanceCurve = LyricsDistanceCurve.Glass,
    focusScaleAmount = 0f,
    vignette = false,
    cascadeChase = false,
    liftSung = false,
    interlude = false,
    seekOnFirstTap = false,
    tapTargetOnText = false,
)
