package com.nichx.niplayer.storage.impl

import org.codelibs.jcifs.smb.context.SingletonContext
import org.codelibs.jcifs.smb.impl.SmbFile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SmbAddressing] 回归测试 —— 缺陷审计 #12（SMB 文件名含 `?` 被截断）的修复护栏。
 *
 * 断言分三类：
 * 1. **等价性**：新寻址（jcifs「父 + 相对名」构造器）与旧的「完整 URL 字符串拼法」在
 *    常规路径下产出的 [SmbFile] 完全一致（URL / UNC / canonical / name 四项）。
 *    这是本次重构「不改变既有行为」的证明 —— 旧拼法的公式在 [legacyUrl] 中复刻。
 * 2. **缺陷修复**：含 `?` 的文件名不再被截断（旧拼法会被截断，测试中同时钉住旧行为作对照）。
 * 3. **已知边界**：相对路径首段含 `:` 时旧拼法可用、新构造器会抛异常，故回退到旧拼法。
 *
 * 全部断言均为离线行为（只构造 [SmbFile]、读 locator，不发起网络请求）。
 */
class SmbAddressingTest {

    private val ctx = SingletonContext.getInstance()

    /**
     * 复刻**已删除**的 `SmbStorage.buildSmbUrl` 公式，作为「旧行为」基准。
     *
     * 保留它的意义：新实现必须与它逐例等价（除含 `?` 的路径外），否则重构就在无测试覆盖的
     * 网络寻址层引入了行为漂移。
     */
    private fun legacyUrl(shareName: String?, rootPrefix: String, path: String): String {
        if (shareName.isNullOrBlank()) {
            val p = path.trim('/')
            return if (p.isEmpty()) "smb://host:445/" else "smb://host:445/$p"
        }
        val share = shareName.trim('/').split("/").first().trim()
        return "smb://host:445/$share/$rootPrefix$path"
    }

    /** 四项寻址结果快照，用于等价性比对。 */
    private fun snapshot(f: SmbFile): String =
        "url=${f.url} unc=${f.uncPath} canon=${f.locator.urlPath} name=${f.name}"

    // ---------------------------------------------------------------- 纯函数

    @Test
    fun `配置了共享路径时共享根与相对路径的拼接规则`() {
        val addr = SmbAddressing("host", 445, "share")
        assertEquals("smb://host:445/share/", addr.shareBaseUrl())
        assertEquals("", addr.relativePath("", ""))
        assertEquals("Movies", addr.relativePath("", "Movies"))
        assertEquals("films/Movies", addr.relativePath("films/", "Movies"))
        assertEquals("smb://host:445/share/films/Movies", addr.buildUrl("films/", "Movies"))
    }

    @Test
    fun `共享路径含子目录时只有首段是共享名`() {
        val addr = SmbAddressing("host", 445, "/share/films/")
        assertEquals("smb://host:445/share/", addr.shareBaseUrl())
        // 前缀由 ensureShare() 计算（"films/"），寻址本身只负责拼接
        assertEquals("films/Movies", addr.relativePath("films/", "Movies"))
    }

    @Test
    fun `未配置共享路径时路径首段即共享名`() {
        val addr = SmbAddressing("host", 445, null)
        assertEquals("smb://host:445/", addr.shareBaseUrl())
        assertEquals("share", addr.relativePath("", "share"))
        assertEquals("share/Movies", addr.relativePath("", "/share/Movies/"))
        assertEquals("smb://host:445/share/Movies", addr.buildUrl("", "share/Movies"))
    }

    @Test
    fun `空白共享路径按未配置处理`() {
        val addr = SmbAddressing("host", 445, "   ")
        assertEquals("smb://host:445/", addr.shareBaseUrl())
        assertEquals("share/Movies", addr.relativePath("", "share/Movies"))
    }

    // ---------------------------------------------------------------- 等价性

    @Test
    fun `常规路径下新旧寻址结果逐项等价`() {
        val cases = listOf(
            Triple("share", "", ""),
            Triple("share", "", "Movies"),
            Triple("share", "", "Movies/"),
            Triple("share", "", "sub/Movies"),
            Triple("share", "films/", "Movies"),
            Triple("share", "films/", "sub/Movies/with space.mkv"),
            Triple("share", "", "deep/a/b/c/d.mkv"),
            Triple("share", "", "中文目录/视频.mkv"),
            Triple("share", "", "with space.mkv"),
            Triple("share", "", "hash#name.mkv"),
            Triple("share", "", "percent%name.mkv"),
            Triple("share", "", "amp&name.mkv"),
            Triple("share", "", "star*name.mkv"),
            Triple("share", "", "pipe|name.mkv"),
            Triple("share", "", "quote\"name.mkv"),
            Triple("share", "", "lt<gt>name.mkv"),
            Triple("share", "", "trailing .mkv"),
            Triple(null, "", ""),
            Triple(null, "", "share"),
            Triple(null, "", "share/"),
            Triple(null, "", "share/Movies"),
            Triple(null, "", "share/Movies/中文 目录.mkv"),
            Triple(null, "", "share/sub/a:b.mkv"),
        )
        for ((share, prefix, path) in cases) {
            val addr = SmbAddressing("host", 445, share)
            val expected = snapshot(SmbFile(legacyUrl(share, prefix, path), ctx))
            val actual = snapshot(addr.resolve(ctx, prefix, path))
            assertEquals("路径 [$share|$prefix|$path] 的寻址结果应与旧拼法一致", expected, actual)
        }
    }

