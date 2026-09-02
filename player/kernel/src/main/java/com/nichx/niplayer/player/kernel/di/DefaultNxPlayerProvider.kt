package com.nichx.niplayer.player.kernel.di

import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.player.kernel.NX_BACKEND_AUTO
import com.nichx.niplayer.player.kernel.NxMediaSource
import com.nichx.niplayer.player.kernel.NxPlayerBackend
import com.nichx.niplayer.player.kernel.NxPlayerProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 默认能力解析器实现。
 *
 * 注入全部已注册的 [NxPlayerBackend]（Hilt `@IntoSet` 多绑定），按下述规则输出有序列表：
 *
 * 1. 读取 [PlayerSettings.playerBackend] 覆盖：
 *    - 非 "auto"：优先保留与 backendId 匹配的内核（无效/空则回退全部）；
 *    - "auto"：保留全部。
 * 2. 按 [NxMediaSource] 能力过滤（source 为 null 时跳过）。
 * 3. 按 [NxPlayerBackend.backendPriority] 降序排序。
 * 4. 若第 2 步结果为空，回退全部已注册内核（media3 恒兜底，保证非空）。
 */
@Singleton
class DefaultNxPlayerProvider @Inject constructor(
    private val backends: Set<@JvmSuppressWildcards NxPlayerBackend>,
) : NxPlayerProvider {

    override fun resolveBackends(source: NxMediaSource?): List<NxPlayerBackend> {
        // 1. 设置覆盖：强制指定内核，或自动选全部
        val forced = PlayerSettings.playerBackend
        val basePool: Collection<NxPlayerBackend> =
            if (forced != NX_BACKEND_AUTO) backends.filter { it.backendId == forced }
            else backends
        val pool = basePool.ifEmpty { backends }

        // 2. 能力过滤（source 为 null 跳过）
        val capable = if (source == null) pool.toList() else pool.filter { it.supports(source) }

        // 3. 优先级降序
        val ordered = capable.sortedByDescending { it.backendPriority }

        // 4. 能力过滤后为空时回退全部，保证解析非空（media3 兜底）
        return ordered.ifEmpty { backends.sortedByDescending { it.backendPriority } }
    }
}