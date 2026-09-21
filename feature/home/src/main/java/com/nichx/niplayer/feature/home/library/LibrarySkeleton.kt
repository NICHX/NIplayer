package com.nichx.niplayer.feature.home.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.components.NiSkeletonBox
import com.nichx.niplayer.designsystem.components.NiSkeletonLine
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiSpacings


@Composable
internal fun LibrarySkeleton(
    isGridView: Boolean,
    modifier: Modifier = Modifier,
) {
    // 骨架与真实列表同构（key 命名一致），数据就绪切换时结构与滚动位置保持，
    // 仅替换 item 内容，避免整树重建。
    if (isGridView) {
        LibraryGridSkeleton(modifier = modifier)
    } else {
        LibraryListSkeleton(modifier = modifier)
    }
}

@Composable
internal fun LibraryListSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = NiSpacings.screenOuter,
            end = NiSpacings.screenOuter,
            top = 4.dp,
            bottom = 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        item(key = "skeleton_chips") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) {
                    NiSkeletonBox(
                        width = 60.dp,
                        height = 32.dp,
                        shape = pillShape,
                    )
                }
            }
        }
        item(key = "section_count") {
            Spacer(Modifier.height(16.dp))
            NiSkeletonLine(widthFraction = 0.15f)
        }
        // 模拟一组存储源分区的 header + 卡片（与真实列表 key 风格一致）
        item(key = "header_local") {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NiSkeletonBox(width = 4.dp, height = 16.dp, shape = RoundedCornerShape(2.dp))
                Spacer(Modifier.width(8.dp))
                NiSkeletonLine(widthFraction = 0.2f)
            }
        }
        repeat(6) {
            item(key = "library_loading_$it") {
                SkeletonCard()
            }
        }
    }
}

@Composable
internal fun LibraryGridSkeleton(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = NiSpacings.screenOuter,
            end = NiSpacings.screenOuter,
            top = 4.dp,
            bottom = 88.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        item(key = "skeleton_chips", span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) {
                    NiSkeletonBox(
                        width = 60.dp,
                        height = 32.dp,
                        shape = pillShape,
                    )
                }
            }
        }
        item(key = "section_count", span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(16.dp))
            NiSkeletonLine(widthFraction = 0.15f)
        }
        items(count = 8, key = { "library_loading_$it" }) {
            SkeletonGridCard()
        }
    }
}

@Composable
internal fun SkeletonGridCard() {
    val extraColors = NiExtraColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(extraColors.surfaceLevel2)
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NiSkeletonBox(width = 40.dp, height = 40.dp, shape = RoundedCornerShape(12.dp))
                NiSkeletonBox(width = 48.dp, height = 22.dp, shape = RoundedCornerShape(6.dp))
            }
            Spacer(Modifier.height(12.dp))
            NiSkeletonLine(widthFraction = 0.7f)
            Spacer(Modifier.height(8.dp))
            NiSkeletonLine(widthFraction = 0.5f)
            Spacer(Modifier.height(6.dp))
            NiSkeletonLine(widthFraction = 0.4f)
        }
    }
}

@Composable
internal fun SkeletonCard() {
    val extraColors = NiExtraColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(cardShape)
            .background(extraColors.surfaceLevel2),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(extraColors.surfaceLevel3),
            )
            Spacer(Modifier.width(12.dp))
            NiSkeletonBox(
                width = 36.dp,
                height = 36.dp,
                shape = CircleShape,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                NiSkeletonLine(widthFraction = 0.6f)
                Spacer(Modifier.height(6.dp))
                NiSkeletonLine(widthFraction = 0.35f)
            }
        }
    }
}
