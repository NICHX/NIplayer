package com.nichx.niplayer.feature.home.settings

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors

/**
 * 关于页：应用版本 + 开源依赖许可证。
 *
 * 替代旧仓库分散的实现（AppSettingFragment 的 `app_version` 项 + 独立 LicenseActivity），
 * v2 合并为单一页面：顶部展示应用版本，下方按分组列出所有开源依赖及其 license。
 *
 * 依赖列表硬编码（随 libs.versions.toml 同步更新），不读 assets 文件——
 * 许可证以 Apache 2.0 为主（另有 jcifs 的 LGPL 2.1、jsoup/BouncyCastle/SLF4J 的 MIT、
 * MMKV 的 BSD 3-Clause、juniversalchardet 的 MPL 1.1），
 * 完整 license 文本可通过依赖项的 URL 在线查看。
 *
 * @param onBack 返回回调
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
    }

    Scaffold(
        topBar = {
            NiTopBar(
                title = "关于 NIplayer",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 应用信息 ----
            item {
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
                            Text(
                                text = versionName.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            // ---- 开源依赖 ----
            items(
                items = LicenseGroup.entries,
                key = { it.name },
            ) { group ->
                LicenseGroupCard(group)
            }
        }
    }
}

@Composable
private fun LicenseGroupCard(group: LicenseGroup) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NiExtraColors.current.surfaceLevel2),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            group.dependencies.forEachIndexed { index, dep ->
                LicenseItemRow(dep)
                if (index < group.dependencies.size - 1) {
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
private fun LicenseItemRow(dep: LicenseDependency) {
    var expanded by rememberSaveable(dep.name) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dep.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${dep.version} · ${dep.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = dep.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = dep.licenseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

/** 开源依赖描述符。 */
data class LicenseDependency(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
    val licenseUrl: String,
)

/**
 * 依赖分组（按功能域聚合）。
 *
 * 列表随 libs.versions.toml 同步更新。v2 相比旧仓库：移除 Arouter / DanmakuFlameMaster /
 * ImmersionBar / PanelSwitchHelper / banner / nanohttpd / sardine-android / 7-Zip-JBinding /
 * glide / smbj（不再使用），SMB 协议改用 jcifs + BouncyCastle；新增 media3 / Coil 3 /
 * Hilt / OkHttp / MMKV / ComposeReorderable。
 */
enum class LicenseGroup(
    val dependencies: List<LicenseDependency>,
) {
    ANDROID_X(
        dependencies = listOf(
            LicenseDependency(
                name = "AndroidX Media3",
                version = "1.10.1",
                license = "Apache 2.0",
                url = "https://github.com/androidx/media",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "AndroidX Compose",
                version = "BOM 2026.06.00",
                license = "Apache 2.0",
                url = "https://developer.android.com/jetpack/compose",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "AndroidX Navigation Compose",
                version = "2.9.8",
                license = "Apache 2.0",
                url = "https://developer.android.com/guide/navigation",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "AndroidX Room",
                version = "2.8.4",
                license = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/room",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "AndroidX Core KTX",
                version = "1.17.0",
                license = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/core",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "AndroidX Activity Compose",
                version = "1.13.0",
                license = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/activity",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "AndroidX Lifecycle",
                version = "2.9.4",
                license = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "AndroidX Media",
                version = "1.7.0",
                license = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/media",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "AndroidX DocumentFile",
                version = "1.1.0",
                license = "Apache 2.0",
                url = "https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
        ),
    ),
    KOTLIN(
        dependencies = listOf(
            LicenseDependency(
                name = "Kotlin Coroutines",
                version = "1.10.2",
                license = "Apache 2.0",
                url = "https://github.com/Kotlin/kotlinx.coroutines",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "Moshi",
                version = "1.15.2",
                license = "Apache 2.0",
                url = "https://github.com/square/moshi",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
        ),
    ),
    NETWORK(
        dependencies = listOf(
            LicenseDependency(
                name = "OkHttp",
                version = "4.12.0",
                license = "Apache 2.0",
                url = "https://github.com/square/okhttp",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "Retrofit",
                version = "2.11.0",
                license = "Apache 2.0",
                url = "https://github.com/square/retrofit",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
        ),
    ),
    STORAGE(
        dependencies = listOf(
            LicenseDependency(
                name = "jcifs",
                version = "3.0.0",
                license = "LGPL 2.1",
                url = "https://github.com/codelibs/jcifs",
                licenseUrl = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html",
            ),
            LicenseDependency(
                name = "BouncyCastle (bcprov)",
                version = "1.80",
                license = "MIT",
                url = "https://github.com/bcgit/bc-java",
                licenseUrl = "https://www.bouncycastle.org/about/license/",
            ),
            LicenseDependency(
                name = "SLF4J",
                version = "1.7.36",
                license = "MIT",
                url = "https://www.slf4j.org/",
                licenseUrl = "https://www.slf4j.org/license.html",
            ),
        ),
    ),
    MEDIA(
        dependencies = listOf(
            LicenseDependency(
                name = "Coil 3",
                version = "3.5.0",
                license = "Apache 2.0",
                url = "https://github.com/coil-kt/coil",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "juniversalchardet",
                version = "2.4.0",
                license = "MPL 1.1",
                url = "https://github.com/albfernandez/juniversalchardet",
                licenseUrl = "https://www.mozilla.org/en-US/MPL/1.1/",
            ),
            LicenseDependency(
                name = "jsoup",
                version = "1.18.3",
                license = "MIT",
                url = "https://github.com/jhy/jsoup",
                licenseUrl = "https://opensource.org/licenses/MIT",
            ),
        ),
    ),
    UI(
        dependencies = listOf(
            LicenseDependency(
                name = "ComposeReorderable",
                version = "0.9.6",
                license = "Apache 2.0",
                url = "https://github.com/yannickpulver/ComposeReorderable",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
        ),
    ),
    DI(
        dependencies = listOf(
            LicenseDependency(
                name = "Hilt / Dagger",
                version = "2.60.1",
                license = "Apache 2.0",
                url = "https://github.com/google/dagger",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "Hilt Navigation Compose",
                version = "1.4.0",
                license = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/hilt",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            ),
            LicenseDependency(
                name = "MMKV",
                version = "1.3.14",
                license = "BSD 3-Clause",
                url = "https://github.com/Tencent/MMKV",
                licenseUrl = "https://opensource.org/licenses/BSD-3-Clause",
            ),
        ),
    ),
}
