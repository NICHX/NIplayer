package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow

import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.SwapVerticalCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiGlassDropdownMenu
import com.nichx.niplayer.designsystem.components.NiGlassOverlay
import com.nichx.niplayer.designsystem.components.NiGlassOverlayKind
import com.nichx.niplayer.designsystem.components.NiGlassOverlayRequest
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiExtendedFAB
import com.nichx.niplayer.designsystem.components.NiFAB
import com.nichx.niplayer.designsystem.components.NiFabVariant
import com.nichx.niplayer.designsystem.components.NiProgressTrack
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity
import com.nichx.niplayer.designsystem.components.NiGlassHairWidth
import com.nichx.niplayer.designsystem.components.niFrostSurfaceColor
import com.nichx.niplayer.designsystem.components.niGlassBorderColor
import com.nichx.niplayer.designsystem.components.glassOnSurface
import com.nichx.niplayer.designsystem.components.glassOnSurfaceMuted
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.MediaFileTypes.isImageFile
import com.nichx.niplayer.storage.StorageFile
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

// 主 FAB 底部偏移，与媒体库页"新增媒体库"按钮位置保持一致
// （该值已包含对应用底部导航栏 NiBottomBar 的避让）
private val FabBottomOffset = 104.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@SuppressLint("LocalContextGetResourceValueCall")
fun FileBrowserScreen(
    storageId: Int,
    initialPath: String = "",
    onBack: () -> Unit,
    onPlayVideo: () -> Unit,
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
    val messageController = LocalAppMessageController.current
    var fileMenu by remember { mutableStateOf<Pair<StorageFile, Boolean>?>(null) }
    var isGridView by remember { mutableStateOf(FileBrowserSettings.isGridView) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    // 排序/过滤下拉菜单锚点（触发按钮的屏幕坐标，供玻璃菜单定位）
    var sortMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    var filterMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    var showFileInfo by remember { mutableStateOf<StorageFile?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    // 文件夹访问加密对话框状态
    var showEncryptDialog by remember { mutableStateOf<StorageFile?>(null) }
    var showDecryptDialog by remember { mutableStateOf<StorageFile?>(null) }
    var showResetPasswordDialog by remember { mutableStateOf<StorageFile?>(null) }
    // 解锁弹窗由 pendingUnlockFolder state 直接驱动：ViewModel 置 null 时立即关闭
    val pendingUnlock by viewModel.pendingUnlockFolder.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    // 文件管理对话框状态
    var renameTarget by remember { mutableStateOf<StorageFile?>(null) }
    var moveTarget by remember { mutableStateOf<StorageFile?>(null) }
    var moveTargets by remember { mutableStateOf<List<StorageFile>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<StorageFile?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // 目录路径 -> 离开时的滚动位置(index, offset)
    // 切换文件夹时记录旧目录位置，返回/进入时恢复原滚动位置，而不是把目录重新顶到最顶
    val pathScrollCache = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    // 记录当前目录的精确滚动位置（在导航动作前调用）
    fun captureCurrentScroll() {
        pathScrollCache[uiState.currentPath] = if (isGridView) {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        } else {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
    }

    // 目录加载完成后：恢复该目录上次离开时的滚动位置
    LaunchedEffect(uiState.currentPath, uiState.isLoading) {
        if (!uiState.isLoading) {
            pathScrollCache[uiState.currentPath]?.let { (index, offset) ->
                if (isGridView) {
                    gridState.scrollToItem(index, offset)
                } else {
                    listState.scrollToItem(index, offset)
                }
            }
        }
    }

    val showScrollToTop by remember {
        derivedStateOf {
            if (isGridView) gridState.firstVisibleItemIndex > 2
            else listState.firstVisibleItemIndex > 2
        }
    }

    val context = LocalContext.current
    var pendingDownloadFiles by remember { mutableStateOf<List<StorageFile>>(emptyList()) }
    val downloadTargetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val files = pendingDownloadFiles
        pendingDownloadFiles = emptyList()
        if (treeUri == null || files.isEmpty()) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) { }
        val dirName = DocumentFile.fromTreeUri(context, treeUri)?.name
            ?: context.getString(R.string.download_dir_default_name)
        if (files.size == 1) {
            viewModel.setDownloadDirAndDownload(files.first(), treeUri.toString(), dirName)
        } else {
            viewModel.setDownloadDirAndDownloadFiles(files, treeUri.toString(), dirName)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StorageFileEvent.NavigateToPlayer -> onPlayVideo()
                is StorageFileEvent.NavigateToImageViewer -> onNavigateToImageViewer()
                is StorageFileEvent.ShowError -> messageController.post(NiMessage.error(event.message))
                is StorageFileEvent.ShowToast -> messageController.post(NiMessage.info(event.message))
                is StorageFileEvent.OpenFileActions ->
                    fileMenu = event.file to event.isFavorited
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

    LaunchedEffect(storageId) {
        viewModel.initialize(storageId, initialPath)
    }

    if (initialPath.isNotEmpty()) {
        LaunchedEffect(initialPath) {
            viewModel.navigateToPath(initialPath)
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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StorageFileEvent.NavigateToPlayer -> onPlayVideo()
                is StorageFileEvent.NavigateToImageViewer -> onNavigateToImageViewer()
                is StorageFileEvent.ShowError -> messageController.post(NiMessage.error(event.message))
                is StorageFileEvent.ShowToast -> messageController.post(NiMessage.info(event.message))
                is StorageFileEvent.OpenFileActions ->
                    fileMenu = event.file to event.isFavorited
            }
        }
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
                    Box(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    if (uiState.canGoUp) { captureCurrentScroll(); viewModel.goUp() } else onBack()
                                },
                                onLongClick = {
                                    if (uiState.canGoUp) viewModel.goToRoot()
                                },
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        NiStyleIcon(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            style = NiAppIconStyle,
                            containerSize = 40.dp,
                            iconSize = 22.dp,
                            contentDescription = stringResource(
                                if (uiState.canGoUp) R.string.storage_file_go_up else R.string.back,
                            ),
                        )
                    }
                },
                actions = {
                    // 显式多选入口（长按已改为打开单文件菜单，多选由此按钮进入）
                    IconButton(onClick = viewModel::enterMultiSelect) {
                        NiStyleIcon(
                            icon = Icons.Rounded.SelectAll,
                            style = NiAppIconStyle,
                            containerSize = 40.dp,
                            iconSize = 22.dp,
                            contentDescription = stringResource(R.string.storage_file_enter_multi_select),
                        )
                    }
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            // 锚点取按钮左下角，菜单从按钮正下方展开（不遮挡按钮）
                            val topLeft = coords.localToRoot(Offset.Zero)
                            sortMenuAnchor = topLeft + Offset(0f, coords.size.height.toFloat())
                        },
                    ) {
                        IconButton(onClick = { showSortMenu = true }) {
                            NiStyleIcon(
                                icon = Icons.Rounded.SwapVert,
                                style = NiAppIconStyle,
                                containerSize = 40.dp,
                                iconSize = 22.dp,
                                contentDescription = stringResource(R.string.storage_file_sort),
                            )
                        }
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
                        }
                    }
                    // 文件类型过滤：全部/视频/音频/图片
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val topLeft = coords.localToRoot(Offset.Zero)
                            filterMenuAnchor = topLeft + Offset(0f, coords.size.height.toFloat())
                        },
                    ) {
                        IconButton(onClick = { showFilterMenu = true }) {
                            NiStyleIcon(
                                icon = Icons.Rounded.FilterAlt,
                                style = NiAppIconStyle,
                                containerSize = 40.dp,
                                iconSize = 22.dp,
                                contentDescription = stringResource(R.string.storage_file_filter_title),
                            )
                        }
                        NiGlassDropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            anchor = IntOffset(filterMenuAnchor.x.toInt(), filterMenuAnchor.y.toInt()),
                        ) {
                            FilterMenuItem(
                                label = stringResource(R.string.storage_file_filter_all),
                                value = FileBrowserSettings.MediaFilter.ALL,
                                current = sortConfig.mediaFilter,
                            ) {
                                viewModel.setMediaFilter(FileBrowserSettings.MediaFilter.ALL)
                                showFilterMenu = false
                            }
                            FilterMenuItem(
                                label = stringResource(R.string.storage_file_filter_video),
                                value = FileBrowserSettings.MediaFilter.VIDEO,
                                current = sortConfig.mediaFilter,
                            ) {
                                viewModel.setMediaFilter(FileBrowserSettings.MediaFilter.VIDEO)
                                showFilterMenu = false
                            }
                            FilterMenuItem(
                                label = stringResource(R.string.storage_file_filter_audio),
                                value = FileBrowserSettings.MediaFilter.AUDIO,
                                current = sortConfig.mediaFilter,
                            ) {
                                viewModel.setMediaFilter(FileBrowserSettings.MediaFilter.AUDIO)
                                showFilterMenu = false
                            }
                            FilterMenuItem(
                                label = stringResource(R.string.storage_file_filter_image),
                                value = FileBrowserSettings.MediaFilter.IMAGE,
                                current = sortConfig.mediaFilter,
                            ) {
                                viewModel.setMediaFilter(FileBrowserSettings.MediaFilter.IMAGE)
                                showFilterMenu = false
                            }
                        }
                    }
                    IconButton(onClick = {
                        val newValue = !isGridView
                        isGridView = newValue
                        FileBrowserSettings.isGridView = newValue
                    }) {
                        NiStyleIcon(
                            icon = if (isGridView) Icons.AutoMirrored.Rounded.ViewList
                            else Icons.Rounded.GridView,
                            style = NiAppIconStyle,
                            containerSize = 40.dp,
                            iconSize = 22.dp,
                            contentDescription = stringResource(
                                if (isGridView) R.string.storage_file_view_list
                                else R.string.storage_file_view_grid,
                            ),
                        )
                    }
                    if (activeDownloadCount > 0) {
                        Box {
                            IconButton(onClick = onNavigateToDownloadManager) {
                                NiStyleIcon(
                                    icon = Icons.Rounded.Download,
                                    style = NiAppIconStyle,
                                    containerSize = 40.dp,
                                    iconSize = 22.dp,
                                    contentDescription = stringResource(R.string.storage_file_download_tasks),
                                )
                            }
                            TransferCountBadge(count = activeDownloadCount)
                        }
                    }
                    if (activeUploadCount > 0) {
                        Box {
                            IconButton(onClick = onNavigateToDownloadManager) {
                                NiStyleIcon(
                                    icon = Icons.Rounded.Upload,
                                    style = NiAppIconStyle,
                                    containerSize = 40.dp,
                                    iconSize = 22.dp,
                                    contentDescription = stringResource(R.string.storage_file_upload_tasks),
                                )
                            }
                            TransferCountBadge(count = activeUploadCount)
                        }
                    }
                    // 常驻传输管理入口：独立全屏页不常驻 Home 底部导航，提供快捷入口查看下载/上传任务
                    IconButton(onClick = onNavigateToDownloadManager) {
                        NiStyleIcon(
                            icon = Icons.Rounded.SwapVerticalCircle,
                            style = NiAppIconStyle,
                            containerSize = 40.dp,
                            iconSize = 22.dp,
                            contentDescription = stringResource(R.string.transfer_manager_title),
                        )
                    }
                },
            )
            }
        },
        ) { padding ->
        // 内容满铺全屏并延伸到顶栏之下，可滚入顶栏模糊区。
        // 顶栏高度用 topInset 让位；浮动面包屑叠加其上，列表从面包屑下方开始滚动、仍能滚到顶栏下被模糊。
        val topInset = padding.calculateTopPadding()
        var breadcrumbHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        // 多选操作栏的液态玻璃背景：捕获页面内容层（列表等），供 drawBackdrop 做真实模糊 + 高光
        val contentBackdropSurface = MaterialTheme.colorScheme.background
        val multiSelectBarBackdrop = rememberLayerBackdrop {
            drawRect(contentBackdropSurface)
            drawContent()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            // 内容层：仅列表/状态，满铺全屏延伸到顶栏下可被模糊；标记为玻璃模糊的背景源
            Column(modifier = Modifier.fillMaxSize().layerBackdrop(multiSelectBarBackdrop)) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        uiState.isLoading && uiState.rawFiles.isEmpty() -> LoadingState()
                        uiState.error != null && uiState.rawFiles.isEmpty() -> ErrorState(
                            message = uiState.error!!,
                            onRetry = { viewModel.retryLoadCurrent() },
                        )
                        uiState.rawFiles.isEmpty() -> EmptyDirState()
                        else -> {
                            if (isGridView) {
                                FileGrid(
                                    files = uiState.files,
                                    thumbnailUrls = thumbnailUrls,
                                    tooShortPaths = tooShortPaths,
                                    encryptedPaths = encryptedPaths,
                                    isMultiSelect = isMultiSelect,
                                    selectedPaths = selectedPaths,
                                    onOpenDirectory = { file -> captureCurrentScroll(); viewModel.openDirectory(file) },
                                    onPlayFile = viewModel::playFile,
                                    onOpenImageFile = viewModel::openImageFile,
                                    onShowFileActions = viewModel::openFileActions,
                                    onToggleSelection = viewModel::toggleSelection,
                                    gridState = gridState,
                                    contentTopInset = topInset + breadcrumbHeight,
                                )
                            } else {
                                FileList(
                                    files = uiState.files,
                                    thumbnailUrls = thumbnailUrls,
                                    tooShortPaths = tooShortPaths,
                                    encryptedPaths = encryptedPaths,
                                    isMultiSelect = isMultiSelect,
                                    selectedPaths = selectedPaths,
                                    uploads = uploads,
                                    onCancelUpload = viewModel::cancelUpload,
                                    onOpenDirectory = { file -> captureCurrentScroll(); viewModel.openDirectory(file) },
                                    onPlayFile = viewModel::playFile,
                                    onOpenImageFile = viewModel::openImageFile,
                                    onShowFileActions = viewModel::openFileActions,
                                    onToggleSelection = viewModel::toggleSelection,
                                    listState = listState,
                                    contentTopInset = topInset + breadcrumbHeight,
                                )
                            }
                        }
                    }
                }
            }

            // 缩略图生成进度条：浮动在面包屑下方，不占布局流（否则会与列表 contentTopInset 叠加造成下方大片空白）
            if (thumbnailProgress >= 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = topInset + breadcrumbHeight),
                ) {
                    ThumbnailProgressBar(progress = thumbnailProgress)
                }
            }

            // 浮动面包屑：固定于顶栏下方、悬浮在内容之上；不占布局流，不挡内容滚到顶栏下被模糊
            if (uiState.currentPath.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset)
                        .onSizeChanged {
                            breadcrumbHeight = with(density) { it.height.toDp() }
                        },
                ) {
                    BreadcrumbBar(
                        path = uiState.currentPath,
                        onGoToRoot = { viewModel.goToRoot() },
                        onJumpToDepth = { depth -> captureCurrentScroll(); viewModel.jumpToDepth(depth) },
                    )
                }
            }

            BackHandler(enabled = fabExpanded) {
                fabExpanded = false
            }

            if (showScrollToTop && !fabExpanded && !isMultiSelect) {
                NiFAB(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    onClick = {
                        scope.launch {
                            if (isGridView) gridState.animateScrollToItem(0)
                            else listState.animateScrollToItem(0)
                        }
                    },
                    contentDescription = stringResource(R.string.storage_file_back_to_top),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 16.dp, end = 16.dp, bottom = FabBottomOffset + 88.dp),
                    variant = NiFabVariant.OUTLINED,
                )
            }

            if (viewModel.supportsFileManagement && !isMultiSelect) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 16.dp, end = 16.dp, bottom = FabBottomOffset),
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        horizontalAlignment = Alignment.End,
                    ) {
                        AnimatedVisibility(
                            visible = fabExpanded,
                            enter = fadeIn() + scaleIn(initialScale = 0.8f),
                            exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        ) {
                            Column(
                                modifier = Modifier.padding(bottom = 16.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                NiExtendedFAB(
                                    icon = Icons.Rounded.Upload,
                                    text = stringResource(R.string.storage_file_upload),
                                    onClick = {
                                        fabExpanded = false
                                        uploadLauncher.launch(arrayOf("*/*"))
                                    },
                                    variant = NiFabVariant.OUTLINED,
                                )
                                NiExtendedFAB(
                                    icon = Icons.Rounded.CreateNewFolder,
                                    text = stringResource(R.string.storage_file_new_folder),
                                    onClick = {
                                        fabExpanded = false
                                        showCreateFolder = true
                                    },
                                    variant = NiFabVariant.OUTLINED,
                                )
                            }
                        }
                        NiFAB(
                            icon = if (fabExpanded) Icons.Rounded.Close else Icons.Rounded.Add,
                            onClick = { fabExpanded = !fabExpanded },
                            contentDescription = stringResource(
                                if (fabExpanded) R.string.storage_file_collapse_menu else R.string.storage_file_new,
                            ),
                            variant = NiFabVariant.PRIMARY,
                        )
                    }
                }
            }

            // 多选模式底部操作栏：固定三操作位（全选/下载/删除），条件不满足时置灰而非显隐
            if (isMultiSelect) {
                val selectedFiles = uiState.files.filter { it.path in selectedPaths && !it.isDirectory }
                MultiSelectActionBar(
                    backdrop = multiSelectBarBackdrop,
                    selectedCount = selectedPaths.size,
                    allSelected = selectedPaths.size >= uiState.files.count { !it.isDirectory } && uiState.files.any { !it.isDirectory },
                    downloadEnabled = selectedFiles.isNotEmpty(),
                    onSelectAll = viewModel::selectAllFiles,
                    onDownload = {
                        if (DownloadSettings.isDownloadDirSet) {
                            viewModel.downloadFiles(
                                selectedFiles,
                                DownloadSettings.downloadDirUri,
                                DownloadSettings.downloadDirName,
                            )
                        } else {
                            pendingDownloadFiles = selectedFiles
                            downloadTargetLauncher.launch(null)
                        }
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
        val selectedForDelete = uiState.files.filter { it.path in selectedPaths }
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

    fileMenu?.let { (file, favorited) ->
        FileActionsSheet(
            file = file,
            isFavorited = favorited,
            canDownload = !file.isDirectory,
            showFileManagement = viewModel.supportsFileManagement,
            isEncrypted = file.isDirectory && encryptedPaths.contains(file.path.trimEnd('/')),
            isRemoteStorage = uiState.isRemoteStorage,
            onDismiss = { fileMenu = null },
            onPlay = {
                fileMenu = null
                viewModel.playFile(file)
            },
            onDownload = {
                fileMenu = null
                if (DownloadSettings.isDownloadDirSet) {
                    viewModel.downloadFile(
                        file,
                        DownloadSettings.downloadDirUri,
                        DownloadSettings.downloadDirName,
                    )
                } else {
                    pendingDownloadFiles = listOf(file)
                    downloadTargetLauncher.launch(null)
                }
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
            onRename = {
                fileMenu = null
                renameTarget = file
            },
            onMove = {
                fileMenu = null
                moveTarget = file
                scope.launch {
                    moveTargets = viewModel.listMoveTargets(file)
                }
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
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameFile(file, newName)
                renameTarget = null
            },
        )
    }

    moveTarget?.let { file ->
        MoveTargetDialog(
            fileName = file.name,
            targets = moveTargets,
            onDismiss = {
                moveTarget = null
                moveTargets = emptyList()
            },
            onSelect = { target ->
                viewModel.moveFile(file, target)
                moveTarget = null
                moveTargets = emptyList()
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

@Composable
private fun SortByMenuItem(
    label: String,
    icon: ImageVector,
    value: FileBrowserSettings.SortBy,
    current: FileBrowserSettings.SortBy,
    ascending: Boolean,
    onSelect: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    val selected = current == value
    DropdownMenuItem(
        modifier = Modifier.height(38.dp),
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        // 当前排序项显示方向箭头（点击切换升降序），非当前项无箭头（点击仅选中）
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = if (ascending) Icons.Rounded.ArrowUpward
                    else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = { if (selected) onToggleDirection() else onSelect() },
    )
}

/** 菜单内的轻量勾选行：点击整行或复选框均可切换，不关闭菜单。 */
@Composable
private fun SortToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}

/** 菜单内的文件类型过滤选项：点击选中并关闭菜单（单选）。 */
@Composable
private fun FilterMenuItem(
    label: String,
    value: FileBrowserSettings.MediaFilter,
    current: FileBrowserSettings.MediaFilter,
    onClick: () -> Unit,
) {
    val selected = current == value
    DropdownMenuItem(
        modifier = Modifier.height(38.dp),
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun ThumbnailProgressBar(progress: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.storage_file_generating_thumbnails),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BreadcrumbBar(
    path: String,
    onJumpToDepth: (Int) -> Unit,
    onGoToRoot: () -> Unit,
) {
    val segments = path.split("/").filter { it.isNotEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 根目录入口：点击直接回到存储根，深层目录无需逐级返回
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onGoToRoot)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Home,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "▸",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            val bgColor = if (isLast)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainerLow

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .then(
                        // 任务8 修复：directoryStack[0] = ROOT（path=""，不出现在 segments 中），
                        // segments[0] 是第一级子目录，对应 directoryStack[1]。
                        // 原实现传 index 会导致点击任意段都跳到比预期浅一级
                        // （点击 segments[0] 跳到 directoryStack[0]=ROOT 根目录）。
                        // 修正为 index + 1 让 segments 索引对齐 directoryStack 索引。
                        if (!isLast) Modifier.clickable { onJumpToDepth(index + 1) }
                        else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (index == 0) Icons.Rounded.FolderOpen
                    else Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isLast) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isLast) {
                Text(
                    text = "▸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.storage_file_connecting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        NiEmptyState(
            icon = Icons.Rounded.Refresh,
            text = stringResource(R.string.storage_file_load_failed),
            hint = message,
            actionText = stringResource(R.string.retry),
            onAction = onRetry,
        )
    }
}

@Composable
private fun EmptyDirState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        NiEmptyState(
            icon = Icons.Rounded.FolderOpen,
            text = stringResource(R.string.storage_file_empty_dir),
            hint = stringResource(R.string.storage_file_empty_dir_hint),
        )
    }
}

@Composable
private fun FileList(
    files: List<StorageFile>,
    thumbnailUrls: Map<String, String>,
    tooShortPaths: Set<String>,
    encryptedPaths: Set<String>,
    isMultiSelect: Boolean,
    selectedPaths: Set<String>,
    uploads: List<ActiveUpload>,
    onCancelUpload: (Long) -> Unit,
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onShowFileActions: (StorageFile) -> Unit,
    onToggleSelection: (StorageFile) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    contentTopInset: Dp = 0.dp,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopInset,
            bottom = FabBottomOffset,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uploads.isNotEmpty()) {
            item(key = "upload-strip") {
                UploadPendingStrip(uploads = uploads, onCancel = onCancelUpload)
            }
        }
        items(
            items = files,
            key = { it.path },
        ) { file ->
            FileRow(
                file = file,
                thumbnailUrl = thumbnailUrls[file.path],
                isTooShort = tooShortPaths.contains(file.path),
                isEncrypted = file.isDirectory && encryptedPaths.contains(file.path.trimEnd('/')),
                isMultiSelect = isMultiSelect,
                isSelected = file.path in selectedPaths,
                onOpenDirectory = onOpenDirectory,
                onPlayFile = onPlayFile,
                onOpenImageFile = onOpenImageFile,
                onShowFileActions = { onShowFileActions(file) },
                onToggleSelection = { onToggleSelection(file) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UploadPendingStrip(uploads: List<ActiveUpload>, onCancel: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uploads.forEach { u ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = u.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (u.fraction >= 0f) {
                        LinearProgressIndicator(
                            progress = { u.fraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    } else {
                        // 总大小未知 → 不确定进度
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (u.fraction >= 0f) {
                                stringResource(R.string.storage_file_upload_progress, (u.fraction * 100).toInt())
                            } else {
                                stringResource(R.string.storage_file_upload_waiting)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (u.speedBytesPerSec > 0) {
                            Text(
                                text = formatUploadSpeed(u.speedBytesPerSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { onCancel(u.taskId) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.storage_file_upload_cancel),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: StorageFile,
    thumbnailUrl: String?,
    isTooShort: Boolean,
    isEncrypted: Boolean,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onShowFileActions: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val isVideo = MediaFileTypes.isVideoFile(file.name)
    val isAudio = MediaFileTypes.isAudioFile(file.name)
    val isImage = MediaFileTypes.isImageFile(file.name)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "rowBgAlpha",
    )

    val thumbShape = RoundedCornerShape(8.dp)

    val rowBgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        NiExtraColors.current.surfaceLevel2
    }

    val rowShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = bgAlpha }
            // 多选模式下取消阴影：列表保持平整，选中态用边框+背景区分，避免阴影干扰视觉
            .then(
                if (isMultiSelect) {
                    Modifier
                } else {
                    Modifier.shadow(elevation = 1.dp, shape = rowShape, clip = false)
                }
            )
            .clip(rowShape)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = rowShape,
                    )
                } else {
                    Modifier
                }
            )
            .background(rowBgColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isMultiSelect) {
                        onToggleSelection()
                    } else {
                        when {
                            file.isDirectory -> onOpenDirectory(file)
                            isVideo || isAudio -> onPlayFile(file)
                            isImage -> onOpenImageFile(file)
                            else -> Unit
                        }
                    }
                },
                onLongClick = {
                    // 长按 = 打开单文件操作菜单；多选模式下长按不弹菜单（避免与多选冲突）
                    if (!isMultiSelect) onShowFileActions()
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiSelect) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(
                    if (isSelected) R.string.storage_file_deselect else R.string.storage_file_select,
                ),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        if (isVideo || isAudio || isImage) {
            // 列表缩略图：音频用 56×56 方形（唱片封套感 + 品牌色底），
            // 视频/图片保持 88×56 16:9 影视感。
            val thumbWidth = if (isAudio) 56.dp else 88.dp
            val thumbHeight = 56.dp
            val thumbBgColor: Color = if (isAudio)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else NiExtraColors.current.surfaceLevel3
            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .clip(thumbShape)
                    .background(thumbBgColor),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 中央播放徽章：仅可播放文件
                    if (isVideo || isAudio) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.storage_file_action_play),
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    // 文件大小角标：右下角
                    if (file.length > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = formatFileSize(file.length),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else if (isVideo && isTooShort) {
                    Text(
                        text = "<15s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isImage -> Icons.Rounded.Image
                            isAudio -> Icons.Rounded.MusicNote
                            else -> Icons.Rounded.Movie
                        },
                        contentDescription = null,
                        tint = if (isAudio)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(if (isAudio) 26.dp else 24.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        } else if (file.isDirectory) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                // 加密文件夹锁定角标
                if (isEncrypted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.storage_file_encrypted),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NiExtraColors.current.surfaceLevel3),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = fileIcon(file, isVideo, isAudio),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            // 文件名 2 行自动缩字，长名从 16sp 缩到 12sp，仍超出则省略
            NiAutoSizeText(
                text = file.name,
                maxLines = 2,
                minFontSize = 12.sp,
                maxFontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
            // 文件大小已移至缩略图右下角角标，此处仅保留文件夹提示
            if (file.isDirectory) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.storage_file_folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (!isMultiSelect) {
            IconButton(
                onClick = onShowFileActions,
                modifier = Modifier.size(32.dp),
            ) {
                NiStyleIcon(
                    icon = Icons.Rounded.MoreVert,
                    style = NiAppIconStyle,
                    containerSize = 32.dp,
                    iconSize = 18.dp,
                    contentDescription = stringResource(R.string.storage_file_more),
                )
            }
        }
    }
}

@Composable
private fun FileGrid(
    files: List<StorageFile>,
    thumbnailUrls: Map<String, String>,
    tooShortPaths: Set<String>,
    encryptedPaths: Set<String>,
    isMultiSelect: Boolean,
    selectedPaths: Set<String>,
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onShowFileActions: (StorageFile) -> Unit,
    onToggleSelection: (StorageFile) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    contentTopInset: Dp = 0.dp,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopInset,
            bottom = FabBottomOffset,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = files,
            key = { it.path },
        ) { file ->
            GridFileCard(
                file = file,
                thumbnailUrl = thumbnailUrls[file.path],
                isTooShort = tooShortPaths.contains(file.path),
                isEncrypted = file.isDirectory && encryptedPaths.contains(file.path.trimEnd('/')),
                isMultiSelect = isMultiSelect,
                isSelected = file.path in selectedPaths,
                onOpenDirectory = onOpenDirectory,
                onPlayFile = onPlayFile,
                onOpenImageFile = onOpenImageFile,
                onShowFileActions = { onShowFileActions(file) },
                onToggleSelection = { onToggleSelection(file) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileCard(
    file: StorageFile,
    thumbnailUrl: String?,
    isTooShort: Boolean,
    isEncrypted: Boolean,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onShowFileActions: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val isVideo = MediaFileTypes.isVideoFile(file.name)
    val isAudio = MediaFileTypes.isAudioFile(file.name)
    val isImage = MediaFileTypes.isImageFile(file.name)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "gridCardScale",
    )

    val cardShape = RoundedCornerShape(16.dp)

    // 卡片仅含缩略图/文件夹图标（16:9 圆角），
    // 文件名在卡片外底部居中显示，长文字两行自动缩字。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(elevation = 1.dp, shape = cardShape, clip = false)
                .clip(cardShape)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = cardShape,
                        )
                    } else {
                        Modifier
                    }
                )
                .background(NiExtraColors.current.surfaceLevel3)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isMultiSelect) {
                            onToggleSelection()
                        } else {
                            when {
                                file.isDirectory -> onOpenDirectory(file)
                                isVideo || isAudio -> onPlayFile(file)
                                isImage -> onOpenImageFile(file)
                                else -> Unit
                            }
                        }
                    },
                    onLongClick = {
                        // 长按 = 打开单文件操作菜单；多选模式下长按不弹菜单（避免与多选冲突）
                        if (!isMultiSelect) onShowFileActions()
                    },
                ),
        ) {
            if (file.isDirectory) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    // 加密文件夹锁定角标
                    if (isEncrypted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.storage_file_encrypted),
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            } else {
                // 文件：统一 16:9 缩略图比例，音乐视频混存时卡片高度对齐。
                val thumbBg: Color = when {
                    isAudio -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else -> NiExtraColors.current.surfaceLevel3
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(thumbBg),
                ) {
                    val hasThumbnail = (isVideo || isAudio || isImage) && thumbnailUrl != null
                    if (hasThumbnail) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 类型角标：左上角，仅在媒体文件显示（多选模式让位于选中指示）
                    val typeLabel = when {
                        isVideo -> stringResource(R.string.storage_file_type_video)
                        isAudio -> stringResource(R.string.storage_file_type_audio)
                        isImage -> stringResource(R.string.storage_file_type_image)
                        else -> null
                    }
                    if (typeLabel != null && !isMultiSelect) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 6.dp, top = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isAudio) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    else Color.Black.copy(alpha = 0.65f),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAudio) MaterialTheme.colorScheme.onPrimary else Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // 中央播放按钮：仅在有缩略图的可播放文件显示，避免与占位图标重叠
                    if ((isVideo || isAudio) && hasThumbnail) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.storage_file_action_play),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // 大小角标：右下角
                    if ((isVideo || isAudio || isImage) && file.length > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = formatFileSize(file.length),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // 无缩略图占位：按文件类型给不同图标 + 软色
                    if (!hasThumbnail) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isAudio) Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                        ),
                                    ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isVideo && isTooShort -> Text(
                                    text = "<15s",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium,
                                )
                                isVideo -> Icon(
                                    imageVector = Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(52.dp),
                                )
                                isAudio -> Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(56.dp),
                                )
                                isImage -> Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(52.dp),
                                )
                                else -> Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                        }
                    }
                }
            }

                    // 多选模式：左上角选中指示（圆形勾选）
                    if (isMultiSelect) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Black.copy(alpha = 0.45f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Add,
                                contentDescription = stringResource(
                                    if (isSelected) R.string.storage_file_selected else R.string.storage_file_select,
                                ),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
        }
        // 文件名：卡片外底部居中，长文字两行自动缩字
        NiAutoSizeText(
            text = file.name,
            maxLines = 2,
            minFontSize = 11.sp,
            maxFontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .align(Alignment.CenterHorizontally),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionsSheet(
    file: StorageFile,
    isFavorited: Boolean,
    canDownload: Boolean,
    showFileManagement: Boolean,
    isEncrypted: Boolean = false,
    isRemoteStorage: Boolean = true,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleQuickAccess: () -> Unit,
    onShowInfo: () -> Unit,
    onRename: () -> Unit = {},
    onMove: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEncrypt: () -> Unit = {},
    onDecrypt: () -> Unit = {},
    onResetPassword: () -> Unit = {},
) {
    val overlayId = remember(file.path) { "file_actions_${file.path}" }
    val isPlayable = !file.isDirectory && (MediaFileTypes.isVideoFile(file.name) || MediaFileTypes.isAudioFile(file.name))

    // 投递到全局玻璃浮层槽位（NiGlassBottomSheet，backdrop 真模糊，透明度随面板设置）
    LaunchedEffect(file, isFavorited, canDownload, showFileManagement, isEncrypted, isRemoteStorage) {
        NiGlassOverlay.show(
            NiGlassOverlayRequest(
                id = overlayId,
                kind = NiGlassOverlayKind.BottomSheet,
                onDismiss = onDismiss,
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

            if (isPlayable) {
                ActionRow(
                    icon = Icons.Rounded.PlayArrow,
                    text = stringResource(R.string.storage_file_action_play),
                    onClick = onPlay,
                )
            }
            if (canDownload) {
                ActionRow(
                    icon = Icons.Rounded.Download,
                    text = stringResource(R.string.storage_file_action_download),
                    onClick = onDownload,
                )
            }
            ActionRow(
                icon = if (isFavorited) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                text = stringResource(
                    if (isFavorited) R.string.storage_file_action_remove_from_quick_access
                    else R.string.storage_file_action_add_to_quick_access,
                ),
                onClick = onToggleQuickAccess,
            )
            if (showFileManagement) {
                ActionRow(
                    icon = Icons.Rounded.Edit,
                    text = stringResource(R.string.storage_file_action_rename),
                    onClick = onRename,
                )
                ActionRow(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    text = stringResource(R.string.storage_file_action_move),
                    onClick = onMove,
                )
                ActionRow(
                    icon = Icons.Rounded.Delete,
                    text = stringResource(R.string.storage_file_action_delete),
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            // 文件夹访问加密（仅远程存储 SMB/WebDAV 支持；本地/SAF 不加密）
            if (file.isDirectory && isRemoteStorage) {
                if (isEncrypted) {
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = stringResource(R.string.storage_file_action_reset_password),
                        onClick = onResetPassword,
                    )
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = stringResource(R.string.storage_file_action_decrypt),
                        onClick = onDecrypt,
                    )
                } else {
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = stringResource(R.string.storage_file_action_encrypt_folder),
                        onClick = onEncrypt,
                    )
                }
            }
            ActionRow(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.storage_file_action_properties),
                onClick = onShowInfo,
            )
            }
        }
    )
    }
    DisposableEffect(overlayId) {
        onDispose { NiGlassOverlay.dismiss(overlayId) }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

private fun fileIcon(file: StorageFile, isVideo: Boolean, isAudio: Boolean): ImageVector = when {
    file.isDirectory -> Icons.Rounded.Folder
    isVideo -> Icons.Rounded.Movie
    isAudio -> Icons.Rounded.MusicNote
    MediaFileTypes.isImageFile(file.name) -> Icons.Rounded.Image
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "${bytes} B" else String.format("%.1f %s", size, units[unitIndex])
}

/**
 * 传输管理入口图标右上角的数量角标。
 *
 * 作为 [IconButton] 的**外层**叠加层（而非其内部子项），避免被 M3 IconButton 的
 * 圆形 Surface 裁剪；用自定义圆角胶囊完整显示数量。
 */
@Composable
private fun BoxScope.TransferCountBadge(count: Int) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onError,
        fontSize = 9.sp,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 1.dp, y = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun FileInfoDialog(file: StorageFile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    NiInfoDialog(
        title = stringResource(R.string.storage_file_properties_title),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            InfoRow(label = stringResource(R.string.storage_file_info_name), value = file.name)
            if (!file.isDirectory && file.length > 0) {
                InfoRow(label = stringResource(R.string.storage_file_info_size), value = formatFileSize(file.length))
            }
            if (file.lastModified > 0) {
                InfoRow(label = stringResource(R.string.storage_file_info_modified), value = formatDate(file.lastModified, context))
            }
            InfoRow(label = stringResource(R.string.storage_file_info_path), value = file.path)
            InfoRow(label = stringResource(R.string.storage_file_info_type), value = fileTypeLabel(file, context))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatDate(timestamp: Long, context: Context): String {
    if (timestamp <= 0) return context.getString(R.string.storage_file_unknown)
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

private fun fileTypeLabel(file: StorageFile, context: Context): String {
    if (file.isDirectory) return context.getString(R.string.storage_file_folder)
    val name = file.name
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.length - 1) return context.getString(R.string.storage_file_file)
    val ext = name.substring(dot + 1).uppercase()
    return when {
        MediaFileTypes.isVideoFile(name) -> context.getString(R.string.storage_file_type_video_ext, ext)
        MediaFileTypes.isAudioFile(name) -> context.getString(R.string.storage_file_type_audio_ext, ext)
        MediaFileTypes.isImageFile(name) -> context.getString(R.string.storage_file_type_image_ext, ext)
        else -> context.getString(R.string.storage_file_type_file_ext, ext)
    }
}

// ---- 文件管理对话框 ----

/** 重命名对话框。预填当前文件名（不含扩展名），用户确认后回调 [onConfirm]。 */
@Composable
fun RenameFileDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // 预填名称：目录取全名，文件取主名（不含扩展名）以便用户改扩展名外的部分
    val isDirectory = !fileName.contains('.')
    val initial = if (isDirectory) fileName else fileName.substringBeforeLast('.')
    var newName by remember { mutableStateOf(initial) }

    NiInfoDialog(
        title = stringResource(R.string.storage_file_rename_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onConfirm(newName.trim()) },
                enabled = newName.isNotBlank() && newName != initial,
            ) { Text(stringResource(R.string.confirm)) }
        },
    ) {
        NiTextField(
            value = newName,
            onValueChange = { newName = it },
            label = stringResource(R.string.storage_file_rename_new_name),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 移动目标选择对话框。列出候选目录供用户选择。 */
@Composable
fun MoveTargetDialog(
    fileName: String,
    targets: List<StorageFile>,
    onDismiss: () -> Unit,
    onSelect: (StorageFile) -> Unit,
) {
    val title = stringResource(R.string.storage_file_move_title, fileName)
    if (targets.isEmpty()) {
        NiInfoDialog(
            title = title,
            onDismiss = onDismiss,
        ) {
            Text(
                text = stringResource(R.string.storage_file_move_no_target),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        NiListItemDialog(
            title = title,
            onDismiss = onDismiss,
            items = targets.map { target ->
                NiDialogItem(
                    label = target.name,
                    icon = Icons.Rounded.Folder,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { onSelect(target) },
                )
            },
        )
    }
}

/** 删除确认对话框。 */
@Composable
fun DeleteConfirmDialog(
    fileName: String,
    isDirectory: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NiInfoDialog(
        title = stringResource(
            if (isDirectory) R.string.storage_file_delete_folder
            else R.string.storage_file_delete_file,
        ),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(stringResource(R.string.delete)) }
        },
    ) {
        Text(
            text = stringResource(
                if (isDirectory) R.string.storage_file_delete_confirm_dir
                else R.string.storage_file_delete_confirm_file,
                fileName,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 新建文件夹对话框。 */
@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    NiInfoDialog(
        title = stringResource(R.string.storage_file_new_folder),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.create)) }
        },
    ) {
        NiTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.storage_file_folder_name),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- 文件夹访问加密对话框 ----

/** 设置 / 取消文件夹访问密码对话框。 */
@Composable
fun FolderPasswordDialog(
    title: String,
    subtitle: String,
    confirmText: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    NiInfoDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onConfirm(password.trim()) },
                enabled = password.length >= 4,
            ) { Text(confirmText) }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NiTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.storage_file_password_label_min4),
                placeholder = stringResource(R.string.storage_file_password_placeholder),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 解锁加密文件夹对话框：密码输入 + 解锁，密码错误时内联提示。 */
@Composable
fun FolderUnlockDialog(
    folder: StorageFile,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onPasswordSubmit: (String) -> Unit,
    onPasswordChange: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    // 弹窗显示即自动聚焦密码输入框并拉起输入法
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    NiInfoDialog(
        title = stringResource(R.string.storage_file_unlock_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onPasswordSubmit(password.trim()) },
                enabled = password.isNotBlank(),
            ) { Text(stringResource(R.string.storage_file_unlock)) }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.storage_file_unlock_body, folder.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NiTextField(
                value = password,
                onValueChange = {
                    password = it
                    onPasswordChange()
                },
                label = stringResource(R.string.storage_file_password_label),
                placeholder = stringResource(R.string.storage_file_password_placeholder),
                isError = errorMessage != null,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 修改文件夹访问密码对话框：验证当前密码 + 输入新密码两次。 */
@Composable
@SuppressLint("LocalContextGetResourceValueCall")
fun ResetFolderPasswordDialog(
    folder: StorageFile,
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String) -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // 弹窗显示即自动聚焦"当前密码"输入框并拉起输入法
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    NiInfoDialog(
        title = stringResource(R.string.storage_file_change_password_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    if (newPassword.length < 4) {
                        error = context.getString(R.string.storage_file_password_min4_error)
                    } else if (newPassword != confirmPassword) {
                        error = context.getString(R.string.storage_file_password_mismatch)
                    } else {
                        onConfirm(oldPassword.trim(), newPassword.trim())
                    }
                },
                enabled = oldPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.storage_file_change_password_body, folder.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NiTextField(
                value = oldPassword,
                onValueChange = {
                    oldPassword = it
                    error = null
                },
                label = stringResource(R.string.storage_file_current_password),
                placeholder = stringResource(R.string.storage_file_current_password_placeholder),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            NiTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    error = null
                },
                label = stringResource(R.string.storage_file_new_password),
                placeholder = stringResource(R.string.storage_file_new_password_placeholder),
                isError = error != null,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            NiTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    error = null
                },
                label = stringResource(R.string.storage_file_confirm_password),
                placeholder = stringResource(R.string.storage_file_confirm_password_placeholder),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 多选模式底部操作栏：液态玻璃胶囊。固定三操作位（全选/下载/删除）+ 顶部"已选N项/关闭"行。 */
@Composable
private fun MultiSelectActionBar(
    backdrop: Backdrop,
    selectedCount: Int,
    allSelected: Boolean,
    downloadEnabled: Boolean,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInLightTheme = !NiExtraColors.current.isDark
    // 与悬浮底栏一致的液态玻璃容器：vibrancy + blur + lens + 高光边 + 柔和阴影，
    // 背景由 [backdrop] 捕获页面内容，实现真实背景模糊；不透明度由 LocalNiGlassOpacity 统一控制
    val containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalNiGlassOpacity.current)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(6f.dp.toPx(), 6f.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 1f) },
                shadow = {
                    Shadow.Default.copy(color = Color.Black.copy(if (isInLightTheme) 0.1f else 0.2f))
                },
                onDrawSurface = { drawRect(containerColor) },
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部行：已选数量 + 关闭按钮（选中态指示下沉到操作栏，并提供一键退出）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.storage_file_selected_count, selectedCount),
                    style = MaterialTheme.typography.labelMedium,
                    // 玻璃底上用高对比前景色，避免灰色次文字对比不足
                    color = glassOnSurfaceMuted(),
                )
                IconButton(onClick = onClose) {
                    NiStyleIcon(
                        icon = Icons.Rounded.Close,
                        style = NiAppIconStyle,
                        containerSize = 32.dp,
                        iconSize = 18.dp,
                        contentDescription = stringResource(R.string.storage_file_cancel_multi_select),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            // 固定三操作位：不按选中内容显隐，仅置灰，保证布局稳定不抖动
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionBarItem(
                    icon = if (allSelected) Icons.Rounded.Close else Icons.Rounded.SelectAll,
                    label = stringResource(
                        if (allSelected) R.string.storage_file_deselect_all
                        else R.string.storage_file_select_all,
                    ),
                    enabled = selectedCount > 0,
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f),
                )
                ActionBarItem(
                    icon = Icons.Rounded.Download,
                    label = stringResource(R.string.storage_file_action_download),
                    enabled = downloadEnabled,
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                )
                ActionBarItem(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.delete),
                    enabled = selectedCount > 0,
                    onClick = onDelete,
                    isDanger = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionBarItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDanger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = when {
        !enabled -> glassOnSurfaceMuted().copy(alpha = 0.5f)
        isDanger -> MaterialTheme.colorScheme.error
        else -> glassOnSurface()
    }
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatUploadSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1000 * 1000 -> String.format("%.1f MB/s", bytesPerSec / (1000.0 * 1000.0))
    bytesPerSec >= 1000 -> "${bytesPerSec / 1000} KB/s"
    else -> "$bytesPerSec B/s"
}
