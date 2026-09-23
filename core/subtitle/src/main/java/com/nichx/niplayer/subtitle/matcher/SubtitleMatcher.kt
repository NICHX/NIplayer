package com.nichx.niplayer.subtitle.matcher

/**
 * 同目录字幕文件匹配。
 *
 * 用于播放视频时自动识别同目录下的外挂字幕（本地 / SMB / WebDAV 共用同一套匹配规则）。
 * 本对象只做**纯字符串匹配**，不涉及任何 IO 与 [com.nichx.niplayer.storage.Storage] 依赖，
 * 目录列举与内容读取由调用方（:feature:player 的 PlayerViewModel）负责。
 *
 * 匹配规则（两段式）：
 * 1. **同名优先**：优先取与视频同名的字幕
 *    - 完全同名：`movie.srt`（stem 等于 `movie`）
 *    - 同名带语言/版本后缀：`movie.chs.srt`、`movie_zh-CN.ass`、`movie-1080p.ass`
 *    只要目录内存在任一「同名」字幕，就**只在同名候选里选**，不会退到目录内其他字幕 ——
 *    避免把同目录里另一部片子的字幕错配到当前视频上。
 * 2. **目录兜底**：目录内没有任何同名字幕时，才在**全部字幕候选**里选。
 *
 * 组内择优顺序：[priority] 语言关键词命中位置 → 文件名（不区分大小写）字典序。
 *
 * @see isSubtitleFile
 * @see pickBest
 */
object SubtitleMatcher {

    /**
     * 识别为外挂字幕的扩展名（小写，不含点）。
     *
     * 只列**确有解析器**的格式：SRT / ASS / SSA。`.vtt` 曾在此列，但项目没有 VTT 解析器
     * ——列出来只会让用户选中一个必然解析失败的文件（表现为「载入了但什么都不显示」），
     * 故移除；将来补上 VTT 解析器时再加回。`.lrc` 属音频歌词，由 AudioPlaybackManager 处理。
     */
    val SUBTITLE_EXTENSIONS: Set<String> = setOf("srt", "ass", "ssa")

    /** 同名后缀分隔符：`movie.chs.srt` / `movie_zh.srt` / `movie-1080p.srt` / `movie chs.srt`。 */
    private val STEM_SEPARATORS = charArrayOf('.', '_', '-', ' ')

    /** 判断文件名是否为支持的外挂字幕（大小写不敏感）。 */
    fun isSubtitleFile(fileName: String): Boolean = extensionOf(fileName) in SUBTITLE_EXTENSIONS

    /** 取扩展名（小写，不含点）。无扩展名时返回空串。 */
    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    /** 去掉最后一段扩展名后的文件名（保留原大小写）。无扩展名时原样返回。 */
    fun stemOf(fileName: String): String = fileName.substringBeforeLast('.')

    /**
     * 从同目录文件名列表 [candidates] 中挑出与 [videoFileName] 最匹配的字幕文件。
     *
     * @param videoFileName 视频文件名（含扩展名，如 `movie.mkv`），路径请先取最后一段
     * @param candidates 同目录下的文件名列表（含目录与视频自身，内部会自行过滤）
     * @param priority 字幕语言优先级，逗号分隔（如 `"chs,cht"`，来自
     *   [com.nichx.niplayer.datastore.SubtitleSettings.subtitlePriority]）。
     *   空串表示不指定，直接按文件名排序
     * @return 命中的字幕文件名（[candidates] 中的原字符串），无可用字幕时返回 null
     */
    fun pickBest(
        videoFileName: String,
        candidates: List<String>,
        priority: String = "",
    ): String? {
        val videoStem = stemOf(videoFileName).lowercase()
        if (videoStem.isEmpty()) return null

        val subtitles = candidates.filter { isSubtitleFile(it) }
        if (subtitles.isEmpty()) return null

        // 同名等级：0 = 完全同名，1 = 同名带后缀，2 = 目录内其他字幕
        val ranks = subtitles.associateWith { sameNameRank(stemOf(it).lowercase(), videoStem) }

        // 只要存在同名（0/1）候选就只用同名候选；否则才用目录内全部字幕兜底
        val pool = if (ranks.values.any { it <= 1 }) {
            subtitles.filter { ranks.getValue(it) <= 1 }
        } else {
            subtitles
        }

        val priorityKeys = parsePriority(priority)
        return pool.minWithOrNull(
            compareBy({ priorityIndexOf(stemOf(it).lowercase(), priorityKeys) }, { it.lowercase() })
        )
    }

    /** 同名等级判定。 */
    private fun sameNameRank(subtitleStem: String, videoStem: String): Int = when {
        subtitleStem == videoStem -> 0
        STEM_SEPARATORS.any { subtitleStem.startsWith("$videoStem$it") } -> 1
        else -> 2
    }

    /**
     * 解析语言优先级串。兼容中英文逗号/分号，忽略空白项。
     *
     * 全部为空时返回空列表，调用方据此退化为按文件名排序。
     */
    private fun parsePriority(priority: String): List<String> =
        priority.split(',', '，', ';', '；')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

    /** 命中的优先级下标；未命中返回 [Int.MAX_VALUE]（排到最后）。 */
    private fun priorityIndexOf(subtitleStem: String, priorityKeys: List<String>): Int {
        if (priorityKeys.isEmpty()) return 0
        val index = priorityKeys.indexOfFirst { subtitleStem.contains(it) }
        return if (index >= 0) index else Int.MAX_VALUE
    }
}
