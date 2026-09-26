package com.nichx.niplayer.metadata.model

/**
 * 判定内容类型所依赖的**可测量事实**。
 *
 * 设计要点：类型判定不再靠文件名关键词（脆弱、易双向误判），
 * 而是靠时长与同目录分布（事实）。文件名关键词只作为加权项，不作唯一依据。
 *
 * 反例（重做前）：`红楼梦 - 第01回.mp3` 因文件名含 `" - "` 被
 * `isLikelyAudiobook()` 放行，拿去搜歌曲封面；而文件名含「小说」二字的正常歌曲被直接拦掉。
 */
data class TrackFacts(
    /** 当前曲目时长（ms）。未知传 0。 */
    val durationMs: Long = 0,

    /** 同目录同类媒体文件数量。默认 1（只有自己）。 */
    val siblingCount: Int = 1,

    /** 同目录各文件时长（ms）。未知项不要放入。 */
    val siblingDurations: List<Long> = emptyList(),
) {
    /**
     * 同目录时长中位数（ms）；无样本时返回 0。
     *
     * 偶数个样本时取偏大的一侧，避免被极短样本拉低。
     */
    val medianSiblingDurationMs: Long
        get() {
            if (siblingDurations.isEmpty()) return 0L
            val sorted = siblingDurations.sorted()
            return sorted[sorted.size / 2]
        }
}
