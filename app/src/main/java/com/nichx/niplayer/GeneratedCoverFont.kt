package com.nichx.niplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.CoverLabelFontStore
import java.io.File

/**
 * 「生成封面」上文件名所使用的字体。
 *
 * 应用**不内置**字体（CJK 手写体近 3MB），默认系统字体；用户在设置里自选的字体由
 * [CoverLabelFontStore] 存进私有目录。这里在**应用根部**解析一次、经
 * `LocalGeneratedCoverFont` 下发给所有用到生成封面的界面（播放器、最近播放卡片、
 * 文件浏览缩略图等），这样设计系统组件本身不必依赖数据层，各处也不用重复解析字体。
 */
@Composable
fun rememberGeneratedCoverFont(): FontFamily {
    val path by CoverLabelFontStore.fontPath.collectAsStateWithLifecycle()
    return remember(path) {
        if (path.isEmpty()) {
            FontFamily.Default
        } else {
            runCatching { FontFamily(Font(File(path))) }.getOrDefault(FontFamily.Default)
        }
    }
}
