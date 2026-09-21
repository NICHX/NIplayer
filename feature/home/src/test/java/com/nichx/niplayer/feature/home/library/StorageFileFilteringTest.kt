package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.datastore.SortConfig
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.StorageFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件浏览「过滤 + 排序」的回归测试。
 *
 * 被测函数 [filterAndSortStorageFiles] 原本是 `StorageFileViewModel`（2787 行）里的
 * `private fun applyFilterAndSort`，本轮为**可测性**提为同包顶层 `internal` 函数 ——
 * 这是 A 型巨石类拆解的正确第一步：**先抽纯逻辑（低风险、可测），再考虑抽有状态协作类**。
 *
 * 逻辑逐字搬迁，仅把两处隐式读取（`FileBrowserSettings.sortFlow.value`、
 * `folderMediaVerdicts`）改为显式参数，语义不变。
 */
class StorageFileFilteringTest {

    private fun file(
        name: String,
        isDirectory: Boolean = false,
        hidden: Boolean = false,
        path: String = "/root/$name",
    ): StorageFile = object : AbstractStorageFile(
        path = path,
        name = name,
        isDirectory = isDirectory,
        isHidden = hidden,
    ) {}

    private fun config(
        sortBy: FileBrowserSettings.SortBy = FileBrowserSettings.SortBy.NAME,
        ascending: Boolean = true,
        showOnlyMediaFiles: Boolean = false,
        showHiddenFiles: Boolean = false,
        hideThumbFolder: Boolean = true,
        hideNoMediaFolders: Boolean = false,
        mediaFilter: FileBrowserSettings.MediaFilter = FileBrowserSettings.MediaFilter.ALL,
    ) = SortConfig(
        sortBy = sortBy,
        ascending = ascending,
        showOnlyMediaFiles = showOnlyMediaFiles,
        showHiddenFiles = showHiddenFiles,
        hideThumbFolder = hideThumbFolder,
        hideNoMediaFolders = hideNoMediaFolders,
        mediaFilter = mediaFilter,
    )

    private fun names(
        files: List<StorageFile>,
        cfg: SortConfig = config(),
        verdicts: Map<String, Boolean> = emptyMap(),
    ) = filterAndSortStorageFiles(files, cfg, verdicts).map { it.name }

    // ── 第 1 级：.thumb 缩略图目录 ─────────────────────────────────────

    @Test
    fun `默认隐藏应用生成的 thumb 目录`() {
        val files = listOf(file("a.mp4"), file(".thumb", isDirectory = true))
        assertEquals(listOf("a.mp4"), names(files))
    }

    @Test
    fun `开启显示隐藏文件后 thumb 目录仍被过滤`() {
        // 这是关键语义：.thumb 由**独立开关**控制，不受 showHiddenFiles 影响
        // 排序注意：自然排序按字符比较，'.'(0x2E) < 'a'(0x61)，故 .nomedia 排在 a.mp4 前
        val files = listOf(file("a.mp4"), file(".thumb", isDirectory = true), file(".nomedia"))
        assertEquals(listOf(".nomedia", "a.mp4"), names(files, config(showHiddenFiles = true)))
    }

    @Test
    fun `关闭 hideThumbFolder 后 thumb 目录才放行`() {
        val files = listOf(file("a.mp4"), file(".thumb", isDirectory = true))
        assertEquals(
            listOf(".thumb", "a.mp4"),
            names(files, config(showHiddenFiles = true, hideThumbFolder = false)),
        )
    }

    @Test
    fun `thumb 过滤是精确名匹配`() {
        // 只过滤名字恰好为 ".thumb" 的条目，".thumb2" / "my.thumb" 不受影响。
        // 用 showHiddenFiles = true 排除「隐藏文件」那一级的干扰（".thumb2" 以点开头，
        // 默认会被隐藏文件规则吃掉，与本用例要验证的 thumb 规则无关）。
        val files = listOf(file(".thumb2", isDirectory = true), file("my.thumb", isDirectory = true))
        assertEquals(listOf(".thumb2", "my.thumb"), names(files, config(showHiddenFiles = true)))
    }

    // ── 第 2 级：隐藏文件 ──────────────────────────────────────────────

    @Test
    fun `默认过滤点开头与标记为隐藏的条目`() {
        val files = listOf(
            file("visible.mp4"),
            file(".dotfile"),
            file("flagged.mp4", hidden = true),
        )
        assertEquals(listOf("visible.mp4"), names(files))
    }

    @Test
    fun `开启后保留隐藏文件`() {
        val files = listOf(file("visible.mp4"), file(".dotfile"), file("flagged.mp4", hidden = true))
        assertEquals(
            listOf("flagged.mp4", "visible.mp4", ".dotfile").sorted(),
            names(files, config(showHiddenFiles = true)).sorted(),
        )
    }

    // ── 第 3 级：仅显示媒体文件（含 sidecar 缩略图排除） ────────────────

    @Test
    fun `仅媒体文件时保留目录`() {
        val files = listOf(file("dir", isDirectory = true), file("doc.txt"))
        assertEquals(listOf("dir"), names(files, config(showOnlyMediaFiles = true)))
    }

    @Test
    fun `仅媒体文件时保留视频音频图片`() {
        val files = listOf(file("v.mp4"), file("a.mp3"), file("i.jpg"), file("doc.txt"))
        assertEquals(
            listOf("a.mp3", "i.jpg", "v.mp4"),
            names(files, config(showOnlyMediaFiles = true)),
        )
    }

