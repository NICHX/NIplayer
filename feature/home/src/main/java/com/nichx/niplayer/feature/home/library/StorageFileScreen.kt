package com.nichx.niplayer.feature.home.library


import com.nichx.niplayer.feature.home.R
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.SwapVerticalCircle
import androidx.compose.material3.DropdownMenuItem
import com.nichx.niplayer.designsystem.components.NiGlassDropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.ExperimentalSettings
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.datastore.OnlineMatchBlacklist
import com.nichx.niplayer.datastore.OnlineMatchCache
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.StorageAccess
import com.nichx.niplayer.designsystem.components.DownloadTargetChooserDialog
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/** 顶栏按钮的液态玻璃模糊/折射参数（AndroidLiquidGlass backdrop 配方）。 */
private object TopBarGlassDefaults {
    const val BlurRadius = 6f
    const val LensRadius = 6f
}

/**
 * 顶栏按钮的**液态玻璃材质**（AndroidLiquidGlass / com.kyant.backdrop）。
 *
 * 复用页面内容层的本地 backdrop（与多选操作栏同款捕获层）做真实液态玻璃：
 * vibrancy + blur + lens + highlight + shadow，底色与底部导航栏 pill 同款灰色
 * （surfaceContainer 按底栏不透明度半透明），保证视觉统一。
 *
 * @param backdrop 页面内容层的本地捕获 backdrop（[rememberLayerBackdrop]），
 *                 null 时（无捕获层）退化为无材质。
 * @param bg 玻璃底色，默认与导航栏 pill 同款灰色
 */
@Composable
private fun Modifier.niLiquidGlass(
    backdrop: Backdrop?,
    bg: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalNiGlassOpacity.current),
): Modifier {
    if (backdrop == null) return this
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            vibrancy()
            blur(TopBarGlassDefaults.BlurRadius.dp.toPx())
            lens(TopBarGlassDefaults.LensRadius.dp.toPx(), TopBarGlassDefaults.LensRadius.dp.toPx())
        },
        highlight = { Highlight.Default.copy(alpha = 0f) },
        shadow = { Shadow.Default.copy(alpha = 0f) },
        onDrawSurface = { drawRect(bg) },
    )
}

/**
 * 圆形玻璃按钮：液态玻璃灰圆底 + tertiary 图标（与底部导航栏 pill 视觉统一）。
 * 默认顶栏尺寸 40dp；FAB 等场景通过 [size]/[iconSize] 调整。
 */
