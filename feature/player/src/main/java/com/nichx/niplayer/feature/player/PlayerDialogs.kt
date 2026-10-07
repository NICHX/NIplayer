package com.nichx.niplayer.feature.player

import android.content.res.Configuration
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.motion.NiAnimatedVisibility
import com.nichx.niplayer.designsystem.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放器内 Dialog 统一样式定义。
 *
 * 设计目标（解决用户反馈的三个问题）：
 * 1. **占全高不好看** → 所有 Dialog 统一 `heightIn(max = 580.dp)`，超出可滚动
 * 2. **整体样式不好看** → 统一 28dp 圆角、16dp 阴影、0.5dp 细边框、统一宽度策略与 padding
 * 3. **高亮度刺眼** → 暗色玻璃拟态配色（视频播放器场景：播放始终是暗色画面，暗色弹窗不刺眼且协调）
 *
 * **重要：本组强制暗色弹窗仅用于视频播放器场景**（[PlayerScreen] 全屏暗色画面）。
 * 音频播放器等浅色场景请使用 core/designsystem 的主题自适应弹窗组件
 * （[com.nichx.niplayer.designsystem.components.NiInfoDialog]、
 * [com.nichx.niplayer.designsystem.components.NiConfirmDialog]、
 * [com.nichx.niplayer.designsystem.components.NiListItemDialog]），
 * 避免浅色模式下出现黑色弹窗。
 *
 * 暗色调色板（固定暗色，不依赖 MaterialTheme.colorScheme.surface）：
 * - 背景：`0xFF1C1C1E` alpha=0.92（iOS 风格深灰半透明，让视频隐约透出降低割裂感）
 * - 文字主色：`0xFFE8E8EA`（柔和白，非纯白降低刺眼度）
 * - 文字次色：`0xFF9E9EA2`
 * - 边框：`0xFFFFFFFF` alpha=0.08（浅色细边框增加层次）
 * - 分隔线：`0xFFFFFFFF` alpha=0.08
 * - 选中高亮：`0xFFFFFFFF` alpha=0.10
 * - 强调色：仍用 MaterialTheme.colorScheme.primary（保留主题色调感）
 */

// ===== 暗色玻璃拟态调色板（播放器专用，固定不跟随系统主题） =====

/** Dialog 背景色：深灰半透明 */
private val PlayerDialogBg = Color(0xFF1C1C1E).copy(alpha = 0.92f)

/** 主文字色：柔和白（非纯白，降低刺眼度） */
private val PlayerTextPrimary = Color(0xFFE8E8EA)

/** 次文字色：中灰 */
private val PlayerTextSecondary = Color(0xFF9E9EA2)

/** 边框色：白色低透明度 */
private val PlayerBorder = Color.White.copy(alpha = 0.06f)

/** 分隔线色 */
private val PlayerDivider = Color.White.copy(alpha = 0.05f)

/** 选中项背景高亮 */
private val PlayerSelectedBg = Color.White.copy(alpha = 0.10f)

/**
 * 播放器统一 Dialog 容器。
 *
 * 强制暗色玻璃拟态背景 + 28dp 圆角 + 高度上限 + 内置滚动。
 * 所有播放器内自定义 Dialog 都应使用此容器以保证视觉统一。
 *
 * @param onDismiss 关闭回调
 * @param modifier 额外修饰符
 * @param maxWidth 最大宽度（默认 360.dp）
 * @param maxHeight 最大高度（默认 580.dp，超出可滚动）
 * @param scrollable 内容是否可滚动（默认 true）
 * @param content 内容
 */
/** 按当前屏幕宽度收缩对话框最大宽度：竖屏下避免 360dp 对话框几乎占满全屏，最多占屏宽 92%。 */
@Composable
fun adaptiveDialogMaxWidth(requestedMaxWidth: Int): Int {
    return minOf(
        requestedMaxWidth,
        (LocalConfiguration.current.screenWidthDp * 0.92f).toInt().coerceAtLeast(280),
    )
}

