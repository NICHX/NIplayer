package com.nichx.niplayer.database.backup

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份编排器（v2 架构）。
 *
 * 不再硬编码备份项清单，而是通过 Hilt 多绑定注入所有 [BackupItem]，
 * 仅负责组装 / 分发 / 版本兼容。新增备份项只需新建 BackupItem 子类 + @IntoSet 注册，
 * 本类无需改动（开闭原则）。
 *
 * 备份文件结构（v2）：
 * ```json
 * {
 *   "version": 2,
 *   "timestamp": 1234567890,
 *   "payload": {
 *     "mediaLibraries": [...],
 *     "quickAccesses": [...],
 *     "appSettings": { ... },
 *     ...
 *   }
 * }
 * ```
 * payload 内每个 key 对应一个 [BackupItem.key]，值为其 [BackupItem.snapshot] 返回的松散对象。
 *
 * v1 → v2 兼容：旧版备份是平铺字段（mediaLibraries/lrcApiUrl/appSettings 在顶层），
 * 导入时检测 version==1，把顶层字段搬到 payload 内，并把 lrcApiUrl/lrcApiAuth/assrtToken
 * 并入 appSettings，再交给各 BackupItem 恢复。
 */
@Singleton
class BackupManager @Inject constructor(
    private val items: Set<@JvmSuppressWildcards BackupItem>,
) {
    private val payloadAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Any::class.java,
            ),
        )

    /**
     * 导出用户数据为 JSON 字符串。
     *
     * 遍历所有 [BackupItem]，收集非 null 快照，组装为 v2 备份结构。
     * 返回的 [snapshot] 为 null 的项不包含在 payload 中（导出时跳过）。
     */
    suspend fun exportToJson(): String {
        val payload = linkedMapOf<String, Any?>()
        for (item in items.sortedBy { it.key }) {
            val snapshot = item.snapshot() ?: continue
            payload[item.key] = snapshot
        }
        val root = linkedMapOf<String, Any?>(
            "version" to BACKUP_VERSION,
            "timestamp" to System.currentTimeMillis(),
            "payload" to payload,
        )
        return payloadAdapter.indent("  ").toJson(root)
    }

    /**
     * 从 JSON 字符串恢复数据。
     *
     * @param json 备份文件内容
     * @param mode 恢复模式：REPLACE 清空后重插（还原）/ MERGE 按主键合并（迁移）
     * @return 恢复摘要列表，每项对应一个有数据的 BackupItem
     */
    suspend fun importFromJson(
        json: String,
        mode: RestoreMode = RestoreMode.REPLACE,
    ): BackupSummary {
        @Suppress("UNCHECKED_CAST")
        val root = payloadAdapter.fromJsonValue(
            // 用 org.json.JSONObject 解析以区分 v1/v2
            JSONObject(json).toMap(),
        ) as Map<String, Any?> ?: throw IllegalArgumentException("无效的备份文件")

        val version = (root["version"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("备份文件缺少版本号")

        if (version != BACKUP_VERSION && version != 1) {
            throw IllegalArgumentException("不支持的备份文件版本: $version")
        }

        // v1 → v2 迁移：把旧版平铺字段转为 payload 结构
        val payload: Map<String, Any?> = if (version == 1) {
            migrateV1ToV2(root)
        } else {
            @Suppress("UNCHECKED_CAST")
            root["payload"] as? Map<String, Any?>
                ?: throw IllegalArgumentException("备份文件缺少 payload")
        }

        if (payload.isEmpty()) {
            throw IllegalArgumentException("备份文件为空或内容无效")
        }

        // 按 BackupItem 的 key 分发恢复，并收集摘要
        val itemByKey = items.associateBy { it.key }
        val descriptions = mutableListOf<String>()
        for ((key, data) in payload) {
            val item = itemByKey[key] ?: continue // 未知 key（未来版本或已移除的项）跳过
            item.restore(data, mode)
            item.describe(data)?.let { descriptions.add(it) }
        }

        return BackupSummary(descriptions)
    }

    /**
     * v1 → v2 备份结构迁移。
     *
     * v1 是平铺结构：mediaLibraries/quickAccesses/.../lrcApiUrl/lrcApiAuth/assrtToken/appSettings 在顶层。
     * v2 是 payload 结构：所有数据在 payload 内，且 lrcApiUrl/lrcApiAuth/assrtToken 并入 appSettings。
     */
    private fun migrateV1ToV2(root: Map<String, Any?>): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>()
        // v1 顶层字段名与 v2 payload key 一致的直接搬入
        val v1Keys = setOf(
            "mediaLibraries", "quickAccesses", "videoBookmarks",
            "extendFolders", "encryptedFolders", "appSettings",
        )
        for (key in v1Keys) {
            root[key]?.let { payload[key] = it }
        }
        // lrcApiUrl/lrcApiAuth/assrtToken 从顶层并入 appSettings
        val lrcApiUrl = root["lrcApiUrl"] as? String
        val lrcApiAuth = root["lrcApiAuth"] as? String
        val assrtToken = root["assrtToken"] as? String
        if (lrcApiUrl != null || lrcApiAuth != null || assrtToken != null) {
            @Suppress("UNCHECKED_CAST")
            val appSettings = (payload["appSettings"] as? Map<String, Any?>)
                ?.let { linkedMapOf<String, Any?>().apply { putAll(it) } }
                ?: linkedMapOf()
            lrcApiUrl?.takeIf { it.isNotBlank() }?.let { appSettings["lrcApiUrl"] = it }
            lrcApiAuth?.takeIf { it.isNotBlank() }?.let { appSettings["lrcApiAuth"] = it }
            assrtToken?.takeIf { it.isNotBlank() }?.let { appSettings["assrtToken"] = it }
            payload["appSettings"] = appSettings
        }
        return payload
    }

    private companion object {
        const val BACKUP_VERSION = 2
    }
}

/** 备份摘要，供 UI 展示恢复结果。 */
data class BackupSummary(
    /** 各备份项的人类可读摘要（如 "存储源: 3 条"、"歌单: 2 个 (15 首曲目)"）。 */
    val descriptions: List<String>,
)

/** JSONObject 转 Map<String, Any?> 的扩展（org.json 递归转 Moshi 松散对象）。 */
private fun JSONObject.toMap(): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    for (key in keys()) {
        map[key] = when (val v = get(key)) {
            is JSONObject -> v.toMap()
            is org.json.JSONArray -> v.toList()
            JSONObject.NULL -> null
            else -> v
        }
    }
    return map
}

/** JSONArray 转 List<Any?>。 */
private fun org.json.JSONArray.toList(): List<Any?> {
    return (0 until length()).map { i ->
        when (val v = get(i)) {
            is JSONObject -> v.toMap()
            is org.json.JSONArray -> v.toList()
            JSONObject.NULL -> null
            else -> v
        }
    }
}
