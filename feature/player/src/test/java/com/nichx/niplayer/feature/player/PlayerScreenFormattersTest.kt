package com.nichx.niplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放器时长/速率格式化函数的回归测试（**A4 巨石文件拆分时新增**）。
 *
 * 背景：拆分 `PlayerScreen.kt` 时暴露出 `formatTime` 曾有**两个语义变体**各自
 * 以 `private fun` 重复存在于 3 个文件里：
 * - 长版（带小时，`1:30:00`）→ 合并为 [formatDuration]
 * - 短版（只到分钟，`90:00`）→ 合并为 [formatDurationShort]
 *
 * 两者**不是重复实现**，差异是刻意的（音频播放页/歌词页要显示 `90:00`）。
 * 本测试把这个差异**钉死**，避免以后被误当作重复代码再次"统一"。
 */
class PlayerScreenFormattersTest {

    // ── formatDuration：长版，> 1 小时进位 ──────────────────────────────

    @Test
    fun `formatDuration 不足一小时显示 mm colon ss`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59_000))
        assertEquals("1:00", formatDuration(60_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun `formatDuration 满一小时进位显示 h colon mm colon ss`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        // 90 分 30 秒 —— 这正是与 formatDurationShort 的分界点
        assertEquals("1:30:30", formatDuration(5_430_000))
        assertEquals("10:00:00", formatDuration(36_000_000))
    }

    @Test
    fun `formatDuration 负数与零按 0 处理`() {
        assertEquals("0:00", formatDuration(-1))
        assertEquals("0:00", formatDuration(-999_999))
    }

    // ── formatDurationShort：短版，**不**进位 ──────────────────────────

    @Test
    fun `formatDurationShort 永不进位到小时`() {
        // 关键语义差异：90 分钟必须显示 90:00 而不是 1:30:00
        assertEquals("90:00", formatDurationShort(5_400_000))
        assertEquals("120:00", formatDurationShort(7_200_000))
        // 与长版在同一输入上必须给出不同结果
        assertEquals("1:30:00", formatDuration(5_400_000))
    }

    @Test
    fun `formatDurationShort 常规与边界`() {
        assertEquals("0:00", formatDurationShort(0))
        assertEquals("0:59", formatDurationShort(59_000))
        assertEquals("1:00", formatDurationShort(60_000))
        assertEquals("59:59", formatDurationShort(3_599_000))
    }

    @Test
    fun `formatDurationShort 负数按 0 处理`() {
        assertEquals("0:00", formatDurationShort(-1))
    }

    // ── formatSleepTimer ────────────────────────────────────────────────

    @Test
    fun `formatSleepTimer 带前导减号`() {
        assertEquals("- 0:00", formatSleepTimer(0))
        assertEquals("- 1:00", formatSleepTimer(60))
        assertEquals("- 59:59", formatSleepTimer(3_599))
        assertEquals("- 120:00", formatSleepTimer(7_200))
    }

    // ── formatSpeed / formatNetworkSpeed ────────────────────────────────

    @Test
    fun `formatSpeed 一位小数并带 x 后缀`() {
        assertEquals("0.5x", formatSpeed(0.5f))
        assertEquals("1.0x", formatSpeed(1.0f))
        assertEquals("2.0x", formatSpeed(2.0f))
    }

    @Test
    fun `formatNetworkSpeed 按量级选择单位`() {
        assertEquals("0 B/s", formatNetworkSpeed(0))
        assertEquals("999 B/s", formatNetworkSpeed(999))
        assertEquals("1 KB/s", formatNetworkSpeed(1_000))
        // KB 分支用 %.0f（不保留小数），与 MB 分支的 %.1f 不同
        assertEquals("1 KB/s", formatNetworkSpeed(1_400))
        assertEquals("1.0 MB/s", formatNetworkSpeed(1_000_000))
        assertEquals("2.5 MB/s", formatNetworkSpeed(2_500_000))
    }

    @Test
    fun `formatNetworkSpeed 边界值取整为 HALF_UP`() {
        // 1.5 KB/s → %.0f 按 HALF_UP 得 2
        assertEquals("2 KB/s", formatNetworkSpeed(1_500))
        // 1.5 MB/s 走 MB 分支，保留一位小数
        assertEquals("1.5 MB/s", formatNetworkSpeed(1_500_000))
    }

    // ── 速度档位常量 ────────────────────────────────────────────────────

    @Test
    fun `速度档位标签与数值一一对应`() {
        assertEquals(SPEED_LABELS.size, SPEED_VALUES.size)
        assertEquals("0.5x", SPEED_LABELS.first())
        assertEquals(0.5f, SPEED_VALUES.first())
        assertEquals("4.0x", SPEED_LABELS.last())
        assertEquals(4.0f, SPEED_VALUES.last())
    }
}
