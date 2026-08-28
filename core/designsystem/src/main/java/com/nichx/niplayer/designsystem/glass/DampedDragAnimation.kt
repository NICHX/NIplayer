package com.nichx.niplayer.designsystem.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 弹簧阻尼的拖拽指示器动画：驱动玻璃液滴（pill）跟随手指平滑滑动，
 * 并对按压/释放产生阻尼缩放（scaleX/scaleY）反馈。
 * 按参考项目（legado-with-MD3 / KernelSU）原样移植。
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {

    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    // 标记当前是否已按下：仅在 canDrag 命中时才按压，释放时间步判定，避免禁用态空转动画
    private var isPressed = false

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        // 参考项目原样：非消费型 inspectDragGestures，可与 InteractiveHighlight 共存
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                if (canDrag(down.position)) {
                    isPressed = true
                    press()
                }
            },
            onDragEnd = {
                onDragStopped()
                if (isPressed) {
                    release()
                    isPressed = false
                }
            },
            onDragCancel = {
                onDragStopped()
                if (isPressed) {
                    release()
                    isPressed = false
                }
            },
        ) { change, dragAmount ->
            val position = change.position
            val previousPosition = change.previousPosition

            val isInside = canDrag(position)
            val wasInside = canDrag(previousPosition)

            if (isInside && wasInside) {
                onDrag(size, dragAmount)
            }
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val coercedTargetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch {
                valueAnimation.animateTo(coercedTargetValue, valueAnimationSpec) {
                    updateVelocity()
                }
            }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val coercedTargetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(coercedTargetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        // 用单调时钟（nanoTime）而非墙钟毫秒，避免掉帧/补帧时同毫秒多次采样，
        // 再配合 clamp 兜底，防止 calculateVelocity 产生异常速度放大 scale 形变
        velocityTracker.addPosition(System.nanoTime() / 1_000_000, Offset(value, 0f))
        val rawVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        val targetVelocity = rawVelocity.coerceIn(-3f, 3f)
        // 仅在目标明显变化时重启弹簧动画，避免拖动时每一帧取消并重建动画（协程风暴）
        if (abs(targetVelocity - velocityAnimation.targetValue) > 0.0001f) {
            animationScope.launch {
                velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
            }
        }
    }
}