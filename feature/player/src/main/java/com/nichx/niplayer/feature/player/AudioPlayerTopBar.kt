package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.nichx.niplayer.datastore.AudioPlayerStyle
import com.nichx.niplayer.designsystem.components.NiGlassDropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.feature.player.theme.ChromeSpec
import com.nichx.niplayer.feature.player.theme.ChromeTint


/** 播放器顶栏"更多"下拉菜单的页面态（单一玻璃菜单原地切换，避免子菜单开合时闪烁）。 */
internal enum class MoreMenuPage { Idle, Main, Speed, Appearance }

/**
 * 顶栏操作按钮组：更多（内含倍速二级菜单 / 均衡器 / 睡眠定时 / 下载）。
 * 竖屏 TopBar 与横屏顶部行共用，保证按钮与菜单样式一致。
 */
@Composable
internal fun TopBarActions(
    onDownload: () -> Unit,
    onEqualizer: () -> Unit,
    speedOptions: List<Float>,
    currentSpeedIndex: Int,
    onSpeedSelect: (Int) -> Unit,
    onMenuOpenChange: (Boolean) -> Unit = {},
    showDownload: Boolean = true,
    sleepTimerText: String = "",
    onSleepTimer: () -> Unit = {},
    showExternalActions: Boolean = false,
    onOpenWith: () -> Unit = {},
    onShare: () -> Unit = {},
    chrome: ChromeSpec,
    /**
     * 是否渲染「更多」按钮。
     *
     * 由**调用点**决定而非主题属性：Apple 把「更多」放在封面下方的歌名行里，
     * 但它的歌词页顶栏与横屏顶栏仍然需要这个按钮，所以不能按主题一刀切。
     */
    showMore: Boolean = true,
    /** 非空时在菜单里提供「外观」二级菜单用于切换主题。 */
    audioStyle: AudioPlayerStyle? = null,
    onStyleSelect: (AudioPlayerStyle) -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    // 顶栏配色族：Apple 压在封面图上用浅色圆底，其余跟随 Material 主题
    val moreContainerColor = when (chrome.tint) {
        ChromeTint.MATERIAL -> onSurface.copy(alpha = 0.08f)
        ChromeTint.LIGHT_ON_DARK -> Color.White.copy(alpha = 0.16f)
    }
    val moreIconColor = when (chrome.tint) {
        ChromeTint.MATERIAL -> onSurface.copy(alpha = 0.8f)
        ChromeTint.LIGHT_ON_DARK -> Color.White
    }
    val moreIcon = chrome.moreIcon
    val moreIconSize = chrome.moreIconSize
    val moreButtonSize = chrome.moreButtonSize
    // 外观三个选项的名称（子菜单用）
    val vinylStyleName = stringResource(R.string.player_audio_appearance_vinyl)
    val glassStyleName = stringResource(R.string.player_audio_appearance_glass)
    val appleMusicStyleName = stringResource(R.string.player_audio_appearance_apple_music)
    fun styleNameOf(option: AudioPlayerStyle): String = when (option) {
        AudioPlayerStyle.VINYL -> vinylStyleName
        AudioPlayerStyle.GLASS -> glassStyleName
        AudioPlayerStyle.APPLE_MUSIC -> appleMusicStyleName
    }
    var menuPage by remember { mutableStateOf(MoreMenuPage.Idle) }
    // 更多/子菜单锚点（More 按钮屏幕坐标，供玻璃菜单定位）
    var moreMenuAnchor by remember { mutableStateOf(IntRect.Zero) }
    val safeSpeedIndex = currentSpeedIndex.coerceIn(0, speedOptions.lastIndex)

    // 任意下拉菜单展开/收起时通知外层（横屏用于暂停自动隐藏计时）
    LaunchedEffect(menuPage) {
        onMenuOpenChange(menuPage != MoreMenuPage.Idle)
    }

    val menuItemPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 睡眠定时进行中：顶栏显示剩余时间，点击直接打开定时设置
        if (sleepTimerText.isNotEmpty()) {
            Text(
                text = sleepTimerText,
                color = Color(0xFFFFAB40),
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(CircleShape)
                    .clickable { onSleepTimer() },
            )
        }
        // 更多：外观（二级菜单）/ 倍速（二级菜单）/ 均衡器 / 睡眠定时 / 下载 收进溢出菜单
        if (showMore) Box(
            modifier = Modifier.onGloballyPositioned { coords ->
                // 锚点取触发按钮的根坐标矩形（供玻璃菜单定位 + 自该角长出来）
                val topLeft = coords.localToRoot(Offset.Zero)
                moreMenuAnchor = IntRect(IntOffset(topLeft.x.toInt(), topLeft.y.toInt()), coords.size)
            },
        ) {
            IconButton(onClick = { menuPage = MoreMenuPage.Main }) {
                Box(
                    modifier = Modifier
                        .size(moreButtonSize)
                        .clip(CircleShape)
                        .background(moreContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = moreIcon,
                        contentDescription = stringResource(R.string.player_more),
                        tint = moreIconColor,
                        modifier = Modifier.size(moreIconSize),
                    )
                }
            }
            // 更多：单一玻璃菜单，按 menuPage 原地切换页面（主菜单/倍速/元数据），
            // 切换时同一 overlay 就地更新内容，避免「旧菜单退场 + 新菜单进场」在锚点重叠闪烁
            NiGlassDropdownMenu(
                expanded = menuPage != MoreMenuPage.Idle,
                onDismissRequest = { menuPage = MoreMenuPage.Idle },
                anchorBounds = moreMenuAnchor,
                contentVersion = menuPage,
            ) {
                when (menuPage) {
                    MoreMenuPage.Main -> {
                        // 外观：子菜单入口，尾部显示当前主题名 + 展开箭头
                        if (audioStyle != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.player_appearance),
                                        fontSize = 14.sp,
                                        color = onSurface,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = null,
                                        tint = onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = styleNameOf(audioStyle),
                                            color = onSurfaceVariant,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                contentPadding = menuItemPadding,
                                onClick = { menuPage = MoreMenuPage.Appearance },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = onSurface.copy(alpha = 0.08f),
                            )
                        }
                        // 倍速：子菜单入口，尾部显示当前档位 + 展开箭头
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.player_speed_icon),
                                    fontSize = 14.sp,
                                    color = onSurface,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = formatSpeedLabel(speedOptions[safeSpeedIndex]),
                                        color = onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                            contentPadding = menuItemPadding,
                            onClick = { menuPage = MoreMenuPage.Speed },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = onSurface.copy(alpha = 0.08f),
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.player_equalizer),
                                    fontSize = 14.sp,
                                    color = onSurface,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Equalizer,
                                    contentDescription = null,
                                    tint = onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            contentPadding = menuItemPadding,
                            onClick = {
                                menuPage = MoreMenuPage.Idle
                                onEqualizer()
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = onSurface.copy(alpha = 0.08f),
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.player_sleep_timer),
                                    fontSize = 14.sp,
                                    color = onSurface,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Bedtime,
                                    contentDescription = null,
                                    tint = onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            contentPadding = menuItemPadding,
                            onClick = {
                                menuPage = MoreMenuPage.Idle
                                onSleepTimer()
                            },
                        )
                        if (showDownload) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.player_download_icon),
                                        fontSize = 14.sp,
                                        color = onSurface,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                contentPadding = menuItemPadding,
                                onClick = {
                                    menuPage = MoreMenuPage.Idle
                                    onDownload()
                                },
                            )
                        }
                        if (showExternalActions) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = onSurface.copy(alpha = 0.08f),
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.player_open_with),
                                        fontSize = 14.sp,
                                        color = onSurface,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                contentPadding = menuItemPadding,
                                onClick = {
                                    menuPage = MoreMenuPage.Idle
                                    onOpenWith()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.player_share),
                                        fontSize = 14.sp,
                                        color = onSurface,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = null,
                                        tint = onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                contentPadding = menuItemPadding,
                                onClick = {
                                    menuPage = MoreMenuPage.Idle
                                    onShare()
                                },
                            )
                        }
                    }
                    MoreMenuPage.Speed -> {
                        // 菜单标题（本版本无 DropdownMenuHeader，用普通文本行代替）
                        Text(
                            text = stringResource(R.string.player_speed_menu_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            thickness = 0.5.dp,
                            color = onSurface.copy(alpha = 0.08f),
                        )
                        speedOptions.forEachIndexed { idx, speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = formatSpeedLabel(speed),
                                        fontSize = 14.sp,
                                        color = if (idx == safeSpeedIndex) primary else onSurface,
                                        fontWeight = if (idx == safeSpeedIndex) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                contentPadding = menuItemPadding,
                                onClick = {
                                    menuPage = MoreMenuPage.Idle
                                    onSpeedSelect(idx)
                                },
                                trailingIcon = if (idx == safeSpeedIndex) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.player_current_speed),
                                            tint = primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                    MoreMenuPage.Appearance -> {
                        // 菜单标题（本版本无 DropdownMenuHeader，用普通文本行代替）
                        Text(
                            text = stringResource(R.string.player_audio_appearance),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            thickness = 0.5.dp,
                            color = onSurface.copy(alpha = 0.08f),
                        )
                        // 从枚举派生：新增外观时自动出现在菜单里，不必手工同步这份清单
                        AudioPlayerStyle.entries.forEach { option ->
                            val selected = option == audioStyle
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = styleNameOf(option),
                                        fontSize = 14.sp,
                                        color = if (selected) primary else onSurface,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                contentPadding = menuItemPadding,
                                onClick = {
                                    menuPage = MoreMenuPage.Idle
                                    onStyleSelect(option)
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                    MoreMenuPage.Idle -> {}
                }
            }
        }
    }
}

