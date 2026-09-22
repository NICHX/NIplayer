package com.nichx.niplayer.feature.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nichx.niplayer.datastore.PlayerControlOrientation
import com.nichx.niplayer.datastore.PlayerControlSurface
import com.nichx.niplayer.datastore.PlayerControlLayout
import com.nichx.niplayer.datastore.ExperimentalSettings
import com.nichx.niplayer.datastore.VrSettings
import com.nichx.niplayer.feature.player.vr.VrFormat
import com.nichx.niplayer.feature.player.vr.VrSurfaceView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.designsystem.components.DownloadTargetChooserDialog
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.player.kernel.NxVideoScaleMode
import com.nichx.niplayer.player.kernel.PlaybackEvent
import com.nichx.niplayer.player.kernel.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Composable
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(UnstableApi::class)
fun PlayerScreen(
    onBack: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    // P0-1 结构改造（2026-09-22）：原先在此顶层 collect positionMs / bufferedMs / networkSpeed，
    // 三者都是每 500ms 变化的高频值（NxMedia3Player 的 positionTicker）。顶层读取意味着
    // **整个 PlayerScreen 函数体每秒重跑 2 次**，连带重建 hudButtons / moreActions 两个 List
    // （由局部 @Composable ctrlButtonUnit 产出，无法 remember），使 PlayerControllerLayer 的
    // 这两个参数引用永不相等 —— 整层因此无法跳过重组（compose 报告可证）。
    // 现改为：
    // - 事件处理（键盘 / 双击 / 横滑 / 快进快退）按需读 viewModel.nxPlayer.positionMs.value，
    //   即事件发生时的实时值；
    // - 展示层（进度条、网速文字）由 PlayerControllerLayer 内部自行 collect 对应 StateFlow，
    //   使重组范围收敛到那几个叶子组件。
    //
    // ⚠️ 事件处理必须读 nxPlayer.positionMs（原始 MutableStateFlow），**不能**读
    //    viewModel.positionMs —— 后者是 stateIn(WhileSubscribed(5000))，无人订阅时上游会停，
    //    `.value` 将冻结在最后一次发射值，导致 seek 基准错误。
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
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsStateWithLifecycle()
    // 本地文件（已下载/缓存直链）来源时隐藏下载按钮
    val isLocalSource by viewModel.isLocalSource.collectAsStateWithLifecycle()
    // P0-1 修复（2026-09-22）：原先在 PlayerControllerLayer 的实参位置直接写
    // `bookmarks.map { it.positionMs }`，每次重组都新建一个 List —— 既产生垃圾，
    // 又让该实参永远"不相等"。改为 remember(bookmarks) 缓存（List 为结构比较，
    // bookmarks 未变时命中缓存并返回同一实例）。
    val bookmarkPositions = remember(bookmarks) { bookmarks.map { it.positionMs } }

    val context = LocalContext.current
    val activity = context as? Activity
    // UX-2 修复（2026-09-22）：播放器手势（双击快进/后退、长按倍速）没有可见按钮，
    // 震动是用户唯一可靠的触发确认 —— 尤其在横屏全屏、手指遮挡画面时。
    // 全仓原先 0 处触觉反馈（performHapticFeedback / LocalHapticFeedback 均无命中）。
    val haptic = LocalHapticFeedback.current
    // 「加强」调整（2026-09-22）：改用当前 API 下**最强**的触觉类型。
    // - API 30+ ：Confirm（系统定义的「正向确认」震动，比 LongPress 更明显）
    // - API 26-29：LongPress（该区间可用的最强类型）
    // ⚠️ 不能直接用 HapticFeedbackType.Confirm 了事 —— Compose 1.10 的 HapticFeedbackType
    //    并未按 API 降级（Confirm 对应 HapticFeedbackConstants.CONFIRM，该常量 API 30 才引入），
    //    在 minSdk 26 的低版本设备上会静默无震动。故此处显式按版本选择。
    val strongHaptic = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackType.Confirm
        } else {
            HapticFeedbackType.LongPress
        }
    }
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

    // VR（环视）播放状态：切换到 GL 全景渲染路径，用陀螺仪环视单眼画面。
    var vrMode by rememberSaveable { mutableStateOf(false) }
    var vrViewRef by remember { mutableStateOf<VrSurfaceView?>(null) }
    // 画面格式索引（0..3，对应 VrFormat 枚举顺序），持久化
    var vrFormatIndex by rememberSaveable { mutableIntStateOf(VrSettings.formatIndex.coerceIn(0, VrFormat.entries.lastIndex)) }
    // VR 可调参数（FOV、陀螺仪灵敏度、视距），持久化并实时同步到渲染视图
    var vrFov by rememberSaveable { mutableIntStateOf(VrSettings.fovDegrees.coerceIn(30, 120)) }
    var vrSensitivity by rememberSaveable { mutableFloatStateOf(VrSettings.gyroSensitivity) }
    var vrZoom by rememberSaveable { mutableFloatStateOf(VrSettings.zoom.coerceIn(VrSettings.MIN_ZOOM, VrSettings.MAX_ZOOM)) }
    // VR 控制条自动收起：进入/交互时显示，闲置后隐藏
    var vrControlsVisible by rememberSaveable { mutableStateOf(true) }
    var vrViewLocked by rememberSaveable { mutableStateOf(false) }

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
    // 进入播放器前的系统亮度（0~1），仅作为"手动亮度模式下实时读值失败"时的兜底值。
    // 退出时不再无脑写回这个快照：自动亮度下它往往是过时/偏低的残留值，强行写回会让
    // 共享窗口（含返回后的目录页）瞬时压暗。恢复逻辑见下方 restoreBrightnessOnExit。
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
    // 退出时恢复进入播放器前的亮度（显式写回具体值，而非 BRIGHTNESS_OVERRIDE_NONE：
    // 部分设备/ROM 对 NONE 不会真正清除窗口覆盖，退出后仍停留在播放器内亮度）。
    // 关键守卫：仅当窗口存在亮度覆盖（用户手势/OSD 调节过，screenBrightness >= 0）时才写回。
    // 若窗口本就是 NONE（未调节，跟随系统/自动亮度），硬写 preEntryBrightness 会把自动亮度
    // 下过时的手动档残留值强行顶上，造成"退出瞬暗再恢复"的闪变——这正是原上报 bug 的根因。
    val restoreBrightnessOnExit: () -> Unit = {
        window?.let { w ->
            val attrs = w.attributes
            if (attrs.screenBrightness >= 0f) {
                attrs.screenBrightness = preEntryBrightness
                w.attributes = attrs
            }
        }
    }
    val capturedBack: () -> Unit = {
        // 立即恢复系统亮度：一触发返回亮度马上恢复（不等待抓帧/动画），让退出过渡全程以
        // 系统亮度呈现。仅在用户调节过亮度时才写回（见 restoreBrightnessOnExit）。
        // PixelCopy 读的是 surface 像素、不受窗口亮度设置影响，提前恢复不影响退出贴图。
        restoreBrightnessOnExit()
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
                // 枚举穷尽化：其余生命周期事件无需处理，显式列出以便 androidx 新增事件时编译报错
                Lifecycle.Event.ON_CREATE,
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY,
                Lifecycle.Event.ON_ANY -> Unit
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
                    val t = (viewModel.nxPlayer.positionMs.value + 10_000)
                        .coerceAtMost(durationMs.coerceAtLeast(1L))
                    viewModel.seekTo(t); true
                }
                Key.DirectionLeft, Key.J -> {
                    val t = (viewModel.nxPlayer.positionMs.value - 10_000).coerceAtLeast(0L)
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

            // 兜底恢复系统亮度：主恢复已提前到 capturedBack 入口；此处兜底覆盖未走
            // capturedBack 的异常路径。仅在用户调节过亮度时才写回（见 restoreBrightnessOnExit）。
            restoreBrightnessOnExit()
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
        // 持久化上次倍速索引，并在进入/切换倍速时把该档速度真正应用到播放内核，
        // 否则重新进入播放器只会恢复 UI 索引（显示对），实际播放仍为默认 1x。
        PlayerSettings.lastSpeedIndex = speedIndex
        viewModel.nxPlayer.setSpeed(SPEED_VALUES[speedIndex])
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

        // VR 模式：进入小窗（PiP）时退出 VR，避免 GL 路径与小窗控件冲突。
        LaunchedEffect(isInPip) {
            if (isInPip) vrMode = false
        }

        // VR 当前画面格式（左右/上下 × 180°/360°）
        val activeVrFormat = VrFormat.fromIndex(vrFormatIndex.coerceIn(0, VrFormat.entries.lastIndex))

        // VR 控制：进入 VR 时默认显示，轻点画面在显示/隐藏间切换（无自动收起，避免与切换冲突）
        LaunchedEffect(vrMode) {
            if (vrMode) vrControlsVisible = true
        }
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
        if (vrMode) {
            // VR 模式：GL 球面渲染路径（等距柱面 + 陀螺仪环视）。整屏投影，忽略画面比例。
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VrSurfaceView(ctx, viewModel.nxPlayer).also { v ->
                        v.setFormat(activeVrFormat.layout, activeVrFormat.halfPanoDegrees)
                        v.setFovDegrees(vrFov.toFloat())
                        v.setGyroSensitivity(vrSensitivity)
                        v.setZoom(vrZoom)
                        v.setInvertYaw(VrSettings.invertYaw)
                        v.setViewLocked(vrViewLocked)
                        // 轻点切换控制条显示/隐藏
                        v.onTap = {
                            vrControlsVisible = !vrControlsVisible
                        }
                        vrViewRef = v
                    }
                },
                update = { v ->
                    v.setVideoSize(videoSize.width, videoSize.height)
                    v.setFormat(activeVrFormat.layout, activeVrFormat.halfPanoDegrees)
                    v.setFovDegrees(vrFov.toFloat())
                    v.setGyroSensitivity(vrSensitivity)
                    v.setZoom(vrZoom)
                    v.setInvertYaw(VrSettings.invertYaw)
                    v.setViewLocked(vrViewLocked)
                },
            )
        } else if (exitFrame != null) {
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

        if (vrMode) {
            // VR 模式：不附加 Compose 手势层，画面触摸完全交给 VrSurfaceView 的原生监听
            // （轻点唤出控制条）。避免 Compose awaitEachGesture 循环在读 SnapshotMutableState
            // 时于主线程自旋，导致"点击即卡死"（APP_SCOUT_WARNING / ANR）。
            Box(modifier = Modifier.fillMaxSize())
        } else {
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
                                            // UX-2：双击手势无可见按钮，震动作为触发确认
                                            haptic.performHapticFeedback(strongHaptic)
                                            when {
                                                startX < third -> {
                                                    viewModel.seekTo(
                                                        (viewModel.nxPlayer.positionMs.value - doubleTapStepMs)
                                                            .coerceAtLeast(0L),
                                                    )
                                                    infoOsd = context.getString(R.string.player_seek_backward_seconds, doubleTapStepMs / 1000)
                                                }
                                                startX > size.width * 2f / 3f -> {
                                                    viewModel.seekTo(
                                                        (viewModel.nxPlayer.positionMs.value + doubleTapStepMs)
                                                            .coerceAtMost(durationMs.coerceAtLeast(1L)),
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
                                // UX-2：长按倍速生效时给一次确认
                                haptic.performHapticFeedback(strongHaptic)
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
                                        val target = (viewModel.nxPlayer.positionMs.value + (dx * pxToMs).toLong())
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
        }

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
            "vr" -> {
                // 仅当原生画面为 VR 帧型（2:1 的 360°/SBS、1:2 的 OU）时才可进入；
                // 已在 VR 模式时允许退出。普通视频禁用该按钮。
                // 实验性功能：VR 播放未开启时整体禁用 VR 入口。
                val vrCapable = ExperimentalSettings.vrPlaybackEnabled &&
                    (isLikelyVrVideo(videoSize.width, videoSize.height) || vrMode)
                HudButtonConfig(
                    id, VrHeadsetIcon,
                    stringResource(R.string.player_vr),
                    tint = if (vrMode) Color(0xFF6C9CFF)
                    else if (vrCapable) Color.White.copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.35f),
                    enabled = vrCapable,
                    onClick = {
                        vrMode = !vrMode
                        infoOsd = if (vrMode) {
                            context.getString(R.string.player_vr_entered)
                        } else {
                            context.getString(R.string.player_vr_exited)
                        }
                    },
                )
            }
            else -> null
        }
        // P0-1 修复（2026-09-22）：loadEntry 内部是 MMKV decodeString（JNI + split + valueOf 反射）。
        // 原先每次重组都重读全部 11 项，而顶层 collect 的 positionMs 每 500ms 触发一次重组
        // → 稳态下 22 次 MMKV 读/秒，只为重建一份几乎从不变化的布局配置。
        // 布局只在「设置 → 播放器设置 → 播放器控制自定义」写入（PlayerControlCustomizeScreen），
        // 该页位于导航栈更底层，PlayerScreen 不可能与其同屏，故按方向缓存即可。
        // ⚠️ ctrlOrientation 必须作为 key：PlayerActivity 声明了 configChanges=orientation，
        //    旋转时不重建 Activity，若用无 key 的 remember 会沿用旧方向的布局。
        val ctrlEntries = remember(ctrlOrientation) {
            PlayerControlLayout.ALL_IDS.mapIndexed { i, id ->
                PlayerControlLayout.loadEntry(id, i, ctrlOrientation)
            }
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
                    positionMsFlow = viewModel.positionMs,
                    durationMs = durationMs,
                    bufferedMsFlow = viewModel.bufferedMs,
                    networkSpeedFlow = viewModel.networkSpeed,
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
                        val target = (viewModel.nxPlayer.positionMs.value - 10_000).coerceAtLeast(0L)
                        viewModel.seekTo(target)
                    },
                    onForward = {
                        val target = (viewModel.nxPlayer.positionMs.value + 10_000)
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
                    bookmarkPositions = bookmarkPositions,
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

        // VR 模式控制条：方向（格式 / 滑动 / 归中）/ 视距 / FOV / 灵敏度 / 退出，闲置自动收起
        if (vrMode && !isInPip) {
            VrControlOverlay(
                formatLabel = when (activeVrFormat.layout) {
                    1 -> stringResource(R.string.player_vr_format_ou_pano, activeVrFormat.halfPanoDegrees)
                    else -> stringResource(R.string.player_vr_format_sbs_pano, activeVrFormat.halfPanoDegrees)
                },
                fovDegrees = vrFov,
                sensitivity = vrSensitivity,
                zoom = vrZoom,
                viewLocked = vrViewLocked,
                visible = vrControlsVisible && !locked,
                onClickFormat = {
                    vrFormatIndex = (vrFormatIndex + 1) % VrFormat.entries.size
                    VrSettings.formatIndex = vrFormatIndex
                },
                onClickRecenter = {
                    vrViewRef?.recenter()
                },
                onToggleViewLock = {
                    vrViewLocked = !vrViewLocked
                    vrViewRef?.setViewLocked(vrViewLocked)
                },
                onFovChange = { delta ->
                    vrFov = (vrFov + delta.toInt()).coerceIn(30, 120)
                    VrSettings.fovDegrees = vrFov
                    vrViewRef?.setFovDegrees(vrFov.toFloat())
                },
                onSensitivityChange = { delta ->
                    vrSensitivity = (vrSensitivity + delta).coerceIn(0.05f, 0.5f)
                    VrSettings.gyroSensitivity = vrSensitivity
                    vrViewRef?.setGyroSensitivity(vrSensitivity)
                },
                onZoomChange = { delta ->
                    vrZoom = (vrZoom + delta).coerceIn(VrSettings.MIN_ZOOM, VrSettings.MAX_ZOOM)
                    VrSettings.zoom = vrZoom
                    vrViewRef?.setZoom(vrZoom)
                },
                onClickExit = {
                    vrMode = false
                    infoOsd = context.getString(R.string.player_vr_exited)
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
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
            // P0-1 结构改造：AbLoopDialog 需要「实时当前位置」显示。
            // 原先由 PlayerScreen 顶层 collect 后传入 —— 那会让整个屏幕每秒重跑 2 次。
            // 现由宿主组件自行 collect，把每 500ms 的重组限制在弹窗内部。
            AbLoopDialogHost(
                viewModel = viewModel,
                abLoopA = abLoopA,
                abLoopB = abLoopB,
                durationMs = durationMs,
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
            val resumeTime = formatDuration(savedPosition)
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


/**
 * AB 循环弹窗宿主（P0-1 结构改造，2026-09-22）。
 *
 * 单独抽出的唯一目的：让 [PlayerScreen] 顶层不必再 collect `positionMs`。
 * 该弹窗需要实时位置，而位置每 500ms 变化 —— 若在 PlayerScreen 顶层读取，
 * 整个屏幕（含 HUD、菜单、字幕层）都会跟着每秒重组 2 次。
 * 把订阅收敛到本组件后，高频重组被限制在弹窗自身的作用域内。
 */
@Composable
private fun AbLoopDialogHost(
    viewModel: PlayerViewModel,
    abLoopA: Long?,
    abLoopB: Long?,
    durationMs: Long,
    onDismiss: () -> Unit,
) {
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    AbLoopDialog(
        abLoopA = abLoopA,
        abLoopB = abLoopB,
        durationMs = durationMs,
        positionMs = positionMs,
        onSetPointA = { viewModel.setAbLoopPointA() },
        onSetPointB = { viewModel.setAbLoopPointB() },
        onClearAbLoop = { viewModel.clearAbLoop() },
        onDismiss = onDismiss,
    )
}
