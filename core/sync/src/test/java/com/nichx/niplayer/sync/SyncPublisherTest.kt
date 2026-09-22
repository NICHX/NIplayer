package com.nichx.niplayer.sync

import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.database.normalizeBaseUrl
import com.nichx.niplayer.database.syncKey
import com.nichx.niplayer.storage.FilePrecondition
import com.nichx.niplayer.storage.RemoteFile
import com.nichx.niplayer.storage.WriteOutcome
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * [SyncPublisher] 的并发 / 重试语义测试（Fake 传输层，无 IO）。
 *
 * 这一段是同步里唯一无法靠"读代码"或本地端到端验证兜住的部分：它同时承担并发保护
 * （条件写）、抢占发现（读回校验）与收敛（重读重合并）。锁定的不变式：
 *
 * - 前置条件随云端版本状态退级：`MustNotExist` → 强 ETag 的 `If-Match` →
 *   `If-Unmodified-Since` → 无条件；
 * - 云端已是折叠结果时**不写入**（稳态同步只读）；
 * - 冲突或读回不一致后重读重合并，且**抢占方的记录也被并入最终内容**（不丢数据）；
 * - 云端非空但损坏时中止且不覆盖；0 字节文件按"无候选"处理但保留并发保护。
 */
class SyncPublisherTest {

    private val adapter: JsonAdapter<PlayHistorySyncFile> =
        Moshi.Builder().build().adapter(PlayHistorySyncFile::class.java)

    // ==================== 前置条件 ====================

    @Test
    fun `云端不存在时以必须不存在为前置条件创建文件`() = runTest {
        val remote = FakeRemote()

        val plan = publisher(remote).publish(local(records = listOf(entity(updatedAt = 100))))

        assertEquals(1, remote.writes.size)
        assertEquals(FilePrecondition.MustNotExist, remote.writes.single().precondition)
        assertEquals(listOf(record(updatedAt = 100)), decode(remote).records)
        assertEquals(1, plan.file.records.size)
    }

    @Test
    fun `云端有强 ETag 时以 If-Match 为前置条件`() = runTest {
        val remote = FakeRemote()
        remote.content = encode(file(records = listOf(record(path = OTHER_PATH, updatedAt = 50))))
        remote.etag = "\"v1\""

        publisher(remote).publish(local(records = listOf(entity(updatedAt = 100))))

        assertEquals(FilePrecondition.MatchesEtag("\"v1\""), remote.writes.single().precondition)
    }

    @Test
    fun `云端没有 ETag 时退化为 If-Unmodified-Since`() = runTest {
        val remote = FakeRemote()
        remote.content = encode(file(records = listOf(record(path = OTHER_PATH, updatedAt = 50))))
        remote.etag = null
        remote.lastModified = MODIFIED_AT

        publisher(remote).publish(local(records = listOf(entity(updatedAt = 100))))

        assertEquals(FilePrecondition.UnmodifiedSince(MODIFIED_AT), remote.writes.single().precondition)
    }

    @Test
    fun `云端既无 ETag 也无修改时间时退化为无条件写入`() = runTest {
        val remote = FakeRemote()
        remote.content = encode(file(records = listOf(record(path = OTHER_PATH, updatedAt = 50))))
        remote.etag = null
        remote.lastModified = 0

        publisher(remote).publish(local(records = listOf(entity(updatedAt = 100))))

        assertEquals(1, remote.writes.size)
        assertNull(remote.writes.single().precondition)
    }

    // ==================== 不必要的写入 ====================

    @Test
    fun `云端已是本次折叠结果时不写入`() = runTest {
        val localState = local(records = listOf(entity(updatedAt = 100)))
        val first = FakeRemote()
        publisher(first).publish(localState)

        val second = FakeRemote()
        second.content = first.content
        second.etag = first.etag

        publisher(second).publish(localState)

        assertTrue("稳态同步不应产生写请求", second.writes.isEmpty())
        assertEquals(1, second.readCount)
    }

