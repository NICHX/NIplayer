package com.nichx.niplayer.storage.impl

import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `WebDavStorage` 的 URL/路径构造回归测试。
 *
 * 为什么测这两个函数：WebDAV 的路径处理是本项目**出过事故**的区域（同类问题在 SMB 上
 * 曾导致连播列表恒空 —— 见 `SmbAddressing` 的修复）。这里 [WebDavStorage.computeRelativePath]
 * 把服务端返回的 `href` 归一成相对路径，[WebDavStorage.resourceUrl] 再把相对路径拼回 URL，
 * 两者必须互为逆运算，否则会出现「列得出的文件打不开」。
 *
 * 两个函数本轮从 `private` 提为 `internal`（仅可见性放宽，实现未动）以便直接测试。
 */
class WebDavStorageUrlTest {

    private val base = "https://dav.example.com/remote.php/dav/files/alice/"

    private fun storage(url: String = base) = WebDavStorage(
        library = MediaLibraryEntity(
            displayName = "dav",
            url = url,
            mediaType = MediaType.WEBDAV_SERVER,
        ),
        sharedHttpClient = OkHttpClient(),
    )

    // ── resourceUrl：相对路径 -> 绝对 URL ──────────────────────────────

    @Test
    fun `空路径返回基址本身`() {
        val s = storage()
        assertEquals(base, s.resourceUrl("").toString())
    }

    @Test
    fun `多级路径逐段拼到基址之后`() {
        val s = storage()
        assertEquals("${base}a.mp4", s.resourceUrl("a.mp4").toString())
        assertEquals("${base}dir/b.mp4", s.resourceUrl("dir/b.mp4").toString())
    }

    @Test
    fun `首尾斜杠被归一`() {
        val s = storage()
        assertEquals("${base}dir/b.mp4", s.resourceUrl("/dir/b.mp4").toString())
        assertEquals("${base}dir/b.mp4", s.resourceUrl("dir/b.mp4/").toString())
        assertEquals("${base}dir/b.mp4", s.resourceUrl("/dir/b.mp4/").toString())
    }

    @Test
    fun `目录路径补尾随斜杠`() {
        val s = storage()
        assertEquals("${base}dir/", s.resourceUrl("dir", isDirectory = true).toString())
        // 已有尾随斜杠时不重复添加
        assertEquals("${base}dir/", s.resourceUrl("dir/", isDirectory = true).toString())
    }

    @Test
    fun `根目录不加多余尾斜杠`() {
        // trimmed 为空时不补斜杠，否则会得到 "//"
        val s = storage()
        assertEquals(base, s.resourceUrl("", isDirectory = true).toString())
    }

    @Test
    fun `路径段中的空格被编码`() {
        val s = storage()
        assertEquals("${base}my%20file.mp4", s.resourceUrl("my file.mp4").toString())
    }

    // ── computeRelativePath：服务端 href -> 相对路径 ───────────────────

    @Test
    fun `去掉基址前缀得到相对路径`() {
        val s = storage()
        assertEquals("a.mp4", s.computeRelativePath("/remote.php/dav/files/alice/a.mp4"))
        assertEquals("dir/b.mp4", s.computeRelativePath("/remote.php/dav/files/alice/dir/b.mp4"))
    }

    @Test
    fun `基址自身归为空串`() {
        // 调用方（parseResponseEntry）会因此跳过该条目
        val s = storage()
        assertEquals("", s.computeRelativePath("/remote.php/dav/files/alice/"))
    }

    @Test
    fun `绝对 URL 的 href 取其路径部分`() {
        val s = storage()
        assertEquals("x.mp4", s.computeRelativePath("$base" + "x.mp4"))
        assertEquals(
            "dir/y.mp4",
            s.computeRelativePath("https://dav.example.com/remote.php/dav/files/alice/dir/y.mp4"),
        )
    }

    @Test
    fun `不在基址下的 href 退化为去前导斜杠`() {
        val s = storage()
        assertEquals("other/y.mp4", s.computeRelativePath("/other/y.mp4"))
        assertEquals("y.mp4", s.computeRelativePath("y.mp4"))
    }

    @Test
    fun `基址带前缀相似的兄弟路径不被误判`() {
        // "/…/alice2/" 不以 "/…/alice/" 开头，必须走退化分支而不是去掉前缀
        val s = storage()
        assertEquals(
            "remote.php/dav/files/alice2/z.mp4",
            s.computeRelativePath("/remote.php/dav/files/alice2/z.mp4"),
        )
    }

    @Test
    fun `编码过的 href 保留编码形式`() {
        // 实现用的是 encodedPath，故 %20 原样保留（本函数不做解码）。
        // 解码发生在调用方 parseResponseEntry 里（Uri.decode + NFC 归一化后才写入 StorageFile.path），
        // 见下一条用例。
        val s = storage()
        assertEquals(
            "my%20file.mp4",
            s.computeRelativePath("/remote.php/dav/files/alice/my%20file.mp4"),
        )
    }

    @Test
    fun `解码后的路径再拼 URL 恰好编码一次`() {
        // 完整链路（两步必须配对，否则「列得出的文件打不开」）：
        //   服务端 href  "/…/alice/my%20file.mp4"
        //     -> computeRelativePath  "my%20file.mp4"          （编码形式）
        //     -> parseResponseEntry   Uri.decode + NFC 归一化  -> StorageFile.path = "my file.mp4"
        //     -> resourceUrl          addPathSegment 重新编码   -> "/…/alice/my%20file.mp4"  ✓
        //
        // 注意：若跳过中间的 decode，直接把 computeRelativePath 的结果交给 resourceUrl，
        // 会得到 %2520（二次编码）。因此**这两步不能被单独"简化"掉一步**。
        val s = storage()
        val encodedHref = "/remote.php/dav/files/alice/my%20file.mp4"
        val decodedPath = "my file.mp4" // = Uri.decode(computeRelativePath(encodedHref))

        assertEquals("my%20file.mp4", s.computeRelativePath(encodedHref))
        assertEquals("${base}my%20file.mp4", s.resourceUrl(decodedPath).toString())
    }

    @Test
    fun `非 ASCII 路径按 UTF-8 编码`() {
        // 中文文件名：解码后是原字符，拼 URL 时按 UTF-8 百分号编码
        val s = storage()
        assertEquals("${base}%E4%B8%AD%E6%96%87.mp4", s.resourceUrl("中文.mp4").toString())
    }
}
