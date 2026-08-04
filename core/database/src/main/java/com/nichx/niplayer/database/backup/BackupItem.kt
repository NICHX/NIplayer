package com.nichx.niplayer.database.backup

/**
 * 备份项接口。每个需要备份的数据源（数据库表 / MMKV 设置组）实现此接口，
 * 通过 Hilt @IntoSet 多绑定自动注册到 [BackupManager]，新增备份项无需改动 BackupManager。
 *
 * 设计要点（Moshi 风格，无 JsonElement DOM）：
 * - [snapshot] / [restore] 基于 [Any]（松散 Java 对象，Moshi 的 toJsonValue/fromJsonValue 天然支持），
 *   各实现类自行用 Moshi JsonAdapter 处理具体类型
 * - [restore] 收到 null 表示旧版备份缺失该项，应跳过（向后兼容）
 * - [describe] 供 UI 展示恢复摘要，返回 null 表示该项无数据可展示
 */
interface BackupItem {

    /** 备份项唯一标识，作为 JSON 字段名与摘要 key。 */
    val key: String

    /**
     * 导出快照。
     *
     * @return 该备份项的松散值（List / Map / 标量），由 BackupManager 统一序列化；
     *         返回 null 表示当前无数据，导出时跳过此 key
     */
    suspend fun snapshot(): Any?

    /**
     * 从快照恢复。
     *
     * @param data 备份中的松散值（与 snapshot 返回类型对应）；null 表示旧版备份无此项，实现应跳过
     * @param mode 恢复模式（REPLACE 清空后重插 / MERGE 按主键合并保留本机独有数据）
     */
    suspend fun restore(data: Any?, mode: RestoreMode)

    /**
     * 生成人类可读的恢复摘要。
     *
     * @param data 备份中的松散值（与 [restore] 收到的相同）；null 表示无此备份项
     * @return 摘要文本（如 "存储源: 3 条"），null 表示不展示
     */
    fun describe(data: Any?): String?
}

/**
 * 恢复模式。
 *
 * - [REPLACE] 还原语义：先清空本机数据，再插入备份内容（等同旧版覆盖式恢复）
 * - [MERGE] 迁移语义：按主键合并，备份优先覆盖同主键项，本机独有的保留
 */
enum class RestoreMode {
    REPLACE,
    MERGE,
}
