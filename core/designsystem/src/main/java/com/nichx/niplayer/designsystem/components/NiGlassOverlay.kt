package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.nichx.niplayer.designsystem.motion.NiAnimatedVisibility
import com.nichx.niplayer.designsystem.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 全局玻璃浮层（**同窗口 overlay**）的单例槽位。
 *
 * 浮层（底部面板 / 居中弹窗）投递到这里，由 App 根部唯一的 [NiGlassOverlayHost] 渲染。
 * 之所以必须经根宿主渲染，而不是在调用点就地绘制：
 * - 根宿主位于 backdrop 捕获层（[LocalNiBackdrop] 的 `layerBackdrop`）**之外**，
 *   浮层用 [com.kyant.backdrop.drawBackdrop] 采样主内容时不会把自己画进采样源，
 *   避免 backdrop 官方文档警告的「捕获→绘制→再捕获」无限递归（SIGSEGV 闪退）；
 * - 同窗口采样定位可靠，真模糊与悬浮底栏同一套机制。
 *
 * 用 App 级 object 单例（而非 Hilt）：这是纯 UI 渲染槽，不承载业务依赖，
 * 生命周期由各浮层组件通过 show/dismiss 控制。
 */
object NiGlassOverlay {

    private val stack = mutableStateListOf<NiGlassOverlayRequest>()

    /** 当前待渲染的浮层栈（从底到顶）。 */
    val requests: List<NiGlassOverlayRequest> get() = stack

    /** 投递一个浮层；同 [NiGlassOverlayRequest.id] 已存在时忽略（不重复压栈）。 */
    fun show(request: NiGlassOverlayRequest) {
        if (stack.none { it.id == request.id }) {
            stack += request
        }
    }

    /**
     * 投递或更新一个浮层：同 [NiGlassOverlayRequest.id] 已存在时用新请求**覆盖**（内容/标题/
     * 关闭回调原地刷新，弹窗不关闭重开），否则压栈。用于状态机驱动的弹窗（如更新流程）：
     * 状态切换时原地更新内容，避免「旧窗退场 + 新窗进场」的闪动。
     */
    fun showOrUpdate(request: NiGlassOverlayRequest) {
        val index = stack.indexOfFirst { it.id == request.id }
        if (index >= 0) {
            stack[index] = request
        } else {
            stack += request
        }
    }

    /** 移除指定浮层。 */
    fun dismiss(id: String) {
        stack.removeAll { it.id == id }
    }

    /** 关闭栈顶浮层（供返回键 / 外部调用）。 */
    fun dismissTop() {
        stack.lastOrNull()?.onDismiss()
    }
}

/** 玻璃浮层展示类型。 */
enum class NiGlassOverlayKind {
    /** 底部弹出面板（[NiGlassBottomSheet]）。 */
    BottomSheet,

    /** 居中对话框（[NiGlassDialog]）。 */
    Dialog,

    /** 锚定下拉菜单（锚点下方展开，[anchor] 定位）。 */
    Dropdown,
}

/**
 * 一次浮层投递请求。
 *
 * @param id 稳定唯一标识（同 id 去重；dismiss 依据）
 * @param kind 浮层形态
 * @param title 可选标题
 * @param anchorBounds 锚点按钮的根坐标矩形（[NiGlassOverlayKind.Dropdown] 用：菜单自其对应角
 *   展开、收起，并按它的大小做「长出来」的几何 morph）
 * @param onDismiss 关闭回调（点击遮罩 / 返回键触发）
 * @param content 浮层内容；始终读取投递时捕获的最新状态
 */
data class NiGlassOverlayRequest(
    val id: String,
    val kind: NiGlassOverlayKind,
    val title: String? = null,
    val anchorBounds: IntRect = IntRect.Zero,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
)

