package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
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

    /** 移除指定浮层。 */
    fun dismiss(id: String) {
        stack.removeAll { it.id == id }
    }

    /** 关闭栈顶浮层（供返回键 / 外部调用）。 */
    fun dismissTop() {
        stack.lastOrNull()?.let { it.onDismiss() }
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

    // 返回键：栈非空时关闭最上层浮层
    BackHandler(enabled = NiGlassOverlay.requests.isNotEmpty()) {
        NiGlassOverlay.dismissTop()
    }

    // 渲染中的浮层集合。新增即时加入；关闭的由 visible 置 false 播退场动画，
    // 延迟 [EXIT_ANIM_BUFFER_MS] 后再移除节点。否则节点被即时移除，进出场动画会被跳过。
    val rendered = remember { mutableStateMapOf<String, NiGlassOverlayRequest>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        snapshotFlow { NiGlassOverlay.requests.map { it.id } }
            .distinctUntilChanged()
            .collect { ids ->
                val idSet = ids.toSet()
                NiGlassOverlay.requests.forEach { rendered[it.id] = it }
                rendered.keys
                    .filterNot { it in idSet }
                    .forEach { id ->
                        scope.launch {
                            delay(EXIT_ANIM_BUFFER_MS)
                            if (NiGlassOverlay.requests.none { it.id == id }) rendered.remove(id)
                        }
                    }
            }
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
        // 展开动画：从顶部向下垂直展开 + 淡入，仿 M3 DropdownMenu 的下拉效果
        AnimatedVisibility(
            visible = active,
            enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(tween(100, easing = FastOutSlowInEasing)) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(160, easing = FastOutSlowInEasing),
            ),
        ) {
            // 玻璃菜单卡片
            Column(
                modifier = Modifier
                    .offset { position }
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
                    }
                    .width(IntrinsicSize.Max)
                    .then(
                        if (glassEnabled) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop!!,
                                shape = { dropdownShape },
                                effects = {
                                    blur(NiGlassSheetBlurRadius.toPx())
                                },
                                onDrawSurface = { drawRect(panelSurface) },
                            )
                        } else {
                            Modifier.background(panelSurface, dropdownShape)
                        }
                    )
                    .border(NiGlassHairWidth, niGlassBorderColor(), dropdownShape)
                    .padding(vertical = 4.dp),
            ) {
                request.content()
            }
        }
    }
}
