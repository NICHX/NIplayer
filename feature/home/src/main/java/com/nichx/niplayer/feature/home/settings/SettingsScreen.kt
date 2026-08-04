package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.home.update.UpdateDialogHost
import com.nichx.niplayer.feature.home.update.UpdateViewModel
import com.nichx.niplayer.navigation.Routes

@Composable
fun SettingsScreen(
    onNavigateToGlobal: (String) -> Unit = {},
) {
    val updateViewModel: UpdateViewModel = hiltViewModel()

    Scaffold(
        topBar = { NiTopBar(title = stringResource(R.string.home_tab_settings)) },
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
                        text = stringResource(group.labelRes),
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
                            entry.route?.let { onNavigateToGlobal(it) }
                                ?: run {
                                    if (entry == SettingsEntry.UPDATE) {
                                        updateViewModel.checkUpdate(auto = false)
                                    }
                                }
                        },
                    )
                }
            }
        }
    }

    // 检查更新对话框（手动触发 + 已下载待安装恢复）
    UpdateDialogHost(viewModel = updateViewModel)
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
                        text = stringResource(R.string.settings_build_code, versionCode),
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
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitleRes?.let { subRes ->
                Text(
                    text = stringResource(subRes),
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
    @StringRes val labelRes: Int,
    val entries: List<SettingsEntry>,
) {
    PLAYBACK(
        labelRes = R.string.settings_group_playback,
        entries = listOf(SettingsEntry.PLAYER, SettingsEntry.PLAYBACK_STATS, SettingsEntry.LRCAPI, SettingsEntry.SCAN, SettingsEntry.CACHE),
    ),
    STORAGE(
        labelRes = R.string.settings_group_storage,
        entries = listOf(SettingsEntry.DOWNLOAD, SettingsEntry.BACKUP),
    ),
    APPEARANCE(
        labelRes = R.string.settings_group_appearance,
        entries = listOf(SettingsEntry.THEME, SettingsEntry.LANGUAGE),
    ),
    ABOUT(
        labelRes = R.string.settings_group_about,
        entries = listOf(SettingsEntry.UPDATE, SettingsEntry.ABOUT),
    ),
}

enum class SettingsEntry(
    val route: String?,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    val icon: ImageVector,
    val iconBg: Color,
) {
    PLAYER(
        route = Routes.User.SETTING_PLAYER,
        titleRes = R.string.settings_entry_player,
        subtitleRes = R.string.settings_entry_player_sub,
        icon = Icons.Filled.PlayCircleOutline,
        iconBg = Color(0xFF2095F4),
    ),
    PLAYBACK_STATS(
        route = Routes.User.PLAYBACK_STATS,
        titleRes = R.string.settings_entry_playback_stats,
        subtitleRes = R.string.settings_entry_playback_stats_sub,
        icon = Icons.Filled.BarChart,
        iconBg = Color(0xFF26A69A),
    ),
    LRCAPI(
        route = Routes.User.LRCAPI,
        titleRes = R.string.settings_entry_lrcapi,
        subtitleRes = R.string.settings_entry_lrcapi_sub,
        icon = Icons.Filled.Link,
        iconBg = Color(0xFFE91E63),
    ),
    SCAN(
        route = Routes.User.SCAN_MANAGER,
        titleRes = R.string.settings_entry_scan,
        subtitleRes = R.string.settings_entry_scan_sub,
        icon = Icons.Filled.Search,
        iconBg = Color(0xFF9C27B0),
    ),
    CACHE(
        route = Routes.User.CACHE_MANAGER,
        titleRes = R.string.settings_entry_cache,
        subtitleRes = R.string.settings_entry_cache_sub,
        icon = Icons.Filled.Cached,
        iconBg = Color(0xFFFF6D00),
    ),
    DOWNLOAD(
        route = Routes.Stream.DOWNLOAD_MANAGER,
        titleRes = R.string.settings_entry_download,
        subtitleRes = R.string.settings_entry_download_sub,
        icon = Icons.Filled.ArrowDownward,
        iconBg = Color(0xFF43A047),
    ),
    BACKUP(
        route = Routes.User.BACKUP,
        titleRes = R.string.settings_entry_backup,
        subtitleRes = R.string.settings_entry_backup_sub,
        icon = Icons.Filled.Restore,
        iconBg = Color(0xFF546E7A),
    ),
    THEME(
        route = Routes.User.SWITCH_THEME,
        titleRes = R.string.settings_entry_theme,
        subtitleRes = R.string.settings_entry_theme_sub,
        icon = Icons.Filled.Palette,
        iconBg = Color(0xFF00ACC1),
    ),
    LANGUAGE(
        route = Routes.User.LANGUAGE,
        titleRes = R.string.settings_entry_language,
        subtitleRes = R.string.settings_entry_language_sub,
        icon = Icons.Filled.Language,
        iconBg = Color(0xFF6A1B9A),
    ),
    UPDATE(
        route = null,
        titleRes = R.string.settings_entry_update,
        subtitleRes = R.string.settings_entry_update_sub,
        icon = Icons.Filled.SystemUpdate,
        iconBg = Color(0xFF00897B),
    ),
    ABOUT(
        route = Routes.User.ABOUT,
        titleRes = R.string.settings_entry_about,
        subtitleRes = R.string.settings_entry_about_sub,
        icon = Icons.Filled.Info,
        iconBg = Color(0xFF757575),
    ),
}