/**
 * App 根部唯一的玻璃浮层宿主：渲染 [NiGlassOverlay] 栈内的全部浮层。
 *
 * 必须挂载在 backdrop 捕获层（`layerBackdrop`）**之外**、同窗口内容层之上，
 * 且处于 [LocalNiBackdrop] 作用域内，浮层才能采样主内容做真模糊而不循环。
 */
@Composable
fun NiGlassOverlayHost(
    bottomInset: Dp = Dp.Unspecified,
) {
    val backdrop = LocalNiBackdrop.current
    val glassEnabled = LocalNiGlassEnabled.current && backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val panelSurface = niGlassPanelSurfaceColor()
    val dropdownShape = RoundedCornerShape(20.dp)
    val anchorGapPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    // 返回键：浮层非空时关闭最上层浮层。
    // OnBackPressedDispatcher 的 onBackPressed() 取「最后注册且启用的回调」（lastOrNull{isEnabled}）。
    // 本宿主位于 App 根部、早于文件浏览等页面注册返回回调；若只在初始注册，会被进入页面时
    // 后注册的页面 BackHandler 抢占（页面回调排它更靠后→先命中→误走导航）。
    // 因此：浮层打开时把本回调重新 remove→add 插到队尾，使其成为最后一个启用回调从而优先生效；
    // 浮层关闭后仅禁用、不随手势动态删除，保持 predictive back 一致（onBackStarted/onBackPressed
    // 均命中同一回调，保证手势动画与提交行为一致）。
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val overlayBackCallback = remember {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                NiGlassOverlay.dismissTop()
            }
        }
    }
    DisposableEffect(backDispatcher) {
        backDispatcher?.addCallback(overlayBackCallback)
        onDispose { overlayBackCallback.remove() }
    }
    // 浮层栈开合变化：打开时重新插到队尾并启用；关闭时禁用
    LaunchedEffect(Unit) {
        snapshotFlow { NiGlassOverlay.requests.isNotEmpty() }
            .distinctUntilChanged()
            .collect { open ->
                if (open) {
                    overlayBackCallback.isEnabled = true
                    // 重新插入队尾，令其成为返回回调队列中 lastOrNull{isEnabled} 命中的回调
                    overlayBackCallback.remove()
                    backDispatcher?.addCallback(overlayBackCallback)
                } else {
                    overlayBackCallback.isEnabled = false
                }
            }
    }

    // 渲染中的浮层集合。新增即时加入；关闭的由 visible 置 false 播退场动画，
    // 延迟 [EXIT_ANIM_BUFFER_MS] 后再移除节点。否则节点被即时移除，进出场动画会被跳过。
    val rendered = remember { mutableStateMapOf<String, NiGlassOverlayRequest>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // 观察完整请求列表而非仅 id 列表：showOrUpdate 同 id 覆盖（内容/标题/关闭回调更新）
        // 时也能触发 rendered 同步，弹窗原地刷新不关闭重开
        snapshotFlow { NiGlassOverlay.requests.toList() }
            .distinctUntilChanged()
            .collect { reqs ->
                val idSet = reqs.map { it.id }.toSet()
                // 本次新增的浮层：用于识别「旧浮层被新浮层接替」这一交接场景
                val incoming = idSet.filterNot { it in rendered.keys }
                reqs.forEach { rendered[it.id] = it }
                rendered.keys
                    .filterNot { it in idSet }
                    .forEach { id ->
                        if (incoming.isNotEmpty()) {
                            // 同一帧内「旧浮层关闭 + 新浮层打开」= 菜单 → 弹窗的交接。
                            // 旧浮层必须立即移除、不播退场：否则旧浮层退场与新浮层进场在同一帧叠加，
                            // 交接瞬间会看到两层同时在动（用户感知为“多闪一次”，与更新/备份弹窗同因）。
                            rendered.remove(id)
                        } else {
                            scope.launch {
                                delay(EXIT_ANIM_BUFFER_MS)
                                if (NiGlassOverlay.requests.none { it.id == id }) rendered.remove(id)
                            }
                        }
                    }
            }
    }

    // ── 全局压暗层（唯一）──
    // 由「是否存在需要压暗的浮层（底部面板/居中弹窗）」驱动，只在这一处做一次淡入淡出。
    // 浮层之间交接（如「⋮ 菜单 → 属性弹窗」）时压暗层保持不变：
    // 若让每个浮层各带一层压暗，交接时「旧压暗淡出 + 新压暗淡入」会叠加，亮度先掉再回升，
    // 用户感知为「压暗层闪一下」。
    val scrimActive = NiGlassOverlay.requests.any { it.kind != NiGlassOverlayKind.Dropdown }
    NiAnimatedVisibility(
        visible = scrimActive,
        enter = fadeIn(tween(MotionTokens.SURFACE, easing = MotionTokens.easeEnter)),
        exit = fadeOut(
            tween(MotionTokens.exitOf(MotionTokens.SURFACE), easing = MotionTokens.easeExit),
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = NiGlassSheetScrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    NiGlassOverlay.dismissTop()
                },
        )
    }

    rendered.forEach { (id, request) ->
        // 可见性 = 请求是否仍在栈中，驱动浮层各自的进入/退出动画
        val active = NiGlassOverlay.requests.any { it.id == id }

        when (request.kind) {
            NiGlassOverlayKind.BottomSheet -> NiGlassBottomSheet(
                show = active,
                onDismissRequest = request.onDismiss,
                title = request.title,
                bottomInset = bottomInset,
            ) {
                request.content()
            }

            NiGlassOverlayKind.Dialog -> NiGlassDialog(
                show = active,
                onDismissRequest = request.onDismiss,
                title = request.title,
            ) {
                request.content()
            }

            NiGlassOverlayKind.Dropdown ->
                DropdownGlassOverlay(
                    active = active,
                    request = request,
                    backdrop = backdrop,
                    glassEnabled = glassEnabled,
                    panelSurface = panelSurface,
                    dropdownShape = dropdownShape,
                    anchorGapPx = anchorGapPx,
                )
        }
    }
}

