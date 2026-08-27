package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState

/**
 * 支持"顶栏渐变模糊"的页面骨架（移植自 legado-with-MD3 AppScaffold 的 M3 分支）。
 *
 * 关键是让内容延伸到顶栏之下滚动，顶栏（[NiTopBar]）再采样它做真实渐进模糊：
 * - 本组件自建一个 [HazeState] 并通过 [LocalHazeState] 提供给子树（[NiTopBar] 读取）。
 * - Scaffold 的 innerPadding 作为"建议偏移"直接传给 [content]，而不通过 `Modifier.padding`
 *   塞外框，因此内容滚动可铺满整个页面（顶栏背后也能绘制）。
 * - 内容层用 `niHazeSource` 标记为顶栏模糊的来源，滚动时经过顶栏下方的内容被模糊。
 *
 * 调用方只需把滚动区域（LazyColumn/Column）的 `contentPadding` 与传入的 [PaddingValues]
 * 合并，即可让条目起始于顶栏之下、又能滚入顶栏区域被模糊。
 *
 * @param topBar         顶栏（通常传入 [NiTopBar]）
 * @param bottomBar      底栏（通常为 null，底部导航由 HomeScreen 统一悬浮）
 * @param snackbarHost   Snackbar 宿主
 * @param contentWindowInsets 内容系统窗口 inset，默认跟随 Scaffold 默认值
 * @param containerColor 页面容器背景色，默认取主题 background
 * @param content        页面内容；参数为 Scaffold innerPadding（含状态栏+顶栏高度/底栏/导航栏），
 *                       应并入滚动区的 contentPadding
 */
@Composable
fun NiScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 每个页面自建模糊状态，顶栏与内容层共享同一实例
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            // 由调用方决定容器色，避免在模糊背后叠一层不透明块
            containerColor = containerColor,
            contentWindowInsets = contentWindowInsets,
        ) { innerPadding ->
            // 内容层满铺全屏（含顶栏背后区域），并标记为顶栏模糊来源
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .niHazeSource(hazeState),
            ) {
                content(innerPadding)
            }
        }
    }
}