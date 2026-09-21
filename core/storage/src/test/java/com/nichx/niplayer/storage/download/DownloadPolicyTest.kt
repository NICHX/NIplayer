package com.nichx.niplayer.storage.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DownloadPolicy] 的回归测试。
 *
 * 对应缺陷审计报告中的两条 P1：
 * - #2 断点续传不校验本地文件实际长度与 offset 一致
 * - #3 下载完成不校验总长度，短读静默判成功
 *
 * 两条的共同后果是「产出被损坏/截断的文件，并被标记为 COMPLETED」，用户无法感知。
 */
class DownloadPolicyTest {

    // ==================== shouldResetResumeOffset ====================

    @Test
    fun `无续传偏移时不重置`() {
        // 首次下载（或已重置）没有残片可依赖，无需重置
        assertFalse(DownloadPolicy.shouldResetResumeOffset(0, 0))
        assertFalse(DownloadPolicy.shouldResetResumeOffset(0, 1024))
    }

    @Test
    fun `文件长度与偏移一致时允许续传`() {
        assertFalse(DownloadPolicy.shouldResetResumeOffset(1024, 1024))
    }

    @Test
    fun `有偏移但文件不存在时必须重置`() {
        // 回归 #1 的后果链：取消竞态把文件删了，但 DB 仍记录 downloadedBytes > 0。
        // 若不重置，恢复时会 createNewFile() 建出 0 字节文件再以 append 模式写入
        // offset 之后的数据，产出前段整段缺失的文件。
        assertTrue(DownloadPolicy.shouldResetResumeOffset(1024, 0))
    }

    @Test
    fun `文件被截断时必须重置`() {
        // 上次 flush 未落盘 / 进程被杀 / 用户手动改动
        assertTrue(DownloadPolicy.shouldResetResumeOffset(4096, 1024))
    }

    @Test
    fun `文件长于偏移时也必须重置`() {
        // 反向不一致同样会错位：append 会从文件真实末尾继续写
        assertTrue(DownloadPolicy.shouldResetResumeOffset(1024, 4096))
    }

    @Test
    fun `偏移与长度均大于零且相等时不重置`() {
        assertFalse(DownloadPolicy.shouldResetResumeOffset(1, 1))
        assertFalse(DownloadPolicy.shouldResetResumeOffset(Long.MAX_VALUE, Long.MAX_VALUE))
    }

    // ==================== isFullyDownloaded ====================

    @Test
    fun `总长度未知时不做判定按完整处理`() {
        // totalBytes <= 0 表示 StorageFile.length 未填充，此时无法校验，
        // 保持既有行为（不因此把正常下载判为失败）
        assertTrue(DownloadPolicy.isFullyDownloaded(0, 0))
        assertTrue(DownloadPolicy.isFullyDownloaded(123, -1))
    }

    @Test
    fun `短读一个字节也不得判定为完成`() {
        // 回归 #3：原实现只要流返回 -1 就置 COMPLETED
        assertFalse(DownloadPolicy.isFullyDownloaded(999, 1000))
    }

    @Test
    fun `零字节写入不得判定为完成`() {
        // 服务端提前断连：流立即 EOF
        assertFalse(DownloadPolicy.isFullyDownloaded(0, 1000))
    }

    @Test
    fun `达到总长度时判定完成`() {
        assertTrue(DownloadPolicy.isFullyDownloaded(1000, 1000))
    }

    @Test
    fun `超过总长度时判定完成`() {
        // 服务端返回多于声明长度（异常但内容可用），不应判失败
        assertTrue(DownloadPolicy.isFullyDownloaded(1001, 1000))
    }

    @Test
    fun `续传场景按绝对位置判定完成`() {
        // offset = 600 起继续读 400 字节 → totalRead = 1000 = totalBytes
        assertTrue(DownloadPolicy.isFullyDownloaded(1000, 1000))
        // 续传中途断连：只读到 800
        assertFalse(DownloadPolicy.isFullyDownloaded(800, 1000))
    }
}
