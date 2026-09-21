package com.nichx.niplayer.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


internal enum class GestureMode { None, Seek, Brightness, Volume }

internal fun getBatteryLevel(context: android.content.Context): Int {
    val manager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
    return manager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}

internal fun adjustVolume(audioManager: android.media.AudioManager?, delta: Int) {
    if (audioManager == null) return
    val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    val target = (current + delta).coerceIn(0, max)
    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
    if (target > 0) VolumeState.restoreVolume = target
}

/**
 * 键盘 M 键静音切换。
 *
 * 修复 BUG：原实现分支写反（非静音时反而调大/静音时无动作）。现语义为：
 * - 当前有声 → 静音，并把当前音量写入 [VolumeState] 作为恢复值
 * - 当前静音 → 恢复到之前音量
 *
 * @return 供下次恢复使用的“静音前音量”
 */
internal fun toggleMute(audioManager: android.media.AudioManager?, previousVolume: Int): Int {
    if (audioManager == null) return previousVolume
    val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    return if (current > 0) {
        VolumeState.restoreVolume = current
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
        current
    } else {
        val restore = VolumeState.restoreVolume.takeIf { it > 0 }
            ?: previousVolume.takeIf { it > 0 }
            ?: (max * DEFAULT_RESTORE_VOLUME_RATIO).toInt().coerceAtLeast(1)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, restore, 0)
        VolumeState.restoreVolume = restore
        previousVolume
    }
}

/**
 * 音量/静音按钮切换（横竖屏两处渲染共用）。
 *
 * 与键盘 [toggleMute] 语义一致，关键差异是恢复值优先取自进程级 [VolumeState.restoreVolume]。
 *
 * 修复 BUG：原实现在静音态下用 `max * 0.5f` 作为回退。由于 [previousMusicVolume] 是
 * Composable 内 `remember`，每次打开新视频（独立 PlayerActivity）都重置为 -1，用户
 * “调到最小 → 连播几个视频 → 点击音量键解除静音”时会直接跳到一半音量，表现为声音突然变大。
 * 改为取跨 Activity 保留的恢复值，仍未知时才用保守默认（30%）。
 *
 * @return 本次点击后的静音状态（true=已静音，false=未静音）
 */
internal fun toggleVolumeButton(
    audioManager: android.media.AudioManager?,
    onPreviousVolumeChange: (Int) -> Unit,
): Boolean {
    if (audioManager == null) return false
    val vol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    if (vol == 0) {
        // 当前静音 → 恢复：优先用跨 Activity 保留的恢复值，避免跳到过高
        val restore = VolumeState.restoreVolume.takeIf { it > 0 }
            ?: (max * DEFAULT_RESTORE_VOLUME_RATIO).toInt().coerceAtLeast(1)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, restore, 0)
        VolumeState.restoreVolume = restore
        onPreviousVolumeChange(restore)
        return false
    } else {
        // 当前有声 → 静音，记录当前音量作为恢复值
        VolumeState.restoreVolume = vol
        onPreviousVolumeChange(vol)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
        return true
    }
}

/**
 * 跨 PlayerActivity 实例保存“解除静音时的恢复音量”。
 *
 * 视频播放器是独立 Activity，每次进入新视频都会重建 PlayerScreen，原先用 Composable
 * `remember` 保存 [PlayerScreen 的 previousMusicVolume] 会随之重置，导致解除静音时失去
 * 用户此前设定的音量而回退到过高默认值（声音突然变大）。此处用进程级单例在多次进入
 * Activity 间保留最近一次非零媒体音量。
 */
object VolumeState {
    /** 用户最近一次非零媒体音量；-1 表示本进程内尚未主动调整过音量。 */
    @Volatile
    var restoreVolume: Int = -1
}

/** 恢复静音时的保守默认音量比例（相对最大音量）。仅在进程内从未调过音量时作为兜底。 */
internal const val DEFAULT_RESTORE_VOLUME_RATIO = 0.3f

internal fun toggleOrientation(activity: android.app.Activity?) {
    activity ?: return
    val current = activity.resources.configuration.orientation
    if (current == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

@Composable
internal fun NoSourceHint(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.player_no_source),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack) {
            Text(stringResource(R.string.player_back))
        }
    }
}

@Composable
internal fun GestureOsd(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
