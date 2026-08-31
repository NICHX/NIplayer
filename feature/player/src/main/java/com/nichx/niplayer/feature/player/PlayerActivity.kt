package com.nichx.niplayer.feature.player

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.nichx.niplayer.designsystem.theme.NiTheme
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
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

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

    override fun finish() {
        super.finish()
        // 窗口级退出转场：向右侧滑出
        overridePendingTransition(R.anim.hold, R.anim.slide_out_right)
    }
}