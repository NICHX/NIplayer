package com.nichx.niplayer.metadata.model

/**
 * 从文件名 + 目录结构解析出的曲目信息（二手数据）。
 *
 * 由 `parseFileName` 产出。纯数据、零 Android 依赖，可直接跑 JVM 单测。
 */
data class ParsedTrack(
    /** 歌名。解析不出时回退为父目录名，保证非空。 */
    val title: String,

    /** 歌手。解析不出为 null。 */
    val artist: String? = null,

    /** 专辑。来自父目录名（父目录不像通用目录时才采用）。 */
    val album: String? = null,

    /** 音轨号，从 `01 - ` / `01.` / `[01]` 等前缀提取。 */
    val trackNo: Int? = null,

    /** 版本标记，如 `Live` / `Remix` / `伴奏` / `Cover`。 */
    val version: String? = null,
)

/**
 * 音频内容类型。
 *
 * 与重做前 `isLikelyAudiobook()` 的区别：那里靠文件名关键词**一票否决**，
 * 这里由 `classify` 基于时长与同目录分布**加权判定**，关键词只是其中一项。
 */
enum class TrackKind {
    /** 歌曲：走在线歌词 / 封面匹配。 */
    MUSIC,

    /** 有声书 / 评书 / 广播剧 / 小说连播：长音频，不匹配歌曲库。 */
    AUDIOBOOK,

    /** 播客 / 脱口秀：同上，但走另一套元数据来源。 */
    PODCAST,

    /** 信息不足无法判定。按 MUSIC 处理但不做激进匹配。 */
    UNKNOWN,
}
