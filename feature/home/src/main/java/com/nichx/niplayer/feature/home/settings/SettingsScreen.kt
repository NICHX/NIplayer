package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.navigation.Routes

@Composable
fun SettingsScreen(
    onNavigateToGlobal: (String) -> Unit = {},
) {
    Scaffold(
        topBar = { NiTopBar(title = "设置") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "app_info") {
                AppInfoCard()
            }

            SettingsGroup.entries.forEach { group ->
                item(key = "group_label_${group.name}") {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
                    )
                }
                item(key = "group_card_${group.name}") {
                    SettingsGroupCard(
                        entries = group.entries,
                        onItemClick = { entry ->
                            if (entry.route.isNotEmpty()) {
                                onNavigateToGlobal(entry.route)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppInfoCard() {
    val context = LocalContext.current
    val (versionName, versionCode) = remember {
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode.toString() else "?"
            Pair(pkg.versionName ?: "unknown", code)
        } catch (_: Exception) {
            Pair("unknown", "?")
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "NIplayer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = " · 编译 $versionCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    entries: List<SettingsEntry>,
    onItemClick: (SettingsEntry) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NiExtraColors.current.surfaceLevel2),
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                SettingsItemRow(entry) { onItemClick(entry) }
                if (index < entries.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItemRow(
    entry: SettingsEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(entry.iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

enum class SettingsGroup(
    val label: String,
    val entries: List<SettingsEntry>,
) {
    PLAYBACK(
        label = "播放",
        entries = listOf(SettingsEntry.PLAYER, SettingsEntry.PLAYBACK_STATS, SettingsEntry.LRCAPI, SettingsEntry.SCAN, SettingsEntry.CACHE),
    ),
    STORAGE(
        label = "存储",
        entries = listOf(SettingsEntry.DOWNLOAD, SettingsEntry.BACKUP),
    ),
    APPEARANCE(
        label = "外观",
        entries = listOf(SettingsEntry.THEME),
    ),
    ABOUT(
        label = "关于",
        entries = listOf(SettingsEntry.ABOUT),
    ),
}

enum class SettingsEntry(
    val route: String,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val iconBg: Color,
) {
    PLAYER(
        route = Routes.User.SETTING_PLAYER,
        title = "播放器",
        subtitle = "解码 / 字幕 / 缩略图",
        icon = Icons.Filled.PlayCircleOutline,
        iconBg = Color(0xFF2095F4),
    ),
    PLAYBACK_STATS(
        route = Routes.User.PLAYBACK_STATS,
        title = "播放统计",
        subtitle = "观看时长 / 播放次数 / 来源分布",
        icon = Icons.Filled.BarChart,
        iconBg = Color(0xFF26A69A),
    ),
    LRCAPI(
        route = Routes.User.LRCAPI,
        title = "音乐元数据",
        subtitle = "歌词 / 封面 API 配置",
        icon = Icons.Filled.Link,
        iconBg = Color(0xFFE91E63),
    ),
    SCAN(
        route = Routes.User.SCAN_MANAGER,
        title = "扫描目录",
        subtitle = "配置扫描路径和屏蔽目录",
        icon = Icons.Filled.Search,
        iconBg = Color(0xFF9C27B0),
    ),
    CACHE(
        route = Routes.User.CACHE_MANAGER,
        title = "缓存管理",
        subtitle = "播放缓存 / 字幕缓存",
        icon = Icons.Filled.Cached,
        iconBg = Color(0xFFFF6D00),
    ),
    DOWNLOAD(
        route = Routes.Stream.DOWNLOAD_MANAGER,
        title = "下载管理",
        subtitle = "下载队列 / 已完成 / 管理中",
        icon = Icons.Filled.ArrowDownward,
        iconBg = Color(0xFF43A047),
    ),
    BACKUP(
        route = Routes.User.BACKUP,
        title = "备份与恢复",
        subtitle = "导出 / 恢复用户数据",
        icon = Icons.Filled.Restore,
        iconBg = Color(0xFF546E7A),
    ),
    THEME(
        route = Routes.User.SWITCH_THEME,
        title = "主题",
        subtitle = "浅色 / 暗色 / 跟随系统",
        icon = Icons.Filled.Palette,
        iconBg = Color(0xFF00ACC1),
    ),
    ABOUT(
        route = Routes.User.ABOUT,
        title = "关于 NIplayer",
        subtitle = "版本 / 开源依赖",
        icon = Icons.Filled.Info,
        iconBg = Color(0xFF757575),
    ),
}
