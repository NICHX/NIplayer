package com.nichx.niplayer.metadata.model

/** 候选来源，决定基础分与展示标签。 */
enum class CandidateSource {
    /** 内嵌标签：一手数据，最高可信。 */
    ID3,

    /** 文件名解析结果。 */
    FILENAME,

    /** 父目录推断（专辑 / 歌手）。 */
    DIRECTORY,

    /** 在线搜索返回的结果。 */
    ONLINE,
}

/**
 * 一个待用户确认的匹配候选。
 *
 * 多候选是本次重做的核心：把重做前的「盲输歌名歌手」（`ManualMatchLyricsDialog`
 * 只有两个输入框）与「永久拉黑整首歌」（`OnlineMatchBlacklist` 语义为放弃），
 * 替换为「从候选里挑一个」。
 */
data class MatchCandidate(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,

    /** 综合得分，越大越可信。 */
    val score: Double = 0.0,

    val source: CandidateSource,
)
