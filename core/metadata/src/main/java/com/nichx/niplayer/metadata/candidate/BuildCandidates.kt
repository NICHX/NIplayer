package com.nichx.niplayer.metadata.candidate

import com.nichx.niplayer.metadata.model.AudioTags
import com.nichx.niplayer.metadata.model.CandidateSource
import com.nichx.niplayer.metadata.model.MatchCandidate
import com.nichx.niplayer.metadata.model.ParsedTrack

/** 内嵌标签候选基础分：一手数据，最高可信。 */
private const val SCORE_ID3 = 100.0

/** 文件名解析候选基础分：二手数据。 */
private const val SCORE_FILENAME = 60.0

/** 目录线索候选基础分：最弱，仅作补充召回。 */
private const val SCORE_DIRECTORY = 30.0

/** 去重键：歌名 + 歌手，忽略大小写与空格。 */
private data class DedupeKey(val title: String, val artist: String)

/**
 * 由内嵌标签与文件名解析结果生成候选列表。
 *
 * 返回结果按得分降序、并按「歌名 + 歌手」归一化去重。
 * [CandidateSource.ONLINE] 不在此产生（需要网络），由调用方拿到在线结果后追加。
 *
 * 多候选是本次重做的核心：把重做前的「盲输歌名歌手」（`ManualMatchLyricsDialog`
 * 只有两个输入框）与「永久拉黑整首歌」（`OnlineMatchBlacklist` 语义为放弃），
 * 替换为「从候选里挑一个」。
 */
fun buildCandidates(tags: AudioTags, parsed: ParsedTrack): List<MatchCandidate> {
    val candidates = mutableListOf<MatchCandidate>()

    val tagTitle = tags.title?.trim().orEmpty()
    val tagArtist = tags.artist?.trim().orEmpty()
    if (tagTitle.isNotEmpty() && tagArtist.isNotEmpty()) {
        candidates += MatchCandidate(
            title = tagTitle,
            artist = tagArtist,
            album = tags.album?.trim()?.takeIf { it.isNotEmpty() },
            score = SCORE_ID3,
            source = CandidateSource.ID3,
        )
    }

    val parsedTitle = parsed.title.trim()
    val parsedArtist = parsed.artist?.trim().orEmpty()
    if (parsedTitle.isNotEmpty()) {
        candidates += MatchCandidate(
            title = parsedTitle,
            artist = parsedArtist,
            album = parsed.album,
            score = SCORE_FILENAME,
            source = CandidateSource.FILENAME,
        )
    }

    // 目录线索：文件名里没歌手，但父目录名很可能是专辑 / 歌手，用它再试一次。
    // 例：`/Music/叶惠美/以父之名.mp3` → 用「叶惠美 + 以父之名」查询。
    val dirHint = parsed.album?.trim()
    if (parsedArtist.isEmpty() && parsedTitle.isNotEmpty() && !dirHint.isNullOrEmpty()) {
        candidates += MatchCandidate(
            title = parsedTitle,
            artist = dirHint,
            album = dirHint,
            score = SCORE_DIRECTORY,
            source = CandidateSource.DIRECTORY,
        )
    }

    return candidates
        .distinctBy(::dedupeKey)
        .sortedByDescending { it.score }
}

private fun dedupeKey(candidate: MatchCandidate): DedupeKey = DedupeKey(
    title = candidate.title.lowercase().replace(" ", ""),
    artist = candidate.artist.lowercase().replace(" ", ""),
)
