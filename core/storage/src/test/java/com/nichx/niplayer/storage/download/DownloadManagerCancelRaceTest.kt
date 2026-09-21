package com.nichx.niplayer.storage.download

import android.content.Context
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.database.entity.DownloadTaskEntity
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.InputStream
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [DownloadManager] 取消与续传路径的回归测试。
 *
 * ## 为什么需要 MockK
 *
 * `DownloadManager` 的依赖中 `Context`（抽象类）与 `StorageFactory`（final 类）无法用简单
 * fake 替代，故本模块引入 MockK（E1）；DAO 与 `Storage` 是接口，relaxed mock 即可。
 *
 * ## 一次重要的自我纠错（记录了本测试的由来）
 *
 * 审计报告 #1 最初判定：`cancelTask` 与 `processTask` 的 `CancellationException` 分支
 * 争抢 `cancellingTasks` 标记，若 `cancelTask` 先移除标记，`processTask` 会把状态覆盖为
 * **PAUSED**，导致「文件已删但状态可恢复」→ 恢复后产出前段缺失的损坏文件。
 *
 * 写成本测试后用**变异测试**验证（临时把 `cancelTask` 改回提前移除标记的写法）——
 * 测试**依然通过**，说明该覆盖根本不会发生。追查后确认原因：
 * Room 生成的 suspend DAO 走 `performSuspending` → `compatCoroutineExecute`
 * → `withContext(...)`（`DBUtil.android.kt:118`），而 `withContext` 会 `ensureActive()`。
 * **在已取消的协程里，`processTask` catch 块中的 `updateState(...)` 会直接抛
 * CancellationException，写入根本落不了库** —— 两个分支都是无效代码，
 * 终态实际只由 `cancelTask` / `pauseTask` 各自的（未取消的）协程写入。
 * 故 #1 描述的 PAUSED 覆盖是**误报**（详见审计报告与修复记录）。
 *
 * ## 本测试真正守护的不变量
 *
 * `cancelTask` 中「状态终结」必须**先于**「文件删除」。原实现顺序相反
 * （`deleteTaskFile` → `updateState(CANCELLED)`），若进程在两者之间死亡，会留下
 * 「任务停在 DOWNLOADING 但文件已消失」的僵死状态 —— 调度循环只拾取 WAITING，任务无法恢复。
 * 这正是本测试第一条断言所锁定的行为（已在变异测试中确认可捕获）。
 */
class DownloadManagerCancelRaceTest {

    private lateinit var cacheDir: File

    /** 每次状态落库时的 (状态, 目标文件当时是否存在)，按发生顺序记录。 */
    private val stateWrites = Collections.synchronizedList(mutableListOf<Pair<Int, Boolean>>())

    /** 每次进度落库时记录的已下载字节数。 */
    private val progressWrites = Collections.synchronizedList(mutableListOf<Long>())

