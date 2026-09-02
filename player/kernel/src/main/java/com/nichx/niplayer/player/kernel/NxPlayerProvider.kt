package com.nichx.niplayer.player.kernel

/**
 * 播放内核能力解析器（多内核接入）。
 *
 * 综合「用户强制选择」（[com.nichx.niplayer.datastore.PlayerSettings.playerBackend]）、
 * 媒体源能力过滤（[NxPlayerBackend.supports]）与内核优先级（[NxPlayerBackend.backendPriority]），
 * 产出有序的可用内核列表（首项为首选，其余为 fallback 链）。
 */
interface NxPlayerProvider {

    /**
     * 解析当前生效内核。

     * - [source] 为 null 时跳过能力过滤（用于构造期不确定具体媒体源的选择）；
     * - 非 null 时按 [NxPlayerBackend.supports] 过滤。
     *
     * 返回列表按 [NxPlayerBackend.backendPriority] 降序；强制选择无效到空时回退到能力解析，
     * 绝不为空（media3 兜底内核恒在）。
     */
    fun resolveBackends(source: NxMediaSource? = null): List<NxPlayerBackend>
}