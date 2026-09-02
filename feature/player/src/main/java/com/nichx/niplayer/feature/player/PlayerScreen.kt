package com.nichx.niplayer.feature.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nichx.niplayer.datastore.PlayerControlOrientation
import com.nichx.niplayer.datastore.PlayerControlSurface
import com.nichx.niplayer.datastore.PlayerControlLayout
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import com.nichx.niplayer.designsystem.components.DownloadTargetChooserDialog
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
@SuppressLint("LocalContextGetResourceValueCall")
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
    val preReadAspectRatio by viewModel.preReadAspectRatio.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val cues by viewModel.cues.collectAsStateWithLifecycle()
    val scaleIndex by viewModel.scaleIndex.collectAsStateWithLifecycle()
    val videoScaleMode by viewModel.nxPlayer.videoScaleMode.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val selectedAudioTrackIndex by viewModel.selectedAudioTrackIndex.collectAsStateWithLifecycle()
    val subtitleTracks by viewModel.subtitleTracks.collectAsStateWithLifecycle()
    val selectedSubtitleTrackIndex by viewModel.selectedSubtitleTrackIndex.collectAsStateWithLifecycle()
    val activeSubtitleTrackIndex by viewModel.activeSubtitleTrackIndex.collectAsStateWithLifecycle()
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
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsStateWithLifecycle()
    // 本地文件（已下载/缓存直链）来源时隐藏下载按钮
    val isLocalSource by viewModel.isLocalSource.collectAsStateWithLifecycle()

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

    // 换源过渡状态：setSource 到新源首帧渲染(RenderingStart)之间，SurfaceView 表面
    // 仍停留在旧帧残影上，而 media3 会提前触发新源的 onVideoSizeChanged 改变布局比例。
    // 若此时直接跟随新比例，旧帧会被压扁/拉伸（竖屏切换下一集时画面先压扁才切换）。
    // 故过渡期内冻结为切换前的显示比例，等新帧真正渲染后再解冻跟随。
    var sourceTransition by remember { mutableStateOf(false) }
    var frozenAspect by remember { mutableFloatStateOf(-1f) }

    // 画中画模式状态：PiP 中隐藏全部播放器控件（控制栏/手势/OSD/弹窗），
    // 仅保留视频画面与字幕，避免小窗内控件挤压遮挡（真机适配问题）
    var isInPip by remember { mutableStateOf(activity?.isInPictureInPictureMode ?: false) }

    // 进入 PiP 前的方向锁定值，PiP 期间释放为 UNSPECIFIED（部分设备方向锁定会拒绝/卡顿 PiP），
    // 退出小窗时恢复，避免影响用户手动旋转
    var pipPrevOrientation by remember { mutableStateOf<Int?>(null) }

    var gestureMode by remember { mutableStateOf(GestureMode.None) }
    var brightnessOsd by remember { mutableStateOf<Float?>(null) }
    var volumeOsd by remember { mutableStateOf<Float?>(null) }
    // 静音恢复值：初始取自进程级 VolumeState（跨 PlayerActivity 实例保留），
    // 避免新视频 Activity 把 previousMusicVolume 重置为 -1，解除静音时回退到过高的默认音量
    var previousMusicVolume by remember { mutableIntStateOf(VolumeState.restoreVolume) }
    var keyboardMuteVolume by remember { mutableIntStateOf(-1) }
    var scaleHint by remember { mutableStateOf<String?>(null) }
    var infoOsd by remember { mutableStateOf<String?>(null) }
    val scaleNames = listOf(
        stringResource(R.string.player_scale_fit),
        stringResource(R.string.player_scale_crop),
        stringResource(R.string.player_scale_stretch),
        "16:9",
    )
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

    // 进入播放器后的 1.5s 返回冷却期：此期间忽略返回操作。SurfaceView 在没有视频首帧时其
    // 空白层会透出白屏，刚进入立即退出会在返回动画里闪白；给首帧渲染留出时间再放行返回
    var backReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500)
        backReady = true
    }
    // 系统返回的统一处理在下方 capturedBack 定义后注册（BackHandler），保证手势/返回键
    // 与应用内返回按钮走同一套贴图退出逻辑

    // 退出转场贴图：播放中抓当前帧作贴图，SurfaceView 移除后用普通 Image 顶住该位置，
    // 随退出 fade 同步淡出（与控件一致）。独立于缩略图开关；DV/HDR 的 HDR buffer 用
    // PixelCopy 抓取会损坏（白屏+品红），检测到 HDR 时跳过贴图直接退出
    var exitFrame by remember { mutableStateOf<Bitmap?>(null) }

    // 进入播放器前的原始方向（通常为竖屏）。退出时先还原再 pop，避免返回页"下降"顿挫。
    // 不能读 PlayerActivity 自身的 requestedOrientation（它创建时恒为 UNSPECIFIED，非进入前
    // MainActivity 的方向）；要按进入时的物理朝向映射为"硬方向"，大屏(Android16 兼容声明生效)
    // 下才能确定性地转回原方向，否则只设置 UNSPECIFIED 会停留在大屏上旋转后的方向。
    val originalOrientation = remember {
        if (activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    // 进入播放器前的系统亮度（0~1）。退出时显式写回该值恢复原亮度：
    // 部分设备/ROM 对 BRIGHTNESS_OVERRIDE_NONE 不会真正清除窗口覆盖（退出后仍停留在播放器
    // 调整后的亮度），而显式写入具体亮度值是生效的，故退出时写回进入前亮度而非 NONE。
    val preEntryBrightness = remember {
        val sys = try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128,
            )
        } catch (e: Exception) {
            128
        }
        (sys / 255f).coerceIn(0.02f, 1f)
    }
    val capturedBack: () -> Unit = {
        // 1.5s 冷却内不响应返回（应用内返回按钮），避免首帧未到就退出导致闪白
        if (backReady) {
            // 立即恢复系统亮度：一触发返回亮度马上回到系统值（不等待抓帧/动画），
            // 让退出过渡全程以系统亮度呈现。写回进入前亮度值：BRIGHTNESS_OVERRIDE_NONE
            // 在本设备不生效（保持播放器内亮度），必须显式写回具体亮度值。
            // PixelCopy 读的是 surface 像素、不受窗口亮度设置影响，提前恢复不影响退出贴图。
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = preEntryBrightness
                w.attributes = attrs
            }
            captureThumbnailOnExit()

            val doExit: () -> Unit = {
                // 先还原方向：configChanges 拦截下瞬时切回竖屏，让深层文件浏览/首页等以竖屏稳定布局后
                // 再 popBackStack。否则方向还原发生在 onDispose（pop 动画之后），popEnter 播放期间
                // 返回页从横屏排布瞬间重排到竖屏排布，表现为主体内容向下坠落
                activity?.requestedOrientation = originalOrientation
                // 提前恢复系统栏：播放器进入时全屏隐藏了状态栏/导航栏（insetsController.hide），
                // 若等 onDispose 才恢复，首页 popEnter 首帧仍按"系统栏隐藏"的 insets 布局（偏高抵顶），
                // 待系统栏出现后 insets 让位造成整页下移到正确位置。这里在 pop 前恢复，让首页
                // 首帧即按正确 insets 就位。systemBarsBehavior 由 onDispose 兜底还原。
                activity?.window?.let { w ->
                    WindowCompat.getInsetsController(w, w.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
                // 亮度已在 capturedBack 入口提前恢复（见上），此处不再重复；
                // onDispose 仍保留兜底恢复（系统返回/异常路径未走 capturedBack 时）。
                onBack()
            }
            val sv = surfaceViewRef
            if (sv != null && sv.width > 0 && sv.height > 0 && mediaInfo?.hdrType == null) {
                // 非 HDR：抓当前帧作退出贴图（PixelCopy 一帧，几乎零成本），随后 Image 顶位随 fade 淡出
                val bmp = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(sv, bmp, { result ->
                        if (result == PixelCopy.SUCCESS) exitFrame = bmp else bmp.recycle()
                        doExit()
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    bmp.recycle()
                    doExit()
                }
            } else {
                // HDR / surface 未就绪：跳过贴图，直接退出
                doExit()
            }
        }
    }

    // 系统返回（返回键 / 手势释放）统一走 capturedBack：与应用内返回按钮同一套贴图退出逻辑。
    // 不依赖 NavHost 的 predictive back（那条路径会让 SurfaceView 不参与 fade），主动接管以
    // 保证手势/按键退出时画面与控件同步淡出
    BackHandler {
        capturedBack()
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
                    // M-23：普通退后台时暂停播放，避免后台视频继续解码消耗电池、干扰系统息屏。
                    // 进入画中画时 activity 处于 PiP 态，此时不暂停（PiP 需保持声音连续），
                    // 恢复大窗后由 ON_RESUME 直接继续播放
                    val inPip = activity?.isInPictureInPictureMode == true
                    if (!inPip && viewModel.nxPlayer.state.value is PlaybackState.Playing) {
                        viewModel.nxPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // ON_RESUME 不自动恢复播放：用户主动从后台回来时应保持暂停态
                    // 避免锁屏/后台→前台自动起播打扰用户（PiP 恢复大窗除外：PiP 中未暂停，无需恢复）
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // PiP 模式监听：进入/退出小窗时同步 isInPip 状态。
    // 退出 PiP 恢复大窗时显示控制栏，方便用户立即操作
    val componentActivity = activity as? ComponentActivity
    DisposableEffect(componentActivity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            val pip = info.isInPictureInPictureMode
            // 方向锁定兼容：PiP 期间释放方向锁定（部分设备方向锁定会拒绝进入 / 小窗卡顿），
            // 退出小窗时恢复进入前的锁定值，避免影响用户后续手动旋转
            if (pip) {
                pipPrevOrientation = activity?.requestedOrientation
                    ?.takeIf { it != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                pipPrevOrientation?.let { activity?.requestedOrientation = it }
                pipPrevOrientation = null
                controllerVisible = true
            }
            isInPip = pip
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    // 进入 PiP 时关闭所有打开中的弹窗：小窗内无法操作弹窗，
    // 且弹窗会遮挡小窗画面（画中画控件适配）
    LaunchedEffect(isInPip) {
        if (isInPip) {
            showSpeedMenu = false
            showMoreMenu = false
            showAudioTrackMenu = false
            showSubtitleMenu = false
            showSubtitleStyle = false
            showSubtitleSearch = false
            showSleepTimerDialog = false
            showMediaInfoDrawer = false
            showLongPressSpeedDialog = false
            showAbLoopDialog = false
            showPlaylistDialog = false
            showBookmarkDialog = false
        }
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
                // 新源首帧已渲染，解除换源过渡冻结，让画面比例跟随新源
                sourceTransition = false
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

    // 黑边检测失败（画面全黑/太暗）时自动重试：等画面变亮后重新抓图检测。
    // 多数影片首帧是黑屏，单次检测会返回 null，需自动重试直到画面变亮或达到上限
    LaunchedEffect(Unit) {
        viewModel.blackBarRetry.collect {
            launch {
                delay(500) // 等待画面变化（黑屏变亮/内容出现）
                triggerBlackBarDetection()
            }
        }
    }

    // 切换视频源时重置检测结果（title 变化代表换台）
    LaunchedEffect(title) {
        if (title.isNotEmpty()) {
            viewModel.resetBlackBarDetection()
            // 进入换源过渡：冻结当前显示比例（frozenAspect 已由主比例处持续跟踪，
            // 此处无需重新读取，避免 onVideoSizeChanged 覆盖目标比例前竞态取到新值），
            // 防止旧帧残影被新源比例压扁，待新源 RenderingStart 后再解冻。
            sourceTransition = true
        }
    }

    val takeScreenshot: () -> Unit = {
        val sv = surfaceViewRef
        val act = activity
        if (sv == null || act == null) {
            infoOsd = context.getString(R.string.player_screenshot_failed_not_ready)
        } else {
            val w = sv.width
            val h = sv.height
            if (w <= 0 || h <= 0) {
                infoOsd = context.getString(R.string.player_screenshot_failed_size)
            } else {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(sv, bitmap, { result ->
                        if (result == PixelCopy.SUCCESS) {
                            viewModel.saveScreenshot(bitmap)
                        } else {
                            infoOsd = context.getString(R.string.player_screenshot_failed_pixelcopy, result)
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    infoOsd = context.getString(
                        R.string.player_screenshot_failed_generic,
                        e.message ?: context.getString(R.string.player_unknown_error),
                    )
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
                        infoOsd = context.getString(R.string.player_unlock_screen_first)
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

    // 自动方向：预读成功时进入即定横/竖屏，否则等 videoSize 兜底校正。仅应用一次，避免覆盖用户手动切换
    var autoOrientationApplied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        // 默认方向：横屏 / 竖屏 / 自动。自动模式优先用播放前预读的宽高比直接锁定
        // （首帧渲染前方向已正确）；未预读成功则先按横屏，随后由下方 LaunchedEffect 校正
        val preRatio = preReadAspectRatio
        activity?.requestedOrientation = when (PlayerSettings.orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> if (preRatio != null) {
                if (preRatio < 1f) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalSystemBarsBehavior = insetsController?.systemBarsBehavior
        activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            // capturedBack 已提前还原方向，此处幂等兜底（系统返回/异常路径仍会走到这里）
            activity?.requestedOrientation = originalOrientation

            // 兜底恢复系统亮度：主恢复已提前到 capturedBack 入口；
            // 此处兜底覆盖未走 capturedBack 的异常路径。写回进入前亮度值（NONE 在本设备不生效）。
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = preEntryBrightness
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

    // 自动方向兜底：预读失败/缺失（无缩略图缓存且预读超时）时，等首个有效视频尺寸就绪后
    // 再按分辨率宽高比锁定横/竖屏（仅应用一次，预读成功时此兜底永不触发）
    LaunchedEffect(videoSize) {
        if (PlayerSettings.orientationMode == 2 &&
            preReadAspectRatio == null && videoSize.isValid && !autoOrientationApplied
        ) {
            autoOrientationApplied = true
            activity?.requestedOrientation = if (videoSize.aspectRatio >= 1f) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
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

    if (showDownloadDialog) {
        DownloadTargetChooserDialog(
            presetPath = DownloadSettings.downloadDirPath,
            onDismiss = { viewModel.closeDownloadDialog() },
            onDownloadToPreset = { viewModel.downloadToPreset() },
            onDownloadToPath = { path, dirName, setAsPreset ->
                viewModel.downloadToPath(path, dirName, setAsPreset)
            },
            // 视频播放器固定深色
            forceDark = true,
        )
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
        val targetAspect = if (activeVideoSize.isValid) activeVideoSize.aspectRatio else 16f / 9f

        // 非过渡期持续把 frozenAspect 跟踪为当前实际显示比例；
        // 换源过渡(sourceTransition=true)期间不再更新它，从而天然锁定"切换前的比例"，
        // 避免 onVideoSizeChanged 提前把目标比例切到新源导致旧帧残影被压扁。
        LaunchedEffect(targetAspect, sourceTransition) {
            if (!sourceTransition) {
                frozenAspect = targetAspect
            }
        }
        val videoAspect = if (sourceTransition && frozenAspect > 0f) frozenAspect else targetAspect

        // PiP 尺寸适配：小窗期间视频尺寸变化（切源/黑边检测完成/首帧渲染）时，
        // 同步更新系统 PiP 宽高比，避免小窗始终保持进入时的单一尺寸。
        // 走 PlayerActivity 统一入口以保留播放控制按钮与无缝尺寸调整。
        LaunchedEffect(isInPip, activeVideoSize) {
            if (isInPip && activeVideoSize.isValid) {
                if (activity is PlayerActivity) {
                    (activity as PlayerActivity).updatePipAspectRatio(activeVideoSize)
                } else {
                    runCatching {
                        activity?.setPictureInPictureParams(
                            PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(activeVideoSize.width, activeVideoSize.height))
                                .build(),
                        )
                    }
                }
            }
        }
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
        if (exitFrame != null) {
            // 退出转场：用抓取的当前帧贴图顶替 SurfaceView，随退出 fade 与控件同步淡出
            Image(
                bitmap = exitFrame!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = surfaceModifier,
            )
        } else {
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
        }

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
        // 订阅字幕样式版本：播放中改字幕位置/字体等样式后（SubtitleEngine.styleVersion 自增）
        // 触发重组，让内嵌 SubtitleView 实时生效
        val subtitleStyleVersion by viewModel.subtitleEngine.styleVersion.collectAsStateWithLifecycle()
        // 内嵌字幕（文本+PGS）垂直偏移：把"字幕位置"（dp，正=上移/负=下移）折算成相对
        // 屏幕高度的归一化偏移，统一改写 cue.line 实现。media3 对 PGS 位图只用 cue.line
        // 定位（PgsParser 设 bitmapY/planeHeight），View padding 只能上移不能下移，
        // 因此必须改写 cue.line 才能让 PGS 支持负值下移
        val subtitlePositionPx = with(LocalDensity.current) {
            remember(subtitleStyleVersion) { SubtitleSettings.bottomPaddingDp.dp.toPx() }
        }
        val subtitleOffsetFraction = if (maxHeight.value > 0f) {
            subtitlePositionPx / with(LocalDensity.current) { maxHeight.toPx() }
        } else 0f
        val adjustedCues = remember(cues, subtitleOffsetFraction) {
            if (subtitleOffsetFraction == 0f) cues
            else cues.map { cue -> applySubtitlePositionOffset(cue, subtitleOffsetFraction) }
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
            } else {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            },
            factory = { ctx ->
                SubtitleView(ctx).apply {
                    setApplyEmbeddedStyles(SubtitleSettings.applyEmbeddedStyles)
                    setFractionalTextSize(embeddedSubtitleFraction)
                    // media3 默认样式 CaptionStyleCompat.DEFAULT 的 backgroundColor 是不透明黑，
                    // SubtitlePainter 会为整条 cue 文本绘制 BackgroundColorSpan，导致内嵌字幕出现整行黑底。
                    // 这里改为透明背景，保留默认白色文字，仅去掉黑框。
                    setStyle(
                        CaptionStyleCompat(
                            AndroidColor.WHITE,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE,
                            AndroidColor.WHITE,
                            null,
                        )
                    )
                }
            },
            update = {
                it.setCues(adjustedCues)
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
                .pointerInput(locked, isInPip) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        // PiP 小窗内禁用全部手势（控制栏/OSD 均隐藏，避免误触干扰小窗画面）
                        if (isInPip) return@awaitEachGesture
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
                                                    infoOsd = context.getString(R.string.player_seek_backward_seconds, doubleTapStepMs / 1000)
                                                }
                                                startX > size.width * 2f / 3f -> {
                                                    viewModel.seekTo(
                                                        (positionMs + doubleTapStepMs).coerceAtMost(durationMs.coerceAtLeast(1L)),
                                                    )
                                                    infoOsd = context.getString(R.string.player_seek_forward_seconds, doubleTapStepMs / 1000)
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
                                        // 记录用户手势选定的非零音量，作为后续解除静音时的恢复值
                                        if (value > 0) VolumeState.restoreVolume = value
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

        // PiP 小窗内不展示错误三按钮层（控件无法在小窗适配），错误反馈由恢复大窗后呈现
        if (!isInPip) (state as? PlaybackState.Error)?.let { err ->
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
                        text = stringResource(R.string.player_error_hint),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = err.cause.message ?: err.cause::class.simpleName ?: stringResource(R.string.player_unknown_error),
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
                            Text(stringResource(R.string.player_retry))
                        }
                        TextButton(
                            onClick = { viewModel.restartFromStart() },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(stringResource(R.string.player_play_from_start))
                        }
                        TextButton(
                            onClick = { capturedBack() },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = Color.White.copy(alpha = 0.7f),
                            ),
                        ) {
                            Text(stringResource(R.string.player_exit))
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

        // ---- 控制功能自定义：把 PlayerControlLayout 里保存的布局翻译成 HUD 按钮与更多菜单 ----
        // 任意功能都可在 HUD 左列 / 右列 / 更多 之间自由移动；这里统一构建动作，HUD 与更多共用。
        val ctrlOrientation = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT)
            PlayerControlOrientation.PORTRAIT else PlayerControlOrientation.LANDSCAPE
        val enterPip: () -> Unit = {
            val size = activeVideoSize
            if (size.isValid) {
                if (activity is PlayerActivity) (activity as PlayerActivity).enterPip(size)
                else activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(size.width, size.height))
                        .build(),
                )
            }
        }
        @Composable
        fun ctrlButtonUnit(id: String): HudButtonConfig? = when (id) {
            "rotate" -> HudButtonConfig(
                id, Icons.Rounded.ScreenRotation,
                stringResource(R.string.player_rotate_screen),
                onClick = { toggleOrientation(activity) },
            )
            "ab_loop" -> {
                val aa = abLoopA
                val bb = abLoopB
                HudButtonConfig(
                    id, AbLoopIcon,
                    stringResource(R.string.player_ab_loop_title),
                    tint = if (aa != null && bb != null && bb > aa) Color(0xFFFFAB40)
                    else if (aa != null) Color(0xFFFFAB40).copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.9f),
                    iconSize = 32.dp,
                    onClick = {
                        when {
                            aa == null -> viewModel.setAbLoopPointA()
                            bb == null || bb <= aa -> viewModel.setAbLoopPointB()
                            else -> viewModel.clearAbLoop()
                        }
                    },
                    onLongClick = { showAbLoopDialog = true },
                )
            }
            "black_bar_crop" -> HudButtonConfig(
                id, Icons.Rounded.Crop,
                stringResource(if (autoBlackBarCrop) R.string.player_crop_black_bar_on else R.string.player_crop_black_bar_off),
                tint = if (autoBlackBarCrop) Color(0xFFFFAB40) else Color.White,
                onClick = {
                    autoBlackBarCrop = !autoBlackBarCrop
                    PlayerSettings.autoDetectBlackBars = autoBlackBarCrop
                    infoOsd = if (autoBlackBarCrop) context.getString(R.string.player_black_bar_crop_on)
                    else context.getString(R.string.player_black_bar_crop_off)
                    if (autoBlackBarCrop) triggerBlackBarDetection()
                    else viewModel.resetBlackBarDetection()
                },
            )
            "lock" -> HudButtonConfig(
                id, Icons.Rounded.LockOpen,
                stringResource(R.string.player_lock),
                tint = Color.White.copy(alpha = 0.9f),
                onClick = { locked = !locked },
            )
            "screenshot" -> HudButtonConfig(
                id, Icons.Rounded.PhotoCamera,
                stringResource(R.string.player_screenshot),
                onClick = { takeScreenshot() },
            )
            "long_press_speed" -> HudButtonConfig(
                id, Icons.Rounded.Speed,
                stringResource(R.string.player_long_press_speed),
                onClick = { showLongPressSpeedDialog = true },
            )
            "pip" -> HudButtonConfig(
                id, Icons.Rounded.PictureInPictureAlt,
                stringResource(R.string.player_picture_in_picture),
                onClick = enterPip,
            )
            "sleep_timer" -> HudButtonConfig(
                id, Icons.Rounded.Bedtime,
                stringResource(R.string.player_sleep_timer),
                onClick = { showSleepTimerDialog = true },
            )
            "media_info" -> HudButtonConfig(
                id, Icons.Rounded.Info,
                stringResource(R.string.player_media_info),
                onClick = { showMediaInfoDrawer = true },
            )
            "bookmarks" -> HudButtonConfig(
                id, Icons.Rounded.Bookmark,
                stringResource(R.string.player_bookmark),
                onClick = { showBookmarkDialog = true },
            )
            else -> null
        }
        val ctrlEntries = PlayerControlLayout.ALL_IDS.mapIndexed { i, id ->
            PlayerControlLayout.loadEntry(id, i, ctrlOrientation)
        }
        // HUD 侧边按钮：配置为 左/右 列且可见的功能
        val hudButtons = ctrlEntries
            .filter { it.visible && it.surface != PlayerControlSurface.MORE }
            .sortedBy { it.order }
            .mapNotNull { e ->
                ctrlButtonUnit(e.id)?.copy(
                    order = e.order,
                    side = if (e.surface == PlayerControlSurface.LEFT) HudButtonSide.LEFT else HudButtonSide.RIGHT,
                )
            }
        // 更多菜单项：配置为「更多」面且可见的功能（pip 需有效尺寸才可点）
        val moreActions = ctrlEntries
            .filter { it.visible && it.surface == PlayerControlSurface.MORE }
            .sortedBy { it.order }
            .mapNotNull { e ->
                val b = ctrlButtonUnit(e.id) ?: return@mapNotNull null
                MoreAction(
                    id = e.id,
                    icon = b.icon,
                    label = b.contentDescription,
                    onClick = b.onClick,
                    enabled = e.id != "pip" || activeVideoSize.isValid,
                )
            }

        AnimatedVisibility(
            // PiP 小窗内隐藏控制栏，由系统 PiP 控件接管（画中画控件适配）
            visible = controllerVisible && !isInPip,
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
                        scaleHint = scaleNames[newIndex]
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
                    blackBarCropActive = autoBlackBarCrop,
                    onToggleBlackBarCrop = {
                        autoBlackBarCrop = !autoBlackBarCrop
                        PlayerSettings.autoDetectBlackBars = autoBlackBarCrop
                        infoOsd = if (autoBlackBarCrop) context.getString(R.string.player_black_bar_crop_on) else context.getString(R.string.player_black_bar_crop_off)
                        if (autoBlackBarCrop) triggerBlackBarDetection()
                        else viewModel.resetBlackBarDetection()
                    },
                    onDownload = { viewModel.requestDownload() },
                    showDownload = !isLocalSource,
                    hudButtons = hudButtons,
                )
            }
        }

        if (!isInPip) longPressSpeedActive?.let { speed ->
            val osdText = when {
                longPressSpeedLocked -> stringResource(R.string.player_speed_locked, formatSpeed(speed))
                inLockZone -> stringResource(R.string.player_speed_lock_on_release, formatSpeed(speed))
                else -> stringResource(R.string.player_speed_long_press, formatSpeed(speed))
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

        if (!isInPip) scaleHint?.let { hint ->
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
                        text = stringResource(R.string.player_scale_hint, hint),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (!isInPip) infoOsd?.let { text ->
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

        if (!isInPip && inLockZone && longPressSpeedActive != null && !longPressSpeedLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 底部挖孔 inset 是硬件稳定值，系统栏隐藏时不变化，避免进入时被导航栏顶起再下移的跳变
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFAB40).copy(alpha = 0.4f))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.player_speed_release_to_lock),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (!isInPip) brightnessOsd?.let { value ->
            GestureOsd(
                icon = Icons.Rounded.BrightnessHigh,
                text = "${(value * 100).toInt()}%",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (!isInPip) volumeOsd?.let { value ->
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
                    label = stringResource(R.string.player_audio_track_auto),
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
                title = stringResource(R.string.player_audio_track),
                items = audioItems,
                onDismiss = { showAudioTrackMenu = false },
            )
        }

        if (showSubtitleMenu) {
            SubtitleManageDialog(
                subtitleTracks = subtitleTracks,
                selectedIndex = selectedSubtitleTrackIndex,
                activeSubtitleTrackIndex = activeSubtitleTrackIndex,
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
            // 更多菜单由同一份自定义目录驱动（surface==MORE && 可见），与 HUD 设置实时同步
            MoreMenuDialog(
                onDismiss = { showMoreMenu = false },
                actions = moreActions,
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
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 15), onClick = { viewModel.startSleepTimer(15); showSleepTimerDialog = false }))
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 30), onClick = { viewModel.startSleepTimer(30); showSleepTimerDialog = false }))
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 60), onClick = { viewModel.startSleepTimer(60); showSleepTimerDialog = false }))
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 90), onClick = { viewModel.startSleepTimer(90); showSleepTimerDialog = false }))
                add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_minutes, 120), onClick = { viewModel.startSleepTimer(120); showSleepTimerDialog = false }))
                if (sleepTimerRemaining != null) {
                    add(NiDialogItem(label = stringResource(R.string.player_sleep_timer_off), onClick = { viewModel.cancelSleepTimer(); showSleepTimerDialog = false }))
                }
            }
            PlayerListDialog(
                title = stringResource(R.string.player_sleep_timer),
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
                title = stringResource(R.string.player_long_press_speed),
                items = items,
                onDismiss = { showLongPressSpeedDialog = false },
            )
        }

        if (showMediaInfoDrawer) {
            PlayerInfoDialog(
                title = stringResource(R.string.player_media_info),
                onDismiss = { showMediaInfoDrawer = false },
            ) {
                val info = mediaInfo
                if (info == null) {
                    Text(
                        text = stringResource(R.string.player_no_media_info),
                        color = PlayerDialogColors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    MediaInfoRow(stringResource(R.string.player_media_info_video_codec), info.videoCodec ?: stringResource(R.string.player_media_info_unknown))
                    MediaInfoRow(stringResource(R.string.player_media_info_audio_codec), info.audioCodec ?: stringResource(R.string.player_media_info_unknown))
                    MediaInfoRow(stringResource(R.string.player_media_info_resolution), info.resolution ?: stringResource(R.string.player_media_info_unknown))
                    MediaInfoRow(
                        stringResource(R.string.player_media_info_bitrate),
                        info.bitrate?.let { "${it / 1000} kbps" } ?: stringResource(R.string.player_media_info_unknown),
                    )
                    MediaInfoRow(
                        stringResource(R.string.player_media_info_frame_rate),
                        info.frameRate?.let { String.format(Locale.US, "%.2f fps", it) } ?: stringResource(R.string.player_media_info_unknown),
                    )
                    MediaInfoRow(stringResource(R.string.player_media_info_hdr), info.hdrType ?: stringResource(R.string.player_media_info_unsupported))
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
                title = stringResource(R.string.player_resume_title),
                text = stringResource(R.string.player_resume_text, resumeTime),
                onConfirm = { resumeDialogMs = null },
                onDismiss = {
                    viewModel.seekTo(0)
                    resumeDialogMs = null
                },
                confirmText = stringResource(R.string.player_resume_continue),
                dismissText = stringResource(R.string.player_play_from_start),
            )
        }
    }
}

// NI_ACCENT / NI_ACCENT_DARK 已移除，统一使用 MaterialTheme.colorScheme.primary

@Composable
private fun MoreMenuDialog(
    onDismiss: () -> Unit,
    /** 更多菜单中的动作列表（已按用户自定义过滤 + 排序，且与 HUD 配置同步）。 */
    actions: List<MoreAction>,
) {
    val onSurface = PlayerDialogColors.textPrimary
    // scrollable = true：把所有功能放进「更多」时项很多，容器在限高内滚动，避免内容超出窗口被裁切。
    PlayerDialog(onDismiss = onDismiss, maxWidth = 340, scrollable = true) {
        Text(
            text = stringResource(R.string.player_more),
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
                    .padding(horizontal = 2.dp, vertical = 3.dp),
            ) {
                // 固定 3 等份槽位，保证各行的图标/项目落在同一列，末行项数不足也不错位
                repeat(3) { i ->
                    val action = row.getOrNull(i)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (action != null) {
                            MoreMenuItem(
                                icon = action.icon,
                                label = action.label,
                                enabled = action.enabled,
                                onClick = action.onClick,
                                isActive = action.isActive,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class GestureMode { None, Seek, Brightness, Volume }

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
private fun applySubtitlePositionOffset(cue: Cue, offsetFraction: Float): Cue {
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
private fun toggleMute(audioManager: android.media.AudioManager?, previousVolume: Int): Int {
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
private fun toggleVolumeButton(
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
private const val DEFAULT_RESTORE_VOLUME_RATIO = 0.3f

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
    val id: String,
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
    val aFormatted = abLoopA?.let { formatTime(it) } ?: stringResource(R.string.player_ab_loop_not_set)
    val bFormatted = abLoopB?.let { formatTime(it) } ?: stringResource(R.string.player_ab_loop_not_set)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val outlineVariant = PlayerDialogColors.divider
        val onSurfaceVariant = PlayerDialogColors.textSecondary
        val surfaceVariant = PlayerDialogColors.background
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 280.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {

                // 标题行：图标 + 标题 + 当前播放时间
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AbLoopIcon,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFFFFAB40) else onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_ab_loop_title),
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

                // A/B 时间显示：两张等宽玻璃卡片
                if (durationMs > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AbLoopPointCard(
                            label = stringResource(R.string.player_ab_loop_point_a),
                            time = if (aSet) aFormatted else stringResource(R.string.player_ab_loop_not_set),
                            accent = Color(0xFFFFAB40),
                            set = aSet,
                            modifier = Modifier.weight(1f),
                        )
                        AbLoopPointCard(
                            label = stringResource(R.string.player_ab_loop_point_b),
                            time = if (abLoopB != null) bFormatted else stringResource(R.string.player_ab_loop_not_set),
                            accent = Color(0xFFFF5252),
                            set = abLoopB != null,
                            modifier = Modifier.weight(1f),
                        )
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

                Spacer(Modifier.height(16.dp))

                // 操作按钮：统一药丸玻璃样式
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AbLoopActionPill(
                        label = if (aSet) stringResource(R.string.player_ab_loop_a_value, aFormatted) else stringResource(R.string.player_ab_loop_set_a, posFormatted),
                        accent = Color(0xFFFFAB40),
                        enabled = !aSet,
                        modifier = Modifier.weight(1f),
                        onClick = onSetPointA,
                    )
                    AbLoopActionPill(
                        label = if (abLoopB != null) stringResource(R.string.player_ab_loop_b_value, bFormatted) else stringResource(R.string.player_ab_loop_set_b, posFormatted),
                        accent = Color(0xFFFF5252),
                        enabled = aSet && abLoopB == null,
                        modifier = Modifier.weight(1f),
                        onClick = onSetPointB,
                    )
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
                                isActive -> stringResource(R.string.player_ab_loop_active, aFormatted, bFormatted)
                                aSet -> stringResource(R.string.player_ab_loop_a_set_prompt)
                                else -> stringResource(R.string.player_ab_loop_b_set_prompt)
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
                            Text(stringResource(R.string.player_ab_loop_clear), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        text = stringResource(R.string.player_ab_loop_quick_hint),
                        color = onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

/** A-B 循环弹窗的端点卡片：A/B 起止时间的高亮玻璃卡片。 */
@Composable
private fun AbLoopPointCard(
    label: String,
    time: String,
    accent: Color,
    set: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (set) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f))
            .border(
                0.5.dp,
                if (set) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (set) accent else Color.White.copy(alpha = 0.25f)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                color = PlayerDialogColors.textSecondary,
                fontSize = 11.sp,
            )
            Text(
                text = time,
                color = if (set) accent else PlayerDialogColors.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** A-B 循环弹窗的操作按钮：统一药丸玻璃样式，未启用时置灰。 */
@Composable
private fun AbLoopActionPill(
    label: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val textColor = if (enabled) accent else PlayerDialogColors.textSecondary.copy(alpha = 0.6f)
    val bg = if (enabled) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)
    val borderColor = if (enabled) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(bg)
            .border(0.5.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 260.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.player_episode_list, currentIndex + 1, playlist.size),
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
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 260.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.player_bookmark_list, bookmarks.size),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PlayerDialogDivider()
                if (bookmarks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_bookmark_empty),
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
                                        contentDescription = stringResource(R.string.player_delete_bookmark),
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
    onDragFractionChange: (Float?) -> Unit = {},
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
                    onDragFractionChange(dragFraction)
                    onSeek(dragFraction)

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            isDragging.value = false
                            onDragFractionChange(null)
                            onSeekFinished()
                            break
                        }
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onDragFractionChange(dragFraction)
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

/**
 * 字幕管理（主菜单）。
 *
 * 布局优化：主菜单保持紧凑（仅 5 个功能入口行），把占面积最大的「轨道列表」和
 * 「延迟调整」收进二级 Dialog（点对应行弹层），「外挂/搜索/样式」继续走原有回调跳转。
 * 当前选项在行右侧以摘要回显，点击即进入对应二级弹层。
 */
@Composable
private fun SubtitleManageDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedIndex: Int,
    activeSubtitleTrackIndex: Int,
    offsetMs: Long,
    onSelectTrack: (Int) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onAddExternal: () -> Unit,
    onSearch: () -> Unit,
    onOpenStyle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val onSurface = PlayerDialogColors.textPrimary
    // 二级弹层开关
    var showTrackDialog by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }

    // 当前字幕摘要（「轨道」行右侧回显）
    val autoSelectedTrack = if (selectedIndex == -1) {
        subtitleTracks.firstOrNull { it.index == activeSubtitleTrackIndex }
    } else {
        null
    }
    val trackSummary = when {
        selectedIndex == -2 -> stringResource(R.string.player_subtitle_none)
        selectedIndex == -1 -> autoSelectedTrack?.let {
            stringResource(R.string.player_subtitle_auto_used, it.label)
        } ?: stringResource(R.string.player_subtitle_auto_by_language)
        else -> subtitleTracks.firstOrNull { it.index == selectedIndex }?.label
            ?: stringResource(R.string.player_subtitle_none)
    }
    // 当前延迟摘要：正 → "+Xms"；负 → "-Xms"；0 → "0ms"
    val delaySummary = when {
        offsetMs < 0 -> "${offsetMs}ms"
        offsetMs > 0 -> "+${offsetMs}ms"
        else -> "0ms"
    }

    if (showTrackDialog) {
        SubtitleTrackDialog(
            subtitleTracks = subtitleTracks,
            selectedIndex = selectedIndex,
            activeSubtitleTrackIndex = activeSubtitleTrackIndex,
            onSelectTrack = onSelectTrack,
            onDismiss = { showTrackDialog = false },
        )
    }
    if (showDelayDialog) {
        SubtitleDelayDialog(
            offsetMs = offsetMs,
            onAdjustOffset = onAdjustOffset,
            onResetOffset = onResetOffset,
            onDismiss = { showDelayDialog = false },
        )
    }

    PlayerDialog(onDismiss = onDismiss, maxWidth = 360, maxHeight = 560) {
        Text(
            text = stringResource(R.string.player_subtitle),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        PlayerDialogDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            SubtitleMenuItem(
                icon = Icons.Rounded.Subtitles,
                label = stringResource(R.string.player_subtitle_track),
                summary = trackSummary,
                onClick = { showTrackDialog = true },
            )
            SubtitleMenuItem(
                icon = Icons.Rounded.Schedule,
                label = stringResource(R.string.player_subtitle_delay),
                summary = delaySummary,
                onClick = { showDelayDialog = true },
            )
            SubtitleMenuItem(
                icon = Icons.Rounded.FolderOpen,
                label = stringResource(R.string.player_subtitle_external),
                onClick = onAddExternal,
            )
            SubtitleMenuItem(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.player_subtitle_search),
                onClick = onSearch,
            )
            SubtitleMenuItem(
                icon = Icons.Rounded.Palette,
                label = stringResource(R.string.player_subtitle_style),
                onClick = onOpenStyle,
            )
        }
    }
}

/** 字幕主菜单的紧凑功能行：图标 + 标题 + 可选摘要 + 右侧箭头。 */
@Composable
private fun SubtitleMenuItem(
    icon: ImageVector,
    label: String,
    summary: String? = null,
    onClick: () -> Unit,
) {
    val onSurface = PlayerDialogColors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (summary != null) {
            Text(
                text = summary,
                color = onSurface.copy(alpha = 0.4f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 字幕轨道选择二级 Dialog（关闭/自动/内嵌轨道列表，选中项高亮）。 */
@Composable
private fun SubtitleTrackDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedIndex: Int,
    activeSubtitleTrackIndex: Int,
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    val outlineVariant = PlayerDialogColors.divider
    val autoSelectedTrack = if (selectedIndex == -1) {
        subtitleTracks.firstOrNull { it.index == activeSubtitleTrackIndex }
    } else {
        null
    }
    val trackItems = buildList<TrackOption> {
        add(TrackOption(stringResource(R.string.player_subtitle_off), -2, stringResource(R.string.player_subtitle_none)))
        add(
            TrackOption(
                stringResource(R.string.player_subtitle_auto),
                -1,
                autoSelectedTrack?.let { stringResource(R.string.player_subtitle_auto_used, it.label) }
                    ?: stringResource(R.string.player_subtitle_auto_by_language),
            )
        )
        subtitleTracks.forEach { track ->
            add(TrackOption(track.label, track.index, stringResource(R.string.player_subtitle_embedded)))
        }
    }

    PlayerDialog(onDismiss = onDismiss, maxWidth = 360, maxHeight = 460) {
        Text(
            text = stringResource(R.string.player_subtitle_track),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
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
    }
}

/** 字幕延迟调整二级 Dialog（-1s…+1s 步进 + 重置）。 */
@Composable
private fun SubtitleDelayDialog(
    offsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    PlayerDialog(onDismiss = onDismiss, maxWidth = 360, maxHeight = 260) {
        Text(
            text = stringResource(R.string.player_subtitle_delay),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()

        Column(modifier = Modifier.fillMaxWidth()) {
            // 当前偏移值 + 内嵌字幕 STUB 提示（仅外挂字幕生效）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_subtitle_external_only),
                    color = onSurface.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (offsetMs > 0) "+${offsetMs}ms" else "${offsetMs}ms",
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
                    0L to stringResource(R.string.player_subtitle_reset),
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
            text = stringResource(R.string.player_speed_menu_title),
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
                contentDescription = stringResource(R.string.player_unlock),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

// ===== 配置化 HUD 按钮系统 =====
// 中部侧边按钮由配置列表驱动，增删/排序/调样式只需改 [HudButtonConfig] 列表。
// 后续用户自定义只需在设置页读写同一份配置列表，无需改动渲染逻辑。

/** 单个 HUD 按钮的配置描述。 */
private data class HudButtonConfig(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String,
    val tint: Color = Color.White,
    val iconSize: Dp = 24.dp,
    val order: Int = 0,
    val side: HudButtonSide = HudButtonSide.LEFT,
    val onClick: () -> Unit = {},
    val onLongClick: (() -> Unit)? = null,
)

private enum class HudButtonSide { LEFT, RIGHT }

/** 垂直排列的 HUD 按钮列（左列/右列），由配置列表驱动渲染。
 *
 * 高度自适应用于防止「把全部按钮放到一侧」时溢出 / 错位：
 * - 竖屏 ([portrait] = true)：按钮列在画面中下部区域垂直居中，区域下方预留底栏高度；
 * - 横屏 ([portrait] = false)：整屏垂直居中；
 * - 当某侧按钮太多而放不下时，先收缩按钮间距，仍放不下则限制该侧数量（截断到可容纳数），
 *   保证任何排布都不超出屏幕。
 */
@Composable
private fun HudButtonColumn(
    configs: List<HudButtonConfig>,
    side: HudButtonSide,
    modifier: Modifier = Modifier,
    portrait: Boolean = false,
) {
    val sideConfigs = configs.filter { it.side == side }.sortedBy { it.order }
    val density = LocalDensity.current
    // 列占满整屏（fillMaxSize），用 side 把按钮固定在左/右边缘并垂直居中，
    // 保证约束高度可测（用于自适应间距/单边数量限制）。
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullH = with(density) { constraints.maxHeight.toDp() }
        // 底部为进度条与控制区，需预留空间避免按钮被遮挡/顶出屏幕
        val bottomReserve = if (portrait) 176.dp else 24.dp
        // 竖屏中部区域底部起点（按钮集中在中下部分，避开顶部挖孔与画面中心）
        val bandTop = if (portrait) fullH * 0.40f else 0.dp
        val avail = (fullH - bottomReserve - bandTop).coerceAtLeast(0.dp)

        val btn = 48.dp
        val minGap = 8.dp
        val idealGap = 12.dp
        val n = sideConfigs.size
        // 横屏每侧最多 3 个（竖屏由可用高度自适应决定数量）
        val maxPerSide = if (portrait) Int.MAX_VALUE else 3
        // 间距自适应：优先 12dp，拥挤则收缩，下限 8dp
        val gap = when {
            n <= 1 -> 0.dp
            else -> maxOf(minGap, minOf((avail - btn * n) / (n - 1), idealGap))
        }
        // 单边数量限制：横屏固定上限 3；竖屏仍放不下时收缩到可容纳数
        val canFitAll = n <= 1 || (n <= maxPerSide && btn * n + minGap * (n - 1) <= avail)
        val shown = if (canFitAll) sideConfigs
        else sideConfigs.take(
            minOf(maxPerSide, ((avail + minGap).value / (btn + minGap).value).toInt().coerceAtLeast(1)),
        )

        val colHeight = btn * shown.size + gap * (shown.size - 1)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = bandTop, bottom = bottomReserve)
                .padding(horizontal = if (side == HudButtonSide.LEFT) 16.dp else 16.dp),
            contentAlignment = if (side == HudButtonSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .height(colHeight),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                shown.forEachIndexed { _, cfg ->
                    if (cfg.onLongClick != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .playerHudGlass()
                                .combinedClickable(
                                    onClick = cfg.onClick,
                                    onLongClick = cfg.onLongClick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = cfg.icon,
                                contentDescription = cfg.contentDescription,
                                tint = cfg.tint,
                                modifier = Modifier.size(cfg.iconSize),
                            )
                        }
                    } else {
                        PlayerHudButton(onClick = cfg.onClick) {
                            Icon(
                                imageVector = cfg.icon,
                                contentDescription = cfg.contentDescription,
                                tint = cfg.tint,
                                modifier = Modifier.size(cfg.iconSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== 控件 HUD 按钮材质 =====
// 按用户要求回退按钮材质（仅保留弹窗材质修改）：中部侧边按钮恢复为原始生硬的半透明黑色
// 圆底（alpha=0.35），不使用玻璃描边，保持与改动前一致的视觉。
private val HudButtonBg = Color.Black.copy(alpha = 0.35f)

/** 统一 HUD 圆形按钮外框：圆角裁剪 + 半透明黑色圆底。 */
private fun Modifier.playerHudGlass(): Modifier = this
    .clip(CircleShape)
    .background(HudButtonBg)

/**
 * 统一 HUD 圆形按钮（单次点击）。用于旋转/去黑边/截图等功能按钮。
 * 图标颜色、尺寸、内容由调用方通过 [content] 提供，保证所有 HUD 按钮视觉一致。
 */
@Composable
private fun PlayerHudButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .playerHudGlass()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
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
    blackBarCropActive: Boolean = false,
    onToggleBlackBarCrop: () -> Unit = {},
    onPlayAtIndex: (Int) -> Unit,
    onTogglePlaylistDialog: () -> Unit,
    bookmarkPositions: List<Long> = emptyList(),
    pipEnabled: Boolean = false,
    onPictureInPicture: () -> Unit = {},
    onShowBookmarks: () -> Unit = {},
    onDownload: () -> Unit = {},
    /** 本地文件（已下载/缓存直链）来源时为 false，隐藏下载按钮。 */
    showDownload: Boolean = true,
    /** 已按用户自定义好的 HUD 按钮配置（含所在侧与序），用于渲染左右列。 */
    hudButtons: List<HudButtonConfig> = emptyList(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

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
                // 用稳定的挖孔安全区替代 statusBarsPadding：进入播放页时系统栏是带动画收起的，
                // statusBars 的 inset 会逐帧变化，导致顶部控件先被顶到靠下位置、再升回最顶部；
                // displayCutout 是硬件挖孔 inset，系统栏隐藏时保持不变，控件从首帧就停在最终位置
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.player_back),
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
                    imageVector = AbLoopIcon,
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
                    contentDescription = stringResource(R.string.player_more),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // 中部 HUD 侧边按钮（拱形左右列）：由配置驱动的按钮自由分布在左右列，
        // 每列自适应高度 + 单边数量限制，即使把全部按钮放到一侧也不会溢出 / 错位。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter),
        ) {
            HudButtonColumn(
                configs = hudButtons,
                side = HudButtonSide.LEFT,
                modifier = Modifier.align(Alignment.CenterStart),
                portrait = isPortrait,
            )
            HudButtonColumn(
                configs = hudButtons,
                side = HudButtonSide.RIGHT,
                modifier = Modifier.align(Alignment.CenterEnd),
                portrait = isPortrait,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 底部挖孔 inset 是硬件稳定值，系统栏隐藏时不变化，避免进入时被导航栏顶起再下移的跳变
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            // 拖动进度条时记录预览位置（fraction），时间文本跟随显示目标时间
            var dragFractionPreview by remember { mutableStateOf<Float?>(null) }
            PlayerProgressBar(
                positionFraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                bufferedFraction = if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f,
                durationMs = durationMs,
                abLoopA = abLoopA,
                abLoopB = abLoopB,
                onSeek = onSeek,
                onSeekFinished = onSeekFinished,
                onDragFractionChange = { dragFractionPreview = it },
                bookmarkPositions = bookmarkPositions,
            )

            Spacer(Modifier.height(2.dp))

            val previewPos = dragFractionPreview?.let { (it * durationMs).toLong() } ?: positionMs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(previewPos),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "-${formatTime((durationMs - previewPos).coerceAtLeast(0L))}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(6.dp))

            if (isPortrait) {
                // 竖屏：底部控制拆为两行布局，确保上/下一集与选集按钮可用
                // 第一行：功能按钮（倍速/音量 | 选集/音轨/字幕）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        val primary = MaterialTheme.colorScheme.primary
                        IconButton(onClick = onToggleSpeedMenu, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = stringResource(R.string.player_speed_icon),
                                tint = if (speedIndex != 1) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        var muted by remember {
                            mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
                        }
                        LaunchedEffect(previousMusicVolume) {
                            muted = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                        }
                        IconButton(
                            onClick = {
                                muted = toggleVolumeButton(audioManager, onPreviousMusicVolumeChange)
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff
                                    else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (muted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                                tint = if (muted) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (playlist.isNotEmpty()) {
                            IconButton(onClick = onTogglePlaylistDialog, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ViewList,
                                    contentDescription = stringResource(R.string.player_episode_list_icon),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        IconButton(onClick = onToggleAudioTrackMenu, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = stringResource(R.string.player_audio_track),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(onClick = onAddSubtitle, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = stringResource(R.string.player_subtitle),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 第二行：核心播放控制（上一集/快退/播放/快进/下一集）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playlist.size > 1) {
                        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.player_episode_previous),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    IconButton(onClick = onRewind, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = stringResource(R.string.player_rewind_10s),
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
                            contentDescription = if (state is PlaybackState.Playing) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = onForward, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = stringResource(R.string.player_forward_10s),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    if (playlist.size > 1) {
                        IconButton(onClick = onSkipNext, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.player_episode_next),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            } else {
                // 横屏：单行三层布局（功能 | 播放控制 | 功能）
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
                                contentDescription = stringResource(R.string.player_scale_icon),
                                tint = if (scaleIndex != 0) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        var muted by remember {
                            mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
                        }
                        LaunchedEffect(previousMusicVolume) {
                            muted = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                        }
                        IconButton(
                            onClick = {
                                muted = toggleVolumeButton(audioManager, onPreviousMusicVolumeChange)
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff
                                    else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (muted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                                tint = if (muted) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (playlist.size > 1) {
                            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = stringResource(R.string.player_episode_previous),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        IconButton(onClick = onRewind, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = stringResource(R.string.player_rewind_10s),
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
                                contentDescription = if (state is PlaybackState.Playing) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        IconButton(onClick = onForward, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = stringResource(R.string.player_forward_10s),
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        if (playlist.size > 1) {
                            IconButton(onClick = onSkipNext, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = stringResource(R.string.player_episode_next),
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
                        if (showDownload) {
                            IconButton(onClick = onDownload, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = stringResource(R.string.player_download_icon),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        IconButton(onClick = onToggleAudioTrackMenu, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = stringResource(R.string.player_audio_track),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(onClick = onAddSubtitle, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = stringResource(R.string.player_subtitle),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (playlist.isNotEmpty()) {
                            IconButton(onClick = onTogglePlaylistDialog, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ViewList,
                                    contentDescription = stringResource(R.string.player_episode_list_icon),
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
}