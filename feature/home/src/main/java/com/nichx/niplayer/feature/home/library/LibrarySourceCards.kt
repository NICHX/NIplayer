package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.components.NiGlassHairWidth
import com.nichx.niplayer.designsystem.components.NiGlassOverlay
import com.nichx.niplayer.designsystem.components.NiGlassOverlayKind
import com.nichx.niplayer.designsystem.components.NiGlassOverlayRequest
import com.nichx.niplayer.designsystem.components.glassOnSurface
import com.nichx.niplayer.designsystem.components.glassOnSurfaceMuted
import com.nichx.niplayer.designsystem.components.niFrostSurfaceColor
import com.nichx.niplayer.designsystem.components.niGlassBorderColor
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiSpacings


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibrarySourceCard(
    library: MediaLibraryEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val extraColors = NiExtraColors.current
    val context = LocalContext.current
    val typeInfo = remember(library.mediaType, extraColors) {
        storageTypeInfo(library.mediaType, extraColors, context)
    }
    val canModify by remember(library.mediaType) {
        derivedStateOf { library.mediaType != MediaType.LOCAL_STORAGE }
    }
    var showMenu by remember { mutableStateOf(false) }

    val brandColor = typeInfo.color
    val colorAlpha10 = remember(brandColor) { brandColor.copy(alpha = 0.1f) }
    val outlineAlpha40 = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                // 语义合并：图标/名称/描述合并为单一节点，降低语义树节点数
                .semantics(mergeDescendants = true) {}
                .clip(cardShape)
                .background(extraColors.surfaceLevel2)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (canModify) { { showMenu = true } } else null,
                )
                .padding(start = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(brandColor),
            )

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colorAlpha10),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = typeInfo.icon,
                    contentDescription = null,
                    tint = brandColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = library.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeInfo.shortName,
                        style = MaterialTheme.typography.labelSmall,
                        color = brandColor,
                    )
                    val describe = library.describe
                    if (describe != null) {
                        Text(
                            text = " · $describe",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = outlineAlpha40,
                modifier = Modifier.padding(end = 12.dp).size(18.dp),
            )
        }

        if (showMenu && canModify) {
            LibrarySourceDropdownMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                onEdit = { showMenu = false; onEdit() },
                onDelete = { showMenu = false; onDelete() },
            )
        }
    }
}

/** 存储源卡片的长按编辑/删除菜单（列表卡片与网格卡片共用）。 */
@Composable
internal fun LibrarySourceDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            shape = menuShape,
            containerColor = niFrostSurfaceColor(),
            border = androidx.compose.foundation.BorderStroke(NiGlassHairWidth, niGlassBorderColor()),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.edit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = onEdit,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = onDelete,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/** 网格视图：双列存储源卡片，含类型色图标 + 类型徽章 + 名称 + 描述。 */
