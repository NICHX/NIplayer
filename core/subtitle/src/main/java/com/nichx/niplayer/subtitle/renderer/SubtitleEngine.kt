package com.nichx.niplayer.subtitle.renderer

import com.nichx.niplayer.subtitle.info.Caption
import com.nichx.niplayer.subtitle.info.Style
import com.nichx.niplayer.subtitle.info.TimedTextObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.TreeMap

/**
 * 字幕渲染引擎。
 *
 * 维护外挂字幕的 [TimedTextObject] 与已解析的 [ParsedCaption] 列表，
 * 根据播放位置（已应用字幕偏移）实时输出 [RenderableCaption] 列表供 UI 渲染。
 *
 * 核心职责：
 * 1. 加载外挂字幕文件（ASS/SSA/SRT），调用 [AssOverrideParser] 预解析所有 Caption
 * 2. 接收播放器位置更新 [update]，按 `positionMs + offsetMs` 查询当前应显示的字幕
 * 3. 应用动画（fad/move）生成最终 [RenderableCaption]，输出到 [renderables] StateFlow
 *
 * 字幕偏移实现：
 * - [setOffsetMs] 修改偏移量（正数延后显示，负数提前显示）
 * - [update] 查询时使用 `effectiveMs = positionMs + offsetMs`
 * - 完全自控时序，正负偏移都精确生效（不依赖 media3 setSubtitleOffsetMs）
 *
 * 线程安全：所有方法假定在主线程调用（与 ExoPlayer.Listener 回调线程一致）。
 *
 * @param textSizeFactor 字体大小因子（相对于视图高度，如 0.0533 表示 5.33% 视图高度）
 */
