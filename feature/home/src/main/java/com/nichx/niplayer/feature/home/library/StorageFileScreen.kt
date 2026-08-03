package com.nichx.niplayer.feature.home.library

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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.NiTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.MediaFileTypes.isImageFile
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.storage.StorageFile

// 主 FAB 底部偏移，与媒体库页"新增媒体库"按钮位置保持一致
// （该值已包含对应用底部导航栏 NiBottomBar 的避让）
private val FabBottomOffset = 104.dp

// 多选底部操作栏避让：NiBottomBar 高度 60dp + 8dp 间距。
// 系统导航栏高度因 HomeTabContent.consumeWindowInsets 被消费、navigationBarsPadding 无效，
// 需在 FileBrowserOverlay 中直接读 LocalWindowInsets 后叠加到此偏移上。
private val MultiSelectBarBottomOffset = 68.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserOverlay(
    storageId: Int,
    initialPath: String = "",
    onBack: () -> Unit,
    onPlayVideo: () -> Unit,
    onNavigateToImageViewer: () -> Unit = {},
    onNavigateToDownloadManager: () -> Unit = {},
) {
    val viewModel: StorageFileViewModel = hiltViewModel(key = "file_browser_$storageId")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val sortConfig by viewModel.sortConfig.collectAsStateWithLifecycle()
    val thumbnailUrls by viewModel.thumbnailUrls.collectAsStateWithLifecycle()
    val tooShortPaths by viewModel.tooShortPaths.collectAsStateWithLifecycle()
    val thumbnailProgress by viewModel.thumbnailProgress.collectAsStateWithLifecycle()
    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val encryptedPaths by viewModel.encryptedPaths.collectAsStateWithLifecycle()
    val isMultiSelect by viewModel.isMultiSelect.collectAsStateWithLifecycle()
    val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var fileMenu by remember { mutableStateOf<Pair<StorageFile, Boolean>?>(null) }
    var isGridView by remember { mutableStateOf(FileBrowserSettings.isGridView) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFileInfo by remember { mutableStateOf<StorageFile?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
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

    // 底部系统导航栏高度：HomeTabContent.consumeWindowInsets 已消费 insets，
    // navigationBarsPadding 在此处无效，须直接读 WindowInsets.getBottom 用于多选操作栏避让。
    val bottomNavInset = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    val showScrollToTop by remember {
        derivedStateOf {
            if (isGridView) gridState.firstVisibleItemIndex > 2
            else listState.firstVisibleItemIndex > 2
        }
    }

    LaunchedEffect(uiState.scrollTargetIndex) {
        if (uiState.scrollTargetIndex >= 0) {
            if (isGridView) {
                gridState.scrollToItem(uiState.scrollTargetIndex)
            } else {
                listState.scrollToItem(uiState.scrollTargetIndex)
            }
            viewModel.clearScrollTarget()
        }
    }

    val context = LocalContext.current
    var pendingDownloadFile by remember { mutableStateOf<StorageFile?>(null) }
    val downloadTargetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val file = pendingDownloadFile
        pendingDownloadFile = null
        if (treeUri == null || file == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) { }
        val dirName = DocumentFile.fromTreeUri(context, treeUri)?.name ?: "下载目录"
        viewModel.setDownloadDirAndDownload(file, treeUri.toString(), dirName)
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

    BackHandler(enabled = true) {
        when {
            isMultiSelect -> viewModel.exitMultiSelect()
            uiState.canGoUp -> viewModel.goUp()
            else -> onBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StorageFileEvent.NavigateToPlayer -> onPlayVideo()
                is StorageFileEvent.NavigateToImageViewer -> onNavigateToImageViewer()
                is StorageFileEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is StorageFileEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is StorageFileEvent.ShowQuickAccessMenu ->
                    fileMenu = event.file to event.isFavorited
            }
        }
    }

    Scaffold(
        topBar = {
            if (isMultiSelect) {
                NiTopBar(
                    title = "已选择 ${selectedPaths.size} 项",
                    navigationIcon = {
                        IconButton(onClick = viewModel::exitMultiSelect) {
                            NiStyleIcon(
                                icon = Icons.Rounded.Close,
                                style = NiAppIconStyle,
                                containerSize = 40.dp,
                                iconSize = 22.dp,
                                contentDescription = "取消多选",
                            )
                        }
                    },
                )
            } else {
            NiTopBar(
                title = uiState.storageName.ifEmpty { "文件浏览" },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.canGoUp) viewModel.goUp() else onBack()
                    }) {
                        NiStyleIcon(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            style = NiAppIconStyle,
                            containerSize = 40.dp,
                            iconSize = 22.dp,
                            contentDescription = if (uiState.canGoUp) "返回上级" else "返回",
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            NiStyleIcon(
                                icon = Icons.AutoMirrored.Rounded.Sort,
                                style = NiAppIconStyle,
                                containerSize = 40.dp,
                                iconSize = 22.dp,
                                contentDescription = "排序",
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = NiExtraColors.current.surfaceLevel3,
                            tonalElevation = 1.dp,
                            shadowElevation = 6.dp,
                        ) {
                            SortByMenuItem(
                                label = "名称",
                                icon = Icons.Rounded.SortByAlpha,
                                value = FileBrowserSettings.SortBy.NAME,
                                current = sortConfig.sortBy,
                            ) {
                                viewModel.setSortBy(FileBrowserSettings.SortBy.NAME)
                                showSortMenu = false
                            }
                            SortByMenuItem(
                                label = "修改时间",
                                icon = Icons.Rounded.Schedule,
                                value = FileBrowserSettings.SortBy.MODIFIED,
                                current = sortConfig.sortBy,
                            ) {
                                viewModel.setSortBy(FileBrowserSettings.SortBy.MODIFIED)
                                showSortMenu = false
                            }
                            SortByMenuItem(
                                label = "大小",
                                icon = Icons.Rounded.Storage,
                                value = FileBrowserSettings.SortBy.SIZE,
                                current = sortConfig.sortBy,
                            ) {
                                viewModel.setSortBy(FileBrowserSettings.SortBy.SIZE)
                                showSortMenu = false
                            }
                            SortByMenuItem(
                                label = "类型",
                                icon = Icons.Rounded.Category,
                                value = FileBrowserSettings.SortBy.TYPE,
                                current = sortConfig.sortBy,
                            ) {
                                viewModel.setSortBy(FileBrowserSettings.SortBy.TYPE)
                                showSortMenu = false
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (sortConfig.ascending) "切换为降序" else "切换为升序",
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (sortConfig.ascending) Icons.Rounded.ArrowUpward
                                        else Icons.Rounded.ArrowDownward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortAscending(!sortConfig.ascending)
                                    showSortMenu = false
                                },
                            )
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
                            contentDescription = if (isGridView) "列表视图" else "网格视图",
                        )
                    }
                    if (activeDownloadCount > 0) {
                        IconButton(onClick = onNavigateToDownloadManager) {
                            BadgedBox(
                                badge = {
                                    Badge { Text("$activeDownloadCount") }
                                },
                            ) {
                                NiStyleIcon(
                                        icon = Icons.Rounded.Download,
                                        style = NiAppIconStyle,
                                        containerSize = 40.dp,
                                        iconSize = 22.dp,
                                        contentDescription = "下载任务",
                                    )
                            }
                        }
                    }
                },
            )
            }
        },
        snackbarHost = { NiSnackbarHost(hostState = snackbarHostState, topAligned = true) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.currentPath.isNotEmpty()) {
                    BreadcrumbBar(
                        path = uiState.currentPath,
                        canGoUp = uiState.canGoUp,
                        onGoUp = { viewModel.goUp() },
                        onJumpToDepth = { depth -> viewModel.jumpToDepth(depth) },
                    )
                }

                if (thumbnailProgress >= 0) {
                    ThumbnailProgressBar(progress = thumbnailProgress)
                }

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
                                    onOpenDirectory = viewModel::openDirectory,
                                    onPlayFile = viewModel::playFile,
                                    onOpenImageFile = viewModel::openImageFile,
                                    onShowQuickAccess = viewModel::checkQuickAccess,
                                    onEnterMultiSelect = viewModel::enterMultiSelect,
                                    onToggleSelection = viewModel::toggleSelection,
                                    gridState = gridState,
                                )
                            } else {
                                FileList(
                                    files = uiState.files,
                                    thumbnailUrls = thumbnailUrls,
                                    tooShortPaths = tooShortPaths,
                                    encryptedPaths = encryptedPaths,
                                    isMultiSelect = isMultiSelect,
                                    selectedPaths = selectedPaths,
                                    onOpenDirectory = viewModel::openDirectory,
                                    onPlayFile = viewModel::playFile,
                                    onOpenImageFile = viewModel::openImageFile,
                                    onShowQuickAccess = viewModel::checkQuickAccess,
                                    onEnterMultiSelect = viewModel::enterMultiSelect,
                                    onToggleSelection = viewModel::toggleSelection,
                                    listState = listState,
                                )
                            }
                        }
                    }
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
                    contentDescription = "回到顶部",
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
                                    text = "上传文件",
                                    onClick = {
                                        fabExpanded = false
                                        uploadLauncher.launch(arrayOf("*/*"))
                                    },
                                    variant = NiFabVariant.OUTLINED,
                                )
                                NiExtendedFAB(
                                    icon = Icons.Rounded.CreateNewFolder,
                                    text = "新建文件夹",
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
                            contentDescription = if (fabExpanded) "收起菜单" else "新建",
                            variant = NiFabVariant.PRIMARY,
                        )
                    }
                }
            }

            // 多选模式底部操作栏：全选 / 添加到歌单 / 删除
            if (isMultiSelect) {
                MultiSelectActionBar(
                    selectedCount = selectedPaths.size,
                    allSelected = selectedPaths.size >= uiState.files.count { !it.isDirectory } && uiState.files.any { !it.isDirectory },
                    onSelectAll = viewModel::selectAllFiles,
                    onAddToPlaylist = { showPlaylistPicker = true },
                    onDelete = { showBatchDeleteConfirm = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = bottomNavInset + MultiSelectBarBottomOffset),
                )
            }
        }
    }

    if (showBatchDeleteConfirm) {
        NiConfirmDialog(
            title = "删除所选",
            text = "确定删除已选择的 ${selectedPaths.size} 项？该操作不可恢复。",
            onConfirm = {
                showBatchDeleteConfirm = false
                viewModel.deleteSelected()
            },
            onDismiss = { showBatchDeleteConfirm = false },
        )
    }

    if (showPlaylistPicker) {
        PlaylistPickerSheet(
            playlists = playlists,
            onPick = { playlistId ->
                showPlaylistPicker = false
                viewModel.addSelectedToPlaylist(playlistId)
            },
            onCreateNew = {
                showPlaylistPicker = false
                showNewPlaylistDialog = true
            },
            onDismiss = { showPlaylistPicker = false },
        )
    }

    if (showNewPlaylistDialog) {
        CreatePlaylistNameDialog(
            onConfirm = { name ->
                showNewPlaylistDialog = false
                viewModel.createPlaylistAndAdd(name)
            },
            onDismiss = { showNewPlaylistDialog = false },
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
                    pendingDownloadFile = file
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
            title = "加密文件夹",
            subtitle = "为「${folder.name}」设置访问密码。设置后进入该文件夹及其子目录需验证，其中文件的播放不再计入播放历史。",
            confirmText = "加密",
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
            title = "取消加密",
            subtitle = "取消「${folder.name}」的访问保护，请输入当前密码确认。",
            confirmText = "取消加密",
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
    onClick: () -> Unit,
) {
    val selected = current == value
    DropdownMenuItem(
        text = {
            Text(
                text = label,
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
                text = "正在生成缩略图…",
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
    canGoUp: Boolean,
    onGoUp: () -> Unit,
    onJumpToDepth: (Int) -> Unit,
) {
    val segments = path.split("/").filter { it.isNotEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                text = "正在连接…",
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
            text = "加载失败",
            hint = message,
            actionText = "重试",
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
            text = "空目录",
            hint = "此目录下没有文件",
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
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onShowQuickAccess: (StorageFile) -> Unit,
    onEnterMultiSelect: (StorageFile) -> Unit,
    onToggleSelection: (StorageFile) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = FabBottomOffset + 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                onShowQuickAccess = { onShowQuickAccess(file) },
                onEnterMultiSelect = { onEnterMultiSelect(file) },
                onToggleSelection = { onToggleSelection(file) },
            )
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
    onShowQuickAccess: () -> Unit,
    onEnterMultiSelect: () -> Unit,
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
                    if (!isMultiSelect) onEnterMultiSelect()
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiSelect) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isSelected) "取消选择" else "选择",
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
                                contentDescription = "播放",
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
                            contentDescription = "已加密",
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
                    text = "文件夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (!isMultiSelect) {
            IconButton(
                onClick = onShowQuickAccess,
                modifier = Modifier.size(32.dp),
            ) {
                NiStyleIcon(
                    icon = Icons.Rounded.MoreVert,
                    style = NiAppIconStyle,
                    containerSize = 32.dp,
                    iconSize = 18.dp,
                    contentDescription = "更多",
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
    onShowQuickAccess: (StorageFile) -> Unit,
    onEnterMultiSelect: (StorageFile) -> Unit,
    onToggleSelection: (StorageFile) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = FabBottomOffset + 80.dp),
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
                onShowQuickAccess = { onShowQuickAccess(file) },
                onEnterMultiSelect = { onEnterMultiSelect(file) },
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
    onShowQuickAccess: () -> Unit,
    onEnterMultiSelect: () -> Unit,
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
                        if (!isMultiSelect) onEnterMultiSelect()
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
                                contentDescription = "已加密",
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
                        isVideo -> "视频"
                        isAudio -> "音乐"
                        isImage -> "图片"
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
                                contentDescription = "播放",
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
                                contentDescription = if (isSelected) "已选择" else "选择",
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPlayable = !file.isDirectory && (MediaFileTypes.isVideoFile(file.name) || MediaFileTypes.isAudioFile(file.name))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 2.dp,
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
                    text = "播放",
                    onClick = onPlay,
                )
            }
            if (canDownload) {
                ActionRow(
                    icon = Icons.Rounded.Download,
                    text = "下载",
                    onClick = onDownload,
                )
            }
            ActionRow(
                icon = if (isFavorited) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                text = if (isFavorited) "从快速访问移除" else "添加到快速访问",
                onClick = onToggleQuickAccess,
            )
            if (showFileManagement) {
                ActionRow(
                    icon = Icons.Rounded.Edit,
                    text = "重命名",
                    onClick = onRename,
                )
                ActionRow(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    text = "移动到",
                    onClick = onMove,
                )
                ActionRow(
                    icon = Icons.Rounded.Delete,
                    text = "删除",
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            // 文件夹访问加密（仅远程存储 SMB/WebDAV 支持；本地/SAF 不加密）
            if (file.isDirectory && isRemoteStorage) {
                if (isEncrypted) {
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = "修改密码",
                        onClick = onResetPassword,
                    )
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = "取消加密",
                        onClick = onDecrypt,
                    )
                } else {
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = "加密此文件夹",
                        onClick = onEncrypt,
                    )
                }
            }
            ActionRow(
                icon = Icons.Rounded.Info,
                text = "属性",
                onClick = onShowInfo,
            )
        }
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