    @Test
    fun `空路径解析为共享根`() {
        val addr = SmbAddressing("host", 445, "share")
        val f = addr.resolve(ctx, "", "")
        assertEquals("smb://host:445/share/", f.url.toString())
        assertEquals("\\", f.uncPath)
    }

    // ---------------------------------------------------------------- 缺陷修复

    @Test
    fun `文件名含问号时 UNC 路径不再被截断`() {
        val addr = SmbAddressing("host", 445, "share")
        val f = addr.resolve(ctx, "", "Movies/a?b.mkv")
        assertEquals("\\Movies\\a?b.mkv", f.uncPath)
        assertEquals("/share/Movies/a?b.mkv", f.locator.urlPath)
        assertEquals("a?b.mkv", f.name)
    }

    @Test
    fun `旧拼法在同一路径上确实会截断`() {
        // 对照：这不是假设 —— 旧实现（含本测试复刻的公式）在 jcifs 3.0.0 下就是截断的。
        // 该断言若失败，说明 jcifs 修好了 URL 解析，届时可删掉回退分支并简化 SmbAddressing。
        val legacy = SmbFile(legacyUrl("share", "", "Movies/a?b.mkv"), ctx)
        assertEquals("\\Movies\\a", legacy.uncPath)
    }

    @Test
    fun `问号出现在目录名与文件名中都可寻址`() {
        val addr = SmbAddressing("host", 445, "share")
        assertEquals("\\a?b\\c?d.mkv", addr.resolve(ctx, "", "a?b/c?d.mkv").uncPath)
        assertEquals("\\中文?目录\\半角?名.mkv", addr.resolve(ctx, "", "中文?目录/半角?名.mkv").uncPath)
    }

    @Test
    fun `未配置共享路径时含问号的文件名同样可寻址`() {
        val addr = SmbAddressing("host", 445, null)
        val f = addr.resolve(ctx, "", "share/a?b.mkv")
        assertEquals("\\a?b.mkv", f.uncPath)
        assertEquals("/share/a?b.mkv", f.locator.urlPath)
        assertEquals("a?b.mkv", f.name)
    }

    @Test
    fun `取子项名称必须用 name 而非 path`() {
        val addr = SmbAddressing("host", 445, "share")
        val base = SmbFile("smb://host:445/share/Movies/", ctx)

        // jcifs 枚举出的子项由「父 + 相对名」构造器创建，其 URL 里 `?` 是编码过的
        val tricky = SmbFile(base, "a?b.mkv")
        assertEquals("a?b.mkv", addr.nameOf(tricky))
        // 对照：旧实现取的是 path（URL 字符串），会得到编码后的名字
        assertEquals("a%3fb.mkv", tricky.path.trimEnd('/').substringAfterLast('/'))

        // 目录名带尾随 `/`，需去掉
        assertEquals("Movies", addr.nameOf(SmbFile(base, "Movies/")))
        assertEquals("plain.mkv", addr.nameOf(SmbFile(base, "plain.mkv")))
        assertEquals("中文目录", addr.nameOf(SmbFile(base, "中文目录/")))
    }

    // ---------------------------------------------------------------- 目录尾随斜杠（回归）

    /**
     * 钉住 jcifs 的拼接行为本身 —— 这是「目录必须带尾随 `/`」这一约束的**依据**。
     *
     * `SmbResourceLocatorImpl.updatePaths:158` 做的是 `canon = 父 canon + 名字`，中间不补分隔符；
     * 而 `getName()`（`SmbResourceLocatorImpl.getName:180`）取 canon 最后一个 `/` 之后的部分。
     * 父 canon 不以 `/` 结尾时，父名会与子名粘在一起。
     *
     * 若将来升级 jcifs 后本用例失败，说明上游修掉了该行为，
     * [SmbAddressing.resolve] 的 `isDirectory` 补斜杠与 [SmbAddressing.nameOf] 的兜底可以一并简化。
     */
    @Test
    fun `jcifs 在父目录 canon 无尾随斜杠时会粘连父名与子名`() {
        val root = SmbFile("smb://host:445/share/", ctx)

        val noSlash = SmbFile(root, "films")
        assertEquals("/share/films", noSlash.locator.urlPath)
        val childOfNoSlash = SmbFile(noSlash, "movie.mkv")
        // URL 与 canon **双双丢失父段** —— 既打不开、名字与路径也错
        assertEquals("smb://host:445/share/movie.mkv", childOfNoSlash.url.toString())
        assertEquals("/share/filmsmovie.mkv", childOfNoSlash.locator.urlPath)
        assertEquals("filmsmovie.mkv", childOfNoSlash.name)

        val withSlash = SmbFile("smb://host:445/share/films/", ctx)
        assertEquals("/share/films/", withSlash.locator.urlPath)
        val childOfSlash = SmbFile(withSlash, "movie.mkv")
        assertEquals("smb://host:445/share/films/movie.mkv", childOfSlash.url.toString())
        assertEquals("movie.mkv", childOfSlash.name)
    }

