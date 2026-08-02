package com.nichx.niplayer.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiDialogItemRow
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.player.kernel.AudioTrackInfo
import com.nichx.niplayer.player.kernel.NxVideoScaleMode
import com.nichx.niplayer.player.kernel.PlaybackEvent
import com.nichx.niplayer.player.kernel.PlaybackState
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.SubtitleTrackInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private val AbLoopColorA = Color(0xFFFFAB40)
private val AbLoopColorB = Color(0xFFFF5252)
private val ThumbRadius = 8.dp

@Composable
@OptIn(UnstableApi::class)
fun PlayerScreen(
    onBack: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val bufferedMs by viewModel.bufferedMs.collectAsStateWithLifecycle()
    val videoSize by viewModel.videoSize.collectAsStateWithLifecycle()
    val effectiveVideoSize by viewModel.effectiveVideoSize.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val cues by viewModel.cues.collectAsStateWithLifecycle()
    val scaleIndex by viewModel.scaleIndex.collectAsStateWithLifecycle()
    val videoScaleMode by viewModel.nxPlayer.videoScaleMode.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val selectedAudioTrackIndex by viewModel.selectedAudioTrackIndex.collectAsStateWithLifecycle()
    val subtitleTracks by viewModel.subtitleTracks.collectAsStateWithLifecycle()
    val selectedSubtitleTrackIndex by viewModel.selectedSubtitleTrackIndex.collectAsStateWithLifecycle()
    val subtitleOffsetMs by viewModel.subtitleOffsetMs.collectAsStateWithLifecycle()
    val playlistInfo by viewModel.playlistInfo.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val mediaInfo by viewModel.mediaInfo.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val longPressSpeedActive by viewModel.longPressSpeedActive.collectAsStateWithLifecycle()
    val longPressSpeedLocked by viewModel.longPressSpeedLocked.collectAsStateWithLifecycle()
    val inLockZone by viewModel.inLockZone.collectAsStateWithLifecycle()
    val abLoopA by viewModel.abLoopA.collectAsStateWithLifecycle()
    val abLoopB by viewModel.abLoopB.collectAsStateWithLifecycle()
    val networkSpeed by viewModel.networkSpeed.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity
    // M-26 修复：activity 为 null 时（嵌入非 Activity 宿主）记录降级提示，
    // 强制横屏/亮度/PiP/系统 bar 控制等会静默失效，用户感知"功能没了"。
    // 此处不阻断渲染（UI 仍可播放），仅在需要 activity 的操作处检查 null 并给 OSD 提示。
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val window = activity?.window

    // M-25 修复：关键 UI 状态改用 rememberSaveable，横竖屏切换 Activity 重建时保留状态。
    // controllerVisible/speedIndex/locked/showXxxMenu 等均需保留避免用户操作中断。
    var controllerVisible by rememberSaveable { mutableStateOf(true) }
    var speedIndex by rememberSaveable {
        mutableIntStateOf(PlayerSettings.lastSpeedIndex.coerceIn(0, SPEED_VALUES.size - 1))
    }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var showSubtitleSearch by rememberSaveable { mutableStateOf(false) }
    var showSubtitleStyle by rememberSaveable { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var showSpeedMenu by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showAudioTrackMenu by rememberSaveable { mutableStateOf(false) }
    var showSubtitleMenu by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }
    var showMediaInfoDrawer by rememberSaveable { mutableStateOf(false) }
    var showLongPressSpeedDialog by rememberSaveable { mutableStateOf(false) }
    var showAbLoopDialog by rememberSaveable { mutableStateOf(false) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showBookmarkDialog by rememberSaveable { mutableStateOf(false) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }

    var gestureMode by remember { mutableStateOf(GestureMode.None) }
    var brightnessOsd by remember { mutableStateOf<Float?>(null) }
    var volumeOsd by remember { mutableStateOf<Float?>(null) }
    var previousMusicVolume by remember { mutableIntStateOf(-1) }
    var keyboardMuteVolume by remember { mutableIntStateOf(-1) }
    var scaleHint by remember { mutableStateOf<String?>(null) }
    var infoOsd by remember { mutableStateOf<String?>(null) }
    val tapHandler = remember { Handler(Looper.getMainLooper()) }
    var pendingSingleTap by remember { mutableStateOf<Runnable?>(null) }

    var resumeDialogMs by remember { mutableStateOf<Long?>(null) }

    var autoBlackBarCrop by remember { mutableStateOf(PlayerSettings.autoDetectBlackBars) }

    /**
     * 退出播放时截取最后一帧设为缩略图。
     *
     * 在返回导航前通过 PixelCopy 抓取 SurfaceView 当前帧并存入 ViewModel，
     * [PlayerViewModel.onCleared] 中优先使用此 Bitmap 保存为缩略图。
     * 对 SMB/WebDAV 更可靠（直接取渲染输出，不依赖 MediaMetadataRetriever 网络读取）。
     *
     * 是否抓帧由 [PlayerViewModel.shouldCaptureThumbnailOnExit] 决定（含生成策略
     * 门控与 HDR 例外）："关闭"策略下跳过避免无谓截图开销；HDR 播放
     * （Dolby Vision / HDR10 / HLG）也跳过——PixelCopy 从 10-bit HDR surface
     * 抓帧在部分设备上返回损坏数据（白屏 + 品红块），改由 getFrameAtTime 路径生成。
     */
    val captureThumbnailOnExit: () -> Unit = {
        if (viewModel.shouldCaptureThumbnailOnExit()) {
            val sv = surfaceViewRef
            if (sv != null && sv.width > 0 && sv.height > 0) {
                val bitmap = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(sv, bitmap, { result ->
                        if (result == PixelCopy.SUCCESS) {
                            viewModel.setLastFrameBitmap(bitmap)
                        } else {
                            bitmap.recycle()
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    bitmap.recycle()
                }
            }
        }
    }

    val capturedBack: () -> Unit = {
        captureThumbnailOnExit()
        onBack()
    }

    // BUG-10 修复：onPause 时保存播放进度，兜底进程被杀 / 后台被回收场景
    // （onCleared 仅在 ViewModel 销毁时触发，Activity 后台被杀时可能不触发）
    // M-23 修复：ON_PAUSE 同时暂停播放，避免后台视频继续解码消耗电池、干扰系统息屏
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.saveProgress()
                    // M-23：后台时暂停播放。PiP 场景由 ON_PAUSE 后单独的 PiP 入口处理，
                    // 进入 PiP 前不应暂停；此处先暂停，PiP 转前台后由 ON_RESUME 决定是否恢复
                    if (viewModel.nxPlayer.state.value is PlaybackState.Playing) {
                        viewModel.nxPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // ON_RESUME 不自动恢复播放：用户主动从后台回来时应保持暂停态
                    // 避免锁屏/后台→前台自动起播打扰用户
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /**
     * 执行 PixelCopy 抓图并触发黑边检测。
     *
     * 调用时机：
     * 1. 首帧渲染后（PlaybackEvent.RenderingStart + 300ms 延迟）
     * 2. 从 Crop/Stretch 切回 Fit 时（redetectBlackBars 事件 + 200ms 延迟）
     */
    val triggerBlackBarDetection: () -> Unit = {
        val sv = surfaceViewRef
        if (sv != null && sv.width > 0 && sv.height > 0) {
            val bitmap = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(sv, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        viewModel.applyBlackBarDetection(bitmap)
                    } else {
                        bitmap.recycle()
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                bitmap.recycle()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.screenshotEvent.collect { message ->
            infoOsd = message
        }
    }

    // BUG-20：切集失败等错误信息通过 OSD 显示
    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { message ->
            infoOsd = message
        }
    }

    LaunchedEffect(Unit) {
        viewModel.abLoopEvent.collect { message ->
            infoOsd = message
        }
    }

    // P3-3：检测到 HDR 视频格式后，首帧 OSD 提示一次
    LaunchedEffect(Unit) {
        viewModel.hdrEvent.collect { hdrType ->
            infoOsd = hdrType
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resumeEvent.collect { positionMs ->
            resumeDialogMs = positionMs
        }
    }

    // 智能黑边检测：首帧渲染后抓图触发检测
    // 延迟 300ms 等待首帧稳定（避免抓到缓冲过程中的过渡帧）
    // M-28 修复：原实现 collect 内 delay(300) 阻塞后续 RenderingStart 事件。
    // 改用 launch 子协程并行处理 delay，不阻塞 collect，连续事件都能被处理。
    LaunchedEffect(Unit) {
        viewModel.nxPlayer.events.collect { event ->
            if (event is PlaybackEvent.RenderingStart) {
                launch {
                    delay(300)
                    triggerBlackBarDetection()
                }
            }
        }
    }

    // 从 Crop/Stretch 切回 Fit 时重新触发黑边检测
    // M-28 修复：同上，delay 改用 launch 子协程，不阻塞 collect
    LaunchedEffect(Unit) {
        viewModel.redetectBlackBars.collect {
            launch {
                delay(200) // 等待 SurfaceView 切回 Fit 比例后再抓图
                triggerBlackBarDetection()
            }
        }
    }

    // 切换视频源时重置检测结果（title 变化代表换台）
    LaunchedEffect(title) {
        if (title.isNotEmpty()) {
            viewModel.resetBlackBarDetection()
        }
    }

    val takeScreenshot: () -> Unit = {
        val sv = surfaceViewRef
        val act = activity
        if (sv == null || act == null) {
            infoOsd = "截图失败：播放器未就绪"
        } else {
            val w = sv.width
            val h = sv.height
            if (w <= 0 || h <= 0) {
                infoOsd = "截图失败：画面尺寸无效"
            } else {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(sv, bitmap, { result ->
                        if (result == PixelCopy.SUCCESS) {
                            viewModel.saveScreenshot(bitmap)
                        } else {
                            infoOsd = "截图失败：PixelCopy 错误 $result"
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    infoOsd = "截图失败：${e.message}"
                }
            }
        }
    }

    val handleKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) {
            false
        } else {
            when (keyEvent.key) {
                Key.Spacebar, Key.K, Key.Enter -> { viewModel.togglePlayPause(); true }
                Key.DirectionRight, Key.L -> {
                    val t = (positionMs + 10_000).coerceAtMost(durationMs.coerceAtLeast(1L))
                    viewModel.seekTo(t); true
                }
                Key.DirectionLeft, Key.J -> {
                    val t = (positionMs - 10_000).coerceAtLeast(0L)
                    viewModel.seekTo(t); true
                }
                Key.DirectionUp -> { adjustVolume(audioManager, +1); true }
                Key.DirectionDown -> { adjustVolume(audioManager, -1); true }
                Key.M -> {
                    keyboardMuteVolume = toggleMute(audioManager, keyboardMuteVolume)
                    previousMusicVolume = keyboardMuteVolume
                    true
                }
                Key.F -> { toggleOrientation(activity); true }
                // M-24 修复：锁定状态下拦截 Escape / 后退键，避免口袋误触直接退出播放器
                // 锁屏核心目的就是防误触，包括系统后退；用户需先解锁再退出
                Key.Escape -> {
                    if (locked) {
                        infoOsd = "请先解锁屏幕"
                        true
                    } else {
                        capturedBack(); true
                    }
                }
                Key.Zero, Key.One, Key.Two, Key.Three, Key.Four,
                Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine -> {
                    val digit = when (keyEvent.key) {
                        Key.Zero -> 0; Key.One -> 1; Key.Two -> 2; Key.Three -> 3; Key.Four -> 4
                        Key.Five -> 5; Key.Six -> 6; Key.Seven -> 7; Key.Eight -> 8; Key.Nine -> 9
                        else -> 0
                    }
                    if (durationMs > 0) viewModel.seekTo(durationMs * digit / 10)
                    true
                }
                else -> false
            }
        }
    }

    DisposableEffect(Unit) {
        val original = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val originalBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        val saved = PlayerSettings.lastBrightness
        if (saved >= 0f) {
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = saved
                w.attributes = attrs
            }
        }

        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalSystemBarsBehavior = insetsController?.systemBarsBehavior
        activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            activity?.requestedOrientation = original

            window?.let { w ->
                val current = w.attributes.screenBrightness
                if (current >= 0f) PlayerSettings.lastBrightness = current
                val attrs = w.attributes
                attrs.screenBrightness = originalBrightness
                w.attributes = attrs
            }
            activity?.window?.let { w ->
                val controller = WindowCompat.getInsetsController(w, w.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                originalSystemBarsBehavior?.let { controller.systemBarsBehavior = it }
            }
            pendingSingleTap?.let { tapHandler.removeCallbacks(it) }
        }
    }

    LaunchedEffect(
        controllerVisible, state, locked,
        showSpeedMenu, showMoreMenu, showAudioTrackMenu, showSubtitleMenu,
        showSubtitleSearch, showSubtitleStyle, showSleepTimerDialog, showMediaInfoDrawer,
        showLongPressSpeedDialog, showAbLoopDialog, showPlaylistDialog, showBookmarkDialog,
    ) {
        if (controllerVisible && !locked
            && !showSpeedMenu && !showMoreMenu && !showAudioTrackMenu && !showSubtitleMenu
            && !showSubtitleSearch && !showSubtitleStyle && !showSleepTimerDialog && !showMediaInfoDrawer
            && !showLongPressSpeedDialog && !showAbLoopDialog && !showPlaylistDialog
            && !showBookmarkDialog
            && state is PlaybackState.Playing
            && (longPressSpeedActive == null || longPressSpeedLocked)
        ) {
            delay(3000)
            controllerVisible = false
        }
    }

    LaunchedEffect(brightnessOsd, volumeOsd) {
        if (brightnessOsd != null || volumeOsd != null) {
            delay(1500)
            brightnessOsd = null
            volumeOsd = null
        }
    }

    LaunchedEffect(scaleHint) {
        if (scaleHint != null) {
            delay(1200)
            scaleHint = null
        }
    }

    LaunchedEffect(speedIndex) {
        PlayerSettings.lastSpeedIndex = speedIndex
    }

    LaunchedEffect(Unit) {
        viewModel.downloadEvent.collect { msg ->
            infoOsd = msg
        }
    }

    LaunchedEffect(infoOsd) {
        if (infoOsd != null) {
            delay(2000)
            infoOsd = null
        }
    }

    val currentScreenBrightness: () -> Float = {
        val sb = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (sb >= 0f) {
            sb
        } else {
            val sys = try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    128,
                )
            } catch (e: Exception) {
                128
            }
            (sys / 255f).coerceIn(0.05f, 1f)
        }
    }

    val applyBrightness: (Float) -> Unit = { value ->
        window?.let { w ->
            val attrs = w.attributes
            attrs.screenBrightness = value
            w.attributes = attrs
        }
    }

    val subtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            subtitleMimeForUri(uri)?.let { viewModel.addSubtitle(uri, it) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .onPreviewKeyEvent(handleKeyEvent),
    ) {
        if (title.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NoSourceHint(onBack = capturedBack)
            }
            return@BoxWithConstraints
        }

        // Fit 模式优先使用智能黑边检测后的有效宽高比（去除视频自带黑边）
        // 检测到黑边时：SurfaceView 用 effectiveVideoSize 比例 + media3 切到裁剪模式
        // → 16:9 视频帧保持比例裁剪填满 2.35:1 surface，正好裁掉上下黑边，画面不变形
        val activeVideoSize = effectiveVideoSize?.takeIf { it.isValid } ?: videoSize
        val videoAspect = if (activeVideoSize.isValid) activeVideoSize.aspectRatio else 16f / 9f
        val screenAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
        val surfaceModifier = when (videoScaleMode) {
            NxVideoScaleMode.Stretch -> {
                // 拉伸：忽略视频比例，填满屏幕（画面变形）
                Modifier.align(Alignment.Center).fillMaxSize()
            }
            NxVideoScaleMode.Crop -> {
                // 裁剪：短边填满屏幕，长边按视频比例溢出，由父 Box clipToBounds 裁剪
                // SurfaceView 尺寸 = 视频比例 × 屏幕短边，media3 无需裁剪（视频精确填满 surface）
                if (videoAspect >= screenAspect) {
                    // 视频比屏幕宽：高度=屏幕高，宽度=高×视频比例（左右溢出裁剪）
                    Modifier.align(Alignment.Center)
                        .requiredHeight(maxHeight)
                        .requiredWidth(maxHeight * videoAspect)
                } else {
                    // 视频比屏幕窄：宽度=屏幕宽，高度=宽/视频比例（上下溢出裁剪）
                    Modifier.align(Alignment.Center)
                        .requiredWidth(maxWidth)
                        .requiredHeight(maxWidth / videoAspect)
                }
            }
            NxVideoScaleMode.Fit -> {
                // 适应：视频完整显示在屏幕内（长边填满，短边留黑边）
                if (videoAspect >= screenAspect) {
                    Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(videoAspect)
                } else {
                    Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(videoAspect)
                }
            }
            NxVideoScaleMode.Ratio16_9 -> {
                // 强制 16:9：忽略视频原始宽高比，始终以 16:9 比例填满短边，画面可能变形
                if (screenAspect >= 16f / 9f) {
                    Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(16f / 9f)
                } else {
                    Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f)
                }
            }
        }
        AndroidView(
            modifier = surfaceModifier,
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            viewModel.nxPlayer.attachSurface(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            viewModel.nxPlayer.attachSurface(null)
                        }
                    })
                    keepScreenOn = true
                    surfaceViewRef = this
                }
            },
        )

        // 内嵌字幕：media3 fractionalTextSize 相对视图高度，竖屏高度暴增导致字号过大，
        // 按 360dp 横屏参考高度折算，保持竖屏绝对字号与横屏一致
        val portraitConfig = LocalConfiguration.current
        val embeddedSubtitleFraction =
            if (portraitConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                (SubtitleSettings.textSizeFraction * 360f / portraitConfig.screenHeightDp)
                    .coerceIn(0.02f, 0.12f)
            } else {
                SubtitleSettings.textSizeFraction
            }
        AndroidView(
            modifier = if (portraitConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                // 竖屏：SubtitleView 画布需匹配视频显示区域（16:9 窄条）的宽高比，
                // 否则 media3 按画布高度百分比拉伸 PGS 位图（bitmapHeight 已设置时不保持宽高比）
                // → 竖屏 1080x2400 画布下 16:9 位图被纵向拉伸成瘦高
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspect)
                    .align(Alignment.Center)
                    .padding(bottom = SubtitleSettings.bottomPaddingDp.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SubtitleSettings.bottomPaddingDp.dp)
            },
            factory = { ctx ->
                SubtitleView(ctx).apply {
                    setApplyEmbeddedStyles(SubtitleSettings.applyEmbeddedStyles)
                    setFractionalTextSize(embeddedSubtitleFraction)
                }
            },
            update = {
                it.setCues(cues)
                it.setApplyEmbeddedStyles(SubtitleSettings.applyEmbeddedStyles)
                it.setFractionalTextSize(embeddedSubtitleFraction)
            },
        )

        // 外挂字幕渲染层（ASS/SSA/SRT 自渲染，支持特效与字幕偏移）
        // 内嵌字幕仍由上面的 SubtitleView (media3 cues) 处理
        // bottomPadding / fontFamily / 描边等样式由 SubtitleOverlay 内部直接读 SubtitleSettings
        SubtitleOverlay(
            engine = viewModel.subtitleEngine,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        val startTime = System.currentTimeMillis()
                        val longPressTimeout = PlayerSettings.longPressTimeoutMs
                        val seekSensitivity = PlayerSettings.seekSensitivity
                        val doubleTapStepMs = PlayerSettings.doubleTapStepSeconds * 1000L
                        var lastX = startX
                        var lastY = startY
                        var dragged = false
                        var longPressTriggered = false
                        var initialBrightness = 0.5f
                        var initialVolume = 0

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                val duration = System.currentTimeMillis() - startTime
                                if (longPressTriggered) {
                                    if (inLockZone) {
                                        viewModel.lockLongPressSpeed()
                                    } else {
                                        viewModel.releaseLongPressSpeed(SPEED_VALUES[speedIndex])
                                    }
                                    viewModel.setInLockZone(false)
                                    longPressTriggered = false
                                }
                                if (!dragged && duration < 250) {
                                    if (locked) {
                                        controllerVisible = !controllerVisible
                                        break
                                    }
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTimeMs < 280) {
                                        pendingSingleTap?.let { tapHandler.removeCallbacks(it) }
                                        pendingSingleTap = null
                                        lastTapTimeMs = 0L
                                        if (doubleTapStepMs > 0) {
                                            val third = size.width / 3f
                                            when {
                                                startX < third -> {
                                                    viewModel.seekTo((positionMs - doubleTapStepMs).coerceAtLeast(0L))
                                                    infoOsd = "快退${doubleTapStepMs / 1000}秒"
                                                }
                                                startX > size.width * 2f / 3f -> {
                                                    viewModel.seekTo(
                                                        (positionMs + doubleTapStepMs).coerceAtMost(durationMs.coerceAtLeast(1L)),
                                                    )
                                                    infoOsd = "快进${doubleTapStepMs / 1000}秒"
                                                }
                                                else -> {
                                                    viewModel.togglePlayPause()
                                                }
                                            }
                                        }
                                    } else {
                                        lastTapTimeMs = now
                                        val r = Runnable {
                                            controllerVisible = !controllerVisible
                                            pendingSingleTap = null
                                        }
                                        pendingSingleTap = r
                                        tapHandler.postDelayed(r, 280)
                                    }
                                }
                                gestureMode = GestureMode.None
                                break
                            }

                            if (locked) continue

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - startX
                            val totalDy = change.position.y - startY

                            if (!dragged && !longPressTriggered
                                && System.currentTimeMillis() - startTime >= longPressTimeout
                                && state is PlaybackState.Playing
                            ) {
                                longPressTriggered = true
                                viewModel.applyLongPressSpeed()
                            }

                            if (longPressTriggered) {
                                val inZone = change.position.y > size.height * 0.8f
                                viewModel.setInLockZone(inZone)
                                change.consume()
                            } else {
                                if (!dragged && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                                    dragged = true
                                    gestureMode = if (abs(totalDx) > abs(totalDy)) {
                                        GestureMode.Seek
                                    } else if (startX < size.width / 2f) {
                                        initialBrightness = currentScreenBrightness()
                                        GestureMode.Brightness
                                    } else {
                                        initialVolume = audioManager?.getStreamVolume(
                                            AudioManager.STREAM_MUSIC
                                        ) ?: 0
                                        GestureMode.Volume
                                    }
                                }
                                when (gestureMode) {
                                    GestureMode.Seek -> {
                                        val durationMsValue = durationMs.takeIf { it > 0 } ?: 0L
                                        val pxToMs = if (size.width > 0) {
                                            durationMsValue.toFloat() / (size.width * seekSensitivity)
                                        } else 0f
                                        val target = (positionMs + (dx * pxToMs).toLong())
                                            .coerceIn(0L, durationMsValue.coerceAtLeast(1L))
                                        viewModel.seekTo(target)
                                        change.consume()
                                    }

                                    GestureMode.Brightness -> {
                                        val ratio = -totalDy / size.height
                                        val value = (initialBrightness + ratio).coerceIn(0f, 1f)
                                        applyBrightness(value)
                                        brightnessOsd = value
                                        change.consume()
                                    }

                                    GestureMode.Volume -> {
                                        val max = audioManager?.getStreamMaxVolume(
                                            AudioManager.STREAM_MUSIC
                                        ) ?: 1
                                        val ratio = -totalDy / size.height
                                        val value = (initialVolume + (ratio * max).toInt())
                                            .coerceIn(0, max)
                                        audioManager?.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            value,
                                            0,
                                        )
                                        volumeOsd = if (max > 0) value.toFloat() / max else 0f
                                        change.consume()
                                    }

                                    GestureMode.None -> Unit
                                }
                            }
                            lastX = change.position.x
                            lastY = change.position.y
                        }
                    }
                },
        )

        (state as? PlaybackState.Error)?.let { err ->
            // C-02 修复：错误覆盖层增加「重试 / 从头播放 / 退出」三按钮
            // 解决 SMB/WebDAV/FTP 断连、解码失败等场景下用户只能退出再重进的问题
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(20.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "播放出错",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = err.cause.message ?: err.cause::class.simpleName ?: "未知错误",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = { viewModel.retryPlayback() },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("重试")
                        }
                        TextButton(
                            onClick = { viewModel.restartFromStart() },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("从头播放")
                        }
                        TextButton(
                            onClick = { capturedBack() },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = Color.White.copy(alpha = 0.7f),
                            ),
                        ) {
                            Text("退出")
                        }
                    }
                }
            }
        }

        if (state is PlaybackState.Buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        AnimatedVisibility(
            visible = controllerVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (locked) {
                LockedOverlay(onToggleLock = { locked = !locked })
            } else {
                PlayerControllerLayer(
                    title = title,
                    state = state,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedMs = bufferedMs,
                    networkSpeed = networkSpeed,
                    speedIndex = speedIndex,
                    abLoopA = abLoopA,
                    abLoopB = abLoopB,
                    speedLabel = longPressSpeedActive?.let { formatSpeed(it) }
                        ?: SPEED_LABELS[speedIndex],
                    scaleIndex = scaleIndex,
                    playlistInfo = playlistInfo,
                    playlist = playlist,
                    currentIndex = currentIndex,
                    sleepTimerText = sleepTimerRemaining?.let { formatSleepTimer(it) } ?: "",
                    previousMusicVolume = previousMusicVolume,
                    onPreviousMusicVolumeChange = { previousMusicVolume = it },
                    onBack = capturedBack,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSeek = { fraction ->
                        if (durationMs > 0) {
                            val ms = (fraction * durationMs).toLong()
                            viewModel.seekTo(ms)
                        }
                    },
                    onSeekFinished = {
                    },
                    onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                    onToggleMoreMenu = { showMoreMenu = !showMoreMenu },
                    onToggleAudioTrackMenu = { showAudioTrackMenu = !showAudioTrackMenu },
                    onCycleScale = {
                        val newIndex = viewModel.cycleScaleMode()
                        scaleHint = SCALE_NAMES[newIndex]
                    },
                    onAddSubtitle = { showSubtitleMenu = true },
                    onSearchSubtitle = { showSubtitleSearch = true },
                    onToggleLock = { locked = !locked },
                    onSkipPrevious = { viewModel.playPrevious() },
                    onSkipNext = { viewModel.playNext() },
                    onRewind = {
                        val target = (positionMs - 10_000).coerceAtLeast(0L)
                        viewModel.seekTo(target)
                    },
                    onForward = {
                        val target = (positionMs + 10_000)
                            .coerceAtMost(durationMs.coerceAtLeast(1L))
                        viewModel.seekTo(target)
                    },
                    onScreenshot = { takeScreenshot() },
                    onSleepTimer = { showSleepTimerDialog = true },
                    onMediaInfo = { showMediaInfoDrawer = true },
                    onLongPressSpeed = { showLongPressSpeedDialog = true },
                    onShowAbLoopDialog = { showAbLoopDialog = true },
                    onQuickToggleAbLoop = {
                        val a = abLoopA
                        val b = abLoopB
                        when {
                            a == null -> viewModel.setAbLoopPointA()
                            b == null || b <= a -> viewModel.setAbLoopPointB()
                            else -> viewModel.clearAbLoop()
                        }
                    },
                    onPlayAtIndex = { viewModel.playAtIndex(it) },
                    onTogglePlaylistDialog = { showPlaylistDialog = true },
                    bookmarkPositions = bookmarks.map { it.positionMs },
                    onAddBookmark = {
                        viewModel.addBookmark()
                        infoOsd = "已添加书签"
                    },
                    onDownload = { viewModel.downloadCurrentFile() },
                )
            }
        }

        longPressSpeedActive?.let { speed ->
            val osdText = when {
                longPressSpeedLocked -> "${formatSpeed(speed)}  已锁定 · 点击解除"
                inLockZone -> "松手锁定 ${formatSpeed(speed)}"
                else -> "${formatSpeed(speed)}  长按快进"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (longPressSpeedLocked) Color(0xFFFFAB40).copy(alpha = 0.35f)
                        else Color.Black.copy(alpha = 0.3f)
                    )
                    .clickable(enabled = longPressSpeedLocked) {
                        viewModel.unlockLongPressSpeed(SPEED_VALUES[speedIndex])
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = if (longPressSpeedLocked || inLockZone) Color(0xFFFFAB40)
                        else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = osdText,
                        color = if (longPressSpeedLocked) Color.White
                        else Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        scaleHint?.let { hint ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AspectRatio,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "画面比例：$hint",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        infoOsd?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (inLockZone && longPressSpeedActive != null && !longPressSpeedLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFAB40).copy(alpha = 0.4f))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "↓ 松手锁定倍速",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        brightnessOsd?.let { value ->
            GestureOsd(
                icon = Icons.Rounded.BrightnessHigh,
                text = "${(value * 100).toInt()}%",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        volumeOsd?.let { value ->
            GestureOsd(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                text = "${(value * 100).toInt()}%",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (showSpeedMenu) {
            SpeedMenuDialog(
                speedIndex = speedIndex,
                onSelectPreset = { index ->
                    speedIndex = index
                    if (longPressSpeedLocked) {
                        viewModel.unlockLongPressSpeed(SPEED_VALUES[index])
                    } else {
                        viewModel.nxPlayer.setSpeed(SPEED_VALUES[index])
                    }
                    showSpeedMenu = false
                },
                onDismiss = { showSpeedMenu = false },
            )
        }

        if (showAudioTrackMenu) {
            val audioItems = buildList {
                add(NiDialogItem(
                    label = "自动",
                    isSelected = selectedAudioTrackIndex == -1,
                    onClick = {
                        viewModel.selectAudioTrack(-1)
                        showAudioTrackMenu = false
                    },
                ))
                audioTracks.forEach { track ->
                    add(NiDialogItem(
                        label = track.label,
                        isSelected = track.index == selectedAudioTrackIndex,
                        onClick = {
                            viewModel.selectAudioTrack(track.index)
                            showAudioTrackMenu = false
                        },
                    ))
                }
            }
            PlayerListDialog(
                title = "音轨",
                items = audioItems,
                onDismiss = { showAudioTrackMenu = false },
            )
        }

        if (showSubtitleMenu) {
            SubtitleManageDialog(
                subtitleTracks = subtitleTracks,
                selectedIndex = selectedSubtitleTrackIndex,
                offsetMs = subtitleOffsetMs,
                onSelectTrack = { viewModel.selectSubtitleTrack(it) },
                onAdjustOffset = { viewModel.adjustSubtitleOffset(it) },
                onResetOffset = { viewModel.resetSubtitleOffset() },
                onAddExternal = {
                    showSubtitleMenu = false
                    subtitleLauncher.launch(arrayOf("*/*"))
                },
                onSearch = {
                    showSubtitleMenu = false
                    showSubtitleSearch = true
                },
                onOpenStyle = {
                    showSubtitleMenu = false
                    showSubtitleStyle = true
                },
                onDismiss = { showSubtitleMenu = false },
            )
        }

        if (showSubtitleStyle) {
            SubtitleStyleDialog(
                onStyleChanged = { viewModel.refreshSubtitleStyle() },
                onDismiss = { showSubtitleStyle = false },
            )
        }

        if (showMoreMenu) {
            MoreMenuDialog(
                videoSize = videoSize,
                activity = activity,
                blackBarCropActive = autoBlackBarCrop,
                onDismiss = { showMoreMenu = false },
                onPictureInPicture = {
                    showMoreMenu = false
                    val w = videoSize.width
                    val h = videoSize.height
                    if (w > 0 && h > 0) {
                        val rational = Rational(w, h)
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(rational)
                            .build()
                        activity?.enterPictureInPictureMode(params)
                    }
                },
                onLongPressSpeed = { showMoreMenu = false; showLongPressSpeedDialog = true },
                onSleepTimer = { showMoreMenu = false; showSleepTimerDialog = true },
                onMediaInfo = { showMoreMenu = false; showMediaInfoDrawer = true },
                onToggleBlackBarCrop = {
                    showMoreMenu = false
                    autoBlackBarCrop = !autoBlackBarCrop
                    PlayerSettings.autoDetectBlackBars = autoBlackBarCrop
                    infoOsd = if (autoBlackBarCrop) "智能去黑边：已开启" else "智能去黑边：已关闭"
                    if (autoBlackBarCrop) {
                        triggerBlackBarDetection()
                    } else {
                        viewModel.resetBlackBarDetection()
                    }
                },
                onShowBookmarks = { showMoreMenu = false; showBookmarkDialog = true },
            )
        }

        if (showSubtitleSearch) {
            SubtitleSearchDialog(
                videoTitle = title,
                onSubtitleDownloaded = { uri, mime ->
                    viewModel.addSubtitle(uri, mime)
                    showSubtitleSearch = false
                },
                onDismiss = { showSubtitleSearch = false },
            )
        }

        if (showSleepTimerDialog) {
            val items = buildList {
                add(NiDialogItem(label = "15 分钟", onClick = { viewModel.startSleepTimer(15); showSleepTimerDialog = false }))
                add(NiDialogItem(label = "30 分钟", onClick = { viewModel.startSleepTimer(30); showSleepTimerDialog = false }))
                add(NiDialogItem(label = "60 分钟", onClick = { viewModel.startSleepTimer(60); showSleepTimerDialog = false }))
                add(NiDialogItem(label = "90 分钟", onClick = { viewModel.startSleepTimer(90); showSleepTimerDialog = false }))
                add(NiDialogItem(label = "120 分钟", onClick = { viewModel.startSleepTimer(120); showSleepTimerDialog = false }))
                if (sleepTimerRemaining != null) {
                    add(NiDialogItem(label = "关闭定时", onClick = { viewModel.cancelSleepTimer(); showSleepTimerDialog = false }))
                }
            }
            PlayerListDialog(
                title = "睡眠定时",
                items = items,
                onDismiss = { showSleepTimerDialog = false },
            )
        }

        if (showLongPressSpeedDialog) {
            val currentSpeed = viewModel.longPressSpeed
            val items = PlayerSettings.LONG_PRESS_SPEED_OPTIONS.map { speed ->
                NiDialogItem(
                    label = formatSpeed(speed),
                    isSelected = speed == currentSpeed,
                    onClick = {
                        viewModel.longPressSpeed = speed
                        showLongPressSpeedDialog = false
                    },
                )
            }
            PlayerListDialog(
                title = "长按倍速",
                items = items,
                onDismiss = { showLongPressSpeedDialog = false },
            )
        }

        if (showMediaInfoDrawer) {
            PlayerInfoDialog(
                title = "媒体信息",
                onDismiss = { showMediaInfoDrawer = false },
            ) {
                val info = mediaInfo
                if (info == null) {
                    Text(
                        text = "暂无媒体信息",
                        color = PlayerDialogColors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    MediaInfoRow("视频编码", info.videoCodec ?: "未知")
                    MediaInfoRow("音频编码", info.audioCodec ?: "未知")
                    MediaInfoRow("分辨率", info.resolution ?: "未知")
                    MediaInfoRow(
                        "码率",
                        info.bitrate?.let { "${it / 1000} kbps" } ?: "未知",
                    )
                    MediaInfoRow(
                        "帧率",
                        info.frameRate?.let { String.format(Locale.US, "%.2f fps", it) } ?: "未知",
                    )
                    MediaInfoRow("HDR", info.hdrType ?: "不支持")
                }
            }
        }

        if (showAbLoopDialog) {
            AbLoopDialog(
                abLoopA = abLoopA,
                abLoopB = abLoopB,
                durationMs = durationMs,
                positionMs = positionMs,
                onSetPointA = { viewModel.setAbLoopPointA() },
                onSetPointB = { viewModel.setAbLoopPointB() },
                onClearAbLoop = { viewModel.clearAbLoop() },
                onDismiss = { showAbLoopDialog = false },
            )
        }

        if (showPlaylistDialog) {
            PlaylistDialog(
                playlist = playlist,
                currentIndex = currentIndex,
                onPlayAtIndex = { index ->
                    viewModel.playAtIndex(index)
                    showPlaylistDialog = false
                },
                onDismiss = { showPlaylistDialog = false },
            )
        }

        if (showBookmarkDialog) {
            BookmarkListDialog(
                bookmarks = bookmarks,
                onSeek = { pos ->
                    viewModel.seekToBookmark(pos)
                    showBookmarkDialog = false
                },
                onDelete = { id -> viewModel.removeBookmark(id) },
                onDismiss = { showBookmarkDialog = false },
            )
        }

        resumeDialogMs?.let { savedPosition ->
            val resumeTime = formatTime(savedPosition)
            PlayerConfirmDialog(
                title = "续播提示",
                text = "检测到上次观看到 $resumeTime，是否继续播放？",
                onConfirm = { resumeDialogMs = null },
                onDismiss = {
                    viewModel.seekTo(0)
                    resumeDialogMs = null
                },
                confirmText = "继续播放",
                dismissText = "从头播放",
            )
        }
    }
}

// NI_ACCENT / NI_ACCENT_DARK 已移除，统一使用 MaterialTheme.colorScheme.primary

@Composable
private fun MoreMenuDialog(
    videoSize: com.nichx.niplayer.player.kernel.VideoSize,
    activity: Activity?,
    blackBarCropActive: Boolean,
    onDismiss: () -> Unit,
    onPictureInPicture: () -> Unit,
    onLongPressSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
    onMediaInfo: () -> Unit,
    onToggleBlackBarCrop: () -> Unit,
    onShowBookmarks: () -> Unit = {},
) {
    val pipEnabled = videoSize.isValid
    val actions = listOf(
        MoreAction(Icons.Rounded.Crop, if (blackBarCropActive) "去黑边：开" else "去黑边：关", onToggleBlackBarCrop, isActive = blackBarCropActive),
        MoreAction(Icons.Rounded.Speed, "长按倍速", onLongPressSpeed),
        MoreAction(Icons.Rounded.PictureInPictureAlt, "画中画", onPictureInPicture, enabled = pipEnabled),
        MoreAction(Icons.Rounded.Bedtime, "睡眠定时", onSleepTimer),
        MoreAction(Icons.Rounded.Info, "媒体信息", onMediaInfo),
        MoreAction(Icons.Rounded.Bookmark, "书签", onShowBookmarks),
    )
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    val onSurfaceVariant = PlayerDialogColors.textSecondary
    val outlineVariant = PlayerDialogColors.divider
    PlayerDialog(onDismiss = onDismiss, maxWidth = 320, scrollable = false) {
        Text(
            text = "更多",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()
        Spacer(Modifier.height(4.dp))
        actions.chunked(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { action ->
                    MoreMenuItem(
                        icon = action.icon,
                        label = action.label,
                        enabled = action.enabled,
                        onClick = action.onClick,
                        isActive = action.isActive,
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.size(72.dp))
                }
            }
        }
    }
}

private enum class GestureMode { None, Seek, Brightness, Volume }

private fun formatTime(ms: Long): String {
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

private fun formatClock(): String {
    val cal = java.util.Calendar.getInstance()
    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val m = cal.get(java.util.Calendar.MINUTE)
    return String.format(Locale.ROOT, "%02d:%02d", h, m)
}

private fun formatSpeed(speed: Float): String {
    return String.format(Locale.ROOT, "%.1fx", speed)
}

/** 格式化网络下载速度（B/s → 可读字符串）。本地文件为 0 时不显示。 */
private fun formatNetworkSpeed(speed: Long): String {
    return when {
        speed >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB/s", speed / 1_000_000f)
        speed >= 1_000 -> String.format(Locale.ROOT, "%.0f KB/s", speed / 1_000f)
        else -> "${speed} B/s"
    }
}

private fun formatSleepTimer(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.ROOT, "- %d:%02d", minutes, secs)
}

private fun getBatteryLevel(context: android.content.Context): Int {
    val manager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
    return manager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}

private fun adjustVolume(audioManager: android.media.AudioManager?, delta: Int) {
    if (audioManager == null) return
    val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    val target = (current + delta).coerceIn(0, max)
    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
}

private fun toggleMute(audioManager: android.media.AudioManager?, previousVolume: Int): Int {
    if (audioManager == null) return previousVolume
    val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    return if (current == 0) {
        val restore = if (previousVolume > 0) previousVolume else 5
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, restore, 0)
        restore
    } else {
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
        current
    }
}

private fun toggleOrientation(activity: android.app.Activity?) {
    activity ?: return
    val current = activity.resources.configuration.orientation
    if (current == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

@Composable
private fun NoSourceHint(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "无播放源",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack) {
            Text("返回")
        }
    }
}

@Composable
private fun GestureOsd(
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

private fun subtitleMimeForUri(uri: android.net.Uri): String? {
    val path = uri.pathSegments.lastOrNull()?.lowercase(Locale.ROOT) ?: return null
    return when {
        path.endsWith(".srt") -> "application/x-subrip"
        path.endsWith(".ass") || path.endsWith(".ssa") -> "text/x-ssa"
        path.endsWith(".vtt") -> "text/vtt"
        else -> null
    }
}

private data class MoreAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isActive: Boolean = false,
)

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = PlayerDialogColors.textSecondary
    val tint = when {
        isActive -> primary
        enabled -> primary
        else -> onSurfaceVariant.copy(alpha = 0.38f)
    }
    val bg = when {
        isActive -> primary.copy(alpha = 0.28f)
        enabled -> primary.copy(alpha = 0.10f)
        else -> PlayerDialogColors.divider
    }
    val textColor = when {
        isActive -> primary
        enabled -> PlayerDialogColors.textPrimary
        else -> onSurfaceVariant.copy(alpha = 0.38f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isActive) Modifier.border(
                    1.5.dp,
                    primary.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp),
                ) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
            .width(72.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AbLoopDialog(
    abLoopA: Long?,
    abLoopB: Long?,
    durationMs: Long,
    positionMs: Long,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAbLoop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isActive = abLoopA != null && abLoopB != null && abLoopB > abLoopA
    val aSet = abLoopA != null
    val posFormatted = formatTime(positionMs)
    val aFormatted = abLoopA?.let { formatTime(it) } ?: "未设置"
    val bFormatted = abLoopB?.let { formatTime(it) } ?: "未设置"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val outlineVariant = PlayerDialogColors.divider
        val onSurfaceVariant = PlayerDialogColors.textSecondary
        val surfaceVariant = PlayerDialogColors.background
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = PlayerDialogColors.background,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, PlayerDialogColors.border),
            modifier = Modifier.widthIn(min = 280.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {

                // 标题行：图标 + 标题 + 当前播放时间
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFFFFAB40) else onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "A-B 段循环",
                        color = PlayerDialogColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = posFormatted,
                        color = PlayerDialogColors.textPrimary.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(16.dp))

                // A/B 时间显示
                if (durationMs > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            Text("起点 A", fontSize = 11.sp, color = onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (aSet) Color(0xFFFFAB40) else outlineVariant),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (aSet) aFormatted else "未设置",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (aSet) Color(0xFFFFAB40) else onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("终点 B", fontSize = 11.sp, color = onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (abLoopB != null) bFormatted else "未设置",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (abLoopB != null) Color(0xFFFF5252) else onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (abLoopB != null) Color(0xFFFF5252) else outlineVariant),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 进度条
                    val aFrac = (abLoopA?.toFloat()?.div(durationMs) ?: 0f).coerceIn(0f, 1f)
                    val bFrac = (abLoopB?.toFloat()?.div(durationMs) ?: 0f).coerceIn(0f, 1f)
                    val posFrac = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val r = CornerRadius(h / 2, h / 2)

                            drawRoundRect(
                                color = surfaceVariant,
                                topLeft = Offset.Zero,
                                size = Size(w, h),
                                cornerRadius = r,
                            )

                            if (isActive) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFFFFAB40), Color(0xFFFFAB40), Color(0xFFFF5252), Color(0xFFFF5252)),
                                    ),
                                    topLeft = Offset(aFrac * w, 0f),
                                    size = Size((bFrac - aFrac).coerceAtLeast(2f) * w, h),
                                    cornerRadius = r,
                                )
                            } else if (aSet) {
                                drawRoundRect(
                                    color = Color(0xFFFFAB40).copy(alpha = 0.5f),
                                    topLeft = Offset(aFrac * w, 0f),
                                    size = Size(w * (1f - aFrac), h),
                                    cornerRadius = r,
                                )
                            }

                            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(posFrac * w, h / 2f))
                            drawCircle(color = Color(0xFF2095F4), radius = 2.dp.toPx(), center = Offset(posFrac * w, h / 2f))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 操作按钮 - 使用 Button 替代 TextButton，更大更醒目
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSetPointA,
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !aSet,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = if (aSet) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (aSet) "起点 $aFormatted" else "设起点 $posFormatted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = onSetPointB,
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = aSet && abLoopB == null,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = if (abLoopB != null) "终点 $bFormatted" else "设终点 $posFormatted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 状态提示 + 清除按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isActive -> Color(0xFFFFAB40).copy(alpha = 0.08f)
                                    aSet -> Color(0xFFFFAB40).copy(alpha = 0.05f)
                                    else -> surfaceVariant
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = when {
                                isActive -> "正在 ${aFormatted} ~ $bFormatted 之间循环"
                                aSet -> "起点已设，请设置终点 B"
                                else -> "播放到目标位置后点击「设起点」"
                            },
                            fontSize = 12.sp,
                            color = when {
                                isActive -> Color(0xFFFFAB40)
                                aSet -> Color(0xFFFFAB40)
                                else -> onSurfaceVariant
                            },
                        )
                    }

                    if (aSet) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onClearAbLoop,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text("清除", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // 快速操作提示
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "快速操作：长按控制器上的 A-B 按钮可快速设置起点/终点",
                        color = onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDialog(
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    onPlayAtIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    val outlineVariant = PlayerDialogColors.divider
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = PlayerDialogColors.background,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, PlayerDialogColors.border),
            modifier = Modifier.widthIn(min = 260.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "剧集列表 (${currentIndex + 1}/${playlist.size})",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PlayerDialogDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((playlist.size.coerceAtMost(8) * 52).dp),
                ) {
                    itemsIndexed(playlist) { index, item ->
                        val isCurrent = index == currentIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) primary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickable { onPlayAtIndex(index) }
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrent) primary
                                else onSurface.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(28.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.fileName,
                                color = if (isCurrent) primary else onSurface,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkListDialog(
    bookmarks: List<VideoBookmarkEntity>,
    onSeek: (Long) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = PlayerDialogColors.background,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, PlayerDialogColors.border),
            modifier = Modifier.widthIn(min = 260.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "书签列表 (${bookmarks.size})",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PlayerDialogDivider()
                if (bookmarks.isEmpty()) {
                    Text(
                        text = "暂无书签，点击顶栏书签图标可在当前进度添加标记",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        color = onSurface.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((bookmarks.size.coerceAtMost(8) * 52).dp),
                    ) {
                        itemsIndexed(bookmarks) { _, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSeek(item.positionMs) }
                                    .padding(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.label?.takeIf { it.isNotBlank() } ?: formatTime(item.positionMs),
                                    color = onSurface,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item.label != null) {
                                    Text(
                                        text = formatTime(item.positionMs),
                                        color = onSurface.copy(alpha = 0.4f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(item.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "删除书签",
                                        tint = onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerProgressBar(
    positionFraction: Float,
    bufferedFraction: Float,
    durationMs: Long,
    abLoopA: Long?,
    abLoopB: Long?,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    bookmarkPositions: List<Long> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val trackHeight = 4.dp
    val thumbRadius = 8.dp
    val isDragging = remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val primary = MaterialTheme.colorScheme.primary
    val primaryDark = MaterialTheme.colorScheme.primary

    val displayFraction = if (isDragging.value) dragFraction else positionFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight + thumbRadius * 2)
            .clip(RoundedCornerShape(trackHeight / 2))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging.value = true
                    dragFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(dragFraction)

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            isDragging.value = false
                            onSeekFinished()
                            break
                        }
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(dragFraction)
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .height(trackHeight),
        ) {
            val w = size.width
            val h = size.height
            val cornerRadius = h / 2

            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            )

            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    topLeft = Offset.Zero,
                    size = Size(w * bufferedFraction.coerceIn(0f, 1f), h),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
            }

            val display = displayFraction.coerceIn(0f, 1f)
            if (display > 0f) {
                val gradient = Brush.horizontalGradient(
                    colors = listOf(primaryDark, primary),
                    startX = 0f, endX = w * display,
                )
                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset.Zero,
                    size = Size(w * display, h),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
            }

            val a = abLoopA
            val b = abLoopB
            if (a != null && b != null && b > a && durationMs > 0) {
                val aX = (a.toFloat() / durationMs).coerceIn(0f, 1f) * w
                val bX = (b.toFloat() / durationMs).coerceIn(0f, 1f) * w
                drawRoundRect(
                    color = AbLoopColorA.copy(alpha = 0.4f),
                    topLeft = Offset(aX, 0f),
                    size = Size((bX - aX).coerceAtLeast(2f), h),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
                drawCircle(
                    color = AbLoopColorA,
                    radius = 4.dp.toPx(),
                    center = Offset(aX, h / 2f),
                )
                drawCircle(
                    color = AbLoopColorB,
                    radius = 4.dp.toPx(),
                    center = Offset(bX, h / 2f),
                )
            }

            // F-19：书签标记（绿色小圆点）
            if (bookmarkPositions.isNotEmpty() && durationMs > 0) {
                bookmarkPositions.forEach { pos ->
                    val x = (pos.toFloat() / durationMs).coerceIn(0f, 1f) * w
                    drawCircle(
                        color = Color(0xFF66BB6A),
                        radius = 3.dp.toPx(),
                        center = Offset(x, h / 2f),
                    )
                }
            }

            val thumbX = w * display
            drawCircle(
                color = Color.White,
                radius = thumbRadius.toPx() - 1.dp.toPx(),
                center = Offset(thumbX, h / 2f),
            )
            drawCircle(
                color = primary,
                radius = thumbRadius.toPx() - 2.dp.toPx(),
                center = Offset(thumbX, h / 2f),
            )
            drawCircle(
                color = Color.White,
                radius = (thumbRadius - 3.dp).toPx(),
                center = Offset(thumbX, h / 2f),
            )
        }
    }
}

@Composable
private fun SubtitleManageDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedIndex: Int,
    offsetMs: Long,
    onSelectTrack: (Int) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onAddExternal: () -> Unit,
    onSearch: () -> Unit,
    onOpenStyle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    val outlineVariant = PlayerDialogColors.divider
    PlayerDialog(onDismiss = onDismiss, maxWidth = 360, maxHeight = 560) {
        Text(
            text = "字幕",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        PlayerDialogDivider()

                Spacer(Modifier.height(8.dp))

                // 字幕轨道列表
                val autoSelectedTrack = if (selectedIndex == -1) {
                    subtitleTracks.firstOrNull { it.isAutoSelected }
                } else {
                    null
                }
                val trackItems = buildList<TrackOption> {
                    add(TrackOption("关闭", -2, "无字幕显示"))
                    add(
                        TrackOption(
                            "自动",
                            -1,
                            autoSelectedTrack?.let { "已使用：${it.label}" }
                                ?: "根据语言偏好自动选择",
                        )
                    )
                    subtitleTracks.forEach { track ->
                        add(TrackOption(track.label, track.index, "内嵌字幕"))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    trackItems.forEach { option: TrackOption ->
                        val isSelected = option.index == selectedIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .clickable { onSelectTrack(option.index) }
                                .padding(horizontal = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) primary
                                        else outlineVariant
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = option.label,
                                    color = if (isSelected) primary else onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    text = option.description,
                                    color = onSurface.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }

                // 字幕延迟调整
                HorizontalDivider(
                    color = outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "字幕延迟",
                                color = onSurface.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                            )
                            // 内嵌字幕偏移 STUB 提示：media3 暂无 setSubtitleOffsetMs API（issue #1976 Open）
                            // 仅外挂字幕（SubtitleEngine）真实生效
                            Text(
                                text = "仅外挂字幕生效",
                                color = onSurface.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(primary.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${offsetMs}ms",
                            color = primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val delayActions = listOf(
                        -1000L to "-1s",
                        -500L to "-0.5s",
                        -100L to "-0.1s",
                        0L to "重置",
                        100L to "+0.1s",
                        500L to "+0.5s",
                        1000L to "+1s",
                    )
                    delayActions.forEach { (delta, label) ->
                        TextButton(
                            onClick = {
                                if (delta == 0L) onResetOffset()
                                else onAdjustOffset(delta)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = label,
                                fontSize = if (delta == 0L) 12.sp else 11.sp,
                                fontWeight = if (delta == 0L) FontWeight.Bold else FontWeight.Medium,
                                color = if (delta == 0L) onSurface.copy(alpha = 0.5f)
                                    else primary,
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onAddExternal,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("外挂字幕", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onSearch,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("搜索字幕", fontSize = 13.sp)
                    }
                }

                // 字幕样式：字体/字号/颜色/描边/位置/应用内嵌样式（二级 Dialog）
                OutlinedButton(
                    onClick = onOpenStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("字幕样式", fontSize = 13.sp)
                }
    }
}

private data class TrackOption(
    val label: String,
    val index: Int,
    val description: String,
)

@Composable
private fun MediaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = PlayerDialogColors.textSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = value,
            color = PlayerDialogColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private val SPEED_LABELS = listOf("0.5x", "1.0x", "1.25x", "1.5x", "2.0x", "2.5x", "3.0x", "4.0x")
private val SPEED_VALUES = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
private val SCALE_NAMES = listOf("适应", "裁剪", "拉伸", "16:9")

@Composable
private fun SpeedMenuDialog(
    speedIndex: Int,
    onSelectPreset: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    PlayerDialog(onDismiss = onDismiss, maxWidth = 320, scrollable = false) {
        Text(
            text = "播放倍速",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()
        Spacer(Modifier.height(8.dp))
        SPEED_LABELS.chunked(4).forEachIndexed { rowIdx, rowItems ->
            val baseIndex = rowIdx * 4
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEachIndexed { colIdx, label ->
                    val index = baseIndex + colIdx
                    val selected = index == speedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) primary.copy(alpha = 0.10f)
                                else Color.Transparent
                            )
                            .clickable { onSelectPreset(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (selected) primary else onSurface,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LockedOverlay(onToggleLock: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Color.Black.copy(alpha = 0.5f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "解锁",
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerControllerLayer(
    title: String,
    state: PlaybackState,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    networkSpeed: Long,
    speedIndex: Int,
    abLoopA: Long?,
    abLoopB: Long?,
    speedLabel: String,
    scaleIndex: Int,
    playlistInfo: String,
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    sleepTimerText: String,
    previousMusicVolume: Int,
    onPreviousMusicVolumeChange: (Int) -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggleSpeedMenu: () -> Unit,
    onToggleMoreMenu: () -> Unit,
    onToggleAudioTrackMenu: () -> Unit,
    onCycleScale: () -> Unit,
    onAddSubtitle: () -> Unit,
    onSearchSubtitle: () -> Unit,
    onToggleLock: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit,
    onMediaInfo: () -> Unit,
    onLongPressSpeed: () -> Unit,
    onShowAbLoopDialog: () -> Unit,
    onQuickToggleAbLoop: () -> Unit,
    onPlayAtIndex: (Int) -> Unit,
    onTogglePlaylistDialog: () -> Unit,
    bookmarkPositions: List<Long> = emptyList(),
    onAddBookmark: () -> Unit = {},
    onDownload: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    var localPreviousVolume by remember { mutableIntStateOf(previousMusicVolume) }

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    LaunchedEffect(previousMusicVolume) {
        localPreviousVolume = previousMusicVolume
    }

    var clockText by remember { mutableStateOf(formatClock()) }
    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = formatClock()
            batteryLevel = getBatteryLevel(context)
            delay(30_000)
        }
    }

    Box(Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.0f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.0f),
                            Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            // A-B 循环指示（竖屏空间紧张时隐藏）
            if (abLoopA != null && abLoopB != null && abLoopB > abLoopA && !isPortrait) {
                Icon(
                    imageVector = Icons.Rounded.RepeatOne,
                    contentDescription = null,
                    tint = Color(0xFFFFAB40),
                    modifier = Modifier.size(20.dp),
                )
            }

            if (networkSpeed > 0L && !isPortrait) {
                Text(
                    text = formatNetworkSpeed(networkSpeed),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

            Box(
                modifier = Modifier.width(48.dp).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = clockText,
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }

            if (batteryLevel >= 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryFull,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }

            if (sleepTimerText.isNotEmpty()) {
                Text(
                    text = sleepTimerText,
                    color = Color(0xFFFFAB40),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            IconButton(onClick = onToggleMoreMenu, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // 中部 A-B 循环 / 锁定按钮（竖屏大幅下移避开挖孔；横屏垂直居中）
        // A-B 按钮：单击快速切换状态（设A→设B并循环→清除），长按打开详细对话框
        val middleButtonsTop = if (isPortrait) {
            // 竖屏：避开顶部挖孔/状态栏区域
            140.dp
        } else {
            // 横屏：垂直居中（按钮组高 = 3×48 + 2×12 = 168dp）
            ((LocalConfiguration.current.screenHeightDp - 168) / 2).coerceAtLeast(56).dp
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = middleButtonsTop),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    // Row 垂直居中：左列(3钮,168dp)比右列(2钮,108dp)只低出高度差的一半(30dp)，
                    // 上移 30dp 使 A-B、书签与右侧锁定、截图对齐
                    .offset(y = (-30).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(onClick = { toggleOrientation(activity) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = "旋转屏幕",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .combinedClickable(
                            onClick = onQuickToggleAbLoop,
                            onLongClick = onShowAbLoopDialog,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            abLoopA != null && abLoopB != null && abLoopB > abLoopA -> Icons.Rounded.RepeatOne
                            abLoopA != null -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "A-B 循环",
                        tint = when {
                            abLoopA != null && abLoopB != null && abLoopB > abLoopA -> Color(0xFFFFAB40)
                            abLoopA != null -> Color(0xFFFFAB40).copy(alpha = 0.6f)
                            else -> Color.White.copy(alpha = 0.9f)
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(onClick = onAddBookmark),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = "添加书签",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = "锁定",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(onClick = onScreenshot),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "截图",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            PlayerProgressBar(
                positionFraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                bufferedFraction = if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f,
                durationMs = durationMs,
                abLoopA = abLoopA,
                abLoopB = abLoopB,
                onSeek = onSeek,
                onSeekFinished = onSeekFinished,
                bookmarkPositions = bookmarkPositions,
            )

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(positionMs),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "-${formatTime((durationMs - positionMs).coerceAtLeast(0L))}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    val primary = MaterialTheme.colorScheme.primary
                    if (isPortrait) {
                        // 竖屏：倍速收为纯图标按钮，隐藏画面比例（空间有限）
                        IconButton(onClick = onToggleSpeedMenu, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = "倍速",
                                tint = if (speedIndex != 1) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    } else {
                        TextButton(
                            onClick = onToggleSpeedMenu,
                            modifier = Modifier.height(44.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = if (speedIndex != 1) primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = speedLabel,
                                    color = if (speedIndex != 1) primary else Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = if (speedIndex != 1) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        IconButton(onClick = onCycleScale, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = "画面比例",
                                tint = if (scaleIndex != 0) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    var muted by remember {
                        mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
                    }
                    LaunchedEffect(previousMusicVolume) {
                        muted = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                    }
                    IconButton(
                        onClick = {
                            val vol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                            if (vol == 0) {
                                val restore = if (localPreviousVolume > 0) localPreviousVolume
                                    else (max * 0.5f).toInt().coerceAtLeast(1)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0)
                                onPreviousMusicVolumeChange(restore)
                                muted = false
                            } else {
                                onPreviousMusicVolumeChange(vol)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                                muted = true
                            }
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff
                                else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (muted) "取消静音" else "静音",
                            tint = if (muted) primary else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (!isPortrait && playlist.size > 1) {
                        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "上一集",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    IconButton(onClick = onRewind, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "后退10秒",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                    ) {
                        Icon(
                            imageVector = if (state is PlaybackState.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state is PlaybackState.Playing) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = onForward, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "前进10秒",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    if (!isPortrait && playlist.size > 1) {
                        IconButton(onClick = onSkipNext, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "下一集",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!isPortrait) {
                        IconButton(onClick = onDownload, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "下载",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    IconButton(onClick = onToggleAudioTrackMenu, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "音轨",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = onAddSubtitle, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = "字幕",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (playlist.isNotEmpty() && !isPortrait) {
                        IconButton(onClick = onTogglePlaylistDialog, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ViewList,
                                contentDescription = "剧集列表",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}