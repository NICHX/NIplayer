package com.nichx.niplayer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nichx.niplayer.common.message.AppMessageController
import kotlinx.coroutines.delay
import com.nichx.niplayer.datastore.LanguageSettings
import com.nichx.niplayer.datastore.ThemeSettings
import com.nichx.niplayer.datastore.GlassSettings
import com.nichx.niplayer.designsystem.components.AppMessageHost
import com.nichx.niplayer.designsystem.components.LocalNiBackdrop
import com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity
import com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity
import com.nichx.niplayer.designsystem.components.LocalNiGlassTopBarOpacity
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
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessScreen
import com.nichx.niplayer.feature.home.search.SearchScreen
import com.nichx.niplayer.feature.home.settings.AboutScreen
import com.nichx.niplayer.feature.home.settings.BackupScreen
import com.nichx.niplayer.feature.home.settings.CacheManagerScreen
import com.nichx.niplayer.feature.home.settings.TransferScreen
import com.nichx.niplayer.feature.home.settings.EqualizerSettingsScreen
import com.nichx.niplayer.feature.home.settings.LrcApiSettingsScreen
import com.nichx.niplayer.feature.home.settings.LanguageScreen
import com.nichx.niplayer.feature.home.settings.MediaLibrarySettingsScreen
import com.nichx.niplayer.feature.home.settings.PlaybackStatsScreen
import com.nichx.niplayer.feature.home.settings.PlayerSettingsScreen
import com.nichx.niplayer.feature.home.settings.ScanManagerScreen
import com.nichx.niplayer.feature.home.settings.ThemeScreen
import com.nichx.niplayer.feature.home.update.UpdateDialogHost
import com.nichx.niplayer.feature.home.update.UpdateViewModel
import com.nichx.niplayer.feature.player.AudioPlaybackManager
import com.nichx.niplayer.feature.player.AudioPlayerScreen
import com.nichx.niplayer.feature.player.MusicBar
import com.nichx.niplayer.feature.player.PlayerActivity
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
            // 液态玻璃不透明度：收集设置改动，经 LocalNiGlassOpacity 下发到全部底部玻璃浮层（导航栏等）
            val glassOpacity by GlassSettings.opacityFlow.collectAsStateWithLifecycle()
            // 顶栏不透明度：与导航栏分开设置，经 LocalNiGlassTopBarOpacity 下发
            val glassTopBarOpacity by GlassSettings.topBarOpacityFlow.collectAsStateWithLifecycle()
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
                    val crashLogSaved = stringResource(R.string.crash_log_saved)
                    val crashDialogCopy = stringResource(R.string.crash_dialog_copy)
                    val crashDialogSaveAsTxt = stringResource(R.string.crash_dialog_save_as_txt)
                    // 日志查看区最大高度：窗口的 50%，日志过长时在弹窗内滚动，避免弹窗撑满全屏
                    val maxLogHeight = with(LocalDensity.current) {
                        (LocalWindowInfo.current.containerSize.height * 0.5f).toDp()
                    }
                    // SAF 保存崩溃日志为 txt 文件
                    val saveCrashLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("text/plain")
                    ) { uri: Uri? ->
                        if (uri != null) {
                            val saved = runCatching {
                                contentResolver.openOutputStream(uri)?.use {
                                    it.write(log.toByteArray(Charsets.UTF_8))
                                } != null
                            }.getOrDefault(false)
                            if (saved) Toast.makeText(this, crashLogSaved, Toast.LENGTH_SHORT).show()
                        }
                    }
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
                            }) { Text(crashDialogCopy) }
                            TextButton(onClick = {
                                val name = "niplayer_crash_${
                                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                                }.txt"
                                saveCrashLauncher.launch(name)
                            }) { Text(crashDialogSaveAsTxt) }
                        },
                    ) {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = maxLogHeight)
                                .verticalScroll(rememberScrollState()),
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
                // 视频播放器已迁移为独立 Activity（PlayerActivity），经 startActivity 窗口级滑入；
                // 音频播放器保持导航内（AUDIO_PLAYER 路由 + 音乐条/均衡器整套 UX 不变）
                val navigateToPlayer: (Boolean) -> Unit = { isAudio ->
                    if (isAudio) {
                        navController.navigate(Routes.Player.AUDIO_PLAYER)
                    } else {
                        startActivity(Intent(this, PlayerActivity::class.java))
                    }
                }
                // 外部页（搜索/快速访问）请求在媒体库 tab 打开文件浏览的待办状态，
                // 回到 Home 根路由后由 HomeScreen 消费（切入媒体库子栈）
                var pendingFileBrowser by remember { mutableStateOf<Pair<Int, String>?>(null) }
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val isPlayerScreen =
                    currentBackStackEntry?.destination?.route == Routes.Player.AUDIO_PLAYER ||
                            // 均衡器是播放器的子页：从全屏播放器进入时不显示 musicbar，
                            // 否则用户会误点 musicbar 再次进播放器，导致返回栈错乱
                            currentBackStackEntry?.destination?.route == Routes.User.EQUALIZER

                // 文件浏览多选态：由 HomeScreen 上抛，多选时隐藏音乐条，避免与多选操作栏堆叠
                var fileBrowserMultiSelect by remember { mutableStateOf(false) }

                CompositionLocalProvider(
                    LocalNiGlassOpacity provides glassOpacity,
                    LocalNiGlassTopBarOpacity provides glassTopBarOpacity,
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
                    // 冷启动预热：首帧稳定后触发一次主内容重绘，提前完成 glass backdrop 捕获与
                    // 渲染管道 / shader 编译，减少用户头几次切换 tab 时的掉帧
                    var prewarmStep by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) {
                        withFrameNanos { }  // 等首帧
                        delay(220)          // 等启动初始化稳定
                        prewarmStep = 1     // 触发一次主内容 / glass backdrop 重绘
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 订阅 prewarmStep 以触发一次性重绘（冷启动预热 glass backdrop）
                            .drawBehind { if (prewarmStep != 0) {} }
                            .layerBackdrop(glassBackdrop),
                    ) {
                    NiNavHost(
                        navController = navController,
                    ) {
                        composable(
                            route = Routes.Home.ROOT,
                        ) {
                            HomeScreen(
                                onNavigateToGlobal = { route -> navController.navigate(route) },
                                onNavigateToSearch = {
                                    navController.navigate(Routes.Local.SEARCH)
                                },
                                onNavigateToPlayHistory = { filter ->
                                    navController.navigate(Routes.Local.playHistoryRoute(filter))
                                },
                                onNavigateToQuickAccess = {
                                    navController.navigate(Routes.Local.QUICK_ACCESS)
                                },
                                onPlayVideo = navigateToPlayer,
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
                            ) {
                            StoragePlusScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.Local.PLAY_HISTORY_ROUTE,
                            arguments = listOf(
                                navArgument("filter") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                            ),
                            ) { backStackEntry ->
                            PlayHistoryScreen(
                                initialFilterOrdinal = backStackEntry.arguments?.getInt("filter") ?: 0,
                                onNavigateToPlayVideo = navigateToPlayer,
                            )
                        }
                        composable(
                            route = Routes.Local.QUICK_ACCESS,
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
                            route = Routes.Local.SEARCH,
                            ) {
                            SearchScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToPlayVideo = navigateToPlayer,
                                onNavigateToStorageFile = { storageId, path ->
                                    // 交给 Home 在媒体库 tab 子栈打开文件浏览，返回栈回到搜索页
                                    pendingFileBrowser = storageId to path
                                    navController.popBackStack(Routes.Home.ROOT, inclusive = false)
                                },
                            )
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
                            ) {
                            ThemeScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.LANGUAGE,
                            ) {
                            LanguageScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.SETTING_PLAYER,
                            ) {
                            PlayerSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.MEDIA_LIBRARY,
                            ) {
                            MediaLibrarySettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.EQUALIZER,
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
                            ) {
                            PlaybackStatsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.BACKUP,
                            ) {
                            BackupScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.LRCAPI,
                            ) {
                            LrcApiSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.CACHE_MANAGER,
                            ) {
                            CacheManagerScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.SCAN_MANAGER,
                            ) {
                            ScanManagerScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.User.ABOUT,
                            ) {
                            AboutScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.Stream.DOWNLOAD_MANAGER,
                            ) {
                            TransferScreen(
                                onBack = { navController.popBackStack() },
                                onPlayVideo = navigateToPlayer,
                                onNavigateToImageViewer = {
                                    navController.navigate(Routes.ImageViewer.VIEWER)
                                },
                            )
                        }
                        composable(
                            route = Routes.ImageViewer.VIEWER,
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) },
                            // 与普通子页一致：显式 pop 退出（淡出，无缩放），避免回退到内置 scaleOut
                            popExitTransition = { fadeOut(tween(300)) },
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
                    }

                    // 全局统一 Snackbar 宿主：跨导航存活，消息从 AppMessageController 全局总线收集，
                    // 播放器全屏页音乐条隐藏时取消底部抬升，避免通知上浮。
                    // 置于 backdrop 捕获层之外（与弹窗/底栏一致），使 snackbar 能 drawBackdrop 真磨砂。
                    AppMessageHost(
                        controller = appMessageController,
                        bottomObstruction =
                            if (isPlayerScreen) 0.dp
                            else NiSnackbarDefaults.MINI_PLAYER_OBSTRUCTION,
                    )

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


