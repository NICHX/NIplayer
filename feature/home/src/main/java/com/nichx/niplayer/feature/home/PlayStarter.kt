package com.nichx.niplayer.feature.home

import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.QuickAccessEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放启动统一分发入口。
 *
 * 历史 / 快速访问启动链已拆分到 [HistoryStartProvider]，本类只做场景分发：
 * - [startFromHistory] / [startFromQuickAccess] → 委托 [HistoryStartProvider]
 *
 * 保持对外方法签名稳定，供首页 / 历史列表 / 快速访问 / 搜索各 ViewModel 注入使用。
 */
@Singleton
class PlayStarter @Inject constructor(
    private val historyStartProvider: HistoryStartProvider,
) {

    /** 从播放历史恢复播放。 */
    suspend fun startFromHistory(history: PlayHistoryEntity): PlayStartResult =
        historyStartProvider.startFromHistory(history)

    /** 从快速访问书签播放文件（非文件夹）。 */
    suspend fun startFromQuickAccess(item: QuickAccessEntity): PlayStartResult =
        historyStartProvider.startFromQuickAccess(item)
}