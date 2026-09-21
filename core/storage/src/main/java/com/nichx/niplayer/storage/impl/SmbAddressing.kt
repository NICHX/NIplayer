package com.nichx.niplayer.storage.impl

import android.util.Log
import org.codelibs.jcifs.smb.CIFSContext
import org.codelibs.jcifs.smb.impl.SmbFile
import java.net.MalformedURLException

/**
 * SMB 资源寻址（缺陷审计 #12 修复）。
 *
 * ## 问题
 *
 * 原实现自己拼接 `smb://host:port/{share}/{path}` 字符串再交给 [SmbFile]，
 * 而 jcifs 用 [java.net.URL] 解析该字符串 —— 文件名里的 `?` 会被当作 query 起点，
 * **`?` 之后的文件名被截断**（`a?b.mkv` 变成 `a`）。实测 jcifs 3.0.0 亦不解码路径中的
 * `%XX`，所以「把 `?` 编码成 `%3F`」是无效修法（服务端会去找名为 `a%3Fb.mkv` 的文件）。
 *
 * ## 修法
 *
 * 改用 jcifs 自己的 [SmbFile]「父 + 相对名」构造器。它内部把相对名交给
 * `encodeRelativePath()`（`?` → `%3f` 并加 `URL:` 标记，由 `java.net.URL` 剥离该标记），
 * 随后由 `SmbResourceLocatorImpl.resolveInContext()` **用原始名字**重算 UNC/URL 路径 ——
 * 因此 `?`、`#`、`%`、空格等字符都能原样保留。
 *
 * ## 边界（实测，见 `SmbFileNameEncodingTest`）
 *
 * | 相对路径首段含 | 完整 URL 拼法（旧） | 父+相对名构造器（新） |
 * |---|---|---|
 * | 普通字符 / 空格 / `#` / `%` / `&` / `*` / `"` / `<` / `>` / 中文 | ✅ | ✅ |
 * | `?` | ❌ 截断 | ✅ |
 * | `:` | ✅ | ❌ 抛 [MalformedURLException]（`a:b.mkv` 的 `a` 被当成 URL 协议名） |
 *
 * 故 [resolve] 以「父+相对名」为首选，仅在首段含 `:` 时回退到完整 URL 拼法。
 * `:` 位于**非首段**（如 `sub/a:b.mkv`）时两种拼法都正常，无需回退。
 *
 * 两种拼法在其余场景下产出的 [SmbFile] **完全等价**（URL、UNC、canonical、name 四项一致），
 * 该等价性由 `SmbAddressingTest` 逐例断言。
 *
 * ## ⚠️ 目录必须以 `/` 结尾（否则子项名字会被父名污染）
 *
 * jcifs 的 `locator.canon = 父 canon + 名字`（`SmbResourceLocatorImpl.updatePaths:158`），
 * **中间不补分隔符**。而 [SmbFile.getName] 正是从 canon 推导的
 * （`SmbResourceLocatorImpl.getName:180` 取 canon 最后一个 `/` 之后的部分）。
 * 于是父 canon 不以 `/` 结尾时，父名会与子名粘连：
 *
 * | 父目录构造方式 | 父 canon | 子项 `getName()` |
 * |---|---|---|
 * | `smb://host/share/films/`（带尾随 `/`） | `/share/films/` | `movie.mkv` ✅ |
 * | `SmbFile(root, "films")`（不带） | `/share/films` | **`filmsmovie.mkv`** ❌ |
 *
 * 而且子项的 **URL 也会丢失父段**：jcifs 用
 * `new URL(父 URL, 相对名)` 构造子项，父 URL 不以 `/` 结尾时
 * `new URL("smb://host/share/films", "movie.mkv")` 会解析成
 * `smb://host/share/movie.mkv`（`films` 被当成文件名而非目录）。
 *
 * 两个后果叠加，这类子项既**打不开**（URL 指向了错误的路径）、**名字与路径也是错的**
 * （文件浏览页把 `path` 拼成 `films/filmsmovie.mkv`）。播放列表里
 * `indexOfFirst { it.filePath == currentFile.path }` 因此永远匹配不上，连播列表恒为空 ——
 * 这正是「SMB 子目录里的视频读不到同文件夹播放列表」的成因。
 *
 * 因此 [resolve] 对目录**强制补尾随 `/`**（[isDirectory] 参数），[nameOf] 另加一层兜底。
 * 注意根目录浏览不受影响（`shareBaseUrl()` 本身就以 `/` 结尾），所以问题只在**子目录**里出现。
 *
 * @param host 主机名或 IP
 * @param port 端口
 * @param shareName [MediaLibraryEntity.smbSharePath][com.nichx.niplayer.database.entity.MediaLibraryEntity.smbSharePath]，
 *   为 null/空白时表示未配置共享路径（浏览起点为服务器根，路径首段即共享名）
 */