/** 浮层退场动画缓冲：等待的内部 exit 动画最长约 280ms，留出裕量后再销毁节点。 */
private const val EXIT_ANIM_BUFFER_MS = 450L

/**
 * 锚定玻璃下拉菜单（同窗口 overlay）——**重写版**。
 *
 * 与旧实现的区别（旧版用 AnimatedVisibility + MutableTransitionState，动画反复出问题）：
 * - **单一 [Animatable] progress 驱动**：进场走 [MotionTokens.springMenu]（欠阻尼 ζ=0.82，
 *   产生「自按钮长出来」的过冲），退场走 [MotionTokens.springMenuExit]（快速收敛）。
 *   规避了「节点首次组合即 visible ⇒ 过渡被直接置为已完成、整段动画被跳过」的旧疾；
 * - **几何 morph**：按锚点矩形与实测菜单尺寸做 x/y 非等比缩放，菜单自锚点所在的**那个角**
 *   长出来（按锚点水平位置自动取 top-start / top-end）；progress 可过冲到 1.06 产生可见回弹；
 * - **不再需要「测量前先不画」的遮罩**：progress 起步为 0 ⇒ 首帧 alpha=0，实测后的第一帧
 *   定位修正天然不可见（旧版那面 `positioned` 遮掩因此删除）；
 * - 方向：锚点偏屏幕右半则贴右、否则贴左；下方放不下且上方有空间时改为向上弹。
 */
