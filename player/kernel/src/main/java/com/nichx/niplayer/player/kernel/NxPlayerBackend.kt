package com.nichx.niplayer.player.kernel

/**
 * 可插拔播放内核的注册契约（多内核接入骨架）。
 *
 * 在 [com.nichx.niplayer.player.kernel.NxPlayer] 之上叠加**内核能力声明**，使多个内核
 * （media3 / mpv / media3+软解）能以 Hilt `@IntoSet` 多绑定方式注册，由后续的能力解析器
 * 按 `supports` + [backendPriority] 选出最佳内核或形成 fallback 链。
 *
 * 设计约束：
 * - **扩展 [NxPlayer]**：任意 backend 本身就是一个 NxPlayer，调用方只面向 NxPlayer 抽象。
 * - **默认兜底语义**：[Media3](#) 内核 [`supports`] 恒返回 true，作为末位兜底；
 *   不得有其它内核在 media3 缺席时被当作兜底。
 * - **媒介无关**：当前 [NxMediaSource] 仅含传输协议信息，暂无法做编解码/字幕能力探测；
 *   等 NxMediaSource 补充格式探测字段后再完善 [supports] 实判。
 *
 * @param backendId 唯一标识，如 "media3" / "mpv" / "media3-soft"。
 * @param backendPriority 优先级，数值大优先被解析器选中；media3 兜底优先级取默认值。
 * @param backendVariantOf 若该内核是某个既有内核的增量变体（如 media3 软解扩展），返回其
 *        backendId；独立内核（media3 / mpv）为 null。
 */
interface NxPlayerBackend : NxPlayer {

    /** 唯一标识。 */
    val backendId: String

    /** 是否可处理该媒体源；media3 兜底内核恒返回 true。 */
    fun supports(source: NxMediaSource): Boolean

    /** 优先级，数值大优先。 */
    val backendPriority: Int

    /** 所属变体来源内核 id；独立内核为 null。 */
    val backendVariantOf: String?
}

/** 播放内核自动选择模式（[com.nichx.niplayer.datastore.PlayerSettings.playerBackend] 的默认值）。 */
const val NX_BACKEND_AUTO: String = "auto"