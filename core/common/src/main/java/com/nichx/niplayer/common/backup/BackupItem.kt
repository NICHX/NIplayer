package com.nichx.niplayer.common.backup

/**
 * 备份项接口。每个需要备份的数据源（数据库表 / MMKV 设置组）实现此接口，
 * 通过 Hilt @IntoSet 多绑定自动注册到 BackupManager，新增备份项无需改动 BackupManager。
 *
 * **A1 架构修复（2026-09-21）**：本 SPI 原先位于 :core:database，导致「设置组」类备份项
 * （AppSettingsBackup）必须留在 Room 模块里，进而使 :core:database 反向依赖 :core:datastore。
 * SPI 下移到无任何工程依赖的 :core:common 后，两边各自实现并注册，该倒置消除。
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
     * 恢复顺序。数值越小越先恢复，仅对数据库项生效。
     *
     * 依赖关系：存储源（0）必须先于引用其自增 id 的快速访问 / 加密文件夹 / 设置项，
     * 否则依赖项无法解析到本机库 id。
     */
    val restoreOrder: Int get() = 0

    /**
     * 导出快照。
     *
     * @return 该备份项的松散值（List / Map / 标量），由 BackupManager 统一序列化；
     *         返回 null 表示当前无数据，导出时跳过此 key
     */
    suspend fun snapshot(): Any?

    /**
     * 恢复前预校验：只解码不写库。
     *
     * BackupManager 会先对所有项调用本方法，任何一项抛异常即整体中止，
     * 避免「前面的表已写入、后面的表解码失败」造成半恢复。
     */
    fun validate(data: Any?)

    /**
     * 从快照恢复。
     *
     * @param data 备份中的松散值（与 snapshot 返回类型对应）；null 表示旧版备份无此项，实现应跳过
     * @param ctx 恢复上下文：模式、存储源 id 映射、告警收集
     */
    suspend fun restore(data: Any?, ctx: RestoreContext)

    /**
     * 生成人类可读的恢复摘要。
     *
     * @param data 备份中的松散值（与 [restore] 收到的相同）；null 表示无此备份项
     * @return 摘要文本（如 "存储源: 3 条"），null 表示不展示
     */
    fun describe(data: Any?): String?
}

/**
 * 数据库备份项标记。
 *
 * BackupManager 会把所有 [DatabaseBackupItem] 放进**同一个 Room 事务**中按 [restoreOrder]
 * 依次恢复，非数据库项（MMKV 设置）在事务提交后再写入 —— 避免数据库半写，
 * 也避免设置在数据表失败后提前生效。
 */
interface DatabaseBackupItem : BackupItem

/**
 * 恢复模式。
 *
 * - [MERGE] 迁移语义：按**业务身份**（存储地址 / 路径等自然键）合并，备份覆盖同项，
 *   本机独有数据保留；自增 id 不会跨设备复用。
 * - [REPLACE] 镜像语义：按业务身份合并后，删除备份中不存在的本机项，
 *   但保留本机已有项的 id，避免播放历史 / 视频索引等未备份数据失去引用。
 */
enum class RestoreMode {
    MERGE,
    REPLACE,
}

/**
 * 恢复上下文。
 *
 * 由 BackupManager 创建并按顺序传给各备份项，负责两件事：
 * 1. **存储源 id 映射**：备份里的自增 id 是源设备的，恢复时必须换成目标设备的本机 id；
 * 2. **告警收集**：跳过 / 需要用户注意的项统一回传给 UI，而不是静默丢弃。
 */
class RestoreContext(
    val mode: RestoreMode,
) {
    private val libraryIdMap = mutableMapOf<Int, Int>()
    private val warningList = mutableListOf<String>()

    /** 备份里的存储源 id → 本机存储源 id；未出现在备份中时返回 null。 */
    fun mapLibraryId(backupId: Int): Int? = libraryIdMap[backupId]?.takeIf { it > 0 }

    /** 存储源落库后登记 id 映射。 */
    fun registerLibraryId(backupId: Int, localId: Int) {
        if (backupId > 0 && localId > 0) libraryIdMap[backupId] = localId
    }

    /** 记录一条面向用户的告警（最多保留 20 条，避免异常备份撑爆弹窗）。 */
    fun warn(message: String) {
        if (warningList.size < MAX_WARNINGS) warningList += message
    }

    /** 恢复过程中产生的告警，供摘要展示。 */
    val warnings: List<String> get() = warningList

    private companion object {
        const val MAX_WARNINGS = 20
    }
}
