package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.datastore.LrcApiSettings
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar

/** lrcapi 项目仓库，用于“使用指引”链接跳转。 */
private const val LRCAPI_GITHUB_URL = "https://github.com/HisAtri/LrcApi"

@Composable
fun LrcApiSettingsScreen(
    onBack: () -> Unit = {},
) {
    var apiUrl by remember { mutableStateOf(LrcApiSettings.apiUrl) }
    var apiAuth by remember { mutableStateOf(LrcApiSettings.apiAuth) }
    var showApiUrlDialog by remember { mutableStateOf(false) }
    var showApiAuthDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.lrcapi_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            SettingsGroupSection(
                title = stringResource(R.string.lrcapi_api_title),
                icon = Icons.Filled.Link,
                iconBg = Color(0xFFE91E63),
                action = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.lrcapi_help),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            ) {
                SettingClickRow(
                    label = stringResource(R.string.lrcapi_url_label),
                    value = apiUrl.ifEmpty { stringResource(R.string.lrcapi_not_set) },
                    onClick = { showApiUrlDialog = true },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingClickRow(
                    label = stringResource(R.string.lrcapi_auth_label),
                    value = if (apiAuth.isBlank()) stringResource(R.string.lrcapi_not_set) else stringResource(R.string.lrcapi_auth_set),
                    onClick = { showApiAuthDialog = true },
                )
            }

            SettingsGroupSection(
                title = stringResource(R.string.lrcapi_note_title),
                icon = Icons.Filled.Info,
                iconBg = Color(0xFF757575),
            ) {
                SettingInfoRow(
                    label = stringResource(R.string.lrcapi_lyrics_label),
                    value = stringResource(R.string.lrcapi_lyrics_value),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingInfoRow(
                    label = stringResource(R.string.lrcapi_cover_label),
                    value = stringResource(R.string.lrcapi_cover_value),
                )
            }

            SettingsGroupSection(
                title = stringResource(R.string.lrcapi_guide_title),
                icon = Icons.Filled.Info,
                iconBg = Color(0xFFE91E63),
            ) {
                val uriHandler = LocalUriHandler.current
                Text(
                    text = stringResource(R.string.lrcapi_guide_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(
                            onClick = { runCatching { uriHandler.openUri(LRCAPI_GITHUB_URL) } },
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.lrcapi_guide_link),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.lrcapi_guide_open),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }

    if (showApiUrlDialog) {
        var input by rememberSaveable { mutableStateOf(apiUrl) }
        NiInfoDialog(
            title = stringResource(R.string.lrcapi_url_dialog_title),
            onDismiss = { showApiUrlDialog = false },
            actions = {
                TextButton(onClick = { showApiUrlDialog = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    apiUrl = input
                    LrcApiSettings.apiUrl = input
                    showApiUrlDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
        ) {
            Text(
                stringResource(R.string.lrcapi_url_dialog_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "http://192.168.1.100:8080",
                label = stringResource(R.string.lrcapi_server_label),
            )
        }
    }

    if (showApiAuthDialog) {
        var input by rememberSaveable { mutableStateOf(apiAuth) }
        NiInfoDialog(
            title = stringResource(R.string.lrcapi_auth_dialog_title),
            onDismiss = { showApiAuthDialog = false },
            actions = {
                TextButton(onClick = { showApiAuthDialog = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    apiAuth = input
                    LrcApiSettings.apiAuth = input
                    showApiAuthDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
        ) {
            Text(
                stringResource(R.string.lrcapi_auth_dialog_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.lrcapi_auth_placeholder),
                label = "Token",
            )
        }
    }

    if (showHelpDialog) {
        NiInfoDialog(
            title = stringResource(R.string.lrcapi_help_title),
            onDismiss = { showHelpDialog = false },
        ) {
            Text(
                stringResource(R.string.lrcapi_help_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.lrcapi_help_point1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.lrcapi_help_point2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.lrcapi_help_api_title),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.lrcapi_help_api_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
