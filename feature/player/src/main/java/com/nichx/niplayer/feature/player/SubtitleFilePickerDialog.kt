package com.nichx.niplayer.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 应用内字幕文件选择器。
 *
 * 取代系统 SAF 选择器：SAF 只能看到被授权的外部存储文档，**无法访问 SMB / WebDAV**，
 * 而本应用的字幕很多就放在网络存储里。本弹层直接复用应用自己的 [Storage] 抽象与
 * `java.io.File`，两条路径都能到。
 *
 * 交互（沿用文件管理器惯例）：
 * - 根页面：本地文件入口 + 各网络存储源
 * - 浏览页：首行「上一级」+ 子目录 + 字幕文件（其他文件不展示）
 *
 * 只有目录和字幕文件会出现在列表里 —— 视频文件等噪声被过滤掉，
 * 免得用户在一堆 `.mkv` 里找 `.srt`。
 *
 * @param onPicked 选中字幕文件回调：`(storageId, dirPath, fileName)`。
 *   本地为 `storageId = null` + 绝对目录；网络为库 ID + 库内相对目录。
 * @param onPickFromSystem 走系统 SAF 选择器。保留为显式入口而非默认路径：
 *   SAF 访问不到网络存储，但对少数只存在于系统文档树/云盘的字幕仍有用，
 *   直接删掉会是一次能力回退。为 null 时不展示该入口。
 * @param initialLocalPath 本地浏览起始目录（通常是当前视频所在目录），null 用外部存储根
 */
@Composable
internal fun SubtitleFilePickerDialog(
    onPicked: (storageId: Int?, dirPath: String, fileName: String) -> Unit,
    onDismiss: () -> Unit,
    onPickFromSystem: (() -> Unit)? = null,
    initialLocalPath: String? = null,
    viewModel: SubtitlePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val secondary = PlayerDialogColors.textSecondary

    // 每次打开都从根页面开始：ViewModel 挂在导航条目上，实例跨弹层存活
    LaunchedEffect(Unit) { viewModel.resetToRoot() }

    // 起始目录只在进入时写入一次，不参与重组（localStartPath 是普通字段，不触发重组）
    LaunchedEffect(initialLocalPath) {
        if (!initialLocalPath.isNullOrBlank()) viewModel.localStartPath = initialLocalPath
    }

    val location = state.location

    // 刻意不用 PlayerDialog：它按内容自适应高度（heightIn(max)），
    // 而文件列表的行数会随目录变化 —— 进目录/返回上级时窗口高度会跟着跳。
    // 选择器直接指定固定尺寸，内部列表自己滚动。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 只按屏幕收缩，保证小屏/横屏下不超出可视区；其余情况恒为 PICKER_*_DP
        val screen = LocalConfiguration.current
        val dialogWidth = minOf(PICKER_WIDTH_DP, screen.screenWidthDp - DIALOG_SCREEN_MARGIN_DP)
            .coerceAtLeast(MIN_DIALOG_WIDTH_DP)
        val dialogHeight = minOf(
            PICKER_HEIGHT_DP,
            (screen.screenHeightDp * DIALOG_SCREEN_HEIGHT_RATIO).toInt(),
        ).coerceAtLeast(MIN_DIALOG_HEIGHT_DP)

        PlayerDialogSurface(
            modifier = Modifier
                .width(dialogWidth.dp)
                .height(dialogHeight.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerDialogTitle(text = stringResource(R.string.player_subtitle_pick_file))
                PlayerDialogDivider()

                // 当前路径摘要：根页面不显示，浏览中显示「本地文件 / a / b」或「库名 / a / b」
                if (location != null) {
                    Text(
                        text = locationLabel(location, state.path),
                        color = secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                    )
                }

                // 列表独占剩余高度并自行滚动：标题与路径行固定不动
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        state.loading -> LoadingRow()
                        state.error != null -> HintRow(text = pickErrorText(state.error!!))
                        location == null -> RootList(
                            libraries = state.libraries,
                            onOpenLocal = viewModel::openLocal,
                            onOpenLibrary = viewModel::openLibrary,
                            onPickFromSystem = onPickFromSystem,
                        )
                        else -> BrowseList(
                            entries = state.entries,
                            onNavigateUp = viewModel::navigateUp,
                            onOpenEntry = viewModel::openEntry,
                            onPickFile = { name ->
                                onPicked(storageIdOf(location), state.path, name)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 选择器固定尺寸（dp）。内容多少不影响窗口大小。 */
private const val PICKER_WIDTH_DP = 380
private const val PICKER_HEIGHT_DP = 520

/** 小屏兜底：左右各留 [DIALOG_SCREEN_MARGIN_DP] / 2，高度最多占屏高 [DIALOG_SCREEN_HEIGHT_RATIO]。 */
private const val DIALOG_SCREEN_MARGIN_DP = 32
private const val DIALOG_SCREEN_HEIGHT_RATIO = 0.72f
private const val MIN_DIALOG_WIDTH_DP = 260
private const val MIN_DIALOG_HEIGHT_DP = 300

/** 根页面：本地入口 + 存储源列表 + 系统选择器兜底入口。 */
@Composable
private fun RootList(
    libraries: List<SubtitlePickerViewModel.LibraryItem>,
    onOpenLocal: () -> Unit,
    onOpenLibrary: (Int) -> Unit,
    onPickFromSystem: (() -> Unit)?,
) {
    PickerRow(
        icon = Icons.Rounded.PhoneAndroid,
        label = stringResource(R.string.player_subtitle_pick_local),
        onClick = onOpenLocal,
    )
    if (libraries.isNotEmpty()) {
        SectionLabel(text = stringResource(R.string.player_subtitle_pick_storage))
        libraries.forEach { library ->
            PickerRow(
                icon = Icons.Rounded.Storage,
                label = library.name,
                onClick = { onOpenLibrary(library.id) },
            )
        }
    }
    if (onPickFromSystem != null) {
        Spacer(Modifier.height(8.dp))
        PickerRow(
            icon = Icons.Rounded.FolderOpen,
            label = stringResource(R.string.player_subtitle_pick_system),
            onClick = onPickFromSystem,
        )
    }
}

/** 浏览页：上一级 + 子目录 + 字幕文件。 */
@Composable
private fun BrowseList(
    entries: List<SubtitlePickerViewModel.Entry>,
    onNavigateUp: () -> Unit,
    onOpenEntry: (SubtitlePickerViewModel.Entry) -> Unit,
    onPickFile: (String) -> Unit,
) {
    PickerRow(
        icon = Icons.Rounded.ArrowUpward,
        label = stringResource(R.string.player_subtitle_pick_up),
        onClick = onNavigateUp,
    )
    if (entries.isEmpty()) {
        HintRow(text = stringResource(R.string.player_subtitle_pick_empty))
        return
    }
    entries.forEach { entry ->
        PickerRow(
            icon = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Subtitles,
            label = entry.name,
            trailing = if (entry.isDirectory) null else formatFileSize(entry.length),
            onClick = {
                if (entry.isDirectory) onOpenEntry(entry) else onPickFile(entry.name)
            },
        )
    }
}

/** 选择器内的一行：图标 + 名称 + 可选右侧说明。 */
@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val onSurface = PlayerDialogColors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = onSurface,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = onSurface.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** 分组标题（如「存储源」）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = PlayerDialogColors.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** 加载中占位行。 */
@Composable
private fun LoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 20.dp),
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.player_subtitle_pick_loading),
            color = PlayerDialogColors.textSecondary,
            fontSize = 13.sp,
        )
    }
}