    @Test
    fun `目录必须以 isDirectory 解析否则子项名字被父名污染`() {
        val addr = SmbAddressing("host", 445, "share")

        // 错误用法（#12 重构引入的回归）：目录未补尾随 `/`
        val broken = addr.resolve(ctx, "", "films")
        assertEquals("/share/films", broken.locator.urlPath)
        assertEquals("filmsmovie.mkv", SmbFile(broken, "movie.mkv").name)

        // 正确用法：listFiles 前必须以 isDirectory = true 解析
        val fixed = addr.resolve(ctx, "", "films", isDirectory = true)
        assertEquals("/share/films/", fixed.locator.urlPath)
        assertEquals("movie.mkv", SmbFile(fixed, "movie.mkv").name)
        // 目录自身的名字不受影响
        assertEquals("films", addr.nameOf(fixed))
    }

    @Test
    fun `未配置共享路径时目录同样以斜杠结尾`() {
        val addr = SmbAddressing("host", 445, null)
        // path 首段即共享名
        val dir = addr.resolve(ctx, "", "share/films", isDirectory = true)
        assertEquals("/share/films/", dir.locator.urlPath)
        assertEquals("movie.mkv", SmbFile(dir, "movie.mkv").name)
    }

    @Test
    fun `nameOf 兜底：父目录漏传 isDirectory 时仍返回正确名字`() {
        val addr = SmbAddressing("host", 445, "share")
        val broken = addr.resolve(ctx, "", "films") // 故意漏传 isDirectory
        val child = SmbFile(broken, "movie.mkv")
        assertEquals("filmsmovie.mkv", child.name) // 原始值确实被污染
        assertEquals("movie.mkv", addr.nameOf(child)) // 兜底纠正
        assertEquals("subdir", addr.nameOf(SmbFile(broken, "subdir/")))
    }

    @Test
    fun `nameOf 兜底不误伤含问号的文件名`() {
        val addr = SmbAddressing("host", 445, "share")
        // 正常路径（父目录已正确补斜杠）：name 与 URL 末段等长，不走兜底，`?` 原样保留
        val normal = SmbFile(SmbFile("smb://host:445/share/films/", ctx), "a?b.mkv")
        assertEquals("a?b.mkv", normal.name)
        assertEquals("a?b.mkv", addr.nameOf(normal))

        // 目录名与文件名恰好构成「后缀关系」的极端情形也不误伤：
        // 目录 a + 文件 abc.mkv，正常路径下 name 与 URL 末段等长
        val tricky = SmbFile(SmbFile("smb://host:445/share/a/", ctx), "abc.mkv")
        assertEquals("abc.mkv", addr.nameOf(tricky))
    }

    @Test
    fun `子目录嵌套时每层目录都必须补尾随斜杠`() {
        val addr = SmbAddressing("host", 445, "share")
        // 逐层枚举：每层都用 isDirectory = true 解析
        val l1 = addr.resolve(ctx, "", "films", isDirectory = true)
        val l2 = addr.resolve(ctx, "films/", "sub", isDirectory = true)
        assertEquals("/share/films/sub/", l2.locator.urlPath)
        assertEquals("movie2.mkv", SmbFile(l2, "movie2.mkv").name)
        assertEquals("sub", addr.nameOf(SmbFile(l1, "sub/")))

        // 中间层漏补斜杠时，深层子项会被污染（说明每一层都不能漏）
        val badL2 = addr.resolve(ctx, "films/", "sub")
        assertEquals("submovie2.mkv", SmbFile(badL2, "movie2.mkv").name)
        // 但 nameOf 的兜底仍能救回来
        assertEquals("movie2.mkv", addr.nameOf(SmbFile(badL2, "movie2.mkv")))
    }

    // ---------------------------------------------------------------- 已知边界

    @Test
    fun `相对路径首段含冒号时回退到旧拼法且不抛异常`() {
        val addr = SmbAddressing("host", 445, "share")
        val f = addr.resolve(ctx, "", "a:b.mkv")
        assertEquals("\\a:b.mkv", f.uncPath)
        assertEquals("a:b.mkv", f.name)
    }

    @Test
    fun `冒号不在首段时无需回退`() {
        val addr = SmbAddressing("host", 445, "share")
        assertEquals("\\sub\\a:b.mkv", addr.resolve(ctx, "", "sub/a:b.mkv").uncPath)
        assertEquals("\\sub\\a:b\\c.mkv", addr.resolve(ctx, "", "sub/a:b/c.mkv").uncPath)
    }
}