internal class SmbAddressing(
    private val host: String,
    private val port: Int,
    private val shareName: String?,
) {

    /** 共享名（`smbSharePath` 的第一段）；未配置共享路径时为 null。 */
    private val shareSegment: String? =
        if (shareName.isNullOrBlank()) null else shareName.trim('/').split("/").first().trim()

    /**
     * 共享根 URL：配置了共享路径时为 `smb://host:port/{share}/`，
     * 否则为服务器根 `smb://host:port/`（其 `listFiles()` 会枚举服务器上的共享）。
     */
    fun shareBaseUrl(): String =
        if (shareSegment == null) "smb://$host:$port/" else "smb://$host:$port/$shareSegment/"

    /**
     * 共享内相对路径。
     *
     * - 配置了共享路径：`{rootPrefix}{path}`（rootPrefix 为共享内子目录前缀，如 `"films/"`）；
     * - 未配置共享路径：`path` 本身即「共享名/共享内路径」，仅去掉首尾 `/`。
     */
    fun relativePath(rootPrefix: String, path: String): String =
        if (shareSegment == null) path.trim('/') else rootPrefix + path

    /**
     * 完整 URL 字符串拼法（旧实现，仅作 [resolve] 的回退路径与诊断输出使用）。
     *
     * 注意：该拼法无法表达含 `?` 的文件名，新代码不应直接使用。
     */
    fun buildUrl(rootPrefix: String, path: String): String {
        val rel = relativePath(rootPrefix, path)
        return if (rel.isEmpty()) shareBaseUrl() else shareBaseUrl() + rel
    }

    /**
     * 把共享内相对路径解析为 [SmbFile]。
     *
     * @param ctx 已认证的 [CIFSContext]
     * @param rootPrefix 共享内子目录前缀（主线 [SmbStorage.shareRootPrefix] 或播放线 playRootPrefix）
     * @param path 相对 [rootPrefix] 的路径；空串表示共享根（或服务器根）
     * @param isDirectory 目标是否为**将要被枚举**的目录。为 true 时强制补尾随 `/`，
     *   否则 jcifs 的 canon 会把父名与子名粘连，子项 `getName()` 会返回 `filmsmovie.mkv`（见类注释）
     */
    fun resolve(
        ctx: CIFSContext,
        rootPrefix: String,
        path: String,
        isDirectory: Boolean = false,
    ): SmbFile {
        val rel = relativePath(rootPrefix, path)
        if (rel.isEmpty()) return SmbFile(shareBaseUrl(), ctx)
        val spec = withTrailingSlash(rel, isDirectory)
        return try {
            SmbFile(SmbFile(shareBaseUrl(), ctx), spec)
        } catch (e: MalformedURLException) {
            // 相对路径首段含 `:` 时 java.net.URL 会把 `:` 之前的部分当成协议名而拒绝
            //（如 "a:b.mkv" → 协议 "a"）。此时回退到完整 URL 拼法：它能正确表达 `:`，
            // 代价是该段若同时含 `?` 会被截断（`?` 与首段 `:` 并存的文件名无法寻址，已知限制）。
            Log.w(
                TAG,
                "SMB 相对路径 [$rel] 首段含 `:`，回退到 URL 字符串拼法（${e.message}）",
            )
            SmbFile(withTrailingSlash(buildUrl(rootPrefix, path), isDirectory), ctx)
        }
    }

    /** 目录规格必须以 `/` 结尾（jcifs 的 canon 拼接不补分隔符，见类注释）。 */
    private fun withTrailingSlash(spec: String, isDirectory: Boolean): String =
        if (!isDirectory || spec.endsWith("/")) spec else "$spec/"

    /**
     * 取子项名称（#12 的另一半）。
     *
     * 用 [SmbFile.getName] 而**不是** `path`（后者是 jcifs 内部的 URL 字符串）：jcifs 枚举出的
     * 子项由「父 + 相对名」构造器创建，其 URL 里 `?` 已被编码成 `%3f`，用 `path` 取名字会把
     * `a?b.mkv` 变成 `a%3fb.mkv` —— 不只是打不开，媒体库里显示的文件名也是错的。
     * [SmbFile.getName] 由 locator 用原始名字推导，`?` 等字符原样保留；目录名带尾随 `/`，去掉。
     *
     * **兜底**：若父目录的 canon 不以 `/` 结尾（例如调用方漏传 [resolve] 的 `isDirectory`），
     * [SmbFile.getName] 会返回「父名 + 子名」的粘连结果。其判定特征是「等于 URL 最后一段且更长」，
     * 此时改取 URL 最后一段。含 `?` 的文件名不会误触发：URL 里是 `a%3fb.mkv`，与 `a?b.mkv`
     * 不构成后缀关系。
     */
    fun nameOf(file: SmbFile): String {
        val name = file.name.trimEnd('/')
        val fromUrl = file.url.toString().trimEnd('/').substringAfterLast('/')
        return if (name.length > fromUrl.length && name.endsWith(fromUrl)) fromUrl else name
    }

    private companion object {
        const val TAG = "SmbAddressing"
    }
}
