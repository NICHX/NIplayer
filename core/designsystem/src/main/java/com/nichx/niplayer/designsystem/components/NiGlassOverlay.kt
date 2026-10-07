package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
 * @param anchor 锚点屏幕坐标（[NiGlassOverlayKind.Dropdown] 用，菜单在锚点下方展开）
 * @param onDismiss 关闭回调（点击遮罩 / 返回键触发）
 * @param content 浮层内容；始终读取投递时捕获的最新状态
 */
data class NiGlassOverlayRequest(
    val id: String,
    val kind: NiGlassOverlayKind,
    val title: String? = null,
    val anchor: IntOffset = IntOffset.Zero,
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

/** 下拉菜单收起的时长（进场走 [MotionTokens.springPanel]，缩放起止点见 [MotionTokens.SCALE_MENU_IN]）。 */
private const val DROPDOWN_EXIT_MS = 120

/**
 * 锚定玻璃下拉菜单（同窗口 overlay）。
 *
 * 菜单从 [NiGlassOverlayRequest.anchor] 锚点下方展开，用 [androidx.compose.ui.layout.onGloballyPositioned]
 * 读取实际布局位置并校正，保证菜单始终落在屏幕内（不超出右/下边缘）。
 * Column + IntrinsicSize.Max 让菜单项垂直排列、宽度贴合最宽项。
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
    var position by remember(request.anchor) {
        mutableStateOf(IntOffset(request.anchor.x, request.anchor.y + anchorGapPx))
    }
    // 首帧还没测出菜单尺寸，位置可能先落在锚点下方、下一帧才被夹回屏内并翻转为向上展开，
    // 这一跳就是「展开时闪一下」。测量到位前先不画卡片。
    var positioned by remember(request.anchor) { mutableStateOf(false) }
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
        // 展开/收起动画。
        //
        // 这里必须用 visibleState 而不是 visible：浮层的节点是「请求到达时才被创建」的，
        // 而 AnimatedVisibility 在首次组合时如果 visible 已经是 true，会把过渡直接置为
        // 已完成——一帧动画都不走。这正是之前菜单「完全没有动画」的原因。用
        // MutableTransitionState 从 false 起步，「出现」就成了一次真正的状态迁移。
        val visibleState = remember { MutableTransitionState(false) }
        visibleState.targetState = active
        AnimatedVisibility(
            visibleState = visibleState,
            // 从锚点那一角（右上）弹性长出来 + 淡入 + 轻微下落，像从按钮下掉出来、带一下回弹
            enter = fadeIn(animationSpec = MotionTokens.springPanel) +
                scaleIn(
                    animationSpec = MotionTokens.springPanel,
                    initialScale = MotionTokens.SCALE_MENU_IN,
                    transformOrigin = MotionTokens.OriginTopEnd,
                ) +
                slideInVertically(
                    animationSpec = MotionTokens.panelSpring<IntOffset>(IntOffset(1, 1)),
                    initialOffsetY = { -it / 6 },
                ),
            exit = fadeOut(tween(DROPDOWN_EXIT_MS, easing = FastOutSlowInEasing)) +
                scaleOut(
                    animationSpec = tween(DROPDOWN_EXIT_MS, easing = FastOutSlowInEasing),
                    targetScale = MotionTokens.SCALE_MENU_OUT,
                    transformOrigin = MotionTokens.OriginTopEnd,
                ),
        ) {
            // 玻璃菜单卡片
            Column(
                modifier = Modifier
                    .offset { position }
                    .graphicsLayer { alpha = if (positioned) 1f else 0f }
                    .onGloballyPositioned { coords ->
                        val menuW = coords.size.width
                        val menuH = coords.size.height
                        val maxX = screenSize.width - menuW
                        val maxY = screenSize.height - menuH
                        // 水平贴合锚点并限制在屏内
                        val x = request.anchor.x.coerceIn(0, maxOf(0, maxX))
                        // 默认从锚点下方展开
                        var y = request.anchor.y + anchorGapPx
                        // 紧贴屏幕底部（下方放不下）时改为向上展开，
                        // 避免菜单被压制到屏幕底、叠压底部操作栏之上
                        if (y + menuH > screenSize.height) {
                            y = request.anchor.y - menuH - anchorGapPx
                        }
                        val corrected = IntOffset(x, y.coerceIn(0, maxOf(0, maxY)))
                        if (corrected != position) position = corrected
                        positioned = true
                    }
                    .width(IntrinsicSize.Max)
                    .then(
                        if (glassEnabled) {
                            Modifier.niLiquidGlassPanel(
                                backdrop = backdrop!!,
                                shape = dropdownShape,
                                surface = panelSurface,
                                blurRadius = NiGlassSheetBlurRadius,
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
}
