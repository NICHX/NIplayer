package com.nichx.niplayer.feature.player

/**
 * 持久化字幕缓存文件的命名与展示名。
 *
 * 缓存文件名格式 `{uniqueKey 哈希}_{原文件名}`：
 * - 哈希前缀保证不同视频的同名字幕（如各集都叫 `chs.srt`）互不覆盖；
 * - 后半段保留原文件名，供界面展示 —— 恢复播放时字幕菜单与失败提示要显示**用户的文件名**，
 *   而不是一串数字。读取侧用 [displayName] 去掉前缀。
 *
 * 抽成纯对象是为了可测试：命名/还原规则一旦错位，症状就是「字幕名显示成一串数字」
 * 或者「不同视频的字幕互相覆盖」，两者都不容易在运行时发现。
 */
internal object SubtitleCacheNaming {

    /** 缓存文件名里需要替换掉的字符：路径分隔符与各平台保留字符。 */
    private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|]""")

    /** 旧版持久化字幕的纯哈希文件名（如 `-1234567890.srt`）：原文件名已无从还原。 */
    private val LEGACY_HASHED_NAME = Regex("""-?\d+\.[A-Za-z0-9]+""")

    /** 缓存文件名里保留的原文件名最大长度（避免超出文件系统名 255 字节上限）。 */
    private const val MAX_SAFE_NAME_LENGTH = 80

    /** 原文件名为空时的兜底名。 */
    private const val FALLBACK_NAME = "subtitle"

    /**
     * 构造缓存文件名。
     *
     * @param uniqueKey 播放历史 uniqueKey（哈希后作前缀）
     * @param sourceFileName 源字幕文件名（含扩展名）
     */
    fun fileName(uniqueKey: String, sourceFileName: String): String {
        val safeName = sourceFileName
            .substringAfterLast('/')
            .replace(ILLEGAL_CHARS, "_")
            .take(MAX_SAFE_NAME_LENGTH)
            .ifBlank { FALLBACK_NAME }
        return "${uniqueKey.hashCode()}_$safeName"
    }

    /**
     * 还原展示名（去掉哈希前缀）。
     *
     * @param cacheFileName 缓存文件名（[fileName] 的产物）
     * @param legacyFallback 旧版纯哈希名无从还原时使用的泛化标签（由调用方给出本地化文案）
     */
    fun displayName(cacheFileName: String, legacyFallback: String): String {
        // 只按首个下划线切分：原文件名自身可以含下划线（如 `电影_01.chs.srt`）
        val name = cacheFileName.substringAfter('_', cacheFileName)
        return if (LEGACY_HASHED_NAME.matches(name)) legacyFallback else name
    }
}