@Composable
private fun DropdownGlassOverlay(
    active: Boolean,
    request: NiGlassOverlayRequest,
    backdrop: Backdrop?,
    glassEnabled: Boolean,
    panelSurface: Color,
    dropdownShape: Shape,
    anchorGapPx: Int,
) {
    val screenSize = LocalWindowInfo.current.containerSize
    val bounds = request.anchorBounds
    // 单一动画驱动：active 变化即开始，不受节点创建时机影响
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            progress.animateTo(1f, MotionTokens.springMenu)
        } else {
            progress.animateTo(0f, MotionTokens.springMenuExit)
        }
    }
    // 实测菜单尺寸（内容变化时重新测量；用于定位与几何 morph）
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    // 锚点偏屏幕右半 ⇒ 贴右（原点取右上/右下角），否则贴左
    val alignEnd = bounds.left + bounds.width / 2 > screenSize.width / 2
    // 目标位置：仅在锚点 / 尺寸变化时重算
    val position = remember(bounds, menuSize, screenSize, alignEnd, anchorGapPx) {
        if (menuSize == IntSize.Zero) {
            IntOffset(bounds.left, bounds.bottom + anchorGapPx)
        } else {
            val roomBelow = bounds.bottom + anchorGapPx + menuSize.height
            val yAbove = bounds.top - anchorGapPx - menuSize.height
            val openAbove = roomBelow > screenSize.height && yAbove > 0
            val x = if (alignEnd) bounds.right - menuSize.width else bounds.left
            val y = if (openAbove) yAbove else bounds.bottom + anchorGapPx
            IntOffset(
                x.coerceIn(0, (screenSize.width - menuSize.width).coerceAtLeast(0)),
                y.coerceIn(0, (screenSize.height - menuSize.height).coerceAtLeast(0)),
            )
        }
    }
    val openAbove = menuSize != IntSize.Zero && position.y < bounds.top
    val origin = TransformOrigin(
        pivotFractionX = if (alignEnd) 1f else 0f,
        pivotFractionY = if (openAbove) 1f else 0f,
    )
    // 过冲几何（1.06 为多出的一点回弹）；视觉量（alpha / 模糊）只取 0..1
    val geometry = progress.value.coerceIn(-0.04f, 1.06f)
    val visual = progress.value.coerceIn(0f, 1f)
    // 收起态 = 锚点按钮的相对大小（限制 0.25..1，避免按钮远小于菜单时缩放过头）
    val collapsedX = if (menuSize.width > 0) {
        (bounds.width.toFloat() / menuSize.width).coerceIn(0.25f, 1f)
    } else {
        0.86f
    }
    val collapsedY = if (menuSize.height > 0) {
        (bounds.height.toFloat() / menuSize.height).coerceIn(0.25f, 1f)
    } else {
        0.86f
    }
    // 模糊随展开渐进（收起时更「清」，展开后到位），与 MeloX 下拉的 blur-in 同源
    val glassBlur = 6f + (NiGlassSheetBlurRadius.value - 6f) * visual
    // 全屏透明点击层：点击外部关闭
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = request.onDismiss,
            ),
    ) {
        // 玻璃菜单卡片：Column + IntrinsicSize.Max 让菜单项垂直排列、宽度贴合最宽项
        Column(
            modifier = Modifier
                .offset { position }
                .onGloballyPositioned { coords ->
                    val measured = IntSize(coords.size.width, coords.size.height)
                    if (measured != menuSize) menuSize = measured
                }
                .graphicsLayer {
                    transformOrigin = origin
                    scaleX = androidx.compose.ui.util.lerp(collapsedX, 1f, geometry)
                    scaleY = androidx.compose.ui.util.lerp(collapsedY, 1f, geometry)
                    alpha = visual
                }
                .width(IntrinsicSize.Max)
                .then(
                    if (glassEnabled) {
                        Modifier.niLiquidGlassPanel(
                            backdrop = backdrop!!,
                            shape = dropdownShape,
                            surface = panelSurface,
                            blurRadius = glassBlur.dp,
                            // 下拉菜单是小面板：开色散折射更像「一块玻璃」，对齐 MeloX 下拉口径
                            chromaticAberration = true,
                        )
                    } else {
                        Modifier.background(panelSurface, dropdownShape)
                    },
                )
                .border(NiGlassHairWidth, niGlassBorderColor(), dropdownShape)
                .padding(vertical = 4.dp),
        ) {
            request.content()
        }
    }
}
