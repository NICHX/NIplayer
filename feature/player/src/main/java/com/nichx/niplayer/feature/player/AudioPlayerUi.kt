package com.nichx.niplayer.feature.player

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.vector.ImageVector
import com.nichx.niplayer.datastore.AudioPlayerStyle

/**
 * 音频播放器两个布局（竖屏 / 横屏）共用的**状态**。
 *
 * 为什么要有它：改造前 `PortraitLayout` 有 43 个参数、`LandscapeLayout` 有 35 个，
 * 其中 35 个完全相同 —— 调用点（`AudioPlayerScreen`）要为两个布局各写一遍 80 行的实参表，
 * 而且「两个布局差在哪」这件事也被摊在了调用点上。
 *
 * 这是**超集**设计：横屏用不到 [showLyrics] / [playlist] / [currentIndex] / 邻居四项，
 * 忽略即可（按主题拆骨架时再按需收窄）。
 *
 * 注意**不标 `@Immutable`**：`List<*>` / `Any?` 无法保证不可变，标了就是撒谎。
 * Kotlin 2.x 的 Compose 强跳过模式下，不稳定参数仍会按 `equals` 比较后再决定是否跳过，
 * 而这是 `data class`，`equals` 是结构比较 —— 语义正确。
 */
internal data class AudioPlayerUiState(
    /** 有可播放内容（标题非空）。 */
    val hasActiveContent: Boolean,
    val playbackError: String?,
    /** 竖屏：当前是否停在歌词页。横屏不用（歌词常驻右列）。 */
    val showLyrics: Boolean,
    val lrcLines: List<LrcLine>,
    /** 实时播放位置。传 `State` 而非 `Long`：让读它的那一小块单独重组。 */
    val positionMs: State<Long>,
    val durationMs: Long,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val hasPrev: Boolean,
    val hasNext: Boolean,
    val coverPath: Any?,
    /** 竖屏：歌单与当前下标（序号显示、换片下标）。横屏不用。 */
    val playlist: List<*>,
    val currentIndex: Int,
    /** 邻居封面与预定的上/下一首下标（-1 = 没有）。只有黑胶主题会读。 */
    val neighborPrevCover: Any?,
    val neighborNextCover: Any?,
    val neighborPrevIndex: Int,
    val neighborNextIndex: Int,
    val playMode: Int,
    val modeIcon: ImageVector,
    val modeLabel: String,
    val speedOptions: List<Float>,
    val currentSpeedIndex: Int,
    val sleepTimerText: String,
    val showDownload: Boolean,
    val showExternalActions: Boolean,
)

/** 音频播放器两个布局共用的**回调**。 */
internal data class AudioPlayerActions(
    val onRetry: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onTogglePlay: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    /** 竖屏：切换封面 ↔ 歌词页。横屏不用（歌词常驻）。 */
    val onToggleLyrics: () -> Unit,
    val onCyclePlayMode: () -> Unit,
    val onShowPlaylist: () -> Unit,
    val onBack: () -> Unit,
    val onDownload: () -> Unit,
    val onEqualizer: () -> Unit,
    val onSpeedSelect: (Int) -> Unit,
    val onSleepTimer: () -> Unit,
    val onOpenWith: () -> Unit,
    val onShare: () -> Unit,
    val onStyleSelect: (AudioPlayerStyle) -> Unit,
)