@Composable
internal fun LibrarySourceGrid(
    libraries: List<MediaLibraryEntity>,
    count: Int,
    onOpen: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (MediaLibraryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = NiSpacings.screenOuter,
            end = NiSpacings.screenOuter,
            top = 0.dp,
            bottom = 88.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "section_count", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.library_storage_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        itemsIndexed(
            items = libraries,
            key = { _, item -> "library_${item.id}" },
        ) { _, library ->
            LibrarySourceGridCard(
                library = library,
                onClick = { onOpen(library.id) },
                onEdit = { onEdit(library.id) },
                onDelete = { onDelete(library) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibrarySourceGridCard(
    library: MediaLibraryEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val extraColors = NiExtraColors.current
    val context = LocalContext.current
    val typeInfo = remember(library.mediaType, extraColors) {
        storageTypeInfo(library.mediaType, extraColors, context)
    }
    val canModify by remember(library.mediaType) {
        derivedStateOf { library.mediaType != MediaType.LOCAL_STORAGE }
    }
    var showMenu by remember { mutableStateOf(false) }

    val brandColor = typeInfo.color
    val colorAlpha10 = remember(brandColor) { brandColor.copy(alpha = 0.1f) }
    // 网格卡片边界：surfaceLevel2 与页面背景接近，需 1dp 描边使其边界清晰
    val cardBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 语义合并：图标/徽章/名称/描述合并为单一节点，降低语义树节点数
                .semantics(mergeDescendants = true) {}
                .clip(cardShape)
                .background(extraColors.surfaceLevel2)
                .border(NiGlassHairWidth, cardBorder, cardShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (canModify) { { showMenu = true } } else null,
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorAlpha10),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = typeInfo.icon,
                        contentDescription = null,
                        tint = brandColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = typeInfo.shortName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = brandColor,
                    modifier = Modifier
                        .background(colorAlpha10, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = library.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            val describeLine = library.describe
                ?: if (library.url.isNotBlank()) library.url else typeInfo.shortName
            // minLines=2 保留固定描述高度，保证所有网格卡片等高、边界对齐
            Text(
                text = describeLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showMenu && canModify) {
            LibrarySourceDropdownMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                onEdit = { showMenu = false; onEdit() },
                onDelete = { showMenu = false; onDelete() },
            )
        }
    }
}

@Composable
internal fun StorageTypePickerSheet(
    onDismiss: () -> Unit,
    onPick: (MediaType) -> Unit,
) {
    val sheetId = "library_storage_type_picker"
    val types = listOf(
        Triple(
            MediaType.SMB_SERVER,
            stringResource(R.string.library_type_smb_label),
            stringResource(R.string.library_type_smb_desc),
        ),
        Triple(
            MediaType.WEBDAV_SERVER,
            stringResource(R.string.library_type_webdav_label),
            stringResource(R.string.library_type_webdav_desc),
        ),
        Triple(
            MediaType.EXTERNAL_STORAGE,
            stringResource(R.string.library_type_external_label),
            stringResource(R.string.library_type_external_desc),
        ),
    )
    val title = stringResource(R.string.library_select_storage_type)

    DisposableEffect(sheetId) {
        onDispose { NiGlassOverlay.dismiss(sheetId) }
    }
    LaunchedEffect(sheetId) {
        NiGlassOverlay.show(
            NiGlassOverlayRequest(
                id = sheetId,
                kind = NiGlassOverlayKind.BottomSheet,
                title = title,
                onDismiss = onDismiss,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    types.forEach { (type, label, desc) ->
                        val typeInfo = storageTypeInfo(type, NiExtraColors.current, LocalContext.current)
                        val iconBg = typeInfo.color.copy(alpha = 0.1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { onPick(type) },
                                )
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(iconBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = typeInfo.icon,
                                    contentDescription = null,
                                    tint = typeInfo.color,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = glassOnSurface(),
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = glassOnSurfaceMuted(),
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

internal data class StorageTypeInfo(
    val icon: ImageVector,
    val shortName: String,
    val color: Color,
)

internal fun storageTypeInfo(mediaType: MediaType, extraColors: NiExtraColors, context: Context): StorageTypeInfo {
    return when (mediaType) {
        MediaType.LOCAL_STORAGE -> StorageTypeInfo(
            Icons.Filled.PhoneAndroid, context.getString(R.string.storage_type_local), extraColors.storageLocalColor,
        )
        MediaType.SMB_SERVER -> StorageTypeInfo(
            Icons.Filled.Computer, "SMB", extraColors.storageSmbColor,
        )
        MediaType.WEBDAV_SERVER -> StorageTypeInfo(
            Icons.Filled.CloudQueue, "WebDAV", extraColors.storageWebdavColor,
        )
        MediaType.EXTERNAL_STORAGE -> StorageTypeInfo(
            Icons.Filled.SdCard, "SAF", extraColors.storageExternalColor,
        )
        MediaType.OTHER_STORAGE -> StorageTypeInfo(
            Icons.Filled.History, context.getString(R.string.storage_type_history), extraColors.storageHistoryColor,
        )
        MediaType.QUICK_ACCESS -> StorageTypeInfo(
            Icons.Filled.SdCard, context.getString(R.string.storage_type_other), extraColors.storageHistoryColor,
        )
    }
}

