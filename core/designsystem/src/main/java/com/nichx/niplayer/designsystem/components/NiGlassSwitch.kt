package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.nichx.niplayer.designsystem.glass.DampedDragAnimation
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import kotlinx.coroutines.flow.collectLatest

/**
 * 液态玻璃开关（Toggle）。
 *
 * 依据官方 Backdrop 库 [LiquidToggle](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidToggle.kt)
 * 完整移植（含 [DampedDragAnimation] 弹簧阻尼交互）：
 *
 * - **轨道**用 [rememberLayerBackdrop] 局部捕获自身绘制（选中/未选中底色），
 *   半透明底色下透出页面背景一并进入纹理；
 * - **滑块**用 [drawBackdrop] 引用该局部轨道 backdrop，叠加 blur + lens 折射 +
 *   高光 + 投影 + 内阴影 + 白色玻璃表层；
 * - **按压缩放**：按下/拖拽时滑块由 [DampedDragAnimation] 驱动放大到 [pressedScale]，
 *   高光与内阴影随按压进度增强、白色表层变透，释放回弹——与参考 demo 的 toggle 一致；
 * - 只引用局部轨道 backdrop、**不引用页面级捕获层**（[NiGlassSwitch] 常驻内容层内，
 *   引用页面 backdrop 会陷入捕获循环导致 SIGSEGV 崩溃）；
 * - **仅识别点击**（无拖拽手势）：点击即切换，按压/回弹动画由 [DampedDragAnimation]
 *   press/release 驱动；滑块位移/底色以弹簧阻尼动画过渡。
 *
 * @param checked 是否开启
 * @param onCheckedChange 切换回调
 * @param modifier 修饰符
 * @param enabled 是否可交互（false 时降低透明度）
 * @param checkedTint 选中态轨道着色（默认主题 primary；如 WebDAV 场景可传存储专属色）
 */
@Composable
fun NiGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedTint: Color = MaterialTheme.colorScheme.primary,
) {
    val isLightTheme = !NiExtraColors.current.isDark
    // 未选中轨道色：深浅主题各异的半透明灰
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(alpha = 0.2f)
        else Color(0xFF787880).copy(alpha = 0.36f)
    val density = LocalDensity.current
    // 滑块位移行程：轨道 64 - 滑块 40 - 两侧内边距 4
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    // 弹簧阻尼动画：fraction 由 checked 状态驱动，press/release 提供按压缩放反馈；
    // 不再挂载拖拽手势（inspectDragGestures），交互仅识别点击（见下方 detectTapGestures）。
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            canDrag = { enabled },
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { dampedDragAnimation.updateValue(it) }
    }
    LaunchedEffect(checked) {
        snapshotFlow { checked }
            .collectLatest { isChecked ->
                val target = if (isChecked) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    // 局部 backdrop：仅捕获轨道自身的绘制（含透出的页面背景）
    val trackBackdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .semantics {
                role = Role.Switch
                // 暴露开关检中状态，供 TalkBack 播报“已开启 / 已关闭”
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
            // 仅识别点击：滑块上不挂载任何拖拽手势节点，点击滑块本体/轨道均命中此处，
            // 点击即触发切换（按压/回弹动画由 checked 变化后的 animateToValue 驱动）。
            .pointerInput(enabled, checked) {
                detectTapGestures {
                    if (enabled) onCheckedChange(!checked)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // ─── 轨道：局部捕获源 + 底色 ───
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(ContinuousCapsule)
                .drawBehind {
                    drawRect(lerp(trackColor, checkedTint, dampedDragAnimation.value))
                }
                .size(width = 64.dp, height = 28.dp),
        )
        // ─── 滑块：液态玻璃 + 按压缩放反馈（无拖拽手势，点击由外层 Box 统一处理）───
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2f.dp.toPx()
                    translationX = lerp(padding, padding + dragWidth, dampedDragAnimation.value)
                }
                .drawBackdrop(
                    backdrop = trackBackdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        Shadow.Default.copy(
                            color = Color.Black.copy(alpha = if (isLightTheme) 0.1f else 0.2f),
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        // 白色玻璃表层：按压时变透，露出更多内部折射
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    },
                )
                .size(width = 40.dp, height = 24.dp),
        )
    }
}
