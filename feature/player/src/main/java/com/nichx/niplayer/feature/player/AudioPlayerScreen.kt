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
import java.util.Locale

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
    // 相邻曲目封面：黑胶主题左右滑动时预览邻居唱片。切歌/切播放模式即刷新，
    // 未命中项在后台补取（随机模式下"下一首"由播放器预定，因此预览与实际一致）。
    val neighborCovers by viewModel.neighborCovers.collectAsStateWithLifecycle()
    val lrcText by audioPlaybackManager?.lrcText?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val playbackError by audioPlaybackManager?.playbackError?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsStateWithLifecycle()
    // 音频续播提示位置：用户主动打开长音频（有声书等）且已存进度时非 null，弹「继续 / 从头」
    val audioResumePromptMs by viewModel.audioResumePromptMs.collectAsStateWithLifecycle()
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

    // 切歌 / 切播放模式后刷新相邻封面（随机模式下"下一首"由播放器预定，故预览与实际一致）。
    // 只有黑胶主题会用到相邻封面（左右滑动时的邻居唱片预览）；简约封面不做滑动切歌，
    // 也就没必要为它白白取图解码。主题切到黑胶时这里会重新取一次。
    LaunchedEffect(currentIndex, playMode, playerStyle) {
        if (playerStyle == AudioPlayerStyle.VINYL) {
            viewModel.refreshNeighborCovers()
        }
    }
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
        messageController.post(NiMessage.info(appearanceSwitchedTemplate.format(Locale.ROOT, nextName)))
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

    // 传输键可用性：与「能否真的切到**另一首**」一致，取播放器**预定**的下标。
    // 顺序/随机模式且列表 ≥ 2 首 → 可用；列表只有 1 首、或"下一首"就是当前曲目
    // （单曲循环）→ 禁用。
    // 原实现用 currentIndex 与列表边界比较（`in 0 until lastIndex` / `> 0`），
    // 在随机模式与首尾回绕下都是错的：末位明明能回绕到 0、随机模式也可能抽到更小的下标。
    val upcomingNextIndex by audioPlaybackManager?.upcomingNextIndex?.collectAsStateWithLifecycle()
        ?: remember { mutableIntStateOf(-1) }
    val upcomingPreviousIndex by audioPlaybackManager?.upcomingPreviousIndex?.collectAsStateWithLifecycle()
        ?: remember { mutableIntStateOf(-1) }
    val hasNext = upcomingNextIndex >= 0 && upcomingNextIndex != currentIndex
    val hasPrev = upcomingPreviousIndex >= 0 && upcomingPreviousIndex != currentIndex

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

        // 两个布局共用同一份状态与回调（见 AudioPlayerUi.kt）。
        // 原先这里为竖屏 / 横屏各写一遍实参表，且「两者差在哪」被摊在了调用点上。
        val uiState = AudioPlayerUiState(
            hasActiveContent = hasActiveContent,
            playbackError = playbackError,
            showLyrics = showLyrics,
            lrcLines = lrcLines,
            positionMs = positionMsState,
            durationMs = durationMs,
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            hasPrev = hasPrev,
            hasNext = hasNext,
            coverPath = coverPath,
            playlist = playlist,
            currentIndex = currentIndex,
            neighborPrevCover = neighborCovers.previous,
            neighborNextCover = neighborCovers.next,
            neighborPrevIndex = neighborCovers.previousIndex,
            neighborNextIndex = neighborCovers.nextIndex,
            playMode = playMode,
            modeIcon = modeIcon,
            modeLabel = stringResource(mode.labelRes),
            speedOptions = speedValues,
            currentSpeedIndex = speedIndex,
            sleepTimerText = sleepTimerText,
            showDownload = !isLocalSource,
            showExternalActions = isLocalSource,
        )
        val uiActions = AudioPlayerActions(
            onRetry = { audioPlaybackManager?.retry() },
            onSeek = { pos -> audioPlaybackManager?.seekTo(pos) },
            onTogglePlay = { audioPlaybackManager?.togglePlayPause() },
            onPrevious = { viewModel.playPrevious() },
            onNext = { viewModel.playNext() },
            onToggleLyrics = { showLyrics = !showLyrics },
            onCyclePlayMode = { audioPlaybackManager?.cyclePlayMode() },
            onShowPlaylist = { showPlaylist = true },
            onBack = onBack,
            onDownload = { viewModel.requestDownload() },
            onEqualizer = onEqualizer,
            onSpeedSelect = { speedIndex = it },
            onSleepTimer = { showSleepTimerDialog = true },
            onOpenWith = onOpenWith,
            onShare = onShareExternal,
            onStyleSelect = onStyleSelect,
        )

        if (isLandscape) {
            LandscapeLayout(state = uiState, actions = uiActions, style = playerStyle)
        } else {
            PortraitLayout(state = uiState, actions = uiActions, style = playerStyle)
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

        audioResumePromptMs?.let { savedPosition ->
            PlayerConfirmDialog(
                title = stringResource(R.string.player_resume_title),
                text = stringResource(R.string.player_resume_text, formatDuration(savedPosition)),
                onConfirm = { viewModel.clearAudioResumePrompt() },
                onDismiss = {
                    audioPlaybackManager?.seekTo(0)
                    viewModel.clearAudioResumePrompt()
                },
                confirmText = stringResource(R.string.player_resume_continue),
                dismissText = stringResource(R.string.player_play_from_start),
            )
        }

        }
}
