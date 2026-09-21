package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.datastore.SortConfig
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.StorageFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件浏览排序的回归测试。
 *
 * **这是 `:feature:home` 的第一个测试**（此前该模块完全没有 `src/test`，即工程化项 E1 的缺口）。
 * 选它作为起点，是因为排序逻辑（[storageFileComparator] / [naturalOrderCompare]）
 * 是该模块里少数**纯函数、无 Android 依赖**的部分 —— 也是后续要把
 * `StorageFileViewModel`（2787 行）拆成协作类时的**特征测试**：先钉住行为，再动结构。
 *
 * 两个函数都是 `internal` 顶层函数，故测试与它们同包。
 */
class StorageFileSortingTest {

    private fun file(
        name: String,
        isDirectory: Boolean = false,
        size: Long = 0L,
        modified: Long = 0L,
    ): StorageFile = object : AbstractStorageFile(
        path = "/root/$name",
        name = name,
        isDirectory = isDirectory,
        length = size,
        lastModified = modified,
    ) {}

    private fun config(
        sortBy: FileBrowserSettings.SortBy = FileBrowserSettings.SortBy.NAME,
        ascending: Boolean = true,
    ) = SortConfig(sortBy = sortBy, ascending = ascending)

    private fun sorted(
        files: List<StorageFile>,
        sortBy: FileBrowserSettings.SortBy = FileBrowserSettings.SortBy.NAME,
        ascending: Boolean = true,
    ): List<String> = files.sortedWith(storageFileComparator(config(sortBy, ascending))).map { it.name }

    // ── naturalOrderCompare：自然排序 ────────────────────────────────────

    @Test
    fun `连续数字按数值比较而非字典序`() {
        // 这是本函数存在的唯一理由：纯字符串比较会把 "10.mp4" 排在 "2.mp4" 前
        assertTrue(naturalOrderCompare("2.mp4", "10.mp4") < 0)
        assertTrue(naturalOrderCompare("10.mp4", "2.mp4") > 0)
        assertTrue(naturalOrderCompare("file2", "file10") < 0)
        assertTrue(naturalOrderCompare("Ep2", "Ep10") < 0)
    }

    @Test
    fun `前导零不影响数值大小`() {
        assertEquals(0, naturalOrderCompare("007", "7"))
        assertEquals(0, naturalOrderCompare("a01", "a1"))
        assertTrue(naturalOrderCompare("a007", "a8") < 0)
    }

    @Test
    fun `非数字部分不区分大小写`() {
        assertEquals(0, naturalOrderCompare("A.mp4", "a.mp4"))
        assertEquals(0, naturalOrderCompare("FILE.MP4", "file.mp4"))
        assertTrue(naturalOrderCompare("Apple", "banana") < 0)
    }

    @Test
    fun `前缀短的排前面`() {
        assertTrue(naturalOrderCompare("a", "ab") < 0)
        assertTrue(naturalOrderCompare("ab", "a") > 0)
        assertEquals(0, naturalOrderCompare("same", "same"))
        assertTrue(naturalOrderCompare("", "x") < 0)
    }

    @Test
    fun `数字段之后继续比较后续字符`() {
        assertTrue(naturalOrderCompare("a1b", "a1c") < 0)
        assertTrue(naturalOrderCompare("a1z", "a2a") < 0)
        // 位数不同时按位数判大小，不再逐位比较
        assertTrue(naturalOrderCompare("a9z", "a10a") < 0)
    }

    @Test
    fun `比较结果与参数顺序反对称`() {
        val samples = listOf("2.mp4", "10.mp4", "a", "A", "007", "7", "", "z9", "z10")
        for (x in samples) {
            for (y in samples) {
                assertEquals(
                    "反对称性失败: $x vs $y",
                    Integer.signum(naturalOrderCompare(x, y)),
                    -Integer.signum(naturalOrderCompare(y, x)),
                )
            }
        }
    }

    // ── storageFileComparator：目录优先 + 四种排序键 ────────────────────

