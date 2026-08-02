package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.datastore.LrcApiSettings
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors

@Composable
fun LrcApiSettingsScreen(
    onBack: () -> Unit = {},
) {
    var apiUrl by remember { mutableStateOf(LrcApiSettings.apiUrl) }
    var apiAuth by remember { mutableStateOf(LrcApiSettings.apiAuth) }
    var showApiUrlDialog by remember { mutableStateOf(false) }
    var showApiAuthDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NiTopBar(
                title = "音乐元数据",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsGroupSection(
                title = "API 接口",
                icon = Icons.Filled.Link,
                iconBg = Color(0xFFE91E63),
                action = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "帮助",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            ) {
                SettingClickRow(
                    label = "API 地址",
                    value = apiUrl.ifEmpty { "未设置" },
                    onClick = { showApiUrlDialog = true },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingClickRow(
                    label = "身份验证",
                    value = if (apiAuth.isBlank()) "未设置" else "已设置",
                    onClick = { showApiAuthDialog = true },
                )
            }

            SettingsGroupSection(
                title = "说明",
                icon = Icons.Filled.Info,
                iconBg = Color(0xFF757575),
            ) {
                SettingInfoRow(
                    label = "歌词获取",
                    value = "播放时自动通过 API 获取",
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingInfoRow(
                    label = "封面获取",
                    value = "浏览及播放时自动回退到 API",
                )
            }
        }
    }

    if (showApiUrlDialog) {
        var input by rememberSaveable { mutableStateOf(apiUrl) }
        NiInfoDialog(
            title = "API 地址",
            onDismiss = { showApiUrlDialog = false },
            actions = {
                TextButton(onClick = { showApiUrlDialog = false }) { Text("取消") }
                TextButton(onClick = {
                    apiUrl = input
                    LrcApiSettings.apiUrl = input
                    showApiUrlDialog = false
                }) { Text("保存") }
            },
        ) {
            Text(
                "请输入 lrcapi 服务地址，如 http://192.168.1.100:8080。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "http://192.168.1.100:8080",
                label = "服务器地址",
            )
        }
    }

    if (showApiAuthDialog) {
        var input by rememberSaveable { mutableStateOf(apiAuth) }
        NiInfoDialog(
            title = "身份验证",
            onDismiss = { showApiAuthDialog = false },
            actions = {
                TextButton(onClick = { showApiAuthDialog = false }) { Text("取消") }
                TextButton(onClick = {
                    apiAuth = input
                    LrcApiSettings.apiAuth = input
                    showApiAuthDialog = false
                }) { Text("保存") }
            },
        ) {
            Text(
                "如果 API 需要 Authorization header，在此输入 token 值。留空表示不需要验证。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "输入 Authorization token",
                label = "Token",
            )
        }
    }

    if (showHelpDialog) {
        NiInfoDialog(
            title = "音乐元数据 API",
            onDismiss = { showHelpDialog = false },
        ) {
            Text(
                "配置 lrcapi 服务后，NIplayer 可在以下场景获取音乐元数据：",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "1. 浏览文件夹时，音频文件无内嵌封面和目录封面时自动回退到 API 获取封面。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "2. 播放音频时，优先加载同目录歌词文件，不存在时通过 API 获取歌词。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "API 接口约定：",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "• GET /lyrics?title=&artist=&album=&path=\n• GET /cover?title=&artist=&album=",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SettingsGroupSection(
    title: String,
    icon: ImageVector,
    iconBg: Color,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        ) {
            Box(
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
            )
            action?.let {
                Spacer(Modifier.weight(1f))
                it()
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NiExtraColors.current.surfaceLevel2),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingClickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
