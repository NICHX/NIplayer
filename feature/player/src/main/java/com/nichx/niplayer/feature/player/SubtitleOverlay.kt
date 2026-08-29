package com.nichx.niplayer.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.subtitle.renderer.RenderableCaption
import com.nichx.niplayer.subtitle.renderer.StyledSpan
import com.nichx.niplayer.subtitle.renderer.SubtitleAlign
import com.nichx.niplayer.subtitle.renderer.SubtitleColor
import com.nichx.niplayer.subtitle.renderer.SubtitleEngine

/**
 * 外挂字幕渲染层。
 *
 * 订阅 [SubtitleEngine.renderables]，按 [RenderableCaption] 的对齐/位置/样式渲染。
 *
 * 渲染策略：
 * - **边框**：在文本四周画 8 方向偏移描边（避免对角线锯齿）
 * - **阴影**：在文本右下方画半透明副本
 * - **多行**：按 [StyledSpan] 中的 `\n` 标记拆行，每行独立测量与绘制
 * - **定位**：[RenderableCaption.position] 非 null 时按归一化坐标绝对定位；否则按 [align] 对齐
 *
 * 字幕样式来源（用户可配，见 [SubtitleSettings]）：
 * - [SubtitleSettings.bottomPaddingDp]：字幕垂直位置（默认 48，正=上移、负=下移，
 *   负值可把默认偏上的字幕往下移）
 * - [SubtitleSettings.fontFamilyKey]：字体族（系统内置 4 选 1，经 [resolveFontFamily] 映射）
 * - [SubtitleSettings.fontColor]：仅 applyEmbeddedStyles=false 时作为 fallback；当 ASS Style / override
 *   tag 指定了 primaryColor 时优先使用 ASS 自带颜色（与 [SubtitleSettings.applyEmbeddedStyles]=true 语义一致）
 * - 描边宽度/颜色、阴影深度由 [SubtitleEngine] 通过 [RenderableCaption.styleOutlineWidth] /
 *   [RenderableCaption.styleOutline] / [RenderableCaption.styleShadowDepth] 注入（用户设置驱动）
 *
 * @param engine 字幕引擎
 * @param modifier 修饰符
 */
