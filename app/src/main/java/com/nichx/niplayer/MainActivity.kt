package com.nichx.niplayer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nichx.niplayer.common.message.AppMessageController
import com.nichx.niplayer.datastore.LanguageSettings
import com.nichx.niplayer.datastore.ThemeSettings
import com.nichx.niplayer.datastore.GlassSettings
import com.nichx.niplayer.designsystem.components.AppMessageHost
import com.nichx.niplayer.designsystem.components.LocalNiBackdrop
import com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity
import com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiGlassOverlayHost
import com.nichx.niplayer.designsystem.components.NiSnackbarDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.theme.NiTheme
import com.nichx.niplayer.feature.home.HomeScreen
import com.nichx.niplayer.feature.home.history.PlayHistoryScreen
import com.nichx.niplayer.feature.home.imageviewer.ImageViewerScreen
import com.nichx.niplayer.feature.home.library.StoragePlusScreen
import com.nichx.niplayer.feature.home.playlist.PlaylistDetailScreen
import com.nichx.niplayer.feature.home.playlist.PlaylistsScreen
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessScreen
import com.nichx.niplayer.feature.home.search.SearchScreen
import com.nichx.niplayer.feature.home.settings.AboutScreen
import com.nichx.niplayer.feature.home.settings.BackupScreen
import com.nichx.niplayer.feature.home.settings.CacheManagerScreen
import com.nichx.niplayer.feature.home.settings.TransferScreen
import com.nichx.niplayer.feature.home.settings.EqualizerSettingsScreen
import com.nichx.niplayer.feature.home.settings.LrcApiSettingsScreen
import com.nichx.niplayer.feature.home.settings.LanguageScreen
import com.nichx.niplayer.feature.home.settings.GlassSettingsScreen
import com.nichx.niplayer.feature.home.settings.PlaybackStatsScreen
import com.nichx.niplayer.feature.home.settings.PlayerSettingsScreen
import com.nichx.niplayer.feature.home.settings.ScanManagerScreen
import com.nichx.niplayer.feature.home.settings.ThemeScreen
import com.nichx.niplayer.feature.home.update.UpdateDialogHost
import com.nichx.niplayer.feature.home.update.UpdateViewModel
import com.nichx.niplayer.feature.player.AudioPlaybackManager
import com.nichx.niplayer.feature.player.AudioPlayerScreen
import com.nichx.niplayer.feature.player.MusicBar
import com.nichx.niplayer.feature.player.PlayerGuardScreen
import com.nichx.niplayer.feature.player.PlayerScreen
import com.nichx.niplayer.navigation.NiNavHost
import com.nichx.niplayer.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var audioPlaybackManager: AudioPlaybackManager

    @Inject lateinit var appMessageController: AppMessageController

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
        requestMediaPermissions()
        requestLocalNetworkPermission()
        setContent {
            val themeConfig by ThemeSettings.themeFlow.collectAsStateWithLifecycle()
            // 液态玻璃不透明度：收集设置改动，经 LocalNiGlassOpacity 下发到全部玻璃浮层
            val glassOpacity by GlassSettings.opacityFlow.collectAsStateWithLifecycle()
            // 面板（对话框/菜单）不透明度：与薄浮层分开设置，经 LocalNiGlassPanelOpacity 下发
            val glassPanelOpacity by GlassSettings.panelOpacityFlow.collectAsStateWithLifecycle()
            val darkTheme = when (themeConfig.mode) {
                ThemeSettings.Mode.LIGHT -> false
                ThemeSettings.Mode.DARK -> true
                ThemeSettings.Mode.SYSTEM -> isSystemInDarkTheme()
            }
            NiTheme(
                darkTheme = darkTheme,
                scheme = themeConfig.scheme,
            ) {
                // O-12：上次崩溃日志提示，启动时读取一次（消费即清除）
                var crashLog by remember {
                    mutableStateOf((application as NiApplication).previousCrashLog)
                }
                crashLog?.let { log ->
                    val crashDialogTitle = stringResource(R.string.crash_dialog_title)
                    val crashDialogIgnore = stringResource(R.string.crash_dialog_ignore)
                    val crashLogCopied = stringResource(R.string.crash_log_copied)
                    val crashDialogCopyClose = stringResource(R.string.crash_dialog_copy_and_close)
                    NiInfoDialog(
                        title = crashDialogTitle,
                        onDismiss = { crashLog = null },
                        actions = {
                            TextButton(onClick = { crashLog = null }) { Text(crashDialogIgnore) }
                            TextButton(onClick = {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", log))
                                Toast.makeText(this, crashLogCopied, Toast.LENGTH_SHORT).show()
                                crashLog = null
                            }) { Text(crashDialogCopyClose) }
                        },
                    ) {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }
                // 版本检测：启动自动检查（24h 节流，静默失败），有更新时弹窗提示
                val updateViewModel: UpdateViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    updateViewModel.checkUpdate(auto = true)
                }
                UpdateDialogHost(viewModel = updateViewModel)

                val bgColor = MaterialTheme.colorScheme.background.toArgb()
                val activityWindow = window
                SideEffect {
                    activityWindow?.setBackgroundDrawable(ColorDrawable(bgColor))
                    val insetsController = activityWindow?.let {
                        WindowCompat.getInsetsController(it, it.decorView)
                    }
                    insetsController?.isAppearanceLightStatusBars = !darkTheme
                    insetsController?.isAppearanceLightNavigationBars = !darkTheme
                }
                val navController = rememberNavController()
                // 外部页（搜索/快速访问）请求在媒体库 tab 打开文件浏览的待办状态，
                // 回到 Home 根路由后由 HomeScreen 消费（切入媒体库子栈）
                var pendingFileBrowser by remember { mutableStateOf<Pair<Int, String>?>(null) }
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val isPlayerScreen =
                    currentBackStackEntry?.destination?.route == Routes.Player.AUDIO_PLAYER ||
                            currentBackStackEntry?.destination?.route == Routes.Player.PLAYER ||
                            currentBackStackEntry?.destination?.route == Routes.Player.GUARD

                // 文件浏览多选态：由 HomeScreen 上抛，多选时隐藏音乐条，避免与多选操作栏堆叠
                var fileBrowserMultiSelect by remember { mutableStateOf(false) }

                CompositionLocalProvider(
                    LocalNiGlassOpacity provides glassOpacity,
                    LocalNiGlassPanelOpacity provides glassPanelOpacity,
                    LocalAppMessageController provides appMessageController,
                ) {
                    // 液态玻璃 backdrop 源：捕获全部页面内容，供同窗口玻璃面板（NiGlassBottomSheet）真模糊
                    val windowBackground = MaterialTheme.colorScheme.background
                    val glassBackdrop = rememberLayerBackdrop {
                        drawRect(windowBackground)
                        drawContent()
                    }
                    CompositionLocalProvider(LocalNiBackdrop provides glassBackdrop) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
                    NiNavHost(
                        navController = navController,
                    ) {
                        composable(
                            route = Routes.Home.ROOT,
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) },
                        ) {
                            HomeScreen(
                                onNavigateToGlobal = { route -> navController.navigate(route) },
                                onNavigateToSearch = {
                                    navController.navigate(Routes.Local.SEARCH)
                                },
                                onNavigateToPlayHistory = {
                                    navController.navigate(Routes.Local.PLAY_HISTORY)
                                },
                                onNavigateToQuickAccess = {
                                    navController.navigate(Routes.Local.QUICK_ACCESS)
                                },
                                onOpenPlaylists = {
                                    navController.navigate(Routes.Playlist.LIST)
                                },
                                onOpenPlaylist = { playlistId ->
                                    navController.navigate(Routes.Playlist.detailRoute(playlistId))
                                },
                                onPlayVideo = { navController.navigate(Routes.Player.GUARD) },
                                onNavigateToStoragePlus = { type, storageId ->
                                    val route = if (type != null) {
                                        Routes.Stream.storagePlusRoute(type)
                                    } else {
                                        Routes.Stream.storagePlusEditRoute(storageId)
                                    }
                                    navController.navigate(route)
                                },
                                onNavigateToImageViewer = {
                                    navController.navigate(Routes.ImageViewer.VIEWER)
                                },
                                onNavigateToDownloadManager = {
                                    navController.navigate(Routes.Stream.DOWNLOAD_MANAGER)
                                },
                                pendingFileBrowser = pendingFileBrowser,
                                onPendingFileBrowserConsumed = { pendingFileBrowser = null },
                                onFileBrowserMultiSelectChanged = { fileBrowserMultiSelect = it },
                            )
                        }
                        composable(
                            route = Routes.Stream.STORAGE_PLUS_ROUTE,
                            arguments = listOf(
                                navArgument("type") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("storageId") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                            ),
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            StoragePlusScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.Local.PLAY_HISTORY,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            PlayHistoryScreen(
                                onNavigateToPlayVideo = { navController.navigate(Routes.Player.GUARD) },
                            )
                        }
                        composable(
                            route = Routes.Local.QUICK_ACCESS,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            QuickAccessScreen(
                                onNavigateToStorageFile = { storageId, path ->
                                    // 交给 Home 在媒体库 tab 子栈打开文件浏览，返回栈回到快速访问页
                                    pendingFileBrowser = storageId to path
                                    navController.popBackStack(Routes.Home.ROOT, inclusive = false)
                                },
                            )
                        }
                        composable(
                            route = Routes.Playlist.LIST,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            PlaylistsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPlaylist = { playlistId ->
                                    navController.navigate(Routes.Playlist.detailRoute(playlistId))
                                },
                            )
                        }
                        composable(
                            route = Routes.Playlist.DETAIL_ROUTE,
                            arguments = listOf(
                                navArgument("playlistId") {
                                    type = NavType.IntType
                                },
                            ),
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            PlaylistDetailScreen(
                                onBack = { navController.popBackStack() },
                                onPlayVideo = { navController.navigate(Routes.Player.GUARD) },
                            )
                        }
                        composable(
                            route = Routes.Local.SEARCH,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            SearchScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToPlayVideo = { navController.navigate(Routes.Player.GUARD) },
                                onNavigateToStorageFile = { storageId, path ->
                                    // 交给 Home 在媒体库 tab 子栈打开文件浏览，返回栈回到搜索页
                                    pendingFileBrowser = storageId to path
                                    navController.popBackStack(Routes.Home.ROOT, inclusive = false)
                                },
                            )
                        }
                        composable(
                            route = Routes.Player.GUARD,
                            enterTransition = { fadeIn(tween(0)) },
                            exitTransition = { fadeOut(tween(0)) },
                        ) {
                            PlayerGuardScreen(
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(Routes.Player.GUARD) { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = Routes.Player.PLAYER,
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) },
                            popEnterTransition = { fadeIn(tween(0)) },
                            popExitTransition = { fadeOut(tween(0)) },
                        ) {
                            PlayerScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.Player.AUDIO_PLAYER,
                            enterTransition = {
                                slideInVertically(tween(350)) { it } + fadeIn(tween(350))
                            },
                            exitTransition = {
                                slideOutVertically(tween(350)) { it } + fadeOut(tween(350))
                            },
                            popEnterTransition = { fadeIn(tween(0)) },
                            popExitTransition = { fadeOut(tween(0)) },
                        ) {
                            AudioPlayerScreen(
                                onBack = { navController.popBackStack() },
                                onEqualizer = { navController.navigate(Routes.User.EQUALIZER) },
                                audioPlaybackManager = audioPlaybackManager,
                            )
                        }
                        composable(
                            route = Routes.User.SWITCH_THEME,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            ThemeScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.LANGUAGE,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            LanguageScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.GLASS,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            GlassSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.SETTING_PLAYER,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            PlayerSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.EQUALIZER,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            EqualizerSettingsScreen(
                                onBack = { navController.popBackStack() },
                                onApplyToPlayer = {
                                    audioPlaybackManager.applyEqualizerSettings()
                                },
                                onApplyLiveToPlayer = {
                                    audioPlaybackManager.applyEqualizerLive()
                                },
                            )
                        }
                        composable(
                            route = Routes.User.PLAYBACK_STATS,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            PlaybackStatsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.BACKUP,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            BackupScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.LRCAPI,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            LrcApiSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.CACHE_MANAGER,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            CacheManagerScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.SCAN_MANAGER,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            ScanManagerScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.ABOUT,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            AboutScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.Stream.DOWNLOAD_MANAGER,
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
                            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally { it / 4 } },
                        ) {
                            TransferScreen(
                                onBack = { navController.popBackStack() },
                                onPlayVideo = { navController.navigate(Routes.Player.GUARD) },
                                onNavigateToImageViewer = {
                                    navController.navigate(Routes.ImageViewer.VIEWER)
                                },
                            )
                        }
                        composable(
                            route = Routes.ImageViewer.VIEWER,
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) },
                        ) {
                            ImageViewerScreen(onBack = { navController.popBackStack() })
                        }
                    }

                    MusicBar(
                        playbackManager = audioPlaybackManager,
                        onNavigateToPlayer = {
                            navController.navigate(Routes.Player.AUDIO_PLAYER)
                        },
                        // 播放器页或文件浏览多选态下隐藏音乐条
                        visible = !isPlayerScreen && !fileBrowserMultiSelect,
                    )

                    // 全局统一 Snackbar 宿主：跨导航存活，消息从 AppMessageController 全局总线收集，
                    // 播放器全屏页音乐条隐藏时取消底部抬升，避免通知上浮。
                    AppMessageHost(
                        controller = appMessageController,
                        bottomObstruction =
                            if (isPlayerScreen) 0.dp
                            else NiSnackbarDefaults.MINI_PLAYER_OBSTRUCTION,
                    )
                    }
                    // 玻璃浮层宿主：渲染 NiGlassOverlay 栈（位于 backdrop 捕获层之外，避免循环采样）
                    NiGlassOverlayHost()
                    }
                    }
                }
            }
        }
    }

    private fun requestMediaPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_MEDIA_CODE)
        }
    }

    private fun requestLocalNetworkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            // Android 17 (API 37+): 需要主动申请本地网络访问权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
                    REQUEST_LOCAL_NETWORK_CODE
                )
            }
        }
    }

    private companion object {
        const val REQUEST_MEDIA_CODE = 1001
        const val REQUEST_LOCAL_NETWORK_CODE = 1002
    }
}