    @Test
    fun `目录始终排在最前`() {
        val files = listOf(file("b.mp4"), file("aDir", isDirectory = true), file("a.mp4"))
        assertEquals(listOf("aDir", "a.mp4", "b.mp4"), sorted(files))
    }

    @Test
    fun `降序时目录仍然在最前`() {
        // 目录优先是「分组」，不参与升降序 —— 这是刻意的语义，别被 reversed() 带偏
        val files = listOf(file("b.mp4"), file("aDir", isDirectory = true), file("a.mp4"))
        assertEquals(listOf("aDir", "b.mp4", "a.mp4"), sorted(files, ascending = false))
    }

    @Test
    fun `按名称排序用自然序`() {
        val files = listOf(file("10.mp4"), file("2.mp4"), file("1.mp4"))
        assertEquals(listOf("1.mp4", "2.mp4", "10.mp4"), sorted(files))
    }

    @Test
    fun `按修改时间排序`() {
        val files = listOf(
            file("new.mp4", modified = 300),
            file("old.mp4", modified = 100),
            file("mid.mp4", modified = 200),
        )
        assertEquals(
            listOf("old.mp4", "mid.mp4", "new.mp4"),
            sorted(files, sortBy = FileBrowserSettings.SortBy.MODIFIED),
        )
        assertEquals(
            listOf("new.mp4", "mid.mp4", "old.mp4"),
            sorted(files, sortBy = FileBrowserSettings.SortBy.MODIFIED, ascending = false),
        )
    }

    @Test
    fun `按大小排序`() {
        val files = listOf(
            file("big.mp4", size = 9000),
            file("small.mp4", size = 10),
            file("mid.mp4", size = 500),
        )
        assertEquals(
            listOf("small.mp4", "mid.mp4", "big.mp4"),
            sorted(files, sortBy = FileBrowserSettings.SortBy.SIZE),
        )
    }

    @Test
    fun `按类型排序取扩展名且不区分大小写`() {
        val files = listOf(file("c.MKV"), file("a.mp4"), file("b.AVI"))
        assertEquals(
            listOf("b.AVI", "c.MKV", "a.mp4"),
            sorted(files, sortBy = FileBrowserSettings.SortBy.TYPE),
        )
    }

    @Test
    fun `无扩展名与以点结尾的文件类型为空串`() {
        val files = listOf(file("z.mp4"), file("noext"), file("trailing."))
        // 空扩展名排最前，其余按扩展名
        assertEquals(
            listOf("noext", "trailing.", "z.mp4"),
            sorted(files, sortBy = FileBrowserSettings.SortBy.TYPE),
        )
    }

    @Test
    fun `以点开头的文件其类型为点后的部分`() {
        // ".hidden" 的 lastIndexOf('.') == 0，既不是 -1 也不等于 length-1，故取 substring(1)
        // 这是现有实现的实际行为，此处刻意钉住，避免重构时被「顺手修正」成空扩展名
        val files = listOf(file("zzz.mp4"), file(".hidden"))
        assertEquals(
            listOf(".hidden", "zzz.mp4"),
            sorted(files, sortBy = FileBrowserSettings.SortBy.TYPE),
        )
    }

    @Test
    fun `同扩展名时保持输入顺序`() {
        // 观察：TYPE 排序**只比较扩展名，没有次级排序键**（不像 NAME 那样兜底）。
        // 故同扩展名之间比较结果为 0，靠 Kotlin sortedWith 的稳定性保持输入顺序。
        // 这意味着「按类型」视图里同类型文件的相对顺序取决于底层目录列举顺序 ——
        // 此处刻意钉住现状，重构时若要加「同类型再按名称」的次级键，需先确认是期望行为变更。
        val files = listOf(file("b.mp4"), file("a.mp4"), file("c.mp4"))
        assertEquals(
            listOf("b.mp4", "a.mp4", "c.mp4"),
            sorted(files, sortBy = FileBrowserSettings.SortBy.TYPE),
        )
    }
}