    @Before
    fun setUp() {
        cacheDir = File(System.getProperty("java.io.tmpdir"), "niplayer-dl-test-${System.nanoTime()}")
        cacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    // ==================== 取消路径 ====================

    @Test
    fun `取消时状态终结必须先于文件删除 且终态为 CANCELLED`() {
        val task = DownloadTaskEntity(
            id = 1L,
            storageId = 1,
            fileName = "movie.mkv",
            filePath = "Movies/movie.mkv",
            uniqueKey = "uk-1",
            totalBytes = 100L * 1024 * 1024,
            downloadedBytes = 0L,
            state = DownloadState.WAITING,
        )

        val manager = newManager(task)
        val targetFile = File(cacheDir, "download/movie.mkv")

        // 等调度循环拾取任务、进入下载，**并已真正建出目标文件**。
        //
        // ⚠️ 不能只等「状态写入 DOWNLOADING」就断言文件已存在：DownloadManager 里
        // `updateState(DOWNLOADING)`（DownloadManager.kt:335）发生在
        // `targetFile.createNewFile()`（同文件 :422）**之前**，两者之间存在窗口期。
        // 本机快、CI 慢 —— CI 上会在窗口内撞到 AssertionError（实测 CI run 130）。
        // 把「文件已存在」并入等待条件即可消除该时序假设，且语义更强：本用例要保证的是
        // 「取消发生在**写入循环进行中**」，而不只是「状态字段已更新」。
        awaitUntil("任务未进入 DOWNLOADING 或目标文件未创建", timeoutMs = 10_000) {
            synchronized(stateWrites) { stateWrites.any { it.first == DownloadState.DOWNLOADING } } &&
                targetFile.exists()
        }
        assertTrue("目标文件应已创建", targetFile.exists())

        // 触发取消 —— 此时 processTask 正阻塞在写入循环中
        manager.cancelTask(task.id)

        // 等取消流程收尾：终态落为 CANCELLED 且文件被删除
        awaitUntil("取消未在超时内收尾", timeoutMs = 15_000) {
            val last = synchronized(stateWrites) { stateWrites.lastOrNull()?.first }
            last == DownloadState.CANCELLED && !targetFile.exists()
        }

        val writes = synchronized(stateWrites) { stateWrites.toList() }
        val states = writes.map { it.first }

        assertEquals("取消后的终态必须是 CANCELLED", DownloadState.CANCELLED, states.last())

        // 核心不变量：第一次写入 CANCELLED 时，文件必须还在
        // （原实现先删文件再写状态，此处会失败）
        val firstCancelled = writes.firstOrNull { it.first == DownloadState.CANCELLED }
        assertTrue("取消流程必须写入 CANCELLED", firstCancelled != null)
        assertTrue(
            "写入 CANCELLED 时目标文件应仍然存在 —— 否则进程若在此刻死亡，会留下" +
                "「任务停在 DOWNLOADING 但文件已消失」的僵死状态（调度循环只拾取 WAITING）",
            firstCancelled!!.second,
        )

        assertFalse("取消后已下载文件必须被删除", targetFile.exists())
    }

    // ==================== 续传偏移校验（#2） ====================

    @Test
    fun `本地文件长度与续传偏移不符时重置为 0 重新下载`() {
        val task = DownloadTaskEntity(
            id = 2L,
            storageId = 1,
            fileName = "resume.mkv",
            filePath = "Movies/resume.mkv",
            uniqueKey = "uk-2",
            totalBytes = 100L * 1024 * 1024,
            // 记录 1MB 已下载，但本地文件实际为空 —— 二者不符
            downloadedBytes = 1024L * 1024,
            state = DownloadState.WAITING,
        )

        val targetFile = File(cacheDir, "download/resume.mkv")
        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(ByteArray(0))

        newManager(task)

        awaitUntil("未观察到进度被重置为 0", timeoutMs = 10_000) {
            synchronized(progressWrites) { progressWrites.contains(0L) }
        }

        val bytes = synchronized(progressWrites) { progressWrites.toList() }
        assertTrue("长度不符时必须把偏移重置为 0（实际进度序列：$bytes）", bytes.contains(0L))
        assertFalse(
            "不得以 1MB 偏移续传 —— 那会从文件真实末尾追加，写出与远程错位的损坏文件",
            bytes.contains(1024L * 1024),
        )
    }

    // ==================== 测试夹具 ====================

    private fun newManager(task: DownloadTaskEntity): DownloadManager {
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns cacheDir
        every { context.getString(any<Int>()) } returns "test-error"

        val targetFile = File(cacheDir, "download/${task.fileName}")

        val taskDao = mockk<DownloadTaskDao>(relaxed = true)
        coEvery { taskDao.getByStates(any()) } answers {
            // 仅当任务仍处于 WAITING 时交给调度循环，避免重复启动
            if (task.state == DownloadState.WAITING) listOf(task) else emptyList()
        }
        coEvery { taskDao.getById(any()) } answers { task }
        // 注意：MockK 的 answer 块默认不挂起，而被测代码正是依赖 DAO 调用的挂起点来观察
        // 协程取消（真实 Room suspend DAO 会在挂起点抛 CancellationException，见类注释）。
        // 因此这里显式 yield()，提供真实挂起点。
        coEvery { taskDao.updateState(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.yield()
            val state = secondArg<Int>()
            synchronized(stateWrites) {
                task.state = state
                stateWrites += state to targetFile.exists()
            }
        }
        coEvery { taskDao.updateProgress(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.yield()
            val bytes = secondArg<Long>()
            val state = thirdArg<Int>()
            synchronized(stateWrites) {
                task.downloadedBytes = bytes
                task.state = state
                stateWrites += state to targetFile.exists()
                progressWrites += bytes
            }
        }

        val library = mockk<MediaLibraryEntity>(relaxed = true)
        val libraryDao = mockk<MediaLibraryDao>(relaxed = true)
        coEvery { libraryDao.getById(any()) } returns library

        val storage = mockk<Storage>(relaxed = true)
        coEvery { storage.openInputStream(any()) } returns EndlessSlowStream()
        coEvery { storage.openInputStream(any(), any()) } returns null
        coEvery { storage.close() } returns Unit

        val factory = mockk<StorageFactory>(relaxed = true)
        every { factory.create(any()) } returns storage

        return DownloadManager(context, factory, taskDao, libraryDao)
    }

    /**
     * 轮询等待条件成立。
     *
     * 等待的是 [DownloadManager] 的**真实 IO 线程**（不是协程），因此只能阻塞当前测试线程，
     * `delay()` 在此无效。ForbiddenMethodCall 针对的是「协程里用 Thread.sleep 顶替 delay」，
     * 不适用于本场景。
     */
    @Suppress("ForbiddenMethodCall")
    private fun awaitUntil(message: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("$message（等待 ${timeoutMs}ms）")
    }

    /**
     * 以固定节奏持续供数的输入流（永不 EOF）。
     *
     * 每次 read 睡 5ms 并返回 1KB：8MB 的字节阈值需 ~40s 才触发，因此真正决定
     * 进度写入时机的是 500ms 的时间阈值 —— 协程在那次挂起点观察到取消。
     */
    private class EndlessSlowStream : InputStream() {
        @Volatile
        private var closed = false

        override fun read(): Int = 0

        /**
         * 刻意慢读：阻塞式 [InputStream.read] 无法用 `delay()` 表达「慢 I/O」，
         * 这里的 Thread.sleep 正是被测对象（下载协程在慢读的挂起点上观察到取消）。
         */
        @Suppress("ForbiddenMethodCall")
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (closed) return -1
            Thread.sleep(5)
            val n = minOf(len, 1024)
            java.util.Arrays.fill(b, off, off + n, 0)
            return n
        }

        override fun close() {
            closed = true
        }
    }
}