// ===== 液态玻璃材质（统一弹窗设计语言） =====
// 覆盖在播放画面上的弹窗用「液态玻璃」表达：垂向渐变营造底部更暗的光学聚焦，
// 顶部一条高光细线呈现玻璃边缘反光，配合细描边与大圆角形成统一、有质感的暗色玻璃面板。
// 透明度与主界面玻璃保持一致（约 91% 不透明），让底层画面隐约透出，避免生硬的不透明色块。
private val LiquidGlassTop = Color(0xE82C2C30)
private val LiquidGlassBottom = Color(0xE8141416)
private val LiquidGlassEdgeHighlight = Color.White.copy(alpha = 0.10f)

/**
 * 播放器统一「液态玻璃」弹窗面板。
 *
 * 负责渲染暗色液态玻璃底（垂向渐变 + 顶部高光细线 + 圆角 + 细描边 + 阴影），
 * 圆角裁剪，内容叠在其上。所有播放器弹窗（[PlayerDialog] 及独立的 AB/选集/书签等）
 * 都通过它保证材质统一。
 *
 * @param modifier 额外修饰符
 * @param shape 面板形状（默认 28dp 圆角）
 * @param content 内容
 */
@Composable
fun PlayerDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 16.dp,
        border = BorderStroke(0.5.dp, PlayerBorder),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(Brush.verticalGradient(listOf(LiquidGlassTop, LiquidGlassBottom))),
        ) {
            // 顶部高光细线（玻璃边缘反光）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LiquidGlassEdgeHighlight)
                    .align(Alignment.TopCenter),
            )
            content()
        }
    }
}

