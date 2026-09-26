package com.nichx.niplayer.metadata.classify

import com.nichx.niplayer.metadata.model.ParsedTrack
import com.nichx.niplayer.metadata.model.TrackFacts
import com.nichx.niplayer.metadata.model.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 内容类型判定测试。
 *
 * 重点是判据的**权重顺序**：时长与同目录分布是可测量的事实，权重远高于文件名关键词。
 * 重做前的 `isLikelyAudiobook()` 是一票否决制（命中关键词即完全跳过匹配），
 * 这里是加权打分制（关键词只是若干加权项之一）。
 */
class ClassifyTest {

    @Test
    fun `普通歌曲判为 MUSIC`() {
        val durations = List(SIBLING_COUNT) { SHORT_TRACK_MS }
        val facts = TrackFacts(
            durationMs = SHORT_TRACK_MS,
            siblingCount = SIBLING_COUNT,
            siblingDurations = durations,
        )
        assertEquals(
            TrackKind.MUSIC,
            classify(facts, ParsedTrack(title = "稻香", artist = "周杰伦")),
        )
    }

    @Test
    fun `单条超长音频判为 AUDIOBOOK`() {
        val facts = TrackFacts(durationMs = LONG_TRACK_MS)
        assertEquals(TrackKind.AUDIOBOOK, classify(facts, ParsedTrack(title = "第一回")))
    }

    @Test
    fun `同目录多集长音频判为 AUDIOBOOK`() {
        val durations = List(SIBLING_COUNT) { MEDIUM_TRACK_MS }
        val facts = TrackFacts(
            durationMs = MEDIUM_TRACK_MS,
            siblingCount = SIBLING_COUNT,
            siblingDurations = durations,
        )
        assertEquals(TrackKind.AUDIOBOOK, classify(facts, ParsedTrack(title = "第一章")))
    }

    @Test
    fun `强关键词命中即倾向有声书`() {
        val facts = TrackFacts(durationMs = LONG_TRACK_MS)
        val parsed = ParsedTrack(title = "明朝那些事儿 有声书 第001集")
        assertEquals(TrackKind.AUDIOBOOK, classify(facts, parsed))
    }

    @Test
    fun `播客关键词判为 PODCAST`() {
        val facts = TrackFacts(durationMs = LONG_TRACK_MS * 2)
        assertEquals(TrackKind.PODCAST, classify(facts, ParsedTrack(title = "某播客 第12期")))
    }

    @Test
    fun `章节标记加同目录多文件判为有声书`() {
        // 这正是旧实现漏拦的形态：文件名含 " - "，于是 return false，
        // 被判为歌曲去搜封面，拿「红楼梦 第01回」搜出毫不相干的东西。
        val durations = List(SIBLING_COUNT) { MEDIUM_TRACK_MS }
        val facts = TrackFacts(
            durationMs = MEDIUM_TRACK_MS,
            siblingCount = SIBLING_COUNT,
            siblingDurations = durations,
        )
        assertEquals(
            TrackKind.AUDIOBOOK,
            classify(facts, ParsedTrack(title = "红楼梦 - 第01回")),
        )
    }

    @Test
    fun `信息不足时不激进判定`() {
        assertEquals(TrackKind.MUSIC, classify(TrackFacts(), ParsedTrack(title = "稻香")))
    }

    @Test
    fun `短音频不会被长关键词误判`() {
        // 关键词与短时长冲突时，时长（事实）权重更高，得分被压到阈值以下。
        // 「小说连播 主题曲」是一首 4 分钟的歌，不该因为有「小说连播」四字就被判为有声书。
        val facts = TrackFacts(durationMs = SHORT_TRACK_MS)
        assertEquals(TrackKind.MUSIC, classify(facts, ParsedTrack(title = "小说连播 主题曲")))
    }

    private companion object {
        const val SHORT_TRACK_MS = 4L * 60 * 1000
        const val MEDIUM_TRACK_MS = 15L * 60 * 1000
        const val LONG_TRACK_MS = 25L * 60 * 1000
        const val SIBLING_COUNT = 20
    }
}
