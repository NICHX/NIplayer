package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 在线匹配（歌词 / 封面）黑名单，按文件或目录粒度生效。
 *
 * 用于在线匹配反复出错的内容（如有声书）：加入黑名单后不再自动请求 API。
 * 手动"重新匹配"不受黑名单限制，且结果会写入缓存优先展示。
 *
 * key 约定：
 * - 远程 / 媒体库文件：`sid:<storageId>:<文件路径>`
 * - 本地文件：`local:<uri 或路径>`
 *
 * 目录级条目以路径前缀生效：文件 key 以 `<目录key>/` 开头即视为命中，
 * 因此黑名单一个目录后其子树内所有文件都不再自动匹配。
 */
object OnlineMatchBlacklist {

    /** 黑名单类别：封面与歌词相互独立。 */
    enum class Kind(val prefKey: String) {
        COVER("match_blacklist_cover"),
        LYRICS("match_blacklist_lyrics"),
    }

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    fun isSkipped(kind: Kind, key: String): Boolean {
        val set = mmkv.decodeStringSet(kind.prefKey, emptySet()) ?: emptySet()
        if (key in set) return true
        return set.any { key.startsWith("$it/") }
    }

    fun skip(kind: Kind, key: String) {
        val set = (mmkv.decodeStringSet(kind.prefKey, emptySet()) ?: emptySet()).toMutableSet()
        set.add(key)
        mmkv.encode(kind.prefKey, set)
    }

    /** 仅移除精确匹配的条目；覆盖此 key 的目录级条目保持不变。 */
    fun unskip(kind: Kind, key: String) {
        val set = (mmkv.decodeStringSet(kind.prefKey, emptySet()) ?: emptySet()).toMutableSet()
        set.remove(key)
        mmkv.encode(kind.prefKey, set)
    }

    /** 清空全部黑名单（设置页"重置忽略列表"）。 */
    fun clear() {
        Kind.entries.forEach { mmkv.remove(it.prefKey) }
    }
}