    @Test
    fun `云端与本机都没有内容时不创建空文件`() = runTest {
        val remote = FakeRemote()

        publisher(remote).publish(local())

        assertTrue(remote.writes.isEmpty())
    }

    // ==================== 并发与收敛 ====================

    @Test
    fun `冲突后重读重合并且最终内容含双方记录`() = runTest {
        val remote = FakeRemote()
        remote.content = encode(file(records = listOf(record(path = OTHER_PATH, updatedAt = 50))))
        remote.etag = "\"v1\""
        var remainingConflicts = 1
        remote.onBeforeWrite = { if (remainingConflicts-- > 0) WriteOutcome.Conflicted else null }

        val plan = publisher(remote).publish(local(records = listOf(entity(updatedAt = 100))))

        assertEquals(2, remote.writes.size)
        assertEquals(
            setOf(syncKey(BASE, PATH), syncKey(BASE, OTHER_PATH)),
            decode(remote).records.map { it.key }.toSet(),
        )
        assertEquals(2, plan.file.records.size)
    }

    @Test
    fun `读回不一致时重试并把抢占方的记录并入最终内容`() = runTest {
        val remote = FakeRemote()
        val intruder = record(path = OTHER_PATH, updatedAt = 500)
        var remainingClobbers = 1
        remote.onBeforeWrite = { payload ->
            if (remainingClobbers-- > 0) {
                // 我们刚写完就被另一台设备覆盖：写请求返回成功，但云端内容是别人的
                remote.content = encode(file(records = listOf(intruder)))
                WriteOutcome.Success("\"intruder\"")
            } else {
                null
            }
        }

        val plan = publisher(remote).publish(local(records = listOf(entity(updatedAt = 100))))

        assertEquals(2, remote.writes.size)
        assertEquals(
            setOf(syncKey(BASE, PATH), syncKey(BASE, OTHER_PATH)),
            decode(remote).records.map { it.key }.toSet(),
        )
        assertEquals(2, plan.file.records.size)
    }

    @Test
    fun `持续冲突时抛出冲突异常`() = runTest {
        val remote = FakeRemote()
        remote.content = encode(file(records = listOf(record(path = OTHER_PATH, updatedAt = 50))))
        remote.onBeforeWrite = { WriteOutcome.Conflicted }

        val error = publishError(publisher(remote), local(records = listOf(entity())))

        assertTrue("期望冲突异常，实际 $error", error is SyncPublishException.Conflict)
    }

    @Test
    fun `写入持续失败时抛出上传失败异常`() = runTest {
        val remote = FakeRemote()
        remote.onBeforeWrite = { WriteOutcome.Failed("disk full") }

        val error = publishError(publisher(remote), local(records = listOf(entity())))

        assertTrue("期望上传失败异常，实际 $error", error is SyncPublishException.UploadFailed)
    }

    // ==================== 异常云端内容 ====================

    @Test
    fun `云端文件损坏时中止且不覆盖`() = runTest {
        val remote = FakeRemote()
        remote.content = "{ not json".toByteArray(Charsets.UTF_8)

        val error = publishError(publisher(remote), local(records = listOf(entity())))

        assertTrue("期望损坏异常，实际 $error", error is SyncPublishException.CorruptRemoteFile)
        assertTrue("损坏时不应写回", remote.writes.isEmpty())
    }

    @Test
    fun `云端 0 字节时按无候选处理但保留并发保护`() = runTest {
        val remote = FakeRemote()
        remote.content = ByteArray(0)
        remote.etag = "\"empty\""

        publisher(remote).publish(local(records = listOf(entity(updatedAt = 100))))

        assertEquals(FilePrecondition.MatchesEtag("\"empty\""), remote.writes.single().precondition)
        assertEquals(1, decode(remote).records.size)
    }

    // ==================== 遗留来源 ====================

