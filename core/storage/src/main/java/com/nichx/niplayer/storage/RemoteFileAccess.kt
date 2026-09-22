package com.nichx.niplayer.storage

/**
 * 远端文件内容及其版本元信息。
 *
 * [etag] / [lastModified] 与 [data] **来自同一次响应**，因此它们共同描述的就是 [data] 对应的
 * 那个版本 —— 这是把它们用作条件写前置条件的前提。若两者分别来自 PROPFIND 与 GET（两次请求），
 * 就可能指向不同版本，条件写会失去意义。
 *
 * 用普通类而非 data class：内容比较应为引用比较，避免对字节数组做逐字节 equals。
 */
class RemoteFile(
    val data: ByteArray,
    /** 服务端 ETag（含引号原样返回）；不可用时为 null。 */
    val etag: String?,
    /** 最后修改时间（ms）；不可用时为 0。 */
    val lastModified: Long,
)

/**
 * 条件写的前置条件。
 *
 * 由调用方按已读到的版本构造，交给传输层转成 HTTP 条件请求，使"读到旧版本后再写"被服务器
 * 拒绝（而不是静默覆盖他人的修改）。
 */
sealed interface FilePrecondition {

    /** 要求目标**不存在**（首次创建）：若服务器上已有该文件，写入必须失败。 */
    data object MustNotExist : FilePrecondition

    /** 要求目标内容的强 ETag 与 [etag] 一致（ETag 原样透传，含引号）。 */
    data class MatchesEtag(val etag: String) : FilePrecondition

    /**
     * 要求目标的最后修改时间不晚于 [epochMillis]。
     *
     * 弱前置：HTTP 日期只有秒级粒度。用于不提供强 ETag 的服务器（此时 ETag 无法用于
     * `If-Match` 的强比较）。
     */
    data class UnmodifiedSince(val epochMillis: Long) : FilePrecondition
}

/** 条件写的三种结果。 */
sealed interface WriteOutcome {

    /** 写入成功。[etag] 为服务器返回的新 ETag；服务器未返回时为 null。 */
    data class Success(val etag: String?) : WriteOutcome

    /** 前置条件不满足（目标已被他人修改）。调用方应重新读取、重新合并后重试。 */
    data object Conflicted : WriteOutcome

    /** 写入失败（网络、权限、服务端错误等）。[message] 供日志。 */
    data class Failed(val message: String?) : WriteOutcome
}
