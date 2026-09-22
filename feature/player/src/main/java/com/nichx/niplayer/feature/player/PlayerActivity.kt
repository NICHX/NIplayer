package com.nichx.niplayer.feature.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.GlassSettings
import com.nichx.niplayer.datastore.LanguageSettings
import com.nichx.niplayer.datastore.ThemeSettings
import com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity
import com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity
import com.nichx.niplayer.designsystem.components.LocalNiGlassTopBarOpacity
import com.nichx.niplayer.designsystem.theme.NiScheme
import com.nichx.niplayer.designsystem.theme.NiTheme
import com.nichx.niplayer.player.kernel.VideoSize
import dagger.hilt.android.AndroidEntryPoint

/**
 * 视频播放器独立 Activity。
 *
 * 把「视频播放」从主界面导航（NavHost 路由）迁移为独立窗口承载，以获得窗口级转场
 * （播放画面随窗口一起从右侧滑入 / 滑出），解决 SurfaceView 不随 Compose slide 转场的问题。
 *
 * - 播放请求仍走全局单例 [PlaybackRequestHolder]（同进程），本 Activity 直接
 *   `consume()` 取走，无需 Intent 传状态。
 * - 页面内容复用 [PlayerScreen]（内含 [BackHandler] / 横竖屏自控 / 全屏沉浸 / PiP），
 *   返回统一走 `finish()`，[PlayerViewModel.onCleared] 照常落进度 / 缩略图 / 云同步。
 * - **音频播放器不受影响**：音频仍走主界面导航内 [AudioPlayerScreen] + 音乐条 + 均衡器。
 *
 * ### 画中画（PiP）
 * - 小窗宽高比始终跟随视频实际尺寸同步，方向锁定在小窗期间暂解除（见 [PlayerScreen]）。
 * - 用户点击系统 PiP 的关闭(X) / 上滑关掉小窗【而非「展开」】时，结束播放器：退出 PiP 后
 *   Activity 变为不可见（走到 [onStop]），据此判定为关闭，调用 [finish] 让
 *   [PlayerViewModel.onCleared] 停播落进度。
 * - **不能**用 [onStart] 判定「是否已回到前台」：进入 PiP 时 Activity 只走 onPause
 *   （小窗仍可见，**不会 onStop**），所以展开回全屏只走 onResume，onStart 永远不会被调用。
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    /** 复用 PlayerScreen 的 ViewModel（同一 ViewModelStore），用于读取播放状态与尺寸。 */
    private val viewModel: PlayerViewModel by viewModels()

    /**
     * 是否处于「PiP 会话」中。
     *
     * 进入 PiP 时置位；展开回全屏（[onResume]）或确认关闭小窗（[onStop] / PiP 退出回调）时复位。
     *
     * ⚠️ 之所以需要这个标志，而不是直接判断 [onStart]：进入 PiP 时 Activity 只是被置为
     * **PAUSED**（小窗仍可见，不会 onStop），因此「展开回全屏」只走 onResume，
     * onStart 永远不会被调用 —— 任何以 onStart 作为「已回到前台」判据的逻辑都会失效。
     */
    private var inPipSession = false

    /**
     * 本次退到后台是否由「主动进入 PiP」引起，供 [PlayerScreen] 在 ON_PAUSE 时判断
     * 「该不该暂停播放」（`true` = 进小窗，不暂停）。
     *
     * 为什么不直接用 [isInPictureInPictureMode]：`onPictureInPictureModeChanged` 自
     * Android 15 起被延后到**进入动画结束**才回调，而 onPause 发生在动画开始 —— 那一刻
     * 该标志仍为 false，会导致刚进小窗就把播放暂停（小窗里画面卡住）。
     * 本应用的 PiP 入口只有 [enterPip] 一处（HUD 按钮 / 自动 PiP），在这里显式标记即可。
     */
    @Volatile
    var pipEntryRequested: Boolean = false
        private set

    /**
     * 当前全屏时视频 SurfaceView 的屏幕矩形，供 PiP 进入时作为 `sourceRectHint`。
     *
     * 直接从窗口视图树查找 SurfaceView 即时计算（不依赖 Compose 副作用时机——Home 手势
     * 触发 [onUserLeaveHint] 时 Compose 的 onGloballyPositioned 写入可能尚未落地，导致
     * 手势路径拿不到源矩形而退化回「窗口先消失、小窗再淡入」）。
     */
    private fun currentVideoSourceRect(): Rect? {
        val sv = findSurfaceView(window?.decorView) ?: return null
        if (sv.width <= 0 || sv.height <= 0) return null
        val loc = IntArray(2)
        sv.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + sv.width, loc[1] + sv.height)
    }

    /** 深度优先查找 Compose AndroidView 挂载的播放 SurfaceView。 */
    private fun findSurfaceView(view: View?): SurfaceView? = when (view) {
        is SurfaceView -> view
        is ViewGroup -> view
            .children
            .firstNotNullOfOrNull { findSurfaceView(it) }
        else -> null
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        setContent {
            // 与 MainActivity 一致的主题注入：读取主题设置 + 玻璃浮层不透明度，经 NiTheme 下发
            val themeConfig by ThemeSettings.themeFlow.collectAsStateWithLifecycle()
            val glassOpacity by GlassSettings.opacityFlow.collectAsStateWithLifecycle()
            val glassTopBarOpacity by GlassSettings.topBarOpacityFlow.collectAsStateWithLifecycle()
            val glassPanelOpacity by GlassSettings.panelOpacityFlow.collectAsStateWithLifecycle()
            val darkTheme = when (themeConfig.mode) {
                ThemeSettings.Mode.LIGHT -> false
                ThemeSettings.Mode.DARK -> true
                ThemeSettings.Mode.SYSTEM -> isSystemInDarkTheme()
            }
            NiTheme(darkTheme = darkTheme, scheme = NiScheme.fromOrdinal(themeConfig.schemeOrdinal)) {
                CompositionLocalProvider(
                    LocalNiGlassOpacity provides glassOpacity,
                    LocalNiGlassTopBarOpacity provides glassTopBarOpacity,
                    LocalNiGlassPanelOpacity provides glassPanelOpacity,
                ) {
                    PlayerScreen(onBack = { finish() })
                }
            }
        }
        // 窗口级进入转场：右侧滑入
        overridePendingTransition(R.anim.slide_in_right, R.anim.hold)
    }

    /**
     * PiP 退出处理：区分「展开回大窗」与「点 X / 上滑关闭小窗」。
     *
     * 判定依据（不依赖回调先后顺序，也不依赖 [onStart]）：
     * - **关闭小窗**：Activity 变为不可见 → 先走 [onStop]。多数 ROM 上 [onStop] 先于本回调，
     *   故此处生命周期已低于 STARTED（已不可见），直接结束播放器。
     * - **展开回大窗**：Activity 仅由 PAUSED 回到 RESUMED，不会 [onStop]，
     *   此时生命周期至少为 STARTED，不做任何结束动作（播放继续）。
     *
     * [inPipSession] 的复位分工：展开路径由 [onResume] 复位；关闭路径由本回调或 [onStop] 复位。
     * 两条路径都不依赖回调先后顺序，避免影响后续的正常退后台。
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            inPipSession = true
            pipEntryRequested = true
        } else {
            pipEntryRequested = false
            if (PipExitPolicy.activityIsInvisible(lifecycle.currentState)) {
                // onStop 已先到 —— 用户关闭了小窗（而非展开），结束播放器
                inPipSession = false
                if (!isFinishing) finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台 = 展开回全屏：PiP 会话结束，播放继续（不做任何暂停/结束动作）
        inPipSession = false
        pipEntryRequested = false
        // 视频后台播放：回前台即退出后台托管（surface 靠回前台 surfaceCreated 自动复挂画面，
        // 播放从未暂停，无需手动 play）。
        VideoBgPlaybackController.stopService(this)
    }

    override fun onStop() {
        super.onStop()
        pipEntryRequested = false
        // 退出 PiP 后进入不可见态（且已不在 PiP 模式）= 用户关闭了小窗（点 X / 上滑）。
        // 展开回全屏不会走到这里，故不会误杀。结束播放器让 [PlayerViewModel.onCleared]
        // 停播落进度，避免小窗关掉后仍在后台继续解码播放。
        if (PipExitPolicy.closedPipWindow(inPipSession, isInPictureInPictureMode)) {
            inPipSession = false
            if (!isFinishing) finish()
        }
    }

    /**
     * 进入"视频后台播放"（后台仅音频）：接管当前视频播放到前台服务，系统保活 + 媒体通知。
     *
     * 由播放器 HUD 按钮主动触发。不暂停播放，仅把渲染表面交由 SurfaceView 生命周期
     * （surfaceDestroyed 自动解绑画面，仅出音频）。
     */
    fun enterVideoBackgroundPlayback() {
        if (isInPictureInPictureMode) return
        val player = viewModel.nxPlayer.mediaSessionPlayer ?: return
        val title = viewModel.title.value
        VideoBgPlaybackController.enter(player, title, viewModel::playNext, viewModel::playPrevious)
        try {
            startForegroundService(Intent(this, VideoBgPlaybackService::class.java))
        } catch (_: Exception) {
            // 活动后台等极端时序下 FGS 启动受限：回滚控制器，保持普通行为
            VideoBgPlaybackController.clear()
        }
        // 异步加载当前视频缩略图作为通知封面（无缓存时静默，不影响后台播放）
        viewModel.loadVideoBackgroundCover()
    }

    /**
     * 进入画中画（手动按钮 / 自动 PiP 共用）。
     *
     * @param sourceRect 全屏时视频渲染区域（屏幕坐标）。传入后可让系统以此为起点做
     *   平滑缩放进入动画；`null` 则系统无源矩形可用，会「窗口先消失、小窗再淡入」。
     *   仅进入时使用，小窗期间调整宽高比（[updatePipAspectRatio]）不传。
     */
    fun enterPip(size: VideoSize, sourceRect: Rect? = null) {
        if (size.isValid && !isInPictureInPictureMode) {
            try {
                val params = buildPipParams(size, sourceRect)
                setPictureInPictureParams(params)
                // 先置位再请求：onUserLeaveHint → enterPip → onPause 的时序下，onPause 里
                // isInPictureInPictureMode 可能仍为 false（见 [pipEntryRequested]），
                // 由本标记保证「进小窗不暂停播放」。请求失败则立刻回滚，避免后续真退后台时不暂停。
                pipEntryRequested = true
                if (!enterPictureInPictureMode(params)) {
                    pipEntryRequested = false
                }
            } catch (_: Exception) {
                pipEntryRequested = false
            }
        }
    }

    /** 小窗期间更新 PiP 宽高比（随视频尺寸/黑边检测变化），由 [PlayerScreen] 调用。 */
    fun updatePipAspectRatio(size: VideoSize) {
        if (isInPictureInPictureMode && size.isValid) {
            try { setPictureInPictureParams(buildPipParams(size)) } catch (_: Exception) {}
        }
    }

    /** 构建 PiP 参数：跟随视频宽高比 + 允许无缝尺寸调整，避免小窗宽高变化闪黑。 */
    private fun buildPipParams(size: VideoSize, sourceRect: Rect? = null): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(pipAspectRatio(size))
        // 源矩形让系统从视频所在区域平滑缩放到小窗；缺省则无缩放动画（先消失再淡入）。
        if (sourceRect != null) {
            builder.setSourceRectHint(sourceRect)
        }
        // setSeamlessResizeEnabled 仅 API 31+ 可用，低版本静默忽略
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    override fun finish() {
        super.finish()
        // 窗口级退出转场：向右侧滑出
        overridePendingTransition(R.anim.hold, R.anim.slide_out_right)
    }
}
