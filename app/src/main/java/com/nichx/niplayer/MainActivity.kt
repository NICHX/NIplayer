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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.nichx.niplayer.designsystem.theme.NiScheme
import com.nichx.niplayer.designsystem.theme.NiTheme
import com.nichx.niplayer.feature.home.homeNavGraph
import com.nichx.niplayer.feature.home.rememberHomeNavGraphState
import com.nichx.niplayer.feature.home.update.UpdateHost
import com.nichx.niplayer.feature.player.playerNavGraph
import com.nichx.niplayer.feature.player.AudioPlaybackManager
import com.nichx.niplayer.feature.player.MusicBar
import com.nichx.niplayer.feature.player.PlayerActivity
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
                // A1 修复：datastore 只存序号，在 UI 边界还原为配色方案枚举
                scheme = NiScheme.fromKey(themeConfig.schemeKey),
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
                // UX-1 修复（2026-09-22）：POST_NOTIFICATIONS 此前**只在 manifest 声明、
                // 从未在运行时请求** —— Android 13+ 上用户从未被询问，系统默认视为拒绝，
                // 于是后台音频播放时通知栏与锁屏**不显示媒体控制卡**。
                // 前台服务本身能跑（不会崩），但「后台可控」这一核心能力对用户是隐形的。
                //
                // 请求时机刻意**不放在启动时**：用户刚打开 App 就被要权限、且无理由说明，
                // 正是本报告批评的反模式。改为**首次真正开始播放音频时**请求 ——
                // 此刻「后台播放控制」的需求对用户是自明的。
                // 被拒后不再反复弹（系统在两次拒绝后也会自动静默），改为提示可去系统设置开启。
                val isAudioPlaying by audioPlaybackManager.isPlaying.collectAsStateWithLifecycle()
                var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (!granted) {
                        appMessageController.postInfo(
                            getString(R.string.audio_notification_permission_denied),
                        )
                    }
                }
                LaunchedEffect(isAudioPlaying) {
                    if (!isAudioPlaying || notificationPermissionAsked) return@LaunchedEffect
                    // 仅 Android 13（API 33）起需要运行时请求该权限
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
                    notificationPermissionAsked = true
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // 版本检测：启动自动检查（24h 节流，静默失败），有更新时弹窗提示。
                // A2 修复：宿主逻辑自持于 :feature:home，:app 不再 import UpdateViewModel / UpdateDialogHost
                UpdateHost()

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
                // A2 修复：外部页（搜索/快速访问）请求在媒体库 tab 打开文件浏览的待办状态，
                // 已下沉到 :feature:home 的 HomeNavGraphState（跨路由存活）
                val homeNavState = rememberHomeNavGraphState()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val isPlayerScreen =
                    currentBackStackEntry?.destination?.route == Routes.Player.AUDIO_PLAYER ||
                            // 均衡器是播放器的子页：从全屏播放器进入时不显示 musicbar，
                            // 否则用户会误点 musicbar 再次进播放器，导致返回栈错乱
                            currentBackStackEntry?.destination?.route == Routes.Settings.EQUALIZER

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
                    // A2 架构修复：路由由各 feature 自己注册（homeNavGraph / playerNavGraph），
                    // :app 只负责装配与提供宿主侧能力，不再逐个 import feature 的屏幕
                    NiNavHost(
                        navController = navController,
                    ) {
                        homeNavGraph(
                            navController = navController,
                            state = homeNavState,
                            onFileBrowserMultiSelectChanged = { fileBrowserMultiSelect = it },
                            onPlayMedia = navigateToPlayer,
                            onApplyEqualizerToPlayer = { audioPlaybackManager.applyEqualizerSettings() },
                            onApplyEqualizerLive = { audioPlaybackManager.applyEqualizerLive() },
                        )
                        playerNavGraph(
                            navController = navController,
                            audioPlaybackManager = audioPlaybackManager,
                            onOpenEqualizer = { navController.navigate(Routes.Settings.EQUALIZER) },
                        )
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