@Composable
fun SubtitleOverlay(
    engine: SubtitleEngine,
    modifier: Modifier = Modifier,
) {
    val renderables by engine.renderables.collectAsState()
    // m-11 修复 + 样式实时响应：订阅 engine.styleVersion（用户改样式后 updateStyleConfig 自增），
    // 与 ON_RESUME 一起驱动版本号，让底部边距/字体族在播放中修改也能立即重组生效
    val styleVersion by engine.styleVersion.collectAsState()
    val density = LocalDensity.current

    // m-11 修复：SubtitleSettings 是 MMKV 封装（非响应式），原注释"每次重组自动应用最新值"
    // 是误导——Composable 中直接读取不会触发重组。用户在设置页改完 bottomPaddingDp /
    // fontFamilyKey 后返回播放器，若无其他触发因子，新值不生效。
    //
    // 修复方案：监听 Lifecycle ON_RESUME 事件（用户从设置页返回时触发）与 engine.styleVersion
    // （播放中改样式触发），自增版本号作为 [layoutCache] 与下方读取的 remember key。
    // 每次版本号变化 → SubtitleOverlay 重组 → 重新读取 SubtitleSettings.* 取最新值，
    // 并重建 layoutCache（ fontFamily 变化时文本测量需重做）。
    val lifecycleOwner = LocalLifecycleOwner.current
    var settingsVersion by remember { mutableIntStateOf(0) }
    val lifecycleObserver = remember(lifecycleOwner) {
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsVersion++
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
    }
    // 读取 SubtitleSettings 时把版本号作为依赖，触发重组并取最新值
    val settingsCombinedVersion = settingsVersion + styleVersion
    val bottomPaddingDp = remember(settingsCombinedVersion) { SubtitleSettings.bottomPaddingDp.dp }
    val fontFamily = remember(settingsCombinedVersion) { resolveFontFamily(SubtitleSettings.fontFamilyKey) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val viewWidthPx = with(density) { maxWidth.toPx() }
        val viewHeightPx = with(density) { maxHeight.toPx() }

        // M-15 修复：将 engine.setViewSize 包到 SideEffect 中，避免在 speculative composition
        // 或每次重组时直接写入 SubtitleEngine.viewHeightPx，违反 Compose 单向数据流。
        // SideEffect 仅在成功 commit 后执行，保证每次尺寸变化只写一次。
        SideEffect {
            engine.setViewSize(viewWidthPx, viewHeightPx)
        }

        if (renderables.isEmpty()) return@BoxWithConstraints

        val measurer = rememberTextMeasurer()

        // M-19 修复：缓存 TextLayoutResult，避免 Canvas 每帧（约 60fps）都重测文本。
        // TextMeasurer.measure 涉及 Bidi、shaping、layout，开销较大，长时间字幕显示时
        // CPU 占用偏高。
        // 缓存键：
        // - renderables 引用（M-14 保证 positionMs/offsetMs/styleConfig/viewHeightPx 未变化时引用稳定）
        // - fontFamily（用户改字体族触发失效）
        // - viewWidthPx（横竖屏切换触发失效）
        // - settingsCombinedVersion（m-11 修复：用户改 bottomPaddingDp/fontFamilyKey 后触发失效）
        // caption.spans 内含 per-span 字号/颜色/样式，spans 变化时 caption 实例变化，
        // 进而 renderables 列表实例变化，触发缓存失效。
        val layoutCache = remember(renderables, fontFamily, viewWidthPx, settingsCombinedVersion) {
            HashMap<RenderableCaption, List<TextLayoutResult>>(renderables.size).also { map ->
                val maxConstraintWidth = (viewWidthPx * 0.9f).toInt().coerceAtLeast(1)
                for (caption in renderables) {
                    if (caption.spans.isEmpty()) continue
                    val lines = splitIntoLines(caption.spans)
                    if (lines.isEmpty()) continue
                    val baseFontSize = caption.styleFontSize.coerceAtLeast(12f)
                    val layouts = lines.map { lineSpans ->
                        val annotated = buildAnnotatedString(caption, lineSpans, baseFontSize, fontFamily)
                        val style = TextStyle(
                            fontSize = baseFontSize.sp,
                            fontFamily = fontFamily,
                            fontStyle = if (lineSpans.any { it.italic == true }) FontStyle.Italic else FontStyle.Normal,
                            fontWeight = if (lineSpans.any { it.bold == true }) FontWeight.Bold else FontWeight.Normal,
                        )
                        measurer.measure(
                            text = annotated,
                            style = style,
                            constraints = Constraints(
                                maxWidth = maxConstraintWidth,
                                maxHeight = Constraints.Infinity,
                            ),
                        )
                    }
                    map[caption] = layouts
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            for (caption in renderables) {
                val lineLayouts = layoutCache[caption] ?: continue
                drawCaptionWithLayouts(
                    caption = caption,
                    lineLayouts = lineLayouts,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    bottomPaddingPx = bottomPaddingDp.toPx(),
                )
            }
        }
    }
}

/**
 * 将 [SubtitleSettings.fontFamilyKey] 解析为 Compose [FontFamily]。
 *
 * 不识别的 key 回退到 [FontFamily.Default]。
 */
private fun resolveFontFamily(key: String): FontFamily = when (key) {
    SubtitleSettings.FONT_FAMILY_KEY_SERIF -> FontFamily.Serif
    SubtitleSettings.FONT_FAMILY_KEY_MONOSPACE -> FontFamily.Monospace
    SubtitleSettings.FONT_FAMILY_KEY_SANS_SERIF -> FontFamily.SansSerif
    else -> FontFamily.Default
}

/**
 * 绘制单条字幕（含边框、阴影、文本、旋转）。
 *
 * M-19 修复：文本测量已移至 Composable 层的 [layoutCache] 中，本函数仅负责绘制，
 * 接收预计算好的 [lineLayouts]，避免每帧重复 measure。
 */
private fun DrawScope.drawCaptionWithLayouts(
    caption: RenderableCaption,
    lineLayouts: List<TextLayoutResult>,
    canvasWidth: Float,
    canvasHeight: Float,
    bottomPaddingPx: Float,
) {
    if (caption.spans.isEmpty() || lineLayouts.isEmpty()) return

    val totalHeight = lineLayouts.fold(0f) { acc, layout -> acc + layout.size.height }
    val maxWidth = lineLayouts.maxOf { it.size.width.toFloat() }

    // 4. 计算绘制起点（x, y）
    val (startX, startY) = computePosition(
        caption = caption,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        textWidth = maxWidth,
        textHeight = totalHeight,
        bottomPaddingPx = bottomPaddingPx,
    )

    // 5. 应用整体旋转（\frz）—— 按 ASS 规范绕 alignment 锚点旋转
    // M-16 修复：原实现绕"文本几何中心"旋转，违反 ASS 规范。
    // ASS 规范中 \frz 的旋转中心是 alignment 锚点：
    // - BOTTOM_LEFT / BOTTOM_CENTER / BOTTOM_RIGHT → 文本底部对应锚点
    // - MIDDLE_* → 文本几何中心
    // - TOP_* → 文本顶部对应锚点
    val rotationDegrees = caption.rotationZ
    val (centerX, centerY) = computeRotationPivot(
        caption = caption,
        startX = startX,
        startY = startY,
        textWidth = maxWidth,
        textHeight = totalHeight,
    )

    val drawBlock: DrawScope.() -> Unit = {
        var currentY = startY
        for (layout in lineLayouts) {
            val lineWidth = layout.size.width.toFloat()
            val lineHeight = layout.size.height.toFloat()

            val lineX = when (caption.align) {
                SubtitleAlign.BOTTOM_LEFT, SubtitleAlign.MIDDLE_LEFT, SubtitleAlign.TOP_LEFT -> startX
                SubtitleAlign.BOTTOM_CENTER, SubtitleAlign.MIDDLE_CENTER, SubtitleAlign.TOP_CENTER -> startX + (maxWidth - lineWidth) / 2f
                SubtitleAlign.BOTTOM_RIGHT, SubtitleAlign.MIDDLE_RIGHT, SubtitleAlign.TOP_RIGHT -> startX + (maxWidth - lineWidth)
            }

            val lineTopLeft = Offset(lineX, currentY)
            val alpha = caption.alpha

            // 阴影：按 \shad 深度偏移（caption.styleShadowDepth 由 SubtitleEngine 注入）
            // m-09 修复：原 alpha 硬编码 0.6f 不可调，现读 caption.styleShadowAlpha（由
            // SubtitleStyleConfig.shadowAlpha 注入，用户在设置页可调）
            val shadowDepth = caption.styleShadowDepth.coerceAtLeast(0f)
            if (shadowDepth > 0f) {
                val shadowColor = colorFromSubtitle(caption.styleBack, SubtitleColor.BLACK)
                    .copy(alpha = caption.styleShadowAlpha * alpha)
                drawTextWithColor(
                    layout = layout,
                    topLeft = lineTopLeft + Offset(shadowDepth, shadowDepth),
                    color = shadowColor,
                )
            }

            // 边框：按 \bord 宽度 8 方向偏移（caption.styleOutlineWidth 由 SubtitleEngine 注入）
            // M-17 修复说明：per-span outlineWidth/outlineColor 的完整支持需要按 span 拆分绘制，
            // 当前架构按行测量（一行一个 TextLayoutResult），per-span 边框需要重写为 per-span 测量+绘制。
            // 此处保持 caption 整体值渲染，per-span \bord/\3c 暂未支持，作为已知限制文档化。
            // 影响范围有限：绝大多数 ASS 字幕使用 Style 级别 \bord，per-span \bord 仅用于特效字幕。
            val outlineWidth = caption.styleOutlineWidth.coerceAtLeast(0f)
            if (outlineWidth > 0f) {
                val outlineColor = colorFromSubtitle(caption.styleOutline, SubtitleColor.BLACK)
                    .copy(alpha = alpha)
                val outlineOffsets = buildOutlineOffsets(outlineWidth)
                for (offset in outlineOffsets) {
                    drawTextWithColor(
                        layout = layout,
                        topLeft = lineTopLeft + offset,
                        color = outlineColor,
                    )
                }
            }

            // 主文本（C-06 修复：不传 color 参数，保留 AnnotatedString 中 per-span 颜色）
            // 原实现 drawText(layout, color, topLeft) 的 color 作为整体 tint 覆盖整个 layout，
            // 导致 ASS \c / \1c per-span 颜色特效完全失效（唱词高亮、多角色多色字幕全部单色）。
            // alpha 通过 drawText 重载的 alpha 参数传入，保持整体淡入淡出效果。
            // 阴影/描边需要单独着色，仍使用 drawTextWithColor 传 color。
            drawText(
                textLayoutResult = layout,
                topLeft = lineTopLeft,
                alpha = alpha,
            )

            currentY += lineHeight
        }
    }

    if (rotationDegrees != 0f) {
        rotate(degrees = rotationDegrees, pivot = Offset(centerX, centerY), block = drawBlock)
    } else {
        drawBlock()
    }
}

/**
 * 计算 \frz 旋转的锚点坐标（M-16 修复）。
 *
 * ASS 规范中旋转中心由 alignment 决定：
 * - BOTTOM_*：底部对应锚点（LEFT=左下角，CENTER=底部中点，RIGHT=右下角）
 * - MIDDLE_*：几何中心
 * - TOP_*：顶部对应锚点（LEFT=左上角，CENTER=顶部中点，RIGHT=右上角）
 */
private fun computeRotationPivot(
    caption: RenderableCaption,
    startX: Float,
    startY: Float,
    textWidth: Float,
    textHeight: Float,
): Pair<Float, Float> {
    val centerX = startX + textWidth / 2f
    val topY = startY
    val bottomY = startY + textHeight
    val midY = startY + textHeight / 2f
    return when (caption.align) {
        SubtitleAlign.BOTTOM_LEFT -> startX to bottomY
        SubtitleAlign.BOTTOM_CENTER -> centerX to bottomY
        SubtitleAlign.BOTTOM_RIGHT -> (startX + textWidth) to bottomY
        SubtitleAlign.MIDDLE_LEFT -> startX to midY
        SubtitleAlign.MIDDLE_CENTER -> centerX to midY
        SubtitleAlign.MIDDLE_RIGHT -> (startX + textWidth) to midY
        SubtitleAlign.TOP_LEFT -> startX to topY
        SubtitleAlign.TOP_CENTER -> centerX to topY
        SubtitleAlign.TOP_RIGHT -> (startX + textWidth) to topY
    }
}

/**
 * 构造边框 8 方向偏移列表（用于描边绘制）。
 *
 * 用 8 方向而非 4 方向，避免对角线处出现锯齿缺口。
 *
 * m-10 修复：原实现 8 方向偏移都是 `Offset(±w, ±w)`，对角线偏移距离 = w*√2 ≈ 1.414w，
 * 视觉上对角线描边比水平/垂直方向厚 41%。现对角线方向偏移改为 `w * 0.7071f`（1/√2），
 * 让 8 方向偏移的视觉描边宽度一致（每方向实际位移 w）。
 */
private fun buildOutlineOffsets(outlineWidth: Float): List<Offset> {
    val w = outlineWidth
    // 对角线补偿系数：1/√2 ≈ 0.7071
    val d = w * 0.7071f
    return listOf(
        Offset(0f, -w), Offset(0f, w),
        Offset(-w, 0f), Offset(w, 0f),
        Offset(-d, -d), Offset(d, -d),
        Offset(-d, d), Offset(d, d),
    )
}

/** 用指定颜色绘制 TextLayoutResult（保留原 span 颜色覆盖）。 */
private fun DrawScope.drawTextWithColor(
    layout: TextLayoutResult,
    topLeft: Offset,
    color: Color,
) {
    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = topLeft,
    )
}

/**
 * 将 spans 拆分为多行。
 *
 * M-18 修复：原实现仅当 `span.text == "\n"`（整个 span 内容就是字面换行符）时才拆分，
 * 对 "Hello\nWorld"（嵌入换行）或 NEWLINE span 文本不是 "\n" 的情况都不拆分，
 * 导致多行字幕可能渲染为单行（被 Constraints 自动 wrap，但失去 ASS 精确行控制）。
 *
 * 现按 `\n` 字符在 span.text 中拆分：一个 span 含 N 个 `\n` 会产生 N+1 行，
 * 拆出的子 span 继承原 span 的所有样式（fontSize/primaryColor/italic/bold 等）。
 */
private fun splitIntoLines(spans: List<StyledSpan>): List<List<StyledSpan>> {
    val lines = mutableListOf<MutableList<StyledSpan>>()
    var current = mutableListOf<StyledSpan>()
    for (span in spans) {
        // 整 span 就是 "\n"：直接结束当前行（保持原行为）
        if (span.text == "\n") {
            lines.add(current)
            current = mutableListOf()
            continue
        }
        // span.text 内嵌换行：按 \n 拆分，子片段继承原 span 样式
        if (span.text.contains('\n')) {
            val parts = span.text.split('\n')
            for ((idx, part) in parts.withIndex()) {
                if (part.isNotEmpty()) {
                    current.add(span.copy(text = part))
                }
                // 除最后一段外，每段后都结束当前行开始新行
                if (idx < parts.size - 1) {
                    lines.add(current)
                    current = mutableListOf()
                }
            }
        } else {
            current.add(span)
        }
    }
    if (current.isNotEmpty()) lines.add(current)
    return lines
}

/** 构建 AnnotatedString，应用每个 span 的样式覆盖 + 用户字体族。 */
private fun buildAnnotatedString(
    caption: RenderableCaption,
    lineSpans: List<StyledSpan>,
    baseFontSize: Float,
    fontFamily: FontFamily,
): AnnotatedString = buildAnnotatedString {
    for (span in lineSpans) {
        val spanStyle = SpanStyle(
            color = colorFromSubtitle(span.primaryColor ?: caption.stylePrimary, SubtitleColor.WHITE),
            fontSize = (span.fontSize ?: baseFontSize).sp,
            fontFamily = fontFamily,
            fontStyle = if (span.italic == true) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (span.bold == true) FontWeight.Bold else FontWeight.Normal,
        )
        withStyle(spanStyle) {
            append(span.text)
        }
    }
}

/**
 * 计算字幕绘制起点（左上角坐标）。
 *
 * @param caption 字幕数据
 * @param canvasWidth 画布宽
 * @param canvasHeight 画布高
 * @param textWidth 文本最大宽度
 * @param textHeight 文本总高度
 * @param bottomPaddingPx 字幕垂直位置（px，正=上移/负=下移，来自 [SubtitleSettings.bottomPaddingDp]）
 * @return (startX, startY) 文本左上角坐标
 */
private fun computePosition(
    caption: RenderableCaption,
    canvasWidth: Float,
    canvasHeight: Float,
    textWidth: Float,
    textHeight: Float,
    bottomPaddingPx: Float,
): Pair<Float, Float> {
    // 优先使用 \pos 绝对定位
    val pos = caption.position
    if (pos != null) {
        val x = pos.first * canvasWidth
        val y = pos.second * canvasHeight
        // \pos 的坐标是文本中心点（ASS 规范），转成左上角
        return (x - textWidth / 2f) to (y - textHeight / 2f)
    }

    // 按 align 自动布局
    val horizontalPadding = canvasWidth * 0.05f // 左右各留 5% 边距
    return when (caption.align) {
        SubtitleAlign.BOTTOM_LEFT, SubtitleAlign.MIDDLE_LEFT, SubtitleAlign.TOP_LEFT -> {
            val x = horizontalPadding
            val y = when (caption.align) {
                SubtitleAlign.TOP_LEFT -> canvasHeight * 0.05f
                SubtitleAlign.MIDDLE_LEFT -> (canvasHeight - textHeight) / 2f
                else -> canvasHeight - textHeight - bottomPaddingPx
            }
            x to y
        }
        SubtitleAlign.BOTTOM_CENTER, SubtitleAlign.MIDDLE_CENTER, SubtitleAlign.TOP_CENTER -> {
            val x = (canvasWidth - textWidth) / 2f
            val y = when (caption.align) {
                SubtitleAlign.TOP_CENTER -> canvasHeight * 0.05f
                SubtitleAlign.MIDDLE_CENTER -> (canvasHeight - textHeight) / 2f
                else -> canvasHeight - textHeight - bottomPaddingPx
            }
            x to y
        }
        SubtitleAlign.BOTTOM_RIGHT, SubtitleAlign.MIDDLE_RIGHT, SubtitleAlign.TOP_RIGHT -> {
            val x = canvasWidth - textWidth - horizontalPadding
            val y = when (caption.align) {
                SubtitleAlign.TOP_RIGHT -> canvasHeight * 0.05f
                SubtitleAlign.MIDDLE_RIGHT -> (canvasHeight - textHeight) / 2f
                else -> canvasHeight - textHeight - bottomPaddingPx
            }
            x to y
        }
    }
}

/** 从 SubtitleColor 转 Compose Color，null 时用默认值。 */
private fun colorFromSubtitle(color: SubtitleColor?, default: SubtitleColor): Color {
    val c = color ?: default
    return Color(
        red = c.r.coerceIn(0f, 1f),
        green = c.g.coerceIn(0f, 1f),
        blue = c.b.coerceIn(0f, 1f),
        alpha = c.a.coerceIn(0f, 1f),
    )
}
