package com.nichx.niplayer.feature.home.settings

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.home.update.UpdateDialogHost
import com.nichx.niplayer.feature.home.update.UpdateViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nichx.niplayer.navigation.Routes

/** 推广信息：标题 + 完整描述（从 GitHub README 解析）。 */
private data class PromoInfo(val title: String, val description: String)

private const val PROMO_RAW_BASE = "https://raw.githubusercontent.com/NICHX/unraid-assistant-releases"
private const val PROMO_JSDEIVR_BASE = "https://cdn.jsdelivr.net/gh/NICHX/unraid-assistant-releases"
private const val PROMO_DESC_MARK = "一款 Android 端 unRAID 服务器管理应用"
private const val PROMO_TITLE_MARK = "unRAID 助手（unRAID Assistant）"
private val PROMO_BRANCHES = listOf("master", "main")

/**
 * 从 GitHub README 拉取推广段落。返回任一可达源的解析结果。
 *
 * 大陆访问限制：`raw.githubusercontent.com` 常不可达，因此优先用 **jsDelivr CDN**
 *（`cdn.jsdelivr.net/gh/...@branch/README.md`，有大陆节点）镜像 README，raw 作为
 * 兜底源依次尝试。全部失败/超时返回 null，调用方据此隐藏推广横幅。
 */
private suspend fun fetchPromoInfo(): PromoInfo? = withContext(Dispatchers.IO) {
    for (branch in PROMO_BRANCHES) {
        // jsDelivr 优先（大陆可达），raw 兜底（非大陆 / 走代理时更快）
        val candidates = listOf(
            "$PROMO_JSDEIVR_BASE@$branch/README.md",
            "$PROMO_RAW_BASE/$branch/README.md",
        )
        for (url in candidates) {
            val body = fetchRaw(url) ?: continue
            val info = parsePromo(body)
            if (info != null) return@withContext info
        }
    }
    null
}

private fun fetchRaw(url: String): String? {
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "NIplayer")
        }
        if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    } finally {
        try { conn?.disconnect() } catch (_: Exception) {}
    }
}

private fun parsePromo(body: String): PromoInfo? {
    val clean = body.replace("\r", "").removePrefix("\uFEFF")
    val title = arrayOf(PROMO_TITLE_MARK, "unRAID 助手", "unRAID Assistant")
        .firstNotNullOfOrNull { mark ->
            clean.indexOf(mark).takeIf { it >= 0 }?.let { clean.substring(it, it + mark.length) }
        }
        ?: "Unraid 助手"
    // 描述取包含关键短语的那一行（README 中该段落在独立行）
    val desc = clean.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.contains(PROMO_DESC_MARK) }
        ?.trim()
        ?: return null
    return PromoInfo(title, desc)
}

@Composable
fun SettingsScreen(
    onNavigateToGlobal: (String) -> Unit = {},
) {
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val context = LocalContext.current
    // 用系统浏览器打开外部链接（项目推广 / 赞助）
    val openExternal: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    // 推广信息：从 GitHub README 运行时拉取，失败/超时则整体+只保留赞助（隐藏推广）
    var promo by remember { mutableStateOf<PromoInfo?>(null) }
    LaunchedEffect(Unit) {
        promo = fetchPromoInfo()
    }

    NiScaffold(
        topBar = { NiTopBar(title = stringResource(R.string.home_tab_settings)) },
    ) { padding ->
        // 底部导航栏避让：玻璃底栏悬浮在 8dp+系统导航栏高度上方（高度 64dp），预留清除空间
        val bottomBarClearance = with(LocalDensity.current) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        } + 88.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + bottomBarClearance,
            ),
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
                    if (group == SettingsGroup.OTHER) {
                        // “其他”组：推广横幅（从 README 拉取，失败则隐藏）+ 赞助，均为外部链接
                        OtherGroupCard(promo = promo, onOpen = openExternal)
                    } else {
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
    // 构建类型：debug 包必然可调试，用 APK 调试标志判定（跨模块不依赖 BuildConfig 传递）
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
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
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_build_code, versionCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(8.dp))
                    BuildTypeBadge(isDebug)
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

/** “其他”组卡片：推广横幅（README 拉取成功才显示）+ 赞助。克制风格，与设置项行式一致。 */
@Composable
private fun OtherGroupCard(promo: PromoInfo?, onOpen: (String) -> Unit) {
    var showPromoDialog by remember { mutableStateOf(false) }
    val promoUrl = stringResource(R.string.promo_unraid_url)
    val sponsorUrl = stringResource(R.string.promo_sponsor_url)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NiExtraColors.current.surfaceLevel2),
    ) {
        Column {
            if (promo != null) {
                // 推广横幅（小型，多行描述收进弹窗；点击看详情）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPromoDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2095F4)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = promo.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.promo_view_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            // 赞助支持
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(sponsorUrl) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF4A63C)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.promo_sponsor_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.promo_sponsor_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
    // 推广详情弹窗：完整描述 + 跳转按钮
    if (showPromoDialog && promo != null) {
        NiInfoDialog(
            title = promo.title,
            onDismiss = { showPromoDialog = false },
            actions = {
                TextButton(onClick = { showPromoDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = {
                    showPromoDialog = false
                    onOpen(promoUrl)
                }) { Text(stringResource(R.string.promo_view_open)) }
            },
        ) {
            Text(
                text = promo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

enum class SettingsGroup(
    @StringRes val labelRes: Int,
    val entries: List<SettingsEntry>,
) {
    PLAYBACK(
        labelRes = R.string.settings_group_playback,
        entries = listOf(SettingsEntry.PLAYER, SettingsEntry.MEDIA_LIBRARY, SettingsEntry.PLAYBACK_STATS, SettingsEntry.LRCAPI, SettingsEntry.SCAN, SettingsEntry.CACHE),
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
    OTHER(
        labelRes = R.string.settings_group_other,
        // 不参与通用行渲染，由 OtherGroupCard 定制（推广卡片 + 赞助）
        entries = emptyList(),
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
    MEDIA_LIBRARY(
        route = Routes.User.MEDIA_LIBRARY,
        titleRes = R.string.settings_entry_media_library,
        subtitleRes = R.string.settings_entry_media_library_sub,
        icon = Icons.Filled.PhotoLibrary,
        iconBg = Color(0xFF00ACC1),
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
