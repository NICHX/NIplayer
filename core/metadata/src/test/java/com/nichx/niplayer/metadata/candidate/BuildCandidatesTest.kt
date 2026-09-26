package com.nichx.niplayer.metadata.candidate

import com.nichx.niplayer.metadata.model.AudioTags
import com.nichx.niplayer.metadata.model.CandidateSource
import com.nichx.niplayer.metadata.model.ParsedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选生成测试。
 *
 * 多候选是本次重做的核心：把重做前的「盲输歌名歌手」与「永久拉黑整首歌」，
 * 替换为「从候选里挑一个」。这里钉住的是候选的**来源优先级**与**去重规则**。
 */
class BuildCandidatesTest {

    @Test
    fun `内嵌标签候选优先于文件名候选`() {
        val tags = AudioTags(title = "稻香", artist = "周杰伦", album = "魔杰座")
        val parsed = ParsedTrack(title = "01 周杰伦 - 稻香", artist = null)

        val result = buildCandidates(tags, parsed)

        val first = result.first()
        assertEquals(CandidateSource.ID3, first.source)
        assertEquals("稻香", first.title)
        assertEquals("周杰伦", first.artist)
        assertEquals("魔杰座", first.album)
        assertTrue("ID3 得分应高于文件名候选", first.score > result.last().score)
    }

    @Test
    fun `无标签时退化为文件名候选`() {
        val result = buildCandidates(AudioTags(), ParsedTrack(title = "稻香", artist = "周杰伦"))

        assertEquals(1, result.size)
        assertEquals(CandidateSource.FILENAME, result.single().source)
    }

    @Test
    fun `歌名与歌手都相同时去重`() {
        val tags = AudioTags(title = "稻香", artist = "周杰伦")
        val parsed = ParsedTrack(title = "稻香", artist = "周杰伦")

        assertEquals(1, buildCandidates(tags, parsed).size)
    }

    @Test
    fun `标签缺 artist 时不产生 ID3 候选`() {
        val tags = AudioTags(title = "稻香", artist = null)

        val result = buildCandidates(tags, ParsedTrack(title = "稻香"))

        assertTrue("半截标签不应产出候选", result.none { it.source == CandidateSource.ID3 })
    }

    @Test
    fun `无歌手但有专辑目录时补一条目录线索候选`() {
        val parsed = ParsedTrack(title = "以父之名", artist = null, album = "叶惠美")

        val result = buildCandidates(AudioTags(), parsed)

        assertEquals(2, result.size)
        val directoryCandidate = result.single { it.source == CandidateSource.DIRECTORY }
        assertEquals("以父之名", directoryCandidate.title)
        assertEquals("叶惠美", directoryCandidate.artist)
        assertTrue("目录线索得分最低", directoryCandidate.score < result.first().score)
    }

    @Test
    fun `已有歌手时不再产生目录线索候选`() {
        val parsed = ParsedTrack(title = "稻香", artist = "周杰伦", album = "魔杰座")

        val result = buildCandidates(AudioTags(), parsed)

        assertTrue(result.none { it.source == CandidateSource.DIRECTORY })
    }
}
