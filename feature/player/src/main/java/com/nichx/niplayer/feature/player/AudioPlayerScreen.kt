package com.nichx.niplayer.feature.player


import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.datastore.AudioPlayerStyle
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.designsystem.components.DownloadTargetChooserDialog
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.theme.MotionTokens
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
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
    val artist by audioPlaybackManager?.currentArtist?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") }
    val coverPath by audioPlaybackManager?.audioCoverPath?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    // 位置每秒上报一次：这里只**创建** State、不读取值，把"读取"下沉到真正显示的叶子
    // （进度条 / 时间文字 / 歌词），避免整页（含黑胶 Canvas）每秒重组。
    val positionMsState = audioPlaybackManager?.positionMs?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(0L) }
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

    // 外部打开 / 分享失败提示（仅本地源显示入口，失败时提示）
    val externalOpenFailedMsg = stringResource(R.string.player_external_open_failed)
    val onOpenWith: () -> Unit = {
        if (audioPlaybackManager?.requestOpenWithExternalApp() == false) {
            messageController.post(NiMessage.info(externalOpenFailedMsg))
        }
    }
    val onShareExternal: () -> Unit = {
        if (audioPlaybackManager?.requestShareExternal() == false) {
            messageController.post(NiMessage.info(externalOpenFailedMsg))
        }
    }

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

    // 音频播放器外观：默认读设置，主题切换走「更多 → 外观」二级菜单
    var playerStyle by remember { mutableStateOf(PlayerSettings.audioPlayerStyle) }
    val vinylStyleName = stringResource(R.string.player_audio_appearance_vinyl)
    val glassStyleName = stringResource(R.string.player_audio_appearance_glass)
    val appleMusicStyleName = stringResource(R.string.player_audio_appearance_apple_music)
    val appearanceSwitchedTemplate = stringResource(R.string.player_appearance_switched)
    val onStyleSelect: (AudioPlayerStyle) -> Unit = { next ->
        playerStyle = next
        PlayerSettings.audioPlayerStyle = next
        val nextName = when (next) {
            AudioPlayerStyle.VINYL -> vinylStyleName
            AudioPlayerStyle.GLASS -> glassStyleName
            AudioPlayerStyle.APPLE_MUSIC -> appleMusicStyleName
        }
        messageController.post(NiMessage.info(appearanceSwitchedTemplate.format(nextName)))
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
        // 背景：黑胶用弱化封面 + 底部渐变；简约封面用封面高斯模糊铺底；
        // Apple Music 的封面网格 + 贴顶封面由各自的布局自己画（见 AppleArtworkMeshBackdrop），
        // 这里只铺一层底色兜底——整屏 RenderEffect 模糊会拖慢首帧，也容易在 GPU 上翻车。
        // 外观切换：整块背景交叉淡入（原为硬切，切到 GLASS/APPLE 时会闪）。
        // 时长走 SURFACE，与弹窗/面板同源。
        Crossfade(
            targetState = playerStyle,
            animationSpec = tween(MotionTokens.SURFACE, easing = MotionTokens.easeEnter),
            modifier = Modifier.fillMaxSize(),
            label = "audioPlayerStyle",
        ) { style ->
            when (style) {
                AudioPlayerStyle.APPLE_MUSIC -> Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
                )
                AudioPlayerStyle.GLASS -> CoverBlurBackground(coverData = coverPath)
                AudioPlayerStyle.VINYL -> BackgroundLayer(coverData = coverPath)
            }
        }

        if (isLandscape) {
            LandscapeLayout(
                hasActiveContent = hasActiveContent,
                playbackError = playbackError,
                onRetry = { audioPlaybackManager?.retry() },
                lrcLines = lrcLines,
                positionMs = positionMsState,
                durationMs = durationMs,
                onSeek = { pos -> audioPlaybackManager?.seekTo(pos) },
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                hasPrev = hasPrev,
                hasNext = hasNext,
                onTogglePlay = { audioPlaybackManager?.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                coverPath = coverPath,
                style = playerStyle,
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
                showExternalActions = isLocalSource,
                onOpenWith = onOpenWith,
                onShare = onShareExternal,
                onStyleSelect = onStyleSelect,
            )
        } else {
            PortraitLayout(
                hasActiveContent = hasActiveContent,
                playbackError = playbackError,
                onRetry = { audioPlaybackManager?.retry() },
                showLyrics = showLyrics,
                lrcLines = lrcLines,
                positionMs = positionMsState,
                durationMs = durationMs,
                onSeek = { pos -> audioPlaybackManager?.seekTo(pos) },
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                hasPrev = hasPrev,
                hasNext = hasNext,
                onTogglePlay = { audioPlaybackManager?.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                coverPath = coverPath,
                style = playerStyle,
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
                showExternalActions = isLocalSource,
                onOpenWith = onOpenWith,
                onShare = onShareExternal,
                onStyleSelect = onStyleSelect,
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

        }
}
