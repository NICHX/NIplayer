package com.nichx.niplayer.feature.home.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiSpacings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.components.NiSectionHeader


/**
 * 首页加载骨架：与 [HomeSingleColumnLayout] 同构的 LazyColumn（item key 命名一致），
 * 数据就绪切换时 LazyColumn 结构与滚动位置保持，仅替换 item 内容，避免整树重建与全量重测量。
 */
@Composable
internal fun HomeSkeletonLayout(
    contentMaxWidth: Dp,
    topInset: Dp,
    recentColumns: Int,
    qaColumns: Int,
) {
    val screenOuter = NiSpacings.responsiveScreenOuter
    val listGap = NiSpacings.responsiveListGap
    // 底部导航栏避让：与真实布局保持一致
    val bottomBarClearance = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    } + 88.dp
    val blockColor = NiExtraColors.current.surfaceLevel3

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = screenOuter,
                end = screenOuter,
                top = topInset + 8.dp,
                bottom = bottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(listGap),
            userScrollEnabled = false,
        ) {
            item(key = "hero") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(blockColor),
                )
            }
            item(key = "video_header") {
                SkeletonHeaderBlock(blockColor)
            }
            item(key = "video_row") {
                Row(horizontalArrangement = Arrangement.spacedBy(listGap)) {
                    repeat(recentColumns) {
                        SkeletonMediaCard(
                            thumbAspectRatio = 16f / 9f,
                            blockColor = blockColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "audio_header") {
                SkeletonHeaderBlock(blockColor)
            }
            item(key = "audio_row") {
                Row(horizontalArrangement = Arrangement.spacedBy(listGap)) {
                    repeat(recentColumns) {
                        SkeletonMediaCard(
                            thumbAspectRatio = 1f,
                            blockColor = blockColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "qa_header") {
                SkeletonHeaderBlock(blockColor)
            }
            item(key = "qa_row_0") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(qaColumns) {
                        SkeletonMediaCard(
                            thumbAspectRatio = 16f / 9f,
                            blockColor = blockColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 首页媒体卡骨架：缩略图占位 + 信息区占位，宽度由 weight 均分。
 *
 * 与真实媒体卡片（RecentMediaGrid：缩略图 + 标题区）结构同构，行高一致，避免
 * 数据就绪切换时内容行变高导致下方内容整体下移。
 */
@Composable
internal fun SkeletonMediaCard(
    thumbAspectRatio: Float,
    blockColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(thumbAspectRatio)
                .clip(RoundedCornerShape(12.dp))
                .background(blockColor),
        )
        // 信息区占位：与真实卡片标题区（NiAutoSizeText 2 行 18sp + padding）同高，
        // 保证骨架与真实行高一致
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .height(36.dp),
        )
    }
}

/** 分区标题骨架占位（与 [NiSectionHeader] 行高一致）。 */
@Composable
internal fun SkeletonHeaderBlock(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}