    @Test
    fun `仅媒体文件时排除 sidecar 缩略图与封面`() {
        // BUG-T-m9：`{name}-thumb.jpg` / `{name}-cover.jpg` 扩展名是图片但实为缓存，不应显示
        val files = listOf(
            file("movie.mp4"),
            file("movie-thumb.jpg"),
            file("song-cover.jpeg"),
            file("real.jpg"),
        )
        assertEquals(
            listOf("movie.mp4", "real.jpg"),
            names(files, config(showOnlyMediaFiles = true)),
        )
    }

    @Test
    fun `sidecar 判定不区分大小写`() {
        val files = listOf(file("MOVIE-THUMB.JPG"), file("Song-Cover.JPEG"), file("keep.jpg"))
        assertEquals(listOf("keep.jpg"), names(files, config(showOnlyMediaFiles = true)))
    }

    @Test
    fun `未开启仅媒体文件时 sidecar 照常显示`() {
        // sidecar 排除只挂在「仅媒体文件」这一级上，默认视图不受影响
        val files = listOf(file("movie-thumb.jpg"))
        assertEquals(listOf("movie-thumb.jpg"), names(files))
    }

    // ── 第 4 级：媒体类型过滤 ──────────────────────────────────────────

    @Test
    fun `类型过滤保留目录`() {
        val files = listOf(file("dir", isDirectory = true), file("a.mp3"), file("v.mp4"))
        assertEquals(
            listOf("dir", "v.mp4"),
            names(files, config(mediaFilter = FileBrowserSettings.MediaFilter.VIDEO)),
        )
    }

    @Test
    fun `按视频音频图片分别过滤`() {
        val files = listOf(file("v.mp4"), file("a.mp3"), file("i.png"))
        assertEquals(
            listOf("a.mp3"),
            names(files, config(mediaFilter = FileBrowserSettings.MediaFilter.AUDIO)),
        )
        assertEquals(
            listOf("i.png"),
            names(files, config(mediaFilter = FileBrowserSettings.MediaFilter.IMAGE)),
        )
    }

    @Test
    fun `ALL 不过滤类型`() {
        val files = listOf(file("v.mp4"), file("a.mp3"), file("doc.txt"))
        assertEquals(listOf("a.mp3", "doc.txt", "v.mp4"), names(files))
    }

    // ── 第 5 级：无媒体文件夹（fail-open） ─────────────────────────────

    @Test
    fun `未判定的文件夹保留`() {
        // fail-open：verdicts 里没有该路径 → 视为「可能有媒体」→ 保留
        val files = listOf(file("unknown", isDirectory = true, path = "/root/unknown"))
        assertEquals(listOf("unknown"), names(files, config(hideNoMediaFolders = true)))
    }

    @Test
    fun `明确判为无媒体的文件夹被移除`() {
        val files = listOf(
            file("empty", isDirectory = true, path = "/root/empty"),
            file("hasMedia", isDirectory = true, path = "/root/hasMedia"),
        )
        assertEquals(
            listOf("hasMedia"),
            names(
                files,
                config(hideNoMediaFolders = true),
                mapOf("/root/empty" to false, "/root/hasMedia" to true),
            ),
        )
    }

    @Test
    fun `判定表按去掉尾斜杠的路径匹配`() {
        val files = listOf(file("dir", isDirectory = true, path = "/root/dir/"))
        assertEquals(
            emptyList<String>(),
            names(files, config(hideNoMediaFolders = true), mapOf("/root/dir" to false)),
        )
    }

    @Test
    fun `文件不受无媒体判定影响`() {
        val files = listOf(file("a.mp4", path = "/root/a.mp4"))
        assertEquals(
            listOf("a.mp4"),
            names(files, config(hideNoMediaFolders = true), mapOf("/root/a.mp4" to false)),
        )
    }

    // ── 组合：过滤链顺序 + 排序 ────────────────────────────────────────

    @Test
    fun `过滤之后套用排序且目录仍在前`() {
        val files = listOf(
            file("b.mp4"),
            file("aDir", isDirectory = true),
            file("a.mp4"),
            file(".hidden.mp4"),
        )
        assertEquals(listOf("aDir", "a.mp4", "b.mp4"), names(files))
    }

    @Test
    fun `多级过滤叠加`() {
        val files = listOf(
            file("keep.mp4"),
            file("keep-thumb.jpg"),          // sidecar → 被第 3 级排除
            file(".thumb", isDirectory = true), // 第 1 级排除
            file(".hidden.mp4"),             // 第 2 级排除
            file("song.mp3"),                // 第 4 级（仅视频）排除
        )
        assertEquals(
            listOf("keep.mp4"),
            names(files, config(showOnlyMediaFiles = true, mediaFilter = FileBrowserSettings.MediaFilter.VIDEO)),
        )
    }

    @Test
    fun `空输入返回空`() {
        assertTrue(names(emptyList()).isEmpty())
    }

    @Test
    fun `isMediaFile 与 sidecar 判定可直接调用`() {
        assertTrue(isMediaFile(file("a.mp4")))
        assertTrue(isMediaFile(file("a.mp3")))
        assertTrue(isMediaFile(file("a.png")))
        assertFalse(isMediaFile(file("a.txt")))
        assertFalse(isMediaFile(file("a-thumb.jpg")))
        assertTrue(isSidecarThumbnailFile("x-cover.jpeg"))
        assertFalse(isSidecarThumbnailFile("x-cover.png"))
    }
}