class SubtitleEngine(
    private val textSizeFactor: Float = 0.0533f,
) {

    /** 已解析的字幕列表（按 startMs 排序，索引与 TreeMap key 对应）。 */
    private val parsed = mutableListOf<ParsedCaption>()

    /**
     * startMs → parsed 索引列表 的快速查询映射（TreeMap 支持 floorEntry/ceilingEntry）。
     *
     * C-05 修复：value 改为 [MutableList] 容纳同 startMs 的多条字幕。
     * ASS/SSA 中同一 startMs 的多条字幕很常见（多行对话、分层 \pos 特效但时间相同），
     * 原实现 `TreeMap<Long, Int>` 同 startMs 后写覆盖前写，导致整行丢失。
     */
    private val startMsToIndex: TreeMap<Long, MutableList<Int>> = TreeMap()

    /** 当前字幕偏移（ms）。正数延后显示，负数提前显示。 */
    private val _offsetMs = MutableStateFlow(0L)
    val offsetMs: StateFlow<Long> = _offsetMs.asStateFlow()

    /** 当前应渲染的字幕列表（已应用动画）。空列表表示无字幕显示。 */
    private val _renderables = MutableStateFlow<List<RenderableCaption>>(emptyList())
    val renderables: StateFlow<List<RenderableCaption>> = _renderables.asStateFlow()

    /** 当前视图高度（px），用于字号换算。由 UI 层在尺寸变化时调用 [setViewSize]。 */
    // m-08 修复：初值由 1080f 改为 0f，配合 [update] 中检测 viewHeightPx<=0 跳过渲染，
    // 避免 setViewSize 之前被 update 触发时按 1080 错误缩放字号。
    private var viewHeightPx: Float = 0f

    /** 当前视图宽度（px），用于字号等比缩放。竖屏时按宽度维度限制字号避免暴增。 */
    private var viewWidthPx: Float = 0f

    /**
     * M-14 修复：渲染缓存，避免 ExoPlayer 每 ~16ms 触发 update 都重新跑 applyAnimation。
     *
     * 缓存命中条件（全部满足才复用上次结果）：
     * - positionMs 与上次相同（无 seek / 暂停态的连续轮询不重复计算）
     * - offsetMs 未变化（用户调字幕偏移会触发重算）
     * - styleConfig 版本号未变化（用户改样式后触发重算）
     * - viewHeightPx 未变化（横竖屏切换触发重算）
     *
     * 命中时直接 return，不写 _renderables.value（StateFlow 自动跳过相同值的发射）。
     * 未命中时正常计算并写入 _renderables.value，同时刷新缓存。
     */
    private var lastUpdatePositionMs: Long = Long.MIN_VALUE
    private var lastUpdateOffsetMs: Long = 0L
    private var lastUpdateStyleConfigVersion: Int = 0
    private var lastUpdateViewHeightPx: Float = 0f
    private var lastUpdateViewWidthPx: Float = 0f

    /** styleConfig 变更版本号，[updateStyleConfig] 时自增，用于触发 [update] 缓存失效。 */
    private var styleConfigVersion: Int = 0

    /** 当前加载的字幕文件名（用于 UI 显示）。 */
    private val _subtitleName = MutableStateFlow<String?>(null)
    val subtitleName: StateFlow<String?> = _subtitleName.asStateFlow()

    /**
     * 字幕样式配置（由外层注入，避免硬编码）。
     *
     * 用户在设置页修改描边宽度/颜色/阴影/文字颜色后，由 PlayerViewModel 调用
     * [updateStyleConfig] 更新；下次 [update] 触发的 [applyAnimation] 即应用新值。
     */
    var styleConfig: SubtitleStyleConfig = SubtitleStyleConfig()
        private set

    /** 更新样式配置（用户改设置后由 PlayerViewModel 调用）。 */
    fun updateStyleConfig(config: SubtitleStyleConfig) {
        styleConfig = config
        // M-14 修复：版本号自增让 [update] 缓存失效，强制下次重新计算
        styleConfigVersion++
        // m-07 修复：原注释"立即重渲染一次"但实现仅赋值 styleConfig 未触发渲染。
        // 现主动调用 [update] 用最近一次 positionMs 重渲染，用户改样式后立即看到效果，
        // 即使暂停态或未在播放也立即刷新（update 内部缓存失效后会重新计算）。
        // lastUpdatePositionMs 在 update 中会被重置，此处复用最近值即可。
        if (parsed.isNotEmpty() && lastUpdatePositionMs != Long.MIN_VALUE) {
            // 反算原 positionMs：effectiveMs = positionMs + offsetMs → positionMs = effectiveMs - offsetMs
            val lastPositionMs = lastUpdatePositionMs - lastUpdateOffsetMs
            update(lastPositionMs)
        }
    }

    /**
     * 加载外挂字幕。
     *
     * @param tto 已解析的 [TimedTextObject]（由 FormatASS/FormatSRT.parseFile 生成）
     * @param fileName 字幕文件名（用于 UI 显示）
     */
    fun load(tto: TimedTextObject, fileName: String?) {
        parsed.clear()
        startMsToIndex.clear()
        tto.captions.values.forEachIndexed { index, caption ->
            val parsedCaption = AssOverrideParser.parse(caption, tto)
            parsed.add(parsedCaption)
            // C-05 修复：同 startMs 的多条字幕追加到同一 list，避免 TreeMap 碰撞丢条
            val key = caption.start?.mseconds ?: 0L
            startMsToIndex.getOrPut(key) { mutableListOf() }.add(index)
        }
        _subtitleName.value = fileName ?: tto.fileName.takeIf { it.isNotEmpty() }
        _renderables.value = emptyList()
        // M-14 修复：加载新字幕后清空缓存键，强制下次 update 重新计算
        lastUpdatePositionMs = Long.MIN_VALUE
    }

    /** 清空字幕（卸载外挂字幕）。 */
    fun clear() {
        parsed.clear()
        startMsToIndex.clear()
        _subtitleName.value = null
        _renderables.value = emptyList()
        _offsetMs.value = 0L
        // M-14 修复：清空缓存键，强制下次 update 重新计算
        lastUpdatePositionMs = Long.MIN_VALUE
    }

    /** 设置字幕偏移（ms）。正数延后，负数提前。 */
    fun setOffsetMs(offsetMs: Long) {
        _offsetMs.value = offsetMs
    }

    /** 设置视图尺寸（用于字号换算）。由 UI 层在 onSizeChanged 调用。 */
    fun setViewSize(widthPx: Float, heightPx: Float) {
        viewHeightPx = if (heightPx > 0f) heightPx else viewHeightPx
        viewWidthPx = if (widthPx > 0f) widthPx else viewWidthPx
    }

    /**
     * 根据播放位置更新当前字幕。
     *
     * @param positionMs 当前播放位置（ms，来自 player.positionMs）
     */
    fun update(positionMs: Long) {
        if (parsed.isEmpty()) {
            _renderables.value = emptyList()
            return
        }

        // m-08 修复：viewHeightPx<=0 表示 setViewSize 尚未调用，此时 computeStyleFontSize
        // 会按 0 计算（字号=0），首帧字幕会显示为不可见或异常小。直接 return 等首次
        // setViewSize 后再渲染。layoutCache 与 _renderables 保持上次有效值，UI 不闪烁。
        if (viewHeightPx <= 0f) return

        val effectiveMs = positionMs + _offsetMs.value
        if (effectiveMs < 0) {
            _renderables.value = emptyList()
            return
        }

        // M-14 修复：缓存命中时直接返回，避免每 ~16ms 重复跑 applyAnimation。
        // 命中条件：positionMs、offsetMs、styleConfig 版本、viewHeightPx 均未变化。
        // 注意：即使 effectiveMs 相同，不同 positionMs 也可能算出相同 effectiveMs
        // （如 offsetMs 变化补偿 positionMs 变化），故用 effectiveMs 作为缓存键更准确。
        if (effectiveMs == lastUpdatePositionMs &&
            _offsetMs.value == lastUpdateOffsetMs &&
            styleConfigVersion == lastUpdateStyleConfigVersion &&
            viewHeightPx == lastUpdateViewHeightPx &&
            viewWidthPx == lastUpdateViewWidthPx
        ) {
            return
        }
        lastUpdatePositionMs = effectiveMs
        lastUpdateOffsetMs = _offsetMs.value
        lastUpdateStyleConfigVersion = styleConfigVersion
        lastUpdateViewHeightPx = viewHeightPx
        lastUpdateViewWidthPx = viewWidthPx

        // 遍历所有 startMs <= effectiveMs 的桶，保留仍在显示区间的 caption
        // （跨 startMs 重叠：A(startMs=1000,endMs=10000) 与 B(startMs=5000,endMs=8000)
        // 在 t=7000 时都应显示。原 floorEntry 只取一个桶，会漏掉其它桶的重叠字幕）
        val renderables = mutableListOf<RenderableCaption>()
        val headMap = startMsToIndex.headMap(effectiveMs, true)
        for (indices in headMap.values) {
            for (index in indices) {
                val current = parsed[index]
                if (effectiveMs < current.startMs || effectiveMs > current.endMs) continue
                val renderable = applyAnimation(current, effectiveMs)
                if (renderable.spans.isNotEmpty()) {
                    renderables.add(renderable)
                }
            }
        }
        _renderables.value = renderables
    }

    /**
     * 对一条 [ParsedCaption] 应用时间相关动画，生成 [RenderableCaption]。
     *
     * 应用顺序：
     * 1. fad → 整体 alpha
     * 2. move → 位置插值
     * 3. transform (\t) → 字号/颜色/旋转/边框宽度的线性插值（应用到每个 span）
     * 4. 合并 \frz 静态旋转（整体）
     *
     * @param parsed 待渲染的字幕
     * @param effectiveMs 已应用偏移的有效时间（ms）
     */
    private fun applyAnimation(parsed: ParsedCaption, effectiveMs: Long): RenderableCaption {
        val elapsedFromStart = effectiveMs - parsed.startMs

        // 1. 计算整体 alpha（fad 动画）
        var alpha = 1f
        val fade = parsed.fade
        if (fade != null) {
            val remainingToEnd = parsed.endMs - effectiveMs
            val fadeInAlpha = if (fade.inMs > 0) {
                (elapsedFromStart.toFloat() / fade.inMs).coerceIn(0f, 1f)
            } else 1f
            val fadeOutAlpha = if (fade.outMs > 0) {
                (remainingToEnd.toFloat() / fade.outMs).coerceIn(0f, 1f)
            } else 1f
            alpha = minOf(fadeInAlpha, fadeOutAlpha)
        }

        // 2. 计算位置（move 动画）
        var position = parsed.pos
        val move = parsed.move
        if (move != null) {
            position = if (elapsedFromStart <= move.t1) {
                move.x1 to move.y1
            } else if (elapsedFromStart >= move.t2) {
                move.x2 to move.y2
            } else {
                val progress = (elapsedFromStart - move.t1).toFloat() / (move.t2 - move.t1).coerceAtLeast(1)
                val x = move.x1 + (move.x2 - move.x1) * progress
                val y = move.y1 + (move.y2 - move.y1) * progress
                x to y
            }
        }

        // 3. 应用 \t 动画到每个 span（生成新 span 列表）
        val animatedSpans = if (parsed.transforms.isNotEmpty()) {
            parsed.rawSpans.map { span ->
                applyTransforms(span, parsed.transforms, elapsedFromStart)
            }
        } else {
            parsed.rawSpans
        }

        // 4. 计算视口等比缩放系数（横屏 16:9 时 ≈ 高度缩放；竖屏时按宽度限制避免字号暴增）
        val viewScale = computeViewScale()

        // 5. per-span \fs 是 ASS 原始逻辑值（如 \fs50 → 50），未按视口缩放，
        // 与已缩放的 Style 字号（styleFontSize）比例失调导致字体变形，统一乘 viewScale。
        // 仅在存在 per-span 字号时复制 map，避免无 \fs 字幕（绝大多数）产生新引用影响缓存。
        val scaledSpans = if (animatedSpans.any { it.fontSize != null }) {
            animatedSpans.map { span ->
                span.fontSize?.let { span.copy(fontSize = it * viewScale) } ?: span
            }
        } else {
            animatedSpans
        }

        // 6. 计算整体旋转（\frz 静态值，\t 旋转已通过 span.rotationZ 应用）
        val overallRotation = parsed.rotationZ

        // 7. 从 Style 提取默认值
        val style = parsed.style
        val styleFontSize = computeStyleFontSize(style, viewScale)
        // 应用用户样式配置：applyEmbeddedStyles=false 时强制覆盖 primaryColor/outlineColor
        // outlineWidth/shadowDepth 始终用 styleConfig（避免硬编码 2f 不可调）
        val cfg = styleConfig
        val stylePrimary = if (!cfg.applyEmbeddedStyles) {
            cfg.primaryColor
        } else {
            parseStyleColor(style.color) ?: cfg.primaryColor
        }
        val styleOutline = if (!cfg.applyEmbeddedStyles) {
            cfg.outlineColor
        } else {
            parseStyleColor(style.backgroundColor) ?: cfg.outlineColor
        }
        val styleBack = SubtitleColor.BLACK

        return RenderableCaption(
            spans = scaledSpans,
            align = parsed.align,
            position = position,
            alpha = alpha,
            rotationZ = overallRotation,
            styleFont = style.font?.takeIf { it.isNotBlank() } ?: "sans-serif",
            styleFontSize = styleFontSize,
            stylePrimary = stylePrimary,
            styleOutline = styleOutline,
            styleBack = styleBack,
            styleOutlineWidth = cfg.outlineWidth,
            styleShadowDepth = cfg.shadowDepth,
            // m-09 修复：原 SubtitleOverlay 硬编码 0.6f，现由 styleConfig.shadowAlpha 注入
            styleShadowAlpha = cfg.shadowAlpha.coerceIn(0f, 1f),
        )
    }

    /**
     * 对单个 span 应用 \t 动画列表。
     *
     * 按顺序处理每个 [TransformAnimation]：
     * - 在 t1 之前：不应用
     * - 在 t1..t2 之间：按进度线性插值目标值
     * - 在 t2 之后：直接使用目标值
     *
     * 支持插值的属性：fontSize, primaryColor, outlineColor, rotationZ, outlineWidth
     */
    private fun applyTransforms(
        span: StyledSpan,
        transforms: List<TransformAnimation>,
        elapsedMs: Long,
    ): StyledSpan {
        var fontSize = span.fontSize
        var primary = span.primaryColor
        var outline = span.outlineColor
        var rotationZ = span.rotationZ
        var outlineWidth = span.outlineWidth

        for (t in transforms) {
            // 字号插值
            t.targetFontSize?.let { target ->
                fontSize = when {
                    elapsedMs <= t.t1 -> fontSize // 动画未开始，保持当前值
                    elapsedMs >= t.t2 -> target
                    else -> {
                        val current = fontSize ?: return@let
                        val progress = ((elapsedMs - t.t1).toFloat() / (t.t2 - t.t1).coerceAtLeast(1))
                        current + (target - current) * progress
                    }
                }
            }

            // 主色插值
            t.targetPrimary?.let { target ->
                primary = when {
                    elapsedMs <= t.t1 -> primary
                    elapsedMs >= t.t2 -> target
                    else -> {
                        val current = primary ?: return@let
                        val progress = ((elapsedMs - t.t1).toFloat() / (t.t2 - t.t1).coerceAtLeast(1))
                        lerpColor(current, target, progress)
                    }
                }
            }

            // 边框色插值
            t.targetOutline?.let { target ->
                outline = when {
                    elapsedMs <= t.t1 -> outline
                    elapsedMs >= t.t2 -> target
                    else -> {
                        val current = outline ?: return@let
                        val progress = ((elapsedMs - t.t1).toFloat() / (t.t2 - t.t1).coerceAtLeast(1))
                        lerpColor(current, target, progress)
                    }
                }
            }

            // 主色 alpha 插值
            t.targetPrimaryAlpha?.let { target ->
                primary = primary?.let { current ->
                    when {
                        elapsedMs <= t.t1 -> current
                        elapsedMs >= t.t2 -> current.copy(a = target)
                        else -> {
                            val progress = ((elapsedMs - t.t1).toFloat() / (t.t2 - t.t1).coerceAtLeast(1))
                            current.copy(a = current.a + (target - current.a) * progress)
                        }
                    }
                }
            }

            // 旋转插值
            t.targetRotationZ?.let { target ->
                rotationZ = when {
                    elapsedMs <= t.t1 -> rotationZ
                    elapsedMs >= t.t2 -> target
                    else -> {
                        val current = rotationZ ?: 0f
                        val progress = ((elapsedMs - t.t1).toFloat() / (t.t2 - t.t1).coerceAtLeast(1))
                        current + (target - current) * progress
                    }
                }
            }

            // 边框宽度插值
            t.targetOutlineWidth?.let { target ->
                outlineWidth = when {
                    elapsedMs <= t.t1 -> outlineWidth
                    elapsedMs >= t.t2 -> target
                    else -> {
                        val current = outlineWidth ?: return@let
                        val progress = ((elapsedMs - t.t1).toFloat() / (t.t2 - t.t1).coerceAtLeast(1))
                        current + (target - current) * progress
                    }
                }
            }
        }

        return span.copy(
            fontSize = fontSize,
            primaryColor = primary,
            outlineColor = outline,
            rotationZ = rotationZ,
            outlineWidth = outlineWidth,
        )
    }

    /** 线性插值两个颜色。 */
    private fun lerpColor(from: SubtitleColor, to: SubtitleColor, progress: Float): SubtitleColor {
        return SubtitleColor(
            r = from.r + (to.r - from.r) * progress,
            g = from.g + (to.g - from.g) * progress,
            b = from.b + (to.b - from.b) * progress,
            a = from.a + (to.a - from.a) * progress,
        )
    }

    /**
     * 计算视口等比缩放系数。
     *
     * ASS 字号与坐标基于 PlayRes（默认 384x288）设计，应按 `min(宽/PlayResX, 高/PlayResY)`
     * 等比缩放，与视频画面区域适配：
     * - 横屏 16:9（如 1920x1080）：min(5.0, 3.75) = 3.75，与旧实现（仅按高度）一致，无回归
     * - 竖屏（如 1080x2400）：min(2.81, 8.33) = 2.81，避免纯高度缩放导致字号暴增
     *
     * 宽度未知（setViewSize 尚未调用）时回退为仅按高度缩放。
     */
    private fun computeViewScale(): Float {
        val heightScale = viewHeightPx / 288f
        if (viewWidthPx <= 0f) return heightScale
        val widthScale = viewWidthPx / 384f
        return minOf(widthScale, heightScale)
    }

    /**
     * 根据 Style.fontSize 和视口等比缩放系数计算实际字号（px）。
     *
     * ASS Style.fontSize 是基于 PlayResY 的逻辑值，需按 [viewScale] 等比缩放。
     * 若 Style.fontSize 无效，则用 [textSizeFactor] * viewHeightPx 作为默认值（同样受宽度维度限制）。
     */
    private fun computeStyleFontSize(style: Style, viewScale: Float): Float {
        val styleSize = style.fontSize?.toFloatOrNull()
        return if (styleSize != null && styleSize > 0f) {
            // ASS 字号基于 PlayResY（默认 288），按等比缩放系数换算
            // 例如 PlayResY=288, fontSize=24, viewScale=3.75 → 24 * 3.75 = 90px
            styleSize * viewScale
        } else {
            // 默认字号 = 视图高度的 textSizeFactor 比例（PlayRes 288 下的逻辑字号经等比缩放）
            textSizeFactor * viewScale * 288f
        }
    }

    private fun parseStyleColor(color: String?): SubtitleColor? {
        if (color.isNullOrBlank()) return null
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
