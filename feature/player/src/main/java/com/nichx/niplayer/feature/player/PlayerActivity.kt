package com.nichx.niplayer.feature.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
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
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.ThemeSettings
import com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity
import com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity
import com.nichx.niplayer.designsystem.theme.NiTheme
import com.nichx.niplayer.player.kernel.PlaybackState
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
 * - 用户点击系统 PiP 的关闭(X)【而非「展开」】时，结束播放器：退出 PiP 后若未回到前台
 *   （未走 [onStart]），判定为关闭，调用 [finish] 让 [PlayerViewModel.onCleared] 停播落进度。
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    /** 复用 PlayerScreen 的 ViewModel（同一 ViewModelStore），用于读取播放状态与尺寸。 */
    private val viewModel: PlayerViewModel by viewModels()

    /** 上一帧是否处于 PiP，用于区分"本次退出 PiP 但未回前台=点X关闭"。 */
    private var pipWasActive = false
    private var pipExitPending = false

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
            val glassPanelOpacity by GlassSettings.panelOpacityFlow.collectAsStateWithLifecycle()
            val darkTheme = when (themeConfig.mode) {
                ThemeSettings.Mode.LIGHT -> false
                ThemeSettings.Mode.DARK -> true
                ThemeSettings.Mode.SYSTEM -> isSystemInDarkTheme()
            }
            NiTheme(darkTheme = darkTheme, scheme = themeConfig.scheme) {
                CompositionLocalProvider(
                    LocalNiGlassOpacity provides glassOpacity,
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
     * PiP 退出处理：区分「展开回大窗」与「点 X 关闭」。
     *
     * 展开会走 [onStart]（取消待定）；点 X 关闭不会 [onStart]，延迟到期后结束播放器，避免
     * 关掉小窗后仍在后台继续解码播放。
     */
    override fun onPictureInPictureModeChanged(isInPictureInPicture: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPicture, newConfig)
        if (pipWasActive && !isInPictureInPicture) {
            pipExitPending = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (pipExitPending) {
                    finish()
                }
            }, 250)
        }
        pipWasActive = isInPictureInPicture
    }

    override fun onStart() {
        super.onStart()
        // 回到前台（展开 PiP / 正常恢复），取消待定的关闭动作
        pipExitPending = false
    }

    /**
     * 用户按 Home / 切出应用时触发。
     *
     * 开启 [PlayerSettings.autoPip] 且播放中时，自动进入画中画而非退后台暂停；
     * 通过 `!isInPictureInPictureMode` 防止手动触发 PiP 时重复进入。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PlayerSettings.autoPip &&
            !isInPictureInPictureMode &&
            viewModel.state.value is PlaybackState.Playing
        ) {
            enterPip(viewModel.videoSize.value)
        }
    }

    /** 进入画中画（手动按钮 / 自动 PiP 共用）。 */
    fun enterPip(size: VideoSize) {
        if (size.isValid && !isInPictureInPictureMode) {
            try {
                val params = buildPipParams(size)
                setPictureInPictureParams(params)
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
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
    private fun buildPipParams(size: VideoSize): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(size.width, size.height))
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