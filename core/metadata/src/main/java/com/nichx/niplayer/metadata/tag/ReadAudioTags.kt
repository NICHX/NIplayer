package com.nichx.niplayer.metadata.tag

import android.media.MediaMetadataRetriever
import com.nichx.niplayer.metadata.model.AudioTags

/**
 * 从 [MediaMetadataRetriever] 取到的原始字符串值。
 *
 * 与 [AudioTags] 分开是为了让「字符串 → 结构化」成为**可单测的纯函数**：
 * [MediaMetadataRetriever] 依赖 Android 框架、无法在 JVM 单测里构造，
 * 而 [toAudioTags] 可以。
 */
internal data class RawTagValues(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNo: String? = null,
    val year: String? = null,
    val lyrics: String? = null,
    val hasEmbeddedPicture: Boolean = false,
)

/**
 * 系统对缺失标签的常见占位符。按缺失处理，避免把它们当成真实歌名 / 专辑名
 * 送去做在线查询（`MusicMetadataService` 里原本只硬编码判了 `[Unknown Album]` 一个）。
 */
private val PLACEHOLDER_VALUES = setOf(
    "<unknown>",
    "[unknown]",
    "[unknown album]",
    "[unknown artist]",
    "unknown artist",
)

/**
 * 原始字符串 → [AudioTags] 的纯转换。
 *
 * - trim 后为空一律转 null
 * - 已知占位符转 null
 * - 数字字段解析失败转 null，不抛异常
 */
internal fun toAudioTags(raw: RawTagValues): AudioTags = AudioTags(
    title = raw.title.normalizeTagValue(),
    artist = raw.artist.normalizeTagValue(),
    album = raw.album.normalizeTagValue(),
    albumArtist = raw.albumArtist.normalizeTagValue(),
    trackNo = raw.trackNo.parseTrackNo(),
    year = raw.year.parseYear(),
    embeddedLyrics = raw.lyrics.normalizeTagValue(),
    hasEmbeddedPicture = raw.hasEmbeddedPicture,
)

private fun String?.normalizeTagValue(): String? {
    val trimmed = this?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.lowercase() in PLACEHOLDER_VALUES) return null
    return trimmed
}

/** 音轨号可能是 `3/12` 形式，取 `/` 前的部分。 */
private fun String?.parseTrackNo(): Int? =
    this?.trim()?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }

/** 年份可能是 `2023` 或 `2023-05-01`，取前 4 位数字并做合理性检查。 */
private fun String?.parseYear(): Int? {
    val digits = this?.trim().orEmpty().takeWhile { it.isDigit() }.take(YEAR_DIGITS)
    return digits.toIntOrNull()?.takeIf { it in MIN_YEAR..MAX_YEAR }
}

private const val YEAR_DIGITS = 4
private const val MIN_YEAR = 1000
private const val MAX_YEAR = 2999

/**
 * 从**已配置好数据源**的 [MediaMetadataRetriever] 读取标签。
 *
 * 设计要点：若调用方已为其它目的（如提取内嵌封面）打开了 retriever，
 * **应复用同一次打开** —— 对 SMB / WebDAV 尤其重要，二次打开意味着二次网络读取。
 * 本函数**不**创建、也**不**释放 retriever。
 *
 * @param hasEmbeddedPicture 是否含内嵌封面。由调用方告知，因为读取
 *   [MediaMetadataRetriever.embeddedPicture] 会把整个图片数据拉进内存，
 *   而调用方往往已经读过一次，不该在这里重复读。
 */
fun readAudioTagsFrom(
    retriever: MediaMetadataRetriever,
    hasEmbeddedPicture: Boolean = false,
): AudioTags = toAudioTags(
    RawTagValues(
        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
        albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
        trackNo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
        year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
        // 内嵌歌词在这里读不到：MediaMetadataRetriever 没有 METADATA_KEY_LYRICS
        // （框架不暴露歌词帧）。要拿 USLT / LYRICS 帧需自行解析容器格式，
        // 故本阶段 lyrics 恒为 null，AudioTags.embeddedLyrics 保留作为后续扩展点。
        hasEmbeddedPicture = hasEmbeddedPicture,
    ),
)

/**
 * 读取标签，数据源由调用方通过 [configure] 提供。
 *
 * `setDataSource` 有 path / uri / url+headers / [android.media.MediaDataSource]
 * 多种形态，无法在库内穷举，故以配置动作的形式交给调用方。
 * retriever 的创建与释放由本函数负责。
 *
 * 配置或读取失败（文件损坏、网络不可达、格式不支持）时返回**空 [AudioTags]**，
 * 不抛异常 —— 标签缺失是常态，调用方应回退到文件名解析。
 */
fun readAudioTags(configure: (MediaMetadataRetriever) -> Unit): AudioTags {
    val retriever = MediaMetadataRetriever()
    return try {
        configure(retriever)
        readAudioTagsFrom(retriever)
    } catch (_: Exception) {
        AudioTags()
    } finally {
        runCatching { retriever.release() }
    }
}

/** 从本地文件路径读取标签。路径不可读时返回空 [AudioTags]。 */
fun readAudioTagsFromPath(path: String): AudioTags = readAudioTags { it.setDataSource(path) }
