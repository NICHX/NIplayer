package com.nichx.niplayer.feature.player


import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.designsystem.components.DownloadTargetChooserDialog
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AudioPlayerScreen(
    onBack: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    audioPlaybackManager: AudioPlaybackManager? = null,
) {
    val messageController = LocalAppMessageController.current

    LaunchedEffect(Unit) {
        viewModel.downloadEvent.collect { msg ->
            messageController.post(NiMessage.info(msg))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            messageController.post(NiMessage.info(msg))
        }
    }

    // 所有播放状态直接从 AudioPlaybackManager 读取
    val isPlaying by audioPlaybackManager?.isPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val title by audioPlaybackManager?.currentTitle?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") }
    val coverPath by audioPlaybackManager?.audioCoverPath?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val positionMs by audioPlaybackManager?.positionMs?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0L) }
    val durationMs by audioPlaybackManager?.durationMs?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0L) }
    val playlist by audioPlaybackManager?.playlist?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val currentIndex by audioPlaybackManager?.currentIndex?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(-1) }
    val lrcText by audioPlaybackManager?.lrcText?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val playbackError by audioPlaybackManager?.playbackError?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsStateWithLifecycle()
    // 本地文件（已下载/缓存直链）来源时隐藏下载按钮
    val isLocalSource by audioPlaybackManager?.isLocalSource?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    // 睡眠定时剩余秒数（由 AudioPlaybackManager 常驻协程维护，后台播放仍生效）
    val sleepTimerRemaining by audioPlaybackManager?.sleepTimerRemaining?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<Int?>(null) }
    val sleepTimerText = sleepTimerRemaining?.let { formatSleepTimer(it) } ?: ""
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }
    var showManualMatchDialog by rememberSaveable { mutableStateOf(false) }

    val hasActiveContent = title.isNotEmpty()

    // 播放模式由 AudioPlaybackManager 统一管理（含持久化），UI 只读订阅
    val playMode by audioPlaybackManager?.playModeIndex?.collectAsStateWithLifecycle()
        ?: remember { mutableIntStateOf(0) }
    val mode = PlayMode.entries[playMode]
    val modeIcon = when (mode) {
        PlayMode.Loop -> Icons.Rounded.Repeat
        PlayMode.Shuffle -> Icons.Rounded.Shuffle
        PlayMode.Single -> Icons.Rounded.RepeatOne
    }

    val lrcLines = remember(lrcText) {
        if (lrcText != null) LrcParser.parse(lrcText!!) else emptyList()
    }

    var showLyrics by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }

    // 倍速：音频独立 4 档（0.5/1/1.5/2），偏好走 PlayerSettings.audioSpeedIndex，
    // 与视频 8 档互不影响
    val speedValues = AudioPlaybackManager.AudioPlaybackSpeedValues
    var speedIndex by rememberSaveable {
        mutableIntStateOf(PlayerSettings.audioSpeedIndex.coerceIn(0, speedValues.lastIndex))
    }
    LaunchedEffect(speedIndex) {
        audioPlaybackManager?.setPlaybackSpeed(speedValues[speedIndex])
        PlayerSettings.audioSpeedIndex = speedIndex
    }

    val hasNext = currentIndex in 0 until playlist.lastIndex
    val hasPrev = currentIndex > 0

    // 横屏 / 竖屏自适应：横屏用左右分栏布局（黑胶 + 控制区），竖屏用原单列布局
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 横屏沉浸全屏：隐藏系统状态栏与手势条，让唱片在无系统栏干扰下真正居中；
    // 轻扫屏幕边缘可临时唤出系统栏。退出横屏（回竖屏/离开页面）时恢复系统栏。
    val activity = LocalActivity.current
    DisposableEffect(isLandscape) {
        if (isLandscape) {
            val window = activity?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            val originalBehavior = controller?.systemBarsBehavior
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                activity?.window?.let { w ->
                    val c = WindowCompat.getInsetsController(w, w.decorView)
                    c.show(WindowInsetsCompat.Type.systemBars())
                    originalBehavior?.let { c.systemBarsBehavior = it }
                }
            }
        } else {
            onDispose { }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundLayer(coverData = coverPath)

        if (isLandscape) {
            LandscapeLayout(
                hasActiveContent = hasActiveContent,
                playbackError = playbackError,
                onRetry = { audioPlaybackManager?.retry() },
                lrcLines = lrcLines,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { pos -> audioPlaybackManager?.seekTo(pos) },
                title = title,
                isPlaying = isPlaying,
                hasPrev = hasPrev,
                hasNext = hasNext,
                onTogglePlay = { audioPlaybackManager?.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                coverPath = coverPath,
                playMode = playMode,
                modeIcon = modeIcon,
                modeLabel = stringResource(mode.labelRes),
                onCyclePlayMode = { audioPlaybackManager?.cyclePlayMode() },
                onShowPlaylist = { showPlaylist = true },
                onBack = onBack,
                onDownload = { viewModel.requestDownload() },
                onEqualizer = onEqualizer,
                speedOptions = speedValues,
                currentSpeedIndex = speedIndex,
                onSpeedSelect = { speedIndex = it },
                showDownload = !isLocalSource,
                sleepTimerText = sleepTimerText,
                onSleepTimer = { showSleepTimerDialog = true },
                onRematchLyrics = { audioPlaybackManager?.forceRematchLyrics() },
                onClearIgnoreLyrics = { audioPlaybackManager?.clearIgnoreCurrentLyrics() },
                onManualMatchLyrics = { showManualMatchDialog = true },
            )
        } else {
            PortraitLayout(
                hasActiveContent = hasActiveContent,
                playbackError = playbackError,
                onRetry = { audioPlaybackManager?.retry() },
                showLyrics = showLyrics,
                lrcLines = lrcLines,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { pos -> audioPlaybackManager?.seekTo(pos) },
                title = title,
                isPlaying = isPlaying,
                hasPrev = hasPrev,
                hasNext = hasNext,
                onTogglePlay = { audioPlaybackManager?.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                coverPath = coverPath,
                playlist = playlist,
                currentIndex = currentIndex,
                playMode = playMode,
                modeIcon = modeIcon,
                modeLabel = stringResource(mode.labelRes),
                onToggleLyrics = { showLyrics = !showLyrics },
                onCyclePlayMode = { audioPlaybackManager?.cyclePlayMode() },
                onShowPlaylist = { showPlaylist = true },
                onBack = onBack,
                onDownload = { viewModel.requestDownload() },
                onEqualizer = onEqualizer,
                speedOptions = speedValues,
                currentSpeedIndex = speedIndex,
                onSpeedSelect = { speedIndex = it },
                showDownload = !isLocalSource,
                sleepTimerText = sleepTimerText,
                onSleepTimer = { showSleepTimerDialog = true },
                onRematchLyrics = { audioPlaybackManager?.forceRematchLyrics() },
                onClearIgnoreLyrics = { audioPlaybackManager?.clearIgnoreCurrentLyrics() },
                onManualMatchLyrics = { showManualMatchDialog = true },
            )
        }

        PlaylistSheet(
            show = showPlaylist && playlist.isNotEmpty(),
            playlist = playlist,
            currentIndex = currentIndex,
            playMode = playMode,
            onDismiss = { showPlaylist = false },
            onPlayAtIndex = { index -> viewModel.playAtIndex(index) },
        )

        if (showDownloadDialog) {
            DownloadTargetChooserDialog(
                presetPath = DownloadSettings.downloadDirPath,
                onDismiss = { viewModel.closeDownloadDialog() },
                onDownloadToPreset = { viewModel.downloadToPreset() },
                onDownloadToPath = { path, dirName, setAsPreset ->
                    viewModel.downloadToPath(path, dirName, setAsPreset)
                },
            )
        }

        if (showSleepTimerDialog) {
            val items = buildList {
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 15), onClick = { audioPlaybackManager?.startSleepTimer(15); showSleepTimerDialog = false }))
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 30), onClick = { audioPlaybackManager?.startSleepTimer(30); showSleepTimerDialog = false }))
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 60), onClick = { audioPlaybackManager?.startSleepTimer(60); showSleepTimerDialog = false }))
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 90), onClick = { audioPlaybackManager?.startSleepTimer(90); showSleepTimerDialog = false }))
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 120), onClick = { audioPlaybackManager?.startSleepTimer(120); showSleepTimerDialog = false }))
                if (sleepTimerRemaining != null) {
                    add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_off), onClick = { audioPlaybackManager?.cancelSleepTimer(); showSleepTimerDialog = false }))
                }
            }
            NiListItemDialog(
                title = stringResource(R.string.player_sleep_timer),
                items = items,
                onDismiss = { showSleepTimerDialog = false },
            )
        }

        if (showManualMatchDialog) {
            ManualMatchLyricsDialog(
                onDismiss = { showManualMatchDialog = false },
                onConfirm = { t, a ->
                    showManualMatchDialog = false
                    audioPlaybackManager?.manualMatchLyrics(t, a)
                },
            )
        }

        }
}
