package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.datastore.ExperimentalSettings
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.home.R

/**
 * “实验性功能”二级设置页：以开关启用/停用各实验性功能（默认关闭）。
 *
 * - VR 播放 / 平铺列表视图
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalScreen(
    onBack: () -> Unit = {},
) {
    var vrEnabled by remember { mutableStateOf(ExperimentalSettings.vrPlaybackEnabled) }
    var flatListEnabled by remember { mutableStateOf(ExperimentalSettings.flatListViewEnabled) }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.settings_entry_experimental),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NiExtraColors.current.surfaceLevel2),
            ) {
                Column {
                    SettingSwitchRow(
                        label = stringResource(R.string.settings_entry_experimental_vr),
                        description = stringResource(R.string.settings_entry_experimental_vr_sub),
                        checked = vrEnabled,
                        onCheckedChange = {
                            vrEnabled = it
                            ExperimentalSettings.vrPlaybackEnabled = it
                        },
                    )
                    SettingSwitchRow(
                        label = stringResource(R.string.settings_entry_experimental_flat_list),
                        description = stringResource(R.string.settings_entry_experimental_flat_list_sub),
                        checked = flatListEnabled,
                        onCheckedChange = {
                            flatListEnabled = it
                            ExperimentalSettings.flatListViewEnabled = it
                        },
                    )
                }
            }

            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
}