package com.nichx.niplayer.metadata.model

/**
 * 音频文件的内嵌标签（ID3 / Vorbis Comment / MP4 atom）。
 *
 * 全部字段可空：标签缺失是常态，调用方需回退到文件名解析。
 * 这是**一手数据**，可信度高于 [ParsedTrack]（文件名是二手数据）。
 *
 * 背景：重做前全仓未读取任何 `METADATA_KEY_TITLE` / `ARTIST` / `ALBUM`，
 * `MediaMetadataRetriever` 只被用来取 `embeddedPicture`，导致文件里明明有准确
 * 的歌手歌名，匹配却只能猜文件名。
 */
data class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNo: Int? = null,
    val year: Int? = null,

    /** 内嵌歌词（ID3 USLT / Vorbis LYRICS 帧）。命中则无需联网。 */
    val embeddedLyrics: String? = null,

    /** 是否含内嵌封面（ID3 APIC 帧）。仅标记存在性，不携带字节。 */
    val hasEmbeddedPicture: Boolean = false,
) {
    /**
     * 标签是否可直接用于匹配。
     *
     * 要求 title 与 artist 同时非空白：只有其一说明标签不完整，
     * 与其用半截标签查询，不如回退到文件名解析。
     */
    val isUsable: Boolean
        get() = !title.isNullOrBlank() && !artist.isNullOrBlank()
}