@Composable
private fun GlassIconCircle(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            // 先裁剪成圆，让涟漪与玻璃底都跟随圆形按钮形状
            .clip(CircleShape)
            .niLiquidGlass(backdrop)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@SuppressLint("LocalContextGetResourceValueCall")
fun FileBrowserScreen(
    storageId: Int,
    initialPath: String = "",
    // 导航请求计数：由宿主每次打开请求时递增，即使 initialPath 相同也重新触发定位，
    // 修复重复点击同一快速访问书签时停留在上次浏览目录的问题
    navTick: Int = 0,
    onBack: () -> Unit,
    onPlayVideo: (Boolean) -> Unit,
    onNavigateToImageViewer: () -> Unit = {},
    onNavigateToDownloadManager: () -> Unit = {},
    // 多选态上抛给宿主：进入多选时由 Home 隐藏底部导航栏、MainActivity 隐藏音乐条
    onFileBrowserMultiSelectChanged: (Boolean) -> Unit = {},
) {
    val viewModel: StorageFileViewModel = hiltViewModel(key = "file_browser_$storageId")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val sortConfig by viewModel.sortConfig.collectAsStateWithLifecycle()
    val thumbnailUrls by viewModel.thumbnailUrls.collectAsStateWithLifecycle()
    val tooShortPaths by viewModel.tooShortPaths.collectAsStateWithLifecycle()
    val thumbnailProgress by viewModel.thumbnailProgress.collectAsStateWithLifecycle()
    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val activeUploadCount by viewModel.activeUploadCount.collectAsStateWithLifecycle()
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    val encryptedPaths by viewModel.encryptedPaths.collectAsStateWithLifecycle()
    val isMultiSelect by viewModel.isMultiSelect.collectAsStateWithLifecycle()
    val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
    val preparingPath by viewModel.preparingPlaybackPath.collectAsStateWithLifecycle()
    val selectableFiles by viewModel.selectableFiles.collectAsStateWithLifecycle()
    val messageController = LocalAppMessageController.current
    var fileMenu by remember { mutableStateOf<Pair<StorageFile, Boolean>?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    // 视图切换下拉菜单及其锚点（触发按钮的屏幕坐标，供玻璃菜单定位）
    var showViewMenu by remember { mutableStateOf(false) }
    var viewMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    // 排序/过滤下拉菜单锚点（触发按钮的屏幕坐标，供玻璃菜单定位）
    var sortMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    var filterMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    // 新建/上传 合并按钮的子菜单
    var showActionMenu by remember { mutableStateOf(false) }
    var actionMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    var showFileInfo by remember { mutableStateOf<StorageFile?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    // 文件夹访问加密对话框状态
    var showEncryptDialog by remember { mutableStateOf<StorageFile?>(null) }
    var showDecryptDialog by remember { mutableStateOf<StorageFile?>(null) }
    var showResetPasswordDialog by remember { mutableStateOf<StorageFile?>(null) }
    // 解锁弹窗由 pendingUnlockFolder state 直接驱动：ViewModel 置 null 时立即关闭
    val pendingUnlock by viewModel.pendingUnlockFolder.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    // 移动/复制冲突挂起状态：非空时弹窗让用户选择「跳过重复 / 覆盖 / 取消」
    val transferConflict by viewModel.transferConflict.collectAsStateWithLifecycle()
    // 批量文件操作进度（移动/复制/删除）：非空时显示进度条浮层
    val fileOpProgress by viewModel.fileOpProgress.collectAsStateWithLifecycle()
    // 文件管理对话框状态
    var renameTarget by remember { mutableStateOf<StorageFile?>(null) }
    var moveTarget by remember { mutableStateOf<StorageFile?>(null) }
    var deleteTarget by remember { mutableStateOf<StorageFile?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    // 多选"更多"菜单展开态
    var showMultiMoreMenu by remember { mutableStateOf(false) }
    // 批量移动/复制：非空时弹出目标目录选择对话框；值是移动还是复制
    var batchTransfer by remember { mutableStateOf<BatchTransferOp?>(null) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val galleryState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    var viewMode by remember { mutableStateOf(FileBrowserSettings.viewMode) }
    val isGridView = viewMode == FileBrowserSettings.ViewMode.GRID
    val isGalleryView = viewMode == FileBrowserSettings.ViewMode.GALLERY
    val isFlatView = viewMode == FileBrowserSettings.ViewMode.FLAT_LIST
    // 平铺列表模式下的展开树状态（子项缓存 / 展开集合 / 加载中集合）
    val treeChildren by viewModel.treeChildren.collectAsStateWithLifecycle()
    val treeExpanded by viewModel.treeExpanded.collectAsStateWithLifecycle()
    val treeLoading by viewModel.treeLoading.collectAsStateWithLifecycle()

    // 目录路径 -> 离开时的滚动位置(index, offset)
    // 切换文件夹时记录旧目录位置，返回/进入时恢复原滚动位置，而不是把目录重新顶到最顶
    val pathScrollCache = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    // 记录当前目录的精确滚动位置（在导航动作前调用）
    fun captureCurrentScroll() {
        pathScrollCache[uiState.currentPath] = when {
            isGridView -> gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            isGalleryView -> galleryState.firstVisibleItemIndex to galleryState.firstVisibleItemScrollOffset
            else -> listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
    }

    // 已恢复过滚动位置的目录。仅当"目录发生变化"时才恢复缓存位置；
    // 原地刷新/删除等 isLoading 翻转不应回拽到旧缓存位置（否则删完文件列表会跳回顶部）
    var lastRestoredPath by remember { mutableStateOf<String?>(null) }

    // 目录加载完成后：恢复该目录上次离开时的滚动位置
    LaunchedEffect(uiState.currentPath, uiState.isLoading) {
        if (!uiState.isLoading && lastRestoredPath != uiState.currentPath) {
            lastRestoredPath = uiState.currentPath
            pathScrollCache[uiState.currentPath]?.let { (index, offset) ->
                when {
                    isGridView -> gridState.scrollToItem(index, offset)
                    isGalleryView -> galleryState.scrollToItem(index, offset)
                    else -> listState.scrollToItem(index, offset)
                }
            }
        }
    }

    val showScrollToTop by remember {
        derivedStateOf {
            when {
                isGridView -> gridState.firstVisibleItemIndex > 2
                isGalleryView -> galleryState.firstVisibleItemIndex > 2
                else -> listState.firstVisibleItemIndex > 2
            }
        }
    }

    val context = LocalContext.current
    // 待下载文件与下载目标选择状态
    var pendingDownloadFiles by remember { mutableStateOf<List<StorageFile>>(emptyList()) }
    var showTargetChooser by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    /** 按给定目标下载（避免目录选择为单次时改动全局预设）。 */
    fun downloadFilesTo(files: List<StorageFile>, targetUrl: String?, targetName: String?) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            viewModel.downloadFile(files.first(), targetUrl, targetName)
        } else {
            viewModel.downloadFiles(files, targetUrl, targetName)
        }
    }

    /** 目标选择确认：按 setAsPreset 决定写预设还是单次下载。 */
    fun commitDownloadToPath(path: String, dirName: String, setAsPreset: Boolean) {
        val files = pendingDownloadFiles
        pendingDownloadFiles = emptyList()
        showTargetChooser = false
        if (setAsPreset) {
            if (files.size == 1) {
                viewModel.setDownloadDirAndDownload(files.first(), path, dirName)
            } else {
                viewModel.setDownloadDirAndDownloadFiles(files, path, dirName)
            }
        } else {
            downloadFilesTo(files, "file://$path", dirName)
        }
    }

    /** 目标选择确认：下载到预设目录。 */
    fun commitDownloadToPreset() {
        val files = pendingDownloadFiles
        pendingDownloadFiles = emptyList()
        showTargetChooser = false
        downloadFilesTo(files, DownloadSettings.downloadDirTargetUrl, DownloadSettings.downloadDirName)
    }

    /** 下载入口：先保证存储权限，再弹出「预设 / 选择」目标选择器。 */
    fun startDownload(files: List<StorageFile>) {
        if (files.isEmpty()) return
        pendingDownloadFiles = files
        if (!StorageAccess.canWriteSharedStorage(context)) {
            showPermissionDialog = true
        } else {
            showTargetChooser = true
        }
    }

    // MediaStore 授权删除 launcher：拉起系统 "删除这些项目？" 弹窗，
    // 结果由 viewModel.finalizePendingConsentDelete 统一落地（清 DB/缩略图/刷新）
    val mediaDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.finalizePendingConsentDelete(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StorageFileEvent.NavigateToPlayer -> onPlayVideo(event.isAudio)
                is StorageFileEvent.NavigateToImageViewer -> onNavigateToImageViewer()
                is StorageFileEvent.ShowError -> messageController.post(NiMessage.error(event.message))
                is StorageFileEvent.ShowToast -> messageController.post(NiMessage.info(event.message))
                is StorageFileEvent.OpenFileActions ->
                    fileMenu = event.file to event.isFavorited
                is StorageFileEvent.RequestMediaStoreDelete -> {
                    // createDeleteRequest 仅 API 30+ 可用；事件由 ViewModel 在 API 30 以上才触发，
                    // 此处仍显式做版本守卫以满足 lint 的 NewApi 检查
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val request = MediaStore.createDeleteRequest(context.contentResolver, event.uris)
                        mediaDeleteLauncher.launch(IntentSenderRequest.Builder(request).build())
                    }
                }
            }
        }
    }

    // 上传文件 launcher：选择单个文件（任意类型），传给 ViewModel 上传到当前目录
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // 从 Uri 查询文件名
            val fileName = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            }.getOrNull() ?: "upload_${System.currentTimeMillis()}"
            viewModel.uploadFile(uri, fileName)
        }
    }

    LaunchedEffect(storageId, navTick) {
        viewModel.initialize(storageId, initialPath)
    }

    if (initialPath.isNotEmpty()) {
        LaunchedEffect(initialPath, navTick) {
            // navTick 不变时 ViewModel 内部去重，避免图片查看等全屏页返回导致重新组合时
            // 重复定位到初始路径、丢失用户当前浏览位置
            viewModel.navigateToPath(initialPath, navTick)
        }
    }

    // 多选态上抛：进入/退出多选时通知宿主隐藏/恢复底栏与音乐条
    LaunchedEffect(isMultiSelect) {
        onFileBrowserMultiSelectChanged(isMultiSelect)
    }
    // 离开本页（pop 出子栈）时复位宿主侧多选态，避免残留隐藏状态
    DisposableEffect(Unit) {
        onDispose { onFileBrowserMultiSelectChanged(false) }
    }

    BackHandler(enabled = true) {
        when {
            isMultiSelect -> viewModel.exitMultiSelect()
            uiState.canGoUp -> { captureCurrentScroll(); viewModel.goUp() }
            else -> onBack()
        }
    }

    // 页面内容层的本地液态玻璃捕获：多选操作栏与顶栏按钮共用，供 drawBackdrop 做真实模糊 + 高光
    val contentBackdropSurface = MaterialTheme.colorScheme.background
    val multiSelectBarBackdrop = rememberLayerBackdrop {
        drawRect(contentBackdropSurface)
        drawContent()
    }

    NiScaffold(
        // 独立全屏目的地，用不透明底色承载页面内容
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isMultiSelect) {
                NiTopBar(
                    title = stringResource(R.string.storage_file_selected_count, selectedPaths.size),
                    navigationIcon = {
                        IconButton(onClick = viewModel::exitMultiSelect) {
                            NiStyleIcon(
                                icon = Icons.Rounded.Close,
                                style = NiAppIconStyle,
                                containerSize = 40.dp,
                                iconSize = 22.dp,
                                contentDescription = stringResource(R.string.storage_file_cancel_multi_select),
                            )
                        }
                    },
                )
            } else {
            NiTopBar(
                title = uiState.storageName.ifEmpty { stringResource(R.string.storage_file_browser_title) },
                navigationIcon = {
                    // 点击逐级返回；长按直接回到存储根目录（深层目录快速返回）
                    GlassIconCircle(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(
                            if (uiState.canGoUp) R.string.storage_file_go_up else R.string.back,
                        ),
                        onClick = {
                            if (uiState.canGoUp) { captureCurrentScroll(); viewModel.goUp() } else onBack()
                        },
                        onLongClick = {
                            if (uiState.canGoUp) viewModel.goToRoot()
                        },
                        backdrop = multiSelectBarBackdrop,
                    )
                },
                actions = {
                    // 常驻：视图切换（图标显示当前模式，点击打开下拉菜单选择并持久化）
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            // 锚点取按钮左下角，菜单从按钮正下方展开（不遮挡按钮）
                            val topLeft = coords.localToRoot(Offset.Zero)
                            viewMenuAnchor = topLeft + Offset(0f, coords.size.height.toFloat())
                        },
                    ) {
                        GlassIconCircle(
                            icon = when (viewMode) {
                                FileBrowserSettings.ViewMode.LIST -> Icons.AutoMirrored.Rounded.ViewList
                                FileBrowserSettings.ViewMode.GRID -> Icons.Rounded.GridView
                                FileBrowserSettings.ViewMode.GALLERY -> Icons.Rounded.PhotoLibrary
                                FileBrowserSettings.ViewMode.FLAT_LIST -> Icons.Rounded.AccountTree
                            },
                            contentDescription = stringResource(
                                when (viewMode) {
                                    FileBrowserSettings.ViewMode.LIST -> R.string.storage_file_view_list
                                    FileBrowserSettings.ViewMode.GRID -> R.string.storage_file_view_grid
                                    FileBrowserSettings.ViewMode.GALLERY -> R.string.storage_file_view_gallery
                                    FileBrowserSettings.ViewMode.FLAT_LIST -> R.string.storage_file_view_flat_list
                                },
                            ),
                            onClick = { showViewMenu = true },
                            backdrop = multiSelectBarBackdrop,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        NiGlassDropdownMenu(
                            expanded = showViewMenu,
                            onDismissRequest = { showViewMenu = false },
                            anchor = IntOffset(viewMenuAnchor.x.toInt(), viewMenuAnchor.y.toInt()),
                        ) {
                            ViewModeMenuItem(
                                label = stringResource(R.string.storage_file_view_list),
                                icon = Icons.AutoMirrored.Rounded.ViewList,
                                value = FileBrowserSettings.ViewMode.LIST,
                                current = viewMode,
                                onSelect = {
                                    showViewMenu = false
                                    viewMode = FileBrowserSettings.ViewMode.LIST
                                    FileBrowserSettings.viewMode = FileBrowserSettings.ViewMode.LIST
                                },
                            )
                            ViewModeMenuItem(
                                label = stringResource(R.string.storage_file_view_grid),
                                icon = Icons.Rounded.GridView,
                                value = FileBrowserSettings.ViewMode.GRID,
                                current = viewMode,
                                onSelect = {
                                    showViewMenu = false
                                    viewMode = FileBrowserSettings.ViewMode.GRID
                                    FileBrowserSettings.viewMode = FileBrowserSettings.ViewMode.GRID
                                },
                            )
                            ViewModeMenuItem(
                                label = stringResource(R.string.storage_file_view_gallery),
                                icon = Icons.Rounded.PhotoLibrary,
                                value = FileBrowserSettings.ViewMode.GALLERY,
                                current = viewMode,
                                onSelect = {
                                    showViewMenu = false
                                    viewMode = FileBrowserSettings.ViewMode.GALLERY
                                    FileBrowserSettings.viewMode = FileBrowserSettings.ViewMode.GALLERY
                                },
                            )
                            if (ExperimentalSettings.flatListViewEnabled) {
                                ViewModeMenuItem(
                                    label = stringResource(R.string.storage_file_view_flat_list),
                                    icon = Icons.Rounded.AccountTree,
                                    value = FileBrowserSettings.ViewMode.FLAT_LIST,
                                    current = viewMode,
                                    onSelect = {
                                        showViewMenu = false
                                        viewMode = FileBrowserSettings.ViewMode.FLAT_LIST
                                        FileBrowserSettings.viewMode = FileBrowserSettings.ViewMode.FLAT_LIST
                                    },
                                )
                            }
                        }
                    }
                    // 常驻：排序
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            // 锚点取按钮左下角，菜单从按钮正下方展开（不遮挡按钮）
                            val topLeft = coords.localToRoot(Offset.Zero)
                            sortMenuAnchor = topLeft + Offset(0f, coords.size.height.toFloat())
                        },
                    ) {
                        GlassIconCircle(
                            icon = Icons.Rounded.SwapVert,
                            contentDescription = stringResource(R.string.storage_file_sort),
                            onClick = { showSortMenu = true },
                            backdrop = multiSelectBarBackdrop,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        NiGlassDropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            anchor = IntOffset(sortMenuAnchor.x.toInt(), sortMenuAnchor.y.toInt()),
                        ) {
                            SortByMenuItem(
                                label = stringResource(R.string.storage_file_sort_name),
                                icon = Icons.Rounded.SortByAlpha,
                                value = FileBrowserSettings.SortBy.NAME,
                                current = sortConfig.sortBy,
                                ascending = sortConfig.ascending,
                                onSelect = {
                                    viewModel.setSortBy(FileBrowserSettings.SortBy.NAME)
                                },
                                onToggleDirection = {
                                    viewModel.setSortAscending(!sortConfig.ascending)
                                },
                            )
                            SortByMenuItem(
                                label = stringResource(R.string.storage_file_sort_modified),
                                icon = Icons.Rounded.Schedule,
                                value = FileBrowserSettings.SortBy.MODIFIED,
                                current = sortConfig.sortBy,
                                ascending = sortConfig.ascending,
                                onSelect = {
                                    viewModel.setSortBy(FileBrowserSettings.SortBy.MODIFIED)
                                },
                                onToggleDirection = {
                                    viewModel.setSortAscending(!sortConfig.ascending)
                                },
                            )
                            SortByMenuItem(
                                label = stringResource(R.string.storage_file_sort_size),
                                icon = Icons.Rounded.Storage,
                                value = FileBrowserSettings.SortBy.SIZE,
                                current = sortConfig.sortBy,
                                ascending = sortConfig.ascending,
                                onSelect = {
                                    viewModel.setSortBy(FileBrowserSettings.SortBy.SIZE)
                                },
                                onToggleDirection = {
                                    viewModel.setSortAscending(!sortConfig.ascending)
                                },
                            )
                            SortByMenuItem(
                                label = stringResource(R.string.storage_file_sort_type),
                                icon = Icons.Rounded.Category,
                                value = FileBrowserSettings.SortBy.TYPE,
                                current = sortConfig.sortBy,
                                ascending = sortConfig.ascending,
                                onSelect = {
                                    viewModel.setSortBy(FileBrowserSettings.SortBy.TYPE)
                                },
                                onToggleDirection = {
                                    viewModel.setSortAscending(!sortConfig.ascending)
                                },
                            )
                            HorizontalDivider()
                            SortToggleRow(
                                label = stringResource(R.string.storage_file_menu_media_only),
                                checked = sortConfig.showOnlyMediaFiles,
                                onCheckedChange = viewModel::toggleShowOnlyMediaFiles,
                            )
                            SortToggleRow(
                                label = stringResource(R.string.storage_file_menu_show_hidden),
                                checked = sortConfig.showHiddenFiles,
                                onCheckedChange = viewModel::toggleShowHiddenFiles,
                            )
                            SortToggleRow(
                                label = stringResource(R.string.storage_file_menu_hide_no_media_folders),
                                checked = sortConfig.hideNoMediaFolders,
                                onCheckedChange = viewModel::toggleHideNoMediaFolders,
                            )
                        }
                    }
                    // 可展开收起 ⋮：默认折叠成单个 ⋮，点击展开更多按钮；再点 ✕ 收起
                    var topBarExpanded by rememberSaveable { mutableStateOf(false) }
                    val totalActiveTasks = activeDownloadCount + activeUploadCount
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 展开态中间按钮组：淡入 + 从右向左横向展开（右侧 ⋮/✕ 位置不动）
                        AnimatedVisibility(
                            visible = topBarExpanded,
                            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 文件类型过滤：全部/视频/音频/图片
                                Box(
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        val topLeft = coords.localToRoot(Offset.Zero)
                                        filterMenuAnchor = topLeft + Offset(0f, coords.size.height.toFloat())
                                    },
                                ) {
                                    GlassIconCircle(
                                        icon = Icons.Rounded.FilterAlt,
                                        contentDescription = stringResource(R.string.storage_file_filter_title),
                                        onClick = { showFilterMenu = true },
                                        backdrop = multiSelectBarBackdrop,
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                    )
                                    NiGlassDropdownMenu(
                                        expanded = showFilterMenu,
                                        onDismissRequest = { showFilterMenu = false },
                                        anchor = IntOffset(filterMenuAnchor.x.toInt(), filterMenuAnchor.y.toInt()),
                                    ) {
                                        FilterMenuItem(
                                            label = stringResource(R.string.storage_file_filter_all),
                                            icon = Icons.Rounded.Apps,
                                            value = FileBrowserSettings.MediaFilter.ALL,
                                            current = sortConfig.mediaFilter,
                                        ) {
                                            viewModel.setMediaFilter(FileBrowserSettings.MediaFilter.ALL)
                                            showFilterMenu = false
                                        }
                                        FilterMenuItem(
                                            label = stringResource(R.string.storage_file_filter_video),
                                            icon = Icons.Rounded.Movie,
                                            value = FileBrowserSettings.MediaFilter.VIDEO,
                                            current = sortConfig.mediaFilter,
                                        ) {
                                            viewModel.setMediaFilter(FileBrowserSettings.MediaFilter.VIDEO)
                                            showFilterMenu = false
                                        }
                                        FilterMenuItem(
                                            label = stringResource(R.string.storage_file_filter_audio),
                                            icon = Icons.Rounded.MusicNote,
                                            value = FileBrowserSettings.MediaFilter.AUDIO,
                                            current = sortConfig.mediaFilter,
                                        ) {
                                            viewModel.setMediaFilter(FileBrowserSettings.MediaFilter.AUDIO)
                                            showFilterMenu = false
                                        }
                                        FilterMenuItem(
                                            label = stringResource(R.string.storage_file_filter_image),
                                            icon = Icons.Rounded.Image,
                                            value = FileBrowserSettings.MediaFilter.IMAGE,
                                            current = sortConfig.mediaFilter,
                                        ) {
                                            viewModel.setMediaFilter(FileBrowserSettings.MediaFilter.IMAGE)
                                            showFilterMenu = false
                                        }
                                    }
                                }
                                // 新建/上传：合并为一个按钮，点击弹出子菜单二选一
                                Box(
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        val topLeft = coords.localToRoot(Offset.Zero)
                                        actionMenuAnchor = topLeft + Offset(0f, coords.size.height.toFloat())
                                    },
                                ) {
                                    GlassIconCircle(
                                        icon = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.storage_file_new),
                                        onClick = { showActionMenu = !showActionMenu },
                                        backdrop = multiSelectBarBackdrop,
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                    )
                                    NiGlassDropdownMenu(
                                        expanded = showActionMenu,
                                        onDismissRequest = { showActionMenu = false },
                                        anchor = IntOffset(actionMenuAnchor.x.toInt(), actionMenuAnchor.y.toInt()),
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.storage_file_upload)) },
                                            leadingIcon = { Icon(Icons.Rounded.Upload, contentDescription = null) },
                                            onClick = {
                                                showActionMenu = false
                                                uploadLauncher.launch(arrayOf("*/*"))
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.storage_file_new_folder)) },
                                            leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                                            onClick = {
                                                showActionMenu = false
                                                showCreateFolder = true
                                            },
                                        )
                                    }
                                }
                                // 传输管理：合并下载/上传任务入口，有活动任务时带徽标
                                Box {
                                    GlassIconCircle(
                                        icon = Icons.Rounded.SwapVerticalCircle,
                                        contentDescription = stringResource(R.string.transfer_manager_title),
                                        onClick = onNavigateToDownloadManager,
                                        backdrop = multiSelectBarBackdrop,
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                    )
                                    if (totalActiveTasks > 0) {
                                        TransferCountBadge(count = totalActiveTasks)
                                    }
                                }
                            }
                        }
                        // 最右固定位：⋮ / ✕ 共用同一槽位原地交叉淡化，避免过渡期双占位导致位移
                        Crossfade(
                            targetState = topBarExpanded,
                            animationSpec = tween(200),
                        ) { expanded ->
                            if (expanded) {
                                GlassIconCircle(
                                    icon = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.storage_file_collapse_menu),
                                    onClick = { topBarExpanded = false },
                                    backdrop = multiSelectBarBackdrop,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            } else {
                                GlassIconCircle(
                                    icon = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.storage_file_more),
                                    onClick = { topBarExpanded = true },
                                    backdrop = multiSelectBarBackdrop,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                },
            )
            }
        },
        ) { padding ->
        // 内容满铺全屏并延伸到顶栏之下，可滚入顶栏模糊区。
        // 顶栏高度用 topInset 让位；面包屑/缩略图进度条作为列表首个 item 融入滚动流，随页面滚动。
        val topInset = padding.calculateTopPadding()
        // 列表头部：面包屑（非根目录）+ 缩略图生成进度条，随列表滚动
        val listHeader: @Composable () -> Unit = {
            Column {
                if (uiState.currentPath.isNotEmpty()) {
                    BreadcrumbBar(
                        path = uiState.currentPath,
                        onGoToRoot = { viewModel.goToRoot() },
                        onJumpToDepth = { depth -> captureCurrentScroll(); viewModel.jumpToDepth(depth) },
                    )
                }
                if (thumbnailProgress >= 0) {
                    ThumbnailProgressBar(progress = thumbnailProgress)
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            // 内容层：仅列表/状态，满铺全屏延伸到顶栏下可被模糊；标记为玻璃模糊的背景源
            Column(modifier = Modifier.fillMaxSize().layerBackdrop(multiSelectBarBackdrop)) {
                // 下拉刷新指示器要避开顶栏：整页内容满铺全屏滚到玻璃顶栏之下，默认指示器定位在
                // 全屏顶部会被透明顶栏盖住；故用自定义 indicator 下移 topInset，显现在顶栏之下。
                val pullRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            isRefreshing = isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = topInset),
                        )
                    },
                ) {
                    when {
                        uiState.isLoading && uiState.rawFiles.isEmpty() -> LoadingState()
                        uiState.error != null && uiState.rawFiles.isEmpty() -> ErrorState(
                            message = uiState.error!!,
                            onRetry = { viewModel.retryLoadCurrent() },
                        )
                        uiState.rawFiles.isEmpty() -> EmptyDirState()
                        else -> {
                            if (isGalleryView) {
                                FileGallery(
                                    files = uiState.files,
                                    thumbnailUrls = thumbnailUrls,
                                    tooShortPaths = tooShortPaths,
                                    encryptedPaths = encryptedPaths,
                                    isMultiSelect = isMultiSelect,
                                    selectedPaths = selectedPaths,
                                    preparingPath = preparingPath,
                                    onOpenDirectory = { file -> captureCurrentScroll(); viewModel.openDirectory(file) },
                                    onPlayFile = viewModel::playFile,
                                    onOpenImageFile = viewModel::openImageFile,
                                    onToggleSelection = viewModel::toggleSelection,
                                    onEnterMultiSelect = viewModel::enterMultiSelect,
                                    galleryState = galleryState,
                                    header = listHeader,
                                    contentTopInset = topInset,
                                )
                            } else if (isGridView) {
                                FileGrid(
                                    files = uiState.files,
                                    thumbnailUrls = thumbnailUrls,
                                    tooShortPaths = tooShortPaths,
                                    encryptedPaths = encryptedPaths,
                                    isMultiSelect = isMultiSelect,
                                    selectedPaths = selectedPaths,
                                    preparingPath = preparingPath,
                                    onOpenDirectory = { file -> captureCurrentScroll(); viewModel.openDirectory(file) },
                                    onPlayFile = viewModel::playFile,
                                    onOpenImageFile = viewModel::openImageFile,
                                    onShowFileActions = viewModel::openFileActions,
                                    onToggleSelection = viewModel::toggleSelection,
                                    onEnterMultiSelect = viewModel::enterMultiSelect,
                                    gridState = gridState,
                                    header = listHeader,
                                    contentTopInset = topInset,
                                )
                            } else if (isFlatView) {
                                FileFlatList(
                                    files = uiState.files,
                                    thumbnailUrls = thumbnailUrls,
                                    tooShortPaths = tooShortPaths,
                                    encryptedPaths = encryptedPaths,
                                    isMultiSelect = isMultiSelect,
                                    selectedPaths = selectedPaths,
                                    preparingPath = preparingPath,
                                    treeChildren = treeChildren,
                                    treeExpanded = treeExpanded,
                                    treeLoading = treeLoading,
                                    uploads = uploads,
                                    onCancelUpload = viewModel::cancelUpload,
                                    onToggleFolder = viewModel::toggleFolderExpanded,
                                    onOpenDirectory = { file -> captureCurrentScroll(); viewModel.openDirectory(file) },
                                    onPlayFile = viewModel::playFile,
                                    onOpenImageFile = viewModel::openImageFile,
                                    onShowFileActions = viewModel::openFileActions,
                                    onToggleSelection = viewModel::toggleSelection,
                                    onEnterMultiSelect = viewModel::enterMultiSelect,
                                    listState = listState,
                                    header = listHeader,
                                    contentTopInset = topInset,
                                )
                            } else {
                                FileList(
                                    files = uiState.files,
                                    thumbnailUrls = thumbnailUrls,
                                    tooShortPaths = tooShortPaths,
                                    encryptedPaths = encryptedPaths,
                                    isMultiSelect = isMultiSelect,
                                    selectedPaths = selectedPaths,
                                    preparingPath = preparingPath,
                                    uploads = uploads,
                                    onCancelUpload = viewModel::cancelUpload,
                                    onOpenDirectory = { file -> captureCurrentScroll(); viewModel.openDirectory(file) },
                                    onPlayFile = viewModel::playFile,
                                    onOpenImageFile = viewModel::openImageFile,
                                    onShowFileActions = viewModel::openFileActions,
                                    onToggleSelection = viewModel::toggleSelection,
                                    onEnterMultiSelect = viewModel::enterMultiSelect,
                                    listState = listState,
                                    header = listHeader,
                                    contentTopInset = topInset,
                                )
                            }
                        }
                }
            }
            }

            if (showScrollToTop && !isMultiSelect && !isGalleryView) {
                GlassIconCircle(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.storage_file_back_to_top),
                    onClick = {
                        scope.launch {
                            // 跳转回顶部：瞬时定位替代滑动，长列表下更省时
                            when {
                                isGridView -> gridState.scrollToItem(0)
                                isGalleryView -> galleryState.scrollToItem(0)
                                else -> listState.scrollToItem(0)
                            }
                        }
                    },
                    backdrop = multiSelectBarBackdrop,
                    size = 56.dp,
                    iconSize = 26.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // 原底部“上传/新建”FAB 已并入顶栏，无需再为其让位，下移到底栏避让位置
                        .padding(start = 16.dp, end = 16.dp, bottom = FabBottomOffset),
                )
            }

            // 批量文件操作进度浮层（移动/复制/删除）
            fileOpProgress?.let {
                FileOpProgressOverlay(progress = it)
            }

            // 多选模式底部操作栏：固定操作位（全选/下载/更多），更多内收起移动/复制/快速访问/删除
            if (isMultiSelect) {
                val selectedFiles = selectableFiles.filter { it.path in selectedPaths && !it.isDirectory }
                MultiSelectActionBar(
                    backdrop = multiSelectBarBackdrop,
                    selectedCount = selectedPaths.size,
                    allSelected = selectedPaths.size >= selectableFiles.count { !it.isDirectory } && selectableFiles.any { !it.isDirectory },
                    downloadEnabled = selectedFiles.isNotEmpty(),
                    fileManagementEnabled = viewModel.supportsFileManagement,
                    moreMenuExpanded = showMultiMoreMenu,
                    onMoreMenuOpenChange = { showMultiMoreMenu = it },
                    onSelectAll = viewModel::selectAllFiles,
                    onDownload = { startDownload(selectedFiles) },
                    onAddToQuickAccess = {
                        val selected = selectableFiles.filter { it.path in selectedPaths }
                        viewModel.addFilesToQuickAccess(selected)
                    },
                    onMove = {
                        showMultiMoreMenu = false
                        val selected = selectableFiles.filter { it.path in selectedPaths }
                        if (selected.isNotEmpty()) batchTransfer = BatchTransferOp.MOVE
                    },
                    onCopy = {
                        showMultiMoreMenu = false
                        val selected = selectableFiles.filter { it.path in selectedPaths }
                        if (selected.isNotEmpty()) batchTransfer = BatchTransferOp.COPY
                    },
                    onDelete = { showBatchDeleteConfirm = true },
                    onClose = viewModel::exitMultiSelect,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                        // 多选态宿主已隐藏底栏/音乐条，底部避让系统手势区后再上抬固定间距
                        .navigationBarsPadding(),
                )
            }
        }
    }

    if (showBatchDeleteConfirm) {
        val selectedForDelete = selectableFiles.filter { it.path in selectedPaths }
        val deleteFileCount = selectedForDelete.count { !it.isDirectory }
        val deleteDirCount = selectedForDelete.count { it.isDirectory }
        NiConfirmDialog(
            title = stringResource(R.string.storage_file_delete_selected_title),
            text = stringResource(
                if (deleteDirCount > 0) R.string.storage_file_delete_selected_body_dir
                else R.string.storage_file_delete_selected_body,
                selectedForDelete.size,
                deleteFileCount,
                deleteDirCount,
            ),
            confirmText = stringResource(R.string.storage_file_delete),
            confirmDanger = true,
            onConfirm = {
                showBatchDeleteConfirm = false
                viewModel.deleteSelected()
            },
            onDismiss = { showBatchDeleteConfirm = false },
        )
    }

    // 批量移动/复制的目标目录选择对话框
    batchTransfer?.let { op ->
        val selected = selectableFiles.filter { it.path in selectedPaths }
        FolderTargetDialog(
            title = stringResource(
                if (op == BatchTransferOp.MOVE) R.string.storage_file_batch_move_title
                else R.string.storage_file_batch_copy_title,
                selected.size,
            ),
            confirmText = stringResource(
                if (op == BatchTransferOp.MOVE) R.string.move_here else R.string.copy_here,
            ),
            startPath = uiState.currentPath,
            listSubfolders = viewModel::listSubfolders,
            toDirectory = viewModel::makeDirectory,
            encryptedPaths = encryptedPaths,
            unlockFolder = viewModel::tryUnlockFolder,
            onDismiss = { batchTransfer = null },
            onConfirm = { target ->
                batchTransfer = null
                when (op) {
                    BatchTransferOp.MOVE -> viewModel.moveFiles(selected, target)
                    BatchTransferOp.COPY -> viewModel.copyFiles(selected, target)
                }
            },
        )
    }

    // 移动/复制冲突弹窗：目标目录已有同名文件时由 ViewModel 挂起状态驱动
    transferConflict?.let { conflict ->
        TransferConflictDialog(
            duplicateCount = conflict.duplicateFiles.size,
            onSkip = { viewModel.resolveTransfer(TransferConflictMode.SKIP_DUPLICATES) },
            onOverwrite = { viewModel.resolveTransfer(TransferConflictMode.OVERWRITE) },
            onDismiss = { viewModel.cancelTransfer() },
        )
    }

    if (showTargetChooser) {
        DownloadTargetChooserDialog(
            presetPath = DownloadSettings.downloadDirPath,
            onDismiss = { showTargetChooser = false },
            onDownloadToPreset = ::commitDownloadToPreset,
            onDownloadToPath = ::commitDownloadToPath,
        )
    }

    if (showPermissionDialog) {
        NiConfirmDialog(
            title = stringResource(R.string.download_dir_permission_title),
            text = stringResource(R.string.download_dir_permission_message),
            confirmText = stringResource(R.string.download_dir_permission_grant),
            onConfirm = {
                showPermissionDialog = false
                StorageAccess.openAllFilesAccessSettings(context)
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    fileMenu?.let { (file, favorited) ->
        FileActionsSheet(
            file = file,
            isFavorited = favorited,
            canDownload = !file.isDirectory,
            showFileManagement = viewModel.supportsFileManagement,
            showDelete = viewModel.supportsDelete,
            isEncrypted = file.isDirectory && encryptedPaths.contains(file.path.trimEnd('/')),
            isRemoteStorage = uiState.isRemoteStorage,
            onDismiss = { fileMenu = null },
            onPlay = {
                fileMenu = null
                viewModel.playFile(file)
            },
            onDownload = {
                fileMenu = null
                startDownload(listOf(file))
            },
            onToggleQuickAccess = {
                fileMenu = null
                if (favorited) viewModel.removeQuickAccess(file)
                else viewModel.addQuickAccess(file)
            },
            onShowInfo = {
                fileMenu = null
                showFileInfo = file
            },
            onClearIgnoreLyrics = {
                fileMenu = null
                // 与播放器 matchKeyFor 一致：远程/媒体库文件 `sid:<storageId>:<path>`
                val matchKey = "sid:$storageId:${file.path}"
                OnlineMatchCache.removeLrc(context, matchKey)
                OnlineMatchBlacklist.skip(OnlineMatchBlacklist.Kind.LYRICS, matchKey)
                messageController.post(NiMessage.info(context.getString(R.string.storage_file_action_clear_ignore_lyrics_done)))
            },
            onRename = {
                fileMenu = null
                renameTarget = file
            },
            onMove = {
                fileMenu = null
                moveTarget = file
            },
            onDelete = {
                fileMenu = null
                deleteTarget = file
            },
            onEncrypt = {
                fileMenu = null
                showEncryptDialog = file
            },
            onDecrypt = {
                fileMenu = null
                showDecryptDialog = file
            },
            onResetPassword = {
                fileMenu = null
                showResetPasswordDialog = file
            },
        )
    }

    showFileInfo?.let { file ->
        FileInfoDialog(file = file, onDismiss = { showFileInfo = null })
    }

    // 文件管理对话框
    renameTarget?.let { file ->
        RenameFileDialog(
            fileName = file.name,
            isDirectory = file.isDirectory,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameFile(file, newName)
                renameTarget = null
            },
        )
    }

    moveTarget?.let { file ->
        FolderTargetDialog(
            title = stringResource(R.string.storage_file_move_title, file.name),
            confirmText = stringResource(R.string.move_here),
            startPath = uiState.currentPath,
            listSubfolders = viewModel::listSubfolders,
            toDirectory = viewModel::makeDirectory,
            encryptedPaths = encryptedPaths,
            unlockFolder = viewModel::tryUnlockFolder,
            onDismiss = { moveTarget = null },
            onConfirm = { target ->
                viewModel.moveFile(file, target)
                moveTarget = null
            },
        )
    }

    deleteTarget?.let { file ->
        DeleteConfirmDialog(
            fileName = file.name,
            isDirectory = file.isDirectory,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteFile(file)
                deleteTarget = null
            },
        )
    }

    if (showCreateFolder) {
        CreateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolder = false
            },
        )
    }

    // 文件夹访问加密对话框（仅远程存储 SMB/WebDAV 显示加密入口）
    val canEncrypt = uiState.isRemoteStorage

    showEncryptDialog?.let { folder ->
        FolderPasswordDialog(
            title = stringResource(R.string.storage_file_encrypt_folder),
            subtitle = stringResource(R.string.storage_file_encrypt_folder_desc, folder.name),
            confirmText = stringResource(R.string.storage_file_encrypt),
            onDismiss = { showEncryptDialog = null },
            onConfirm = { password ->
                viewModel.encryptFolder(folder, password)
                showEncryptDialog = null
            },
            visible = canEncrypt,
        )
    }

    showDecryptDialog?.let { folder ->
        FolderPasswordDialog(
            title = stringResource(R.string.storage_file_decrypt),
            subtitle = stringResource(R.string.storage_file_decrypt_desc, folder.name),
            confirmText = stringResource(R.string.storage_file_decrypt_confirm),
            onDismiss = { showDecryptDialog = null },
            onConfirm = { password ->
                viewModel.decryptFolder(folder, password)
                showDecryptDialog = null
            },
            visible = canEncrypt,
        )
    }

    showResetPasswordDialog?.let { folder ->
        ResetFolderPasswordDialog(
            folder = folder,
            onDismiss = { showResetPasswordDialog = null },
            onConfirm = { oldPassword, newPassword ->
                viewModel.resetFolderPassword(folder, oldPassword, newPassword)
                showResetPasswordDialog = null
            },
        )
    }

    pendingUnlock?.let { folder ->
        FolderUnlockDialog(
            folder = folder,
            errorMessage = unlockError,
            onDismiss = {
                viewModel.clearUnlockError()
                viewModel.cancelUnlock()
            },
            onPasswordSubmit = { password ->
                viewModel.submitFolderPassword(password)
            },
            onPasswordChange = { viewModel.clearUnlockError() },
        )
    }
}