    @Test
    fun `遗留来源的墓碑参与折叠并被写回`() = runTest {
        val remote = FakeRemote()
        val legacy = file(deletes = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200)))

        val plan = publisher(remote)
            .publish(local(records = listOf(entity(id = 5, updatedAt = 100))), listOf(legacy))

        assertEquals(listOf(5), plan.deletes)
        assertEquals(listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200)), decode(remote).deletes)
    }

    // ==================== fixtures ====================

    /** 极简 Fake 传输层：单文件、可编程的写入结果。 */
    private class FakeRemote {
        var content: ByteArray? = null
        var etag: String? = null
        var lastModified: Long = 0L
        var readCount: Int = 0

        /** 写入前回调：返回非 null 则用它作为结果（并可自行改写 [content] 模拟他人覆盖）。 */
        var onBeforeWrite: ((ByteArray) -> WriteOutcome?)? = null

        val writes = ArrayList<Write>()

        data class Write(val data: ByteArray, val precondition: FilePrecondition?)

        fun read(): RemoteFile? {
            readCount++
            return content?.let { RemoteFile(it, etag, lastModified) }
        }

        fun write(data: ByteArray, precondition: FilePrecondition?): WriteOutcome {
            writes += Write(data, precondition)
            onBeforeWrite?.invoke(data)?.let { return it }
            content = data
            etag = "\"v${writes.size + 1}\""
            return WriteOutcome.Success(etag)
        }
    }

    private fun publisher(remote: FakeRemote) = SyncPublisher(
        readRemote = { remote.read() },
        writeRemote = { data, precondition -> remote.write(data, precondition) },
        adapter = adapter,
        backoffMs = 0,
    )

    /** 执行一次发布并返回异常（无异常时为 null）；取消照常向上传播。 */
    private suspend fun publishError(
        publisher: SyncPublisher,
        local: SyncMerge.LocalState,
        legacy: List<PlayHistorySyncFile> = emptyList(),
    ): Throwable? = try {
        publisher.publish(local, legacy)
        null
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    private fun encode(file: PlayHistorySyncFile): ByteArray =
        adapter.toJson(file).toByteArray(Charsets.UTF_8)

    private fun decode(remote: FakeRemote): PlayHistorySyncFile {
        val content = requireNotNull(remote.content) { "云端文件不存在" }
        return requireNotNull(adapter.fromJson(content.toString(Charsets.UTF_8)))
    }

    private fun local(records: List<PlayHistoryEntity> = emptyList()) = SyncMerge.LocalState(
        records = records,
        tombstones = emptyList(),
        storageIdByNormalizedBaseUrl = mapOf(normalizeBaseUrl(BASE) to STORAGE_ID),
        normalizedBaseUrlByStorageId = mapOf(STORAGE_ID to normalizeBaseUrl(BASE)),
    )

    private fun file(
        records: List<SyncRecord> = emptyList(),
        deletes: List<SyncDelete> = emptyList(),
    ) = PlayHistorySyncFile(records = records, deletes = deletes)

    private fun entity(
        id: Int = 0,
        path: String = PATH,
        updatedAt: Long = 100,
    ) = PlayHistoryEntity(
        id = id,
        videoName = path.substringAfterLast('/'),
        url = "$BASE$path",
        mediaType = MediaType.WEBDAV_SERVER,
        videoPosition = 10,
        videoDuration = 1_000,
        playTime = Date(updatedAt),
        uniqueKey = "$STORAGE_ID:$path",
        storagePath = path,
        storageId = STORAGE_ID,
        updatedAt = updatedAt,
    )

    private fun record(
        path: String = PATH,
        updatedAt: Long = 100,
    ) = SyncRecord(
        key = syncKey(BASE, path),
        baseUrl = BASE,
        videoName = path.substringAfterLast('/'),
        url = "$BASE$path",
        mediaType = MediaType.WEBDAV_SERVER.value,
        videoPosition = 10,
        videoDuration = 1_000,
        playTime = updatedAt,
        httpHeader = null,
        storagePath = path,
        updatedAt = updatedAt,
    )

    private companion object {
        const val STORAGE_ID = 1
        const val BASE = "https://dav.example.com/dav"
        const val PATH = "/movies/a.mkv"
        const val OTHER_PATH = "/movies/b.mkv"
        const val MODIFIED_AT = 1_700_000_000_000L
    }
}
