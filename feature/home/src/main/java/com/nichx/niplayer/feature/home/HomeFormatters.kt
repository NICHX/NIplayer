package com.nichx.niplayer.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nichx.niplayer.database.enums.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页 / 历史 / 搜索共用的展示格式化函数。
 *
 * 合并前 [mediaTypeLabel] 在 home / history / search **三个包各有一份逐字节相同的副本**，
 * [formatPlayTime] 在 history / search 各一份 —— 由「重复函数体扫描」发现，
 * 三者同在 `:feature:home` 模块内，故合并到根包共享。实现逐字未改。
 */

@Composable
internal fun mediaTypeLabel(type: MediaType): String = when (type) {
    MediaType.LOCAL_STORAGE -> stringResource(R.string.storage_type_local)
    MediaType.EXTERNAL_STORAGE -> stringResource(R.string.storage_type_device)
    MediaType.SMB_SERVER -> "SMB"
    MediaType.WEBDAV_SERVER -> "WebDAV"
    MediaType.QUICK_ACCESS -> stringResource(R.string.storage_type_quick)
    MediaType.OTHER_STORAGE -> stringResource(R.string.storage_type_other)
}

internal fun formatPlayTime(date: Date): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(date)
}
