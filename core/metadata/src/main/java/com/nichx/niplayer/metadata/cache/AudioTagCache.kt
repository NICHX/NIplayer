package com.nichx.niplayer.metadata.cache

import com.nichx.niplayer.metadata.model.AudioTags

/**
 * 音频标签的进程内缓存（LRU）。
 *
 * 存在的理由：读标签需要打开文件（远程文件要拉文件头，可能数 MB），
 * 而**读取时机与使用时机往往不同** —— 浏览目录提取封面时顺手读到标签，
 * 真正用它构造查询词却是在播放期。缓存把两者解耦，避免同一首歌反复打开文件。
 *
 * 仅进程内有效：跨进程重启后首次访问仍需重新读取。
 * 持久化留给后续的 `audio_match` 表（P2）。
 */
object AudioTagCache {

    /** 容量上限。一首歌一条，200 条足以覆盖一次连续播放会话。internal 供测试断言淘汰行为。 */
    internal const val MAX_ENTRIES = 200

    private const val INITIAL_CAPACITY = 16
    private const val LOAD_FACTOR = 0.75f

    /** accessOrder = true：读取也会把条目移到队尾，实现 LRU 淘汰。 */
    private val cache = object : LinkedHashMap<String, AudioTags>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioTags>): Boolean =
            size > MAX_ENTRIES
    }

    /** 当前缓存条目数（供测试与诊断使用）。 */
    @get:Synchronized
    val size: Int
        get() = cache.size

    @Synchronized
    fun put(key: String, tags: AudioTags) {
        cache[key] = tags
    }

    @Synchronized
    fun get(key: String): AudioTags? = cache[key]

    @Synchronized
    fun clear() {
        cache.clear()
    }

    /**
     * 生成缓存键。与 `OnlineMatchBlacklist` / `OnlineMatchCache` 的约定保持一致：
     * - 媒体库文件：`sid:<storageId>:<路径>`
     * - 本地文件：`local:<uri 或路径>`
     */
    fun keyFor(storageId: Int?, path: String): String =
        if (storageId != null) "sid:$storageId:$path" else "local:$path"
}
