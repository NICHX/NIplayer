package com.nichx.niplayer.metadata.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 解析器语料测试。
 *
 * 语料在 `src/test/resources/parse_cases.txt`，每行 6 列用 `|` 分隔：
 * `fileName|parentDir|grandParentDir|title|artist|trackNo`，空值写 `-`。
 *
 * 一次跑完整份语料并汇总全部失败项（而非逐条参数化），便于直接看出
 * 「这一版准确率多少、错在哪些格式」—— 这正是重做前最缺的反馈：
 * 旧 `parseTitleArtist()` 无法单测，改一个正则只能装到手机上肉眼看。
 */
class ParseFileNameTest {

    @Test
    fun `语料全部解析正确`() {
        val cases = readCases()
        assertTrue("语料为空，检查 parse_cases.txt", cases.isNotEmpty())

        val failures = cases.mapNotNull(::diffOf)
        val passed = cases.size - failures.size
        val accuracy = String.format(Locale.ROOT, "%.1f", passed * 100.0 / cases.size)

        assertTrue(
            "准确率 $accuracy%（$passed/${cases.size}），失败项：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `数字开头的歌名不被当作音轨号`() {
        // `500 Miles` 若被解析成音轨 500 / 歌名 Miles 就是明显错误，
        // 故 TRACK_NO_REGEX 要求数字后跟明确的 - . _ 或括号，不接受纯空格。
        val parsed = parseFileName("500 Miles.mp3")
        assertEquals("500 Miles", parsed.title)
        assertNull(parsed.trackNo)
    }

    @Test
    fun `无空格破折号只在含中文时切分`() {
        // `X-Ray` 不能被拆成 X / Ray
        assertNull(parseFileName("X-Ray.mp3").artist)
        assertEquals("X-Ray", parseFileName("X-Ray.mp3").title)
        assertEquals("周杰伦", parseFileName("周杰伦-稻香.mp3").artist)
    }

    @Test
    fun `通用目录名不当作专辑或歌手`() {
        val parsed = parseFileName("稻香.mp3", parentDir = "Music", grandParentDir = "sdcard")
        assertEquals("稻香", parsed.title)
        assertNull(parsed.album)
        assertNull(parsed.artist)
    }

    @Test
    fun `目录线索在文件名无歌手时生效`() {
        val parsed = parseFileName("以父之名.mp3", parentDir = "叶惠美", grandParentDir = "周杰伦")
        assertEquals("以父之名", parsed.title)
        assertEquals("叶惠美", parsed.album)
        assertEquals("周杰伦", parsed.artist)
    }

    @Test
    fun `版本标记被剥离且不误伤歌手括号`() {
        assertEquals("Live", parseFileName("稻香 (Live).flac").version)
        assertEquals("稻香", parseFileName("稻香 (Live).flac").title)
        // `[周杰伦]` 是歌手，不是版本标记，必须保留
        assertNull(parseFileName("[周杰伦]稻香.mp3").version)
        assertEquals("周杰伦", parseFileName("[周杰伦]稻香.mp3").artist)
    }

    private fun diffOf(case: Case): String? {
        val actual = parseFileName(case.fileName, case.parentDir, case.grandParentDir)
        val actualTrackNo = actual.trackNo?.toString().orEmpty()
        val problems = buildList {
            if (actual.title != case.title) add("title 期望「${case.title}」实际「${actual.title}」")
            if (actual.artist.orEmpty() != case.artist) {
                add("artist 期望「${case.artist}」实际「${actual.artist.orEmpty()}」")
            }
            if (actualTrackNo != case.trackNo) {
                add("trackNo 期望「${case.trackNo}」实际「$actualTrackNo」")
            }
        }
        if (problems.isEmpty()) return null
        return "「${case.fileName}」(父=${case.parentDir} 祖=${case.grandParentDir}) → " +
            problems.joinToString("；")
    }

    private fun readCases(): List<Case> {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(RESOURCE)) {
            "缺少语料文件 $RESOURCE"
        }
        return stream.bufferedReader().useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map(::toCase)
                .toList()
        }
    }

    private fun toCase(line: String): Case {
        val cols = line.split('|')
        require(cols.size == COLUMN_COUNT) { "语料列数应为 $COLUMN_COUNT，实际 ${cols.size}：$line" }
        return Case(
            fileName = cols[0],
            parentDir = cols[1].orNullIfDash(),
            grandParentDir = cols[2].orNullIfDash(),
            title = cols[3],
            artist = cols[4].orNullIfDash().orEmpty(),
            trackNo = cols[5].orNullIfDash().orEmpty(),
        )
    }

    private fun String.orNullIfDash(): String? = takeIf { it != DASH }

    private data class Case(
        val fileName: String,
        val parentDir: String?,
        val grandParentDir: String?,
        val title: String,
        val artist: String,
        val trackNo: String,
    )

    private companion object {
        const val RESOURCE = "parse_cases.txt"
        const val COLUMN_COUNT = 6
        const val DASH = "-"
    }
}
