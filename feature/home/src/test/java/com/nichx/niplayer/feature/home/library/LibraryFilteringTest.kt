package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 媒体库类型过滤的回归测试（**`:feature:home` 首批测试之一**）。
 *
 * [filterByType] 是纯函数，也是后续拆分 `LibraryScreen` / `StorageFileViewModel` 时的特征测试。
 */
class LibraryFilteringTest {

    private fun lib(name: String, type: MediaType) = MediaLibraryEntity(
        displayName = name,
        url = "https://example.invalid/$name",
        mediaType = type,
    )

    private val all = listOf(
        lib("local", MediaType.LOCAL_STORAGE),
        lib("device", MediaType.EXTERNAL_STORAGE),
        lib("smb", MediaType.SMB_SERVER),
        lib("dav", MediaType.WEBDAV_SERVER),
        lib("quick", MediaType.QUICK_ACCESS),
        lib("other", MediaType.OTHER_STORAGE),
    )

    private fun names(filter: LibraryFilter) =
        filterByType(filter, all).map { it.displayName }

    @Test
    fun `ALL 原样返回且不复制列表`() {
        // 刻意钉住：ALL 直接返回入参本身（不是副本），调用方若改返回列表会影响入参
        assertSame(all, filterByType(LibraryFilter.ALL, all))
    }

    @Test
    fun `LOCAL 同时匹配内置存储与外部存储`() {
        // 这是本函数最容易被改错的一处：LOCAL 是「两类」而不是单类
        assertEquals(listOf("local", "device"), names(LibraryFilter.LOCAL))
    }

    @Test
    fun `SMB 只匹配 SMB`() {
        assertEquals(listOf("smb"), names(LibraryFilter.SMB))
    }

    @Test
    fun `WEBDAV 只匹配 WebDAV`() {
        assertEquals(listOf("dav"), names(LibraryFilter.WEBDAV))
    }

    @Test
    fun `快速访问与其它类型不被任何具名过滤器收录`() {
        // 观察：QUICK_ACCESS / OTHER_STORAGE 只在 ALL 里出现 —— 具名过滤器不覆盖它们。
        // 此处钉住现状；若产品上要覆盖，属行为变更，需显式确认。
        val covered = LibraryFilter.entries
            .filter { it != LibraryFilter.ALL }
            .flatMap { names(it) }
            .toSet()
        assertTrue("quick" !in covered)
        assertTrue("other" !in covered)
    }

    @Test
    fun `保持入参顺序`() {
        val shuffled = listOf(
            lib("z", MediaType.SMB_SERVER),
            lib("a", MediaType.SMB_SERVER),
        )
        assertEquals(listOf("z", "a"), filterByType(LibraryFilter.SMB, shuffled).map { it.displayName })
    }

    @Test
    fun `空列表返回空`() {
        LibraryFilter.entries.forEach { filter ->
            assertEquals(emptyList<MediaLibraryEntity>(), filterByType(filter, emptyList()))
        }
    }
}
