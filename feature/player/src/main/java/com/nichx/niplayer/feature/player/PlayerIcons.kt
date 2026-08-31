package com.nichx.niplayer.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * A-B 循环专用图标。
 *
 * Material 图标库没有「A-B 区间循环」的语义图标：[Icons.Rounded.Repeat] 表达「列表重复」、
 * [Icons.Rounded.RepeatOne] 表达「循环单集」，用于 A-B 循环语义不准确。
 *
 * 本图标采用「圆形环形（代表循环/重复区间）+ 居中 A/B 字形（代表区间起止端点）」的组合，
 * 全部使用描边绘制（无填充），图标整体染色时环与字母同色、字母在开放圆心处清晰可见。
 *
 * A/B 字形为写意笔画（圆头描边）：A 为三角 + 横杠，B 为竖脊 + 两个右凸圆弧。
 */
val AbLoopIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    // 外圈环形：完整圆环 + 顶部圆角箭头，表达「循环」。用两个半圆拼成整圆。
    val ringPath = PathParser().parsePathString(
        "M12 5 A7 7 0 1 1 12 19 A7 7 0 1 1 12 5 Z"
    ).toNodes()
    // 内圈 A 字形（写意三角 + 横杠）
    val letterA = PathParser().parsePathString(
        "M10 9.5 L8.4 14.5 M10 9.5 L11.6 14.5 M9 11.9 L11 11.9"
    ).toNodes()
    // 内圈 B 字形（竖脊 + 上下两个右凸弧）
    val letterB = PathParser().parsePathString(
        "M13.6 9.5 V14.5 " +
            "M13.6 11.9 C15.2 11.9 15.6 11.2 15.6 10.6 C15.6 10.0 15.2 9.5 13.6 9.6 " +
            "M13.6 11.9 C15.2 11.9 15.6 12.6 15.6 13.2 C15.6 13.6 15.2 14.5 13.6 14.4"
    ).toNodes()
    ImageVector.Builder(
        name = "AbLoop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 环：描边比字母略粗
        addPath(
            pathData = ringPath,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        // A 字形描边
        addPath(
            pathData = letterA,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        // B 字形描边
        addPath(
            pathData = letterB,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}

/**
 * 竖滑切视频专用图标：上下两组尖角箭头，表达「上下滑动切换上一集/下一集」。
 *
 * Material 图标库没有语义化的「竖向滑动切换」图标，
 * 本图标用上下两组 V 字形尖角（描边绘制，圆头）组成竖直滑动指示，整体染色时按状态着色。
 */
val SwipeSwitchIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    // 上箭头组：朝上的两个 V 形尖角，指示「上滑看下一集」
    val upGroup = PathParser().parsePathString(
        "M9 6 L12 3 L15 6 M9 9.5 L12 6.5 L15 9.5"
    ).toNodes()
    // 下箭头组：朝下的两个 V 形尖角，指示「下滑看上一集」
    val downGroup = PathParser().parsePathString(
        "M9 14.5 L12 17.5 L15 14.5 M9 18 L12 21 L15 18"
    ).toNodes()
    ImageVector.Builder(
        name = "SwipeSwitch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = upGroup,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            pathData = downGroup,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}