@Composable
fun PlayerDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Int = 360,
    maxHeight: Int = 580,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 竖屏下按屏幕宽度动态收缩，横屏时仍用 maxWidth 上限
        val effectiveMaxWidth = adaptiveDialogMaxWidth(maxWidth)
        // 高度同样按屏幕比例封顶，避免内容多时弹窗顶满整个屏幕（始终留出上下余白，较美观）
        val effectiveMaxHeight =
            minOf(maxHeight, (LocalConfiguration.current.screenHeightDp * 0.72f).toInt().coerceAtLeast(320))
        PlayerDialogSurface(
            modifier = modifier
                .widthIn(min = 280.dp, max = effectiveMaxWidth.dp)
                .heightIn(max = effectiveMaxHeight.dp),
        ) {
            val colModifier = if (scrollable) {
                Modifier.verticalScroll(rememberScrollState())
            } else {
                Modifier
            }
            Column(
                modifier = colModifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

/** 抽屉入场/退场时长（ms），退场时长同时用于延迟销毁窗口。 */
private const val DRAWER_ENTER_MS = MotionTokens.SURFACE
private val DRAWER_EXIT_MS = MotionTokens.exitOf(DRAWER_ENTER_MS)

/**
 * 播放器侧边 / 底部弹层（视频场景专用）。
 *
 * 与居中 [PlayerDialog] 共用同一「液态玻璃」材质（[PlayerDialogSurface]），
 * 但会随屏幕方向切换形态：
 * - 横屏：贴右侧、全高，从右滑入；
 * - 竖屏：贴底部、通栏、限高，从下滑入 —— 竖屏下右侧抽屉几乎占满屏宽且全高，
 *   既遮挡画面又不利于单手操作，改为底部弹层更符合手机使用习惯。
 *
 * 交互：
 * - 打开：滑入 + 淡入（[DRAWER_ENTER_MS]）
 * - 关闭：外部点击 / 返回键 / 右上角关闭 均先播放滑出动画，动画结束后再回调 [onDismiss]
 *   （若直接回调，外层 `if (show)` 会立刻移除窗口，看不到退场）
 * - 内容区默认由本组件提供纵向滚动；[scrollable] = false 时交给内容自行滚动，
 *   用于内容内含 LazyColumn 等自身可滚动组件的场景，避免嵌套滚动崩溃
 *
 * @param onDismiss 关闭回调
 * @param title 标题
 * @param modifier 额外修饰符
 * @param maxWidth 横屏抽屉最大宽度（默认 360dp，仍按屏宽自适应收缩）
 * @param onBack 左上角返回回调；为 null 时不显示返回箭头
 * @param scrollable 内容区是否由本组件提供纵向滚动（默认 true）
 * @param content 内容（具备 ColumnScope，可用 weight 占满剩余高度）
 */
@Composable
fun PlayerSideDrawer(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    maxWidth: Int = 360,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { visible = true }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // 竖屏底部弹层限高：内容短时按内容收缩，列表长时最多占屏高约 72%
    val sheetMaxHeight = (configuration.screenHeightDp * 0.72f).toInt().coerceAtLeast(320).dp

    // 先播退场动画，再通知外部销毁窗口
    val requestClose: () -> Unit = {
        if (visible) {
            visible = false
            scope.launch {
                delay(DRAWER_EXIT_MS.toLong())
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val drawerWidth = adaptiveDialogMaxWidth(maxWidth)
        val sheetAlignment = if (isPortrait) Alignment.BottomCenter else Alignment.CenterEnd
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = sheetAlignment,
        ) {
            // 点击面板外的画面区域关闭。
            // 自绘背板：满屏内容下 Dialog 的窗口级 outside-click 不可靠，这里显式处理。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = requestClose,
                    ),
            )
            NiAnimatedVisibility(
                // 用 NiAnimatedVisibility：该节点可能以 visible=true 首次组合，
                // 裸 AnimatedVisibility 会把过渡直接置为已完成、跳过入场。
                visible = visible,
                enter = if (isPortrait) {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(DRAWER_ENTER_MS, easing = MotionTokens.easeEnter),
                    ) + fadeIn(animationSpec = tween(DRAWER_ENTER_MS))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(DRAWER_ENTER_MS, easing = MotionTokens.easeEnter),
                    ) + fadeIn(animationSpec = tween(DRAWER_ENTER_MS))
                },
                exit = if (isPortrait) {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(DRAWER_EXIT_MS, easing = MotionTokens.easeExit),
                    ) + fadeOut(animationSpec = tween(DRAWER_EXIT_MS))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(DRAWER_EXIT_MS, easing = MotionTokens.easeExit),
                    ) + fadeOut(animationSpec = tween(DRAWER_EXIT_MS))
                },
                modifier = Modifier.align(sheetAlignment),
            ) {
                // 吸收面板空白处的点击，避免穿透到背板误关闭
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* 拦截穿透 */ },
                ) {
                    // 竖屏贴底通栏（仅上方圆角）；横屏贴右全高（仅左侧圆角）
                    val surfaceShape = if (isPortrait) {
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    } else {
                        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                    }
                    PlayerDialogSurface(
                        shape = surfaceShape,
                        modifier = if (isPortrait) {
                            modifier
                                .fillMaxWidth()
                                .heightIn(max = sheetMaxHeight)
                        } else {
                            modifier
                                .fillMaxHeight()
                                .width(drawerWidth.dp)
                        },
                    ) {
                        Column(modifier = if (isPortrait) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                            if (isPortrait) {
                                // 底部弹层拖拽指示条
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 2.dp)
                                        .size(width = 32.dp, height = 4.dp)
                                        .clip(CircleShape)
                                        .background(PlayerTextSecondary.copy(alpha = 0.4f))
                                        .align(Alignment.CenterHorizontally),
                                )
                            }
                            // 头部：可选返回 + 标题 + 关闭
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            ) {
                                if (onBack != null) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.player_subtitle_back),
                                        tint = PlayerTextPrimary,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable(onClick = onBack)
                                            .padding(8.dp)
                                            .size(20.dp),
                                    )
                                } else {
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                    text = title,
                                    color = PlayerTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp),
                                )
                                IconButton(
                                    onClick = requestClose,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.player_close),
                                        tint = PlayerTextSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = PlayerDivider,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            val bodyModifier = if (isPortrait) {
                                // 竖屏让内容按需撑高：短内容得到矮面板，长列表才占到限高
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            }
                            if (scrollable) {
                                Column(modifier = bodyModifier.verticalScroll(rememberScrollState())) {
                                    content()
                                }
                            } else {
                                Column(modifier = bodyModifier) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 播放器列表选择 Dialog（替代 [com.nichx.niplayer.designsystem.components.NiListItemDialog]）。
 *
 * 标题 + 分隔线 + 列表项。列表项 48dp 行高，选中项高亮 + Check 图标。
 * 列表项超多时自动滚动。
 *
 * @param title 标题
 * @param items 列表项（复用 NiDialogItem data class）
 * @param onDismiss 关闭回调
 */
@Composable
fun PlayerListDialog(
    title: String,
    items: List<NiDialogItem>,
    onDismiss: () -> Unit,
) {
    PlayerDialog(onDismiss = onDismiss, maxHeight = 560) {
        PlayerDialogTitle(text = title)
        HorizontalDivider(
            color = PlayerDivider,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { index, item ->
            PlayerItemRow(item)
            if (index < items.size - 1) {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/**
 * 播放器列表选择「抽屉版」（与 [PlayerListDialog] 内容一致，改为右侧抽屉）。
 *
 * 用于视频场景下的长列表选择（如音轨）；标题由抽屉头部承载。
 */
@Composable
fun PlayerListDrawer(
    title: String,
    items: List<NiDialogItem>,
    onDismiss: () -> Unit,
) {
    PlayerSideDrawer(onDismiss = onDismiss, title = title) {
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { index, item ->
            PlayerItemRow(item)
            if (index < items.size - 1) {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/**
 * 播放器信息展示「抽屉版」（替代 [com.nichx.niplayer.designsystem.components.NiInfoDialog] 的播放器场景实现）。
 *
 * 标题 + 分隔线 + 自定义内容，改为右侧抽屉（媒体信息等长文本在横屏下更易读、不挡画面中心）。
 * 内容超长时自动滚动。
 *
 * @param title 标题
 * @param onDismiss 关闭回调
 * @param content 内容
 */
@Composable
fun PlayerInfoDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    PlayerSideDrawer(onDismiss = onDismiss, title = title) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            content()
        }
    }
}

/**
 * 播放器确认 Dialog（替代 [com.nichx.niplayer.designsystem.components.NiConfirmDialog]）。
 *
 * 标题 + 文本 + 确认/取消按钮。
 */
@Composable
fun PlayerConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "",
    dismissText: String = "",
) {
    // 默认按钮文案从资源解析（Composable 默认参数无法调用 stringResource）
    val resolvedConfirmText = confirmText.ifEmpty { stringResource(R.string.player_confirm) }
    val resolvedDismissText = dismissText.ifEmpty { stringResource(R.string.player_cancel) }
    PlayerDialog(onDismiss = onDismiss, maxWidth = 340, scrollable = false) {
        PlayerDialogTitle(text = title)
        HorizontalDivider(
            color = PlayerDivider,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = PlayerTextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(resolvedDismissText, color = PlayerTextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onConfirm) {
                Text(resolvedConfirmText, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 统一标题样式 */
@Composable
fun PlayerDialogTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        color = PlayerTextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 统一分隔线 */
@Composable
fun PlayerDialogDivider(horizontalPadding: Int = 16) {
    HorizontalDivider(
        color = PlayerDivider,
        modifier = Modifier.padding(horizontal = horizontalPadding.dp),
    )
}

/** 列表项行（暗色风格） */
@Composable
fun PlayerItemRow(item: NiDialogItem) {
    val isSelected = item.isSelected
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = if (isSelected) primaryColor else PlayerTextPrimary
    val iconTint = if (isSelected) primaryColor else (item.iconTint ?: PlayerTextPrimary)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp)
            .background(
                color = if (isSelected) PlayerSelectedBg else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = item.onClick)
            .padding(horizontal = 12.dp),
    ) {
        val icon = item.icon
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        } else if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(30.dp))
        }
        Text(
            text = item.label,
            color = item.labelColor ?: textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 暴露调色板供其他 Dialog 复用（如 SubtitleManageDialog 内的次级文字） */
object PlayerDialogColors {
    val background = PlayerDialogBg
    val textPrimary = PlayerTextPrimary
    val textSecondary = PlayerTextSecondary
    val border = PlayerBorder
    val divider = PlayerDivider
    val selectedBg = PlayerSelectedBg
}