@Composable
private fun FileInfoDialog(file: StorageFile, onDismiss: () -> Unit) {
    NiInfoDialog(
        title = "文件属性",
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            InfoRow(label = "名称", value = file.name)
            if (!file.isDirectory && file.length > 0) {
                InfoRow(label = "大小", value = formatFileSize(file.length))
            }
            if (file.lastModified > 0) {
                InfoRow(label = "修改时间", value = formatDate(file.lastModified))
            }
            InfoRow(label = "路径", value = file.path)
            InfoRow(label = "类型", value = fileTypeLabel(file))
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

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "未知"
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

private fun fileTypeLabel(file: StorageFile): String {
    if (file.isDirectory) return "文件夹"
    val name = file.name
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.length - 1) return "文件"
    val ext = name.substring(dot + 1).uppercase()
    return when {
        MediaFileTypes.isVideoFile(name) -> "视频 (.$ext)"
        MediaFileTypes.isAudioFile(name) -> "音频 (.$ext)"
        MediaFileTypes.isImageFile(name) -> "图片 (.$ext)"
        else -> "文件 (.$ext)"
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
        title = "重命名",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = { onConfirm(newName.trim()) },
                enabled = newName.isNotBlank() && newName != initial,
            ) { Text("确定") }
        },
    ) {
        NiTextField(
            value = newName,
            onValueChange = { newName = it },
            label = "新名称",
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
    val title = "移动「$fileName」到"
    if (targets.isEmpty()) {
        NiInfoDialog(
            title = title,
            onDismiss = onDismiss,
        ) {
            Text(
                text = "没有可用的目标目录",
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
        title = "删除${if (isDirectory) "文件夹" else "文件"}",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("删除") }
        },
    ) {
        Text(
            text = "确定要删除「$fileName」吗？" +
                if (isDirectory) "\n注意：仅当文件夹为空时才能删除。" else "",
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
        title = "新建文件夹",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
    ) {
        NiTextField(
            value = name,
            onValueChange = { name = it },
            label = "文件夹名称",
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
            TextButton(onClick = onDismiss) { Text("取消") }
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
                label = "访问密码（至少 4 位）",
                placeholder = "请输入密码",
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
        title = "输入密码解锁",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = { onPasswordSubmit(password.trim()) },
                enabled = password.isNotBlank(),
            ) { Text("解锁") }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "「${folder.name}」已加密，请输入访问密码：",
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
                label = "访问密码",
                placeholder = "请输入密码",
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
fun ResetFolderPasswordDialog(
    folder: StorageFile,
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String) -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // 弹窗显示即自动聚焦"当前密码"输入框并拉起输入法
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    NiInfoDialog(
        title = "修改密码",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = {
                    if (newPassword.length < 4) {
                        error = "新密码至少 4 位"
                    } else if (newPassword != confirmPassword) {
                        error = "两次输入的新密码不一致"
                    } else {
                        onConfirm(oldPassword.trim(), newPassword.trim())
                    }
                },
                enabled = oldPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
            ) { Text("保存") }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "修改「${folder.name}」的访问密码，需验证当前密码。",
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
                label = "当前密码",
                placeholder = "请输入当前密码",
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
                label = "新密码",
                placeholder = "至少 4 位",
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
                label = "确认新密码",
                placeholder = "再次输入新密码",
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

/** 多选模式底部操作栏：全选 / 添加到歌单 / 删除。 */
@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionBarItem(
                icon = if (allSelected) Icons.Rounded.Close else Icons.Rounded.SelectAll,
                label = if (allSelected) "取消全选" else "全选",
                enabled = selectedCount > 0,
                onClick = onSelectAll,
                modifier = Modifier.weight(1f),
            )
            ActionBarItem(
                icon = Icons.Rounded.PlaylistAdd,
                label = "添加到歌单",
                enabled = selectedCount > 0,
                onClick = onAddToPlaylist,
                modifier = Modifier.weight(1f),
            )
            ActionBarItem(
                icon = Icons.Rounded.Delete,
                label = "删除",
                enabled = selectedCount > 0,
                onClick = onDelete,
                isDanger = true,
                modifier = Modifier.weight(1f),
            )
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
        !enabled -> MaterialTheme.colorScheme.outline
        isDanger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
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

/** 选歌单弹窗（多选添加入口②）：列出全部歌单 + 新建歌单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    playlists: List<PlaylistWithCount>,
    onPick: (Int) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "添加到歌单",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    text = "暂无歌单，请先新建",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(playlists, key = { it.playlist.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(playlist.playlist.id) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = playlist.playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${playlist.itemCount} 个条目",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateNew)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "新建歌单",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 新建歌单名称输入对话框（多选添加入口内）。 */
@Composable
private fun CreatePlaylistNameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    NiInfoDialog(
        title = "新建歌单",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
    ) {
        NiTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = "歌单名称",
            placeholder = "例如：我的最爱",
        )
    }
}