/** 空目录 / 失败提示行。 */
@Composable
private fun HintRow(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = text,
            color = PlayerDialogColors.textSecondary,
            fontSize = 13.sp,
        )
    }
}

/** 当前路径摘要文案。 */
@Composable
private fun locationLabel(location: SubtitlePickerViewModel.Location, path: String): String {
    val prefix = when (location) {
        SubtitlePickerViewModel.Location.Local ->
            stringResource(R.string.player_subtitle_pick_local)
        is SubtitlePickerViewModel.Location.Remote -> location.libraryName
    }
    return if (path.isEmpty()) prefix else "$prefix / ${path.replace('/', ' ')}"
}

/** 浏览位置的存储库 ID；本地为 null。 */
private fun storageIdOf(location: SubtitlePickerViewModel.Location): Int? = when (location) {
    SubtitlePickerViewModel.Location.Local -> null
    is SubtitlePickerViewModel.Location.Remote -> location.libraryId
}

/** 错误码 → 文案。未知错误统一按「读取目录失败」处理。 */
@Composable
private fun pickErrorText(code: String): String = when (code) {
    SubtitlePickerViewModel.ERROR_LIBRARY_MISSING ->
        stringResource(R.string.player_subtitle_pick_error_missing)
    SubtitlePickerViewModel.ERROR_NOT_BROWSABLE ->
        stringResource(R.string.player_subtitle_pick_error_unsupported)
    else -> stringResource(R.string.player_subtitle_pick_error)
}

/** 文件大小展示：< 1KB 显示字节，< 1MB 显示 KB，否则 MB。 */
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}