@Composable
internal fun TopBar(
    title: String,
    onBack: () -> Unit,
    onDownload: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    speedOptions: List<Float> = listOf(1f),
    currentSpeedIndex: Int = 0,
    onSpeedSelect: (Int) -> Unit = {},
    showDownload: Boolean = true,
    sleepTimerText: String = "",
    onSleepTimer: () -> Unit = {},
    showExternalActions: Boolean = false,
    onOpenWith: () -> Unit = {},
    onShare: () -> Unit = {},
    chrome: ChromeSpec,
    audioStyle: AudioPlayerStyle? = null,
    onStyleSelect: (AudioPlayerStyle) -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val backContainerColor = when (chrome.tint) {
        ChromeTint.MATERIAL -> onSurface.copy(alpha = 0.08f)
        ChromeTint.LIGHT_ON_DARK -> Color.Transparent
    }
    val iconTint = when (chrome.tint) {
        ChromeTint.MATERIAL -> onSurface.copy(alpha = 0.8f)
        ChromeTint.LIGHT_ON_DARK -> Color.White.copy(alpha = 0.9f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(backContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.player_back),
                    tint = iconTint,
                )
            }
        }

        // 标题：空标题时（无此场景）不显示占位文本，用 Spacer 维持两端按钮间距
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = when (chrome.tint) {
                    ChromeTint.MATERIAL -> onSurface.copy(alpha = 0.9f)
                    ChromeTint.LIGHT_ON_DARK -> Color.White.copy(alpha = 0.9f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        TopBarActions(
            onDownload = onDownload,
            onEqualizer = onEqualizer,
            speedOptions = speedOptions,
            currentSpeedIndex = currentSpeedIndex,
            onSpeedSelect = onSpeedSelect,
            showDownload = showDownload,
            sleepTimerText = sleepTimerText,
            onSleepTimer = onSleepTimer,
            showExternalActions = showExternalActions,
            onOpenWith = onOpenWith,
            onShare = onShare,
            chrome = chrome,
            audioStyle = audioStyle,
            onStyleSelect = onStyleSelect,
        )
    }
}

/** 倍速文字标签：整数档省略小数（1.0x、1.25x）。 */
internal fun formatSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }
}


