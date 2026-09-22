package com.nichx.niplayer.sync

import com.nichx.niplayer.storage.FilePrecondition
import com.nichx.niplayer.storage.RemoteFile
import com.nichx.niplayer.storage.WriteOutcome
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.delay

/**
 * 「读云端 → 折叠 → 条件写 → 读回校验」的编排器。
 *
 * 只依赖两个窄回调（读 / 写）而不是整个 `Storage`，也不引用任何 Android 类型 —— 因此整段
 * 并发与重试语义可以用纯 JVM 单测覆盖。这是同步里最容易出错的一段：它同时承担并发保护
 * （条件写）、抢占发现（读回校验）与收敛（重读重合并），却无法靠单元测试以外的静态阅读确认。
 *
 * 每轮都用**未改动的本机状态**重新计算，循环内不产生累积副作用：本地 DB 由调用方在拿到返回的
 * 计划后一次性落地。
 */
internal class SyncPublisher(
    private val readRemote: suspend () -> RemoteFile?,
    private val writeRemote: suspend (ByteArray, FilePrecondition?) -> WriteOutcome,
    private val adapter: JsonAdapter<PlayHistorySyncFile>,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val backoffMs: Long = BACKOFF_MS,
) {

    /**
     * 把折叠结果发布到云端，返回**成功发布的那一轮**所对应的计划。
     *
     * @throws SyncPublishException.Conflict 前置条件持续不满足（云端被其他设备反复修改）
     * @throws SyncPublishException.UploadFailed 写入持续失败
     * @throws SyncPublishException.CorruptRemoteFile 云端文件非空但无法解析
     */
    suspend fun publish(
        local: SyncMerge.LocalState,
        legacyFiles: List<PlayHistorySyncFile> = emptyList(),
    ): SyncMerge.MergePlan {
        var lastFailure: WriteOutcome.Failed? = null
        for (attempt in 0 until maxAttempts) {
            if (attempt > 0) delay(backoffMs * attempt)

            val remote = readRemote()
            // 0 字节文件（撕裂写残留）不含任何信息，按"没有候选"处理；但仍用它的 ETag 做条件写，
            // 避免把它当成"文件不存在"而绕过并发保护
            val remoteFile = remote?.takeIf { it.data.isNotEmpty() }?.let { decode(it) }
            val plan = SyncMerge.plan(local, listOfNotNull(remoteFile) + legacyFiles)
            val payload = adapter.toJson(plan.file).toByteArray(Charsets.UTF_8)

            // 云端还没有这个文件、本机也没有任何可发布内容：不创建空文件
            if (remote == null && plan.file.isEmpty()) return plan
            // 云端已是本次折叠结果：无需写入（稳态同步只读不写）
            if (remote != null && remote.data.contentEquals(payload)) return plan

            when (val outcome = writeRemote(payload, preconditionOf(remote))) {
                is WriteOutcome.Success -> {
                    if (readBackMatches(payload)) return plan
                    lastFailure = null
                }

                WriteOutcome.Conflicted -> lastFailure = null

                is WriteOutcome.Failed -> lastFailure = outcome
            }
        }
        throw lastFailure
            ?.let { SyncPublishException.UploadFailed(it.message) }
            ?: SyncPublishException.Conflict()
    }

    /**
     * 构造条件写前置条件。
     *
     * 强 ETag 最可靠；没有 ETag 时退到 `If-Unmodified-Since`（秒级粒度）；两者都没有就只能靠
     * 读回校验兜底（弱验证符由传输层过滤，不会发出 `If-Match`）。
     */
    private fun preconditionOf(remote: RemoteFile?): FilePrecondition? {
        if (remote == null) return FilePrecondition.MustNotExist
        val etag = remote.etag
        if (etag != null) return FilePrecondition.MatchesEtag(etag)
        return if (remote.lastModified > 0) FilePrecondition.UnmodifiedSince(remote.lastModified) else null
    }

    /**
     * 读回校验。
     *
     * 上传成功不等于写进去了：OkHttp 会跟随 PUT 的 301/302 并把请求重放成 GET（丢掉 body），
     * 此时响应仍是 2xx；也可能刚写完就被其他设备覆盖。两种情况都必须发现并重来。
     */
    private suspend fun readBackMatches(payload: ByteArray): Boolean {
        val readBack = try {
            readRemote()?.data
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // 读回动作本身失败（瞬时抖动）不阻断本轮：代价是失去一次抢占发现机会
            return true
        }
        return readBack != null && readBack.contentEquals(payload)
    }

    /** 解析云端文件；非空但损坏时中止本轮 —— 不能当作空文件，否则会丢掉其中的墓碑。 */
    private fun decode(remote: RemoteFile): PlayHistorySyncFile =
        try {
            adapter.fromJson(remote.data.toString(Charsets.UTF_8))
                ?: throw SyncPublishException.CorruptRemoteFile()
        } catch (e: SyncPublishException) {
            throw e
        } catch (e: Exception) {
            throw SyncPublishException.CorruptRemoteFile(e)
        }

    private companion object {
        /** 条件写失败后的最大重试轮数（含冲突重读与读回不一致）。 */
        const val MAX_ATTEMPTS = 5

        /** 重试退避基数（ms）。 */
        const val BACKOFF_MS = 500L
    }
}

/** 发布阶段的可预期失败；由调用方转成面向用户的提示。 */
internal sealed class SyncPublishException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {

    /** 前置条件持续不满足：云端文件被其他设备反复修改。 */
    class Conflict : SyncPublishException()

    /** 写入持续失败（网络 / 权限 / 服务端错误）。 */
    class UploadFailed(message: String?) : SyncPublishException(message)

    /** 云端文件非空但无法解析。 */
    class CorruptRemoteFile(cause: Throwable? = null) : SyncPublishException(cause?.message, cause)
}
