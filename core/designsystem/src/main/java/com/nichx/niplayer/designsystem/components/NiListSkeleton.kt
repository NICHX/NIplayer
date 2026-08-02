package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 通用列表骨架屏（O-26）。
 *
 * 统一各列表页（文件浏览/播放历史/存储源/下载管理/缓存管理）的加载态展示，
 * 替代各自定义的 private Skeleton 组件与 [androidx.compose.material3.CircularProgressIndicator] 加载。
 *
 * 渲染 [itemCount] 个列表项骨架，每项由前导图标方块 + 两行文本骨架组成，
 * 与项目典型列表项（[com.nichx.niplayer.designsystem.components.NiVideoThumbnail] + 标题 + 副标题）视觉对齐。
 *
 * @param itemCount 骨架项数量，默认 8
 * @param itemHeight 单项高度，默认 64.dp
 * @param leadingSize 前导方块尺寸，默认 40.dp
 */
@Composable
fun NiListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8,
    itemHeight: Dp = 64.dp,
    leadingSize: Dp = 40.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(itemCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                NiSkeletonBox(
                    width = leadingSize,
                    height = leadingSize,
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NiSkeletonLine(widthFraction = 0.7f)
                    NiSkeletonLine(widthFraction = 0.4f)
                }
            }
        }
    }
}
