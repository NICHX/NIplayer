package com.nichx.niplayer.database.backup

import androidx.room.withTransaction
import com.nichx.niplayer.common.backup.BackupItem
import com.nichx.niplayer.common.backup.DatabaseBackupItem
import com.nichx.niplayer.common.backup.RestoreContext
import com.nichx.niplayer.common.backup.RestoreMode
import com.nichx.niplayer.database.NiplayerDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份编排器（v2 架构）。
 *
 * 通过 Hilt 多绑定注入所有 [BackupItem]，仅负责组装 / 校验 / 分发 / 版本兼容。
 * 新增备份项只需新建实现类 + @IntoSet 注册，本类无需改动（开闭原则）。
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
 *
 * ## 恢复流程（两阶段）
 * 1. **校验**：把所有已知项解码一遍，任何一项失败即中止，不写任何数据；
 * 2. **应用**：所有 [DatabaseBackupItem] 按 [BackupItem.restoreOrder] 在**同一个 Room 事务**内
 *    恢复（存储源 → 扩展目录 → 加密文件夹 → 快速访问），事务提交后再写 MMKV 设置。
 *
 * 恢复模式与业务身份对账见 [BackupTable] / [planMerge]。
 *
 * v1 → v2 兼容：旧版备份是平铺字段（mediaLibraries/lrcApiUrl/appSettings 在顶层），
 * 导入时检测 version==1，把顶层字段搬到 payload 内，并把 lrcApiUrl/lrcApiAuth/assrtToken
 * 并入 appSettings，再交给各 BackupItem 恢复。
 */
@Singleton
class BackupManager @Inject constructor(
    private val db: NiplayerDatabase,
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

    private val databaseItems: List<DatabaseBackupItem>
        get() = items.filterIsInstance<DatabaseBackupItem>()

    private val nonDatabaseItems: List<BackupItem>
        get() = items.filterNot { it is DatabaseBackupItem }

    /**
     * 导出用户数据为 JSON 字符串。
     *
     * 数据库表在同一个读事务内取快照，避免并发写入导致「半新半旧」；
     * 返回 null 的项不写入 payload。
     */
    suspend fun exportToJson(): String {
        val payload = sortedMapOf<String, Any?>()
        db.withTransaction {
            for (item in databaseItems.sortedBy { it.key }) {
                item.snapshot()?.let { payload[item.key] = it }
            }
        }
        for (item in nonDatabaseItems.sortedBy { it.key }) {
            item.snapshot()?.let { payload[item.key] = it }
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
     * @param mode 恢复模式：MERGE 按业务身份合并（本机独有保留）；REPLACE 镜像到备份状态
     * @return 恢复摘要与告警，供 UI 展示
     * @throws IllegalArgumentException 备份结构无效 / 版本不支持 / 任一备份项解码失败
     */
    suspend fun importFromJson(
        json: String,
        mode: RestoreMode = RestoreMode.MERGE,
    ): BackupSummary {
        val root: Map<String, Any?> = payloadAdapter.fromJsonValue(
            // 用 org.json.JSONObject 解析以区分 v1/v2
            JSONObject(json).toMap(),
        ) ?: throw IllegalArgumentException("无效的备份文件")

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

        val ctx = RestoreContext(mode)
        val itemByKey = items.associateBy { it.key }
        val entries = mutableListOf<Entry>()
        for ((key, data) in payload) {
            val item = itemByKey[key]
            if (item == null) {
                // 已下线的历史备份项（如 v1 的视频书签）静默跳过；其余未知项提示用户
                if (key !in LEGACY_IGNORED_KEYS) ctx.warn("忽略未知备份项：$key")
                continue
            }
            entries += Entry(key, item, data)
        }

        // 阶段 1：全部解码校验，任何一项失败都不会产生写入
        entries.forEach { it.item.validate(it.data) }

        // 阶段 2a：数据库项统一事务，按依赖顺序恢复
        val dbEntries = entries
            .filter { it.item is DatabaseBackupItem }
            .sortedWith(compareBy({ it.item.restoreOrder }, { it.key }))
        db.withTransaction {
            dbEntries.forEach { it.item.restore(it.data, ctx) }
        }

        // 阶段 2b：设置项在数据库提交后写入，并做库 id 重映射
        entries
            .filterNot { it.item is DatabaseBackupItem }
            .sortedBy { it.key }
            .forEach { entry ->
                runCatching { entry.item.restore(entry.data, ctx) }
                    .onFailure { ctx.warn("${entry.key} 恢复失败：${it.message ?: "未知错误"}") }
            }

        val descriptions = entries.mapNotNull { it.item.describe(it.data) }
        return BackupSummary(descriptions, ctx.warnings)
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
        // 注意：videoBookmarks 是 v1 备份格式的**历史 key**。视频书签功能已于 DB v20 移除，
        // 该 key 不再对应任何 BackupItem，恢复时会被下方的 `itemByKey[key] ?: continue` 安全跳过。
        // 保留它是为了让 v1→v2 的字段搬运保持忠实 —— 不改变历史备份文件的解析行为。
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

    private data class Entry(
        val key: String,
        val item: BackupItem,
        val data: Any?,
    )

    private companion object {
        const val BACKUP_VERSION = 2

        /** 历史上存在、现已下线的备份 key：恢复时静默跳过，不打扰用户。 */
        val LEGACY_IGNORED_KEYS = setOf("videoBookmarks")
    }
}

/**
 * 备份摘要，供 UI 展示恢复结果。
 *
 * @param descriptions 各备份项的人类可读摘要（如 "存储源: 3 条"、"快速访问: 5 条"）
 * @param warnings 跳过项 / 需用户注意的提示（未知 key、引用缺失、设置写入失败等）
 */
data class BackupSummary(
    val descriptions: List<String>,
    val warnings: List<String> = emptyList(),
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
