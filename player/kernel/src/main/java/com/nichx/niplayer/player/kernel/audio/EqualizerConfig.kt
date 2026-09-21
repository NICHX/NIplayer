package com.nichx.niplayer.player.kernel.audio

/**
 * 均衡器配置快照（纯数据，不含任何持久化依赖）。
 *
 * **A1 架构修复（2026-09-21）**：[NiEqualizer] 原先直接读取 :core:datastore 的全局
 * `AudioSettings`（MMKV 单例），使**播放内核依赖设置持久化层**（依赖倒置）。现由上层经
 * [EqualizerConfigProvider] 注入配置，内核只处理数据本身。
 *
 * @property enabled 均衡器总开关。false 时不创建效果实例（避免效果链插入爆响），
 *   且把各频段拉平为 0 mB（unity 增益 ≈ 旁路）
 * @property presetIndex 预设索引；`>= 0` 使用系统预设，`< 0` 使用 [bandLevelsMb] 自定义增益
 * @property bandLevelsMb 各频段自定义增益（mB），下标即 band 索引，缺省按 0 处理
 */
data class EqualizerConfig(
    val enabled: Boolean,
    val presetIndex: Int,
    val bandLevelsMb: List<Int>,
) {

    /** 取指定频段增益；越界返回 0（与 `AudioSettings.getBandLevel` 的默认值一致）。 */
    fun bandLevel(band: Int): Int = bandLevelsMb.getOrElse(band) { 0 }

    companion object {
        /** 默认配置：关闭、预设 0、无自定义增益。 */
        val DEFAULT = EqualizerConfig(enabled = false, presetIndex = 0, bandLevelsMb = emptyList())
    }
}

/**
 * 均衡器配置来源。
 *
 * 由**上层**（依赖设置持久化层的模块）实现并在组合根装配，使 :player:kernel 不必依赖
 * :core:datastore —— 即依赖倒置原则里的「客户端定义抽象，实现由外部注入」。
 */
fun interface EqualizerConfigProvider {

    /** 读取当前生效的均衡器配置。 */
    fun current(): EqualizerConfig
}
