package com.nichx.niplayer.feature.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import java.util.Locale


/**
 * 应用内嵌字幕（media3 cue）的垂直偏移（正=上移），统一改写 [Cue.line]。
 *
 * media3 定位：
 * - 文本 cue 默认 [Cue.DIMEN_UNSET] 贴底（bottomPaddingFraction），转成 END 锚定 line=1 后平移
 * - PGS 位图 cue 由 [Cue.line] = bitmapY/planeHeight（PgsParser 设置）决定，且只认 line，
 *   View padding 只能上移不能下移，改写 line 才能支持负值下移
 *
 * PGS 位图下移时预留位图高度作为 line 上限，避免顶锚点被推到屏幕底后内容溢出被裁。
 *
 * @param cue 原始 cue
 * @param offsetFraction 垂直偏移（相对画面高度，正=上移）
 * @return 改写后的 cue
 */
@OptIn(UnstableApi::class)
internal fun applySubtitlePositionOffset(cue: Cue, offsetFraction: Float): Cue {
    val isPlainText = cue.bitmap == null && cue.line == Cue.DIMEN_UNSET
    val baseLine = if (isPlainText) 1f else cue.line
    val anchor = if (isPlainText) Cue.ANCHOR_TYPE_END else cue.lineAnchor
    // 位图（PGS）下移上限：line 最多到 1-位图高度比例；位图高度用 16:9 cueBox 近似估算
    val maxLine = if (cue.bitmap != null) {
        val bmp = cue.bitmap
        val bmpHeightRatio = cue.bitmapHeight.takeIf { it != Cue.DIMEN_UNSET }
            ?: (bmp?.let { it.height.toFloat() / it.width * (16f / 9f) } ?: 0.2f).coerceIn(0.05f, 0.5f)
        (1f - bmpHeightRatio).coerceIn(0.5f, 1f)
    } else 1f
    val newLine = (baseLine - offsetFraction).coerceIn(0f, maxLine)
    return cue.buildUpon()
        .setLine(newLine, Cue.LINE_TYPE_FRACTION)
        .setLineAnchor(anchor)
        .build()
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/**
 * 短时长格式：只到分钟（`mm:ss`），**不**进位到小时。
 *
 * 与 [formatDuration] 的差异是**刻意的**：音频播放页与歌词页显示 `90:00` 而非 `1:30:00`。
 * 原先这两个变体各自以 `private fun formatTime` 重复存在于 3 个文件里（A4 拆分巨石文件时暴露），
 * 现合并到本模块级函数，实现逐字未变。
 */
internal fun formatDurationShort(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

internal fun formatClock(): String {
    val cal = java.util.Calendar.getInstance()
    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val m = cal.get(java.util.Calendar.MINUTE)
    return String.format(Locale.ROOT, "%02d:%02d", h, m)
}

internal fun formatSpeed(speed: Float): String {
    return String.format(Locale.ROOT, "%.1fx", speed)
}

/** 格式化网络下载速度（B/s → 可读字符串）。本地文件为 0 时不显示。 */
internal fun formatNetworkSpeed(speed: Long): String {
    return when {
        speed >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB/s", speed / 1_000_000f)
        speed >= 1_000 -> String.format(Locale.ROOT, "%.0f KB/s", speed / 1_000f)
        else -> "${speed} B/s"
    }
}

internal fun formatSleepTimer(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.ROOT, "- %d:%02d", minutes, secs)
}

internal fun subtitleMimeForUri(uri: android.net.Uri): String? {
    val path = uri.pathSegments.lastOrNull()?.lowercase(Locale.ROOT) ?: return null
    return when {
        path.endsWith(".srt") -> "application/x-subrip"
        path.endsWith(".ass") || path.endsWith(".ssa") -> "text/x-ssa"
        path.endsWith(".vtt") -> "text/vtt"
        else -> null
    }
}

internal val SPEED_LABELS = listOf("0.5x", "1.0x", "1.25x", "1.5x", "2.0x", "2.5x", "3.0x", "4.0x")
internal val SPEED_VALUES = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
