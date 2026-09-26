package com.nichx.niplayer.metadata.classify

import com.nichx.niplayer.metadata.model.ParsedTrack
import com.nichx.niplayer.metadata.model.TrackFacts
import com.nichx.niplayer.metadata.model.TrackKind

/** 长音频阈值：20 分钟。超过即高度疑似有声书单集。 */
private const val LONG_DURATION_MS = 20L * 60 * 1000

/** 中等音频阈值：10 分钟。 */
private const val MEDIUM_DURATION_MS = 10L * 60 * 1000

/** 短音频阈值：8 分钟。低于此值倾向歌曲。 */
private const val SHORT_DURATION_MS = 8L * 60 * 1000

/** 同目录文件数达到此值，才认为「一整部剧 / 一张专辑」。 */
private const val MANY_SIBLINGS = 5

/** 判定为有声书的最低得分。 */
private const val AUDIOBOOK_THRESHOLD = 30

private val STRONG_AUDIOBOOK_KEYWORDS = listOf(
    "有声书", "有声小说", "audiobook", "audio book", "评书", "相声",
    "广播剧", "小说连播", "朗读",
)

private val PODCAST_KEYWORDS = listOf("播客", "podcast", "脱口秀", "talkshow", "talk show")

private val CHAPTER_REGEX = Regex(
    """第[0-9零一二两三四五六七八九十百千]*[章节回集卷话课部]""" +
        """|\b(?:chapter|episode|season|ep|part)\s*\d+""",
    RegexOption.IGNORE_CASE,
)

/**
 * 判定音频内容类型。
 *
 * 与重做前 `isLikelyAudiobook()`（`AudioPlaybackManager.kt:413`）的根本区别：
 * 那里是**一票否决** —— 命中关键词就完全跳过在线匹配，导致双向误判：
 * - 漏拦：`红楼梦 - 第01回.mp3` 因文件名含 `" - "` 被 `return !fileName.contains(" - ")` 放行
 * - 误拦：文件名含「小说」二字的正常歌曲被直接拦掉
 *
 * 这里改为**加权打分**，且权重按可靠性排序：时长与同目录分布（可测量的事实）
 * 权重远高于文件名关键词（脆弱信号）。关键词只作加权项，不作唯一依据。
 */
fun classify(facts: TrackFacts, parsed: ParsedTrack): TrackKind {
    val haystack = listOfNotNull(parsed.title, parsed.artist, parsed.album).joinToString(" ")
    val lower = haystack.lowercase()

    val hasStrongKeyword = STRONG_AUDIOBOOK_KEYWORDS.any { lower.contains(it) }
    val isPodcast = PODCAST_KEYWORDS.any { lower.contains(it) }
    val hasChapter = CHAPTER_REGEX.containsMatchIn(haystack)

    val duration = facts.durationMs
    val median = facts.medianSiblingDurationMs
    val manySiblings = facts.siblingCount >= MANY_SIBLINGS

    var score = 0
    if (duration >= LONG_DURATION_MS) {
        score += 60
    } else if (duration >= MEDIUM_DURATION_MS) {
        score += 20
    }
    if (duration in 1..SHORT_DURATION_MS) score -= 50

    if (manySiblings && median >= MEDIUM_DURATION_MS) score += 50
    if (manySiblings && median in 1..SHORT_DURATION_MS) score -= 30

    if (hasStrongKeyword) score += 30
    if (isPodcast) score += 30
    if (hasChapter) score += if (manySiblings) 30 else 15

    return when {
        score < AUDIOBOOK_THRESHOLD -> TrackKind.MUSIC
        isPodcast -> TrackKind.PODCAST
        else -> TrackKind.AUDIOBOOK
    }
}
