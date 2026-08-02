package com.nichx.niplayer.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.nichx.niplayer.navigation.Routes

/**
 * 主页底部导航 Tab 定义。
 *
 * v2 三 Tab 划分（消除旧仓库 MediaFragment/MineFragment 展示同一 MediaLibraryEntity
 * 的冗余设计）：
 * - [HOME] 首页：信息聚合（最近播放 + 快速访问）
 * - [LIBRARY] 媒体库：存储源管理（Local/SMB/WebDAV）
 * - [SETTINGS] 设置：设置中心
 *
 * 图标使用 Material Icons（Home / VideoLibrary / Settings），替代旧仓库
 * ic_main_media / ic_main_mine / ic_main_personal 位图资源。
 */
enum class HomeTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(
        route = Routes.Home.HOME,
        label = "首页",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    LIBRARY(
        route = Routes.Home.LIBRARY,
        label = "媒体库",
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
    ),
    SETTINGS(
        route = Routes.Home.SETTINGS,
        label = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    );

    companion object {
        /** 默认起始 Tab：首页。 */
        val Start: HomeTab = HOME

        /** 根据 route 查找 Tab，找不到返回 null。 */
        fun fromRoute(route: String?): HomeTab? =
            entries.firstOrNull { it.route == route }
    }
}
