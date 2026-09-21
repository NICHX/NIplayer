package com.nichx.niplayer.storage.impl

import org.codelibs.jcifs.smb.context.SingletonContext
import org.codelibs.jcifs.smb.impl.SmbFile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 固化 jcifs 3.0.0 的 SMB URL 解析行为 —— 缺陷审计 #12 的修复依据与回归护栏。
 *
 * ## 背景
 *
 * 原 `SmbStorage.buildSmbUrl` 自己拼接 `smb://host:port/share/path` 字符串，再交给
 * `SmbFile(url, ctx)`。审计最初认为「文件名含 `#` / `?` / `%` / `&` 都会被截断」，
 * 并建议做 URL 编码 —— **实测两者都不准确**。本测试用真实 jcifs 把这些结论钉住。
 *
 * ## 实测结论（jcifs 3.0.0）
 *
 * | 字符 | 自己拼完整 URL（旧实现） | 父+相对名构造器 |
 * |---|---|---|
 * | 空格 | ✅ 正常 | ✅ 正常 |
 * | `#` | ✅ 正常（`Handler.parseURL` 会把 fragment 拼回 path） | ✅ 正常 |
 * | `&` | ✅ 正常 | ✅ 正常 |
 * | `%` | ✅ 原样保留 | ✅ 原样保留 |
 * | **`?`** | ❌ **截断**（被拆成 query 且不拼回 path） | ✅ **正常** |
 *
 * 且 jcifs **不会解码** 路径中的 `%XX`（`a%3fb.mkv` 在 UNC 路径里就是字面量 `a%3fb.mkv`），
 * 因此**「把 `?` 编码成 `%3F`」是无效方案，反而会让服务端去找名为 `a%3Fb.mkv` 的文件**。
 *
 * ## 修复状态（第 8 批，2026-09-20）
 *
 * 已改用 jcifs 自己的 `SmbFile(parent: SmbResource, name: String)` 构造器 ——
 * 它内部走 `encodeRelativePath()`（把 `?` 编码为 `%3f` 并加 `URL:` 标记，由 `java.net.URL`
 * 剥离该标记），随后由 `SmbResourceLocatorImpl.resolveInContext()` **用原始名字**重算
 * UNC / canonical 路径。实现见 `SmbAddressing`，其回归测试见 `SmbAddressingTest`。
 *
 * **本文件只负责钉住 jcifs 的底层行为**（即「为什么必须这么修」），
 * 修复后的目标行为断言在 `SmbAddressingTest` 中。
 */
class SmbFileNameEncodingTest {

    private val ctx = SingletonContext.getInstance()
    private val parent = SmbFile("smb://host:445/share/Movies/", ctx)

    /** 需要特殊处理的文件名样本。 */
    private val trickyNames = listOf(
        "plain.mkv",
        "with space.mkv",
        "hash#name.mkv",
        "question?name.mkv",
        "percent%name.mkv",
        "amp&name.mkv",
        "半角?与中文.mkv",
    )

    /**
     * 规格：用 jcifs 的「父 + 相对名」构造器时，**所有样本都能得到正确的 UNC 路径**。
     *
     * 这是 #12 的目标行为 —— 第 8 批已实施，`SmbAddressing.resolve` 走的就是这条构造路径
     *（`SmbAddressingTest` 对每个样本另有断言）。
     */
    @Test
    fun `父加相对名构造器可正确表达所有特殊字符文件名`() {
        for (name in trickyNames) {
            val file = SmbFile(parent, name)
            assertEquals(
                "文件名 [$name] 的 UNC 路径应原样保留",
                "\\Movies\\$name",
                file.uncPath,
            )
        }
    }

    /**
     * 记录当前实现路径的缺陷：自己拼完整 URL 时，`?` 之后的文件名被截断。
     *
     * 此断言存在的意义是「钉住问题」—— 它证明现有 `buildSmbUrl` 方案无法表达含 `?` 的文件名。
     * 若将来 jcifs 修好了这一点，本测试会失败，届时可简化修复方案。
     */
    @Test
    fun `自己拼完整 URL 时问号之后的文件名被截断`() {
        val file = SmbFile("smb://host:445/share/Movies/question?name.mkv", ctx)
        assertEquals("\\Movies\\question", file.uncPath)
    }

    /**
     * 记录 `#` 不会截断（推翻审计最初的判断）。
     *
     * jcifs 的 `Handler.parseURL` 在 `super.parseURL` 之后执行 `path += '#' + ref`，
     * 把 fragment 重新拼回 path，因此 `#` 在文件名中是安全的 —— 但这是**库的实现细节**，
     * 不应作为依赖；改用父+相对名构造器后即可摆脱该依赖。
     */
    @Test
    fun `自己拼完整 URL 时井号不会截断文件名`() {
        val file = SmbFile("smb://host:445/share/Movies/hash#name.mkv", ctx)
        assertEquals("\\Movies\\hash#name.mkv", file.uncPath)
    }

    /**
     * 记录 jcifs **不解码** 路径中的 `%XX`。
     *
     * 这条是「不要用百分号编码来修复 #12」的直接依据：
     * 编码后的 `%3F` 会作为字面量进入 UNC 路径，服务端将找不到对应文件。
     */
    @Test
    fun `jcifs 不解码路径中的百分号转义`() {
        val encoded = SmbFile("smb://host:445/share/Movies/question%3Fname.mkv", ctx)
        assertEquals("\\Movies\\question%3Fname.mkv", encoded.uncPath)

        // 小写形式同样不解码（jcifs 自己生成的是小写 %3f）
        val lowercase = SmbFile("smb://host:445/share/Movies/question%3fname.mkv", ctx)
        assertEquals("\\Movies\\question%3fname.mkv", lowercase.uncPath)
    }
}
