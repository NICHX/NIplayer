package com.nichx.niplayer.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SyncMerge.Order] 全序语义的回归测试。
 *
 * 锁定的关键不变式：
 * - 时间戳大者胜；
 * - **同时间戳时墓碑胜过记录**（等价于旧实现的 `updatedAt <= deletedAt` 判删）；
 * - 记录的平局按播放位置 / 播放时间 / url 依次打破，且**两端算出同一胜者**（对称性）——
 *   若不满足，两台设备会各自判定"我赢"，永不收敛。
 */
class SyncMergeTest {

    @Test
    fun `时间戳较晚的候选获胜`() {
        val older = SyncMerge.recordOrder(timestamp = 100, videoPosition = 99, playTime = 100, url = "u")
        val newer = SyncMerge.recordOrder(timestamp = 200, videoPosition = 1, playTime = 200, url = "u")

        assertTrue(newer > older)
        assertTrue(older < newer)
    }

    @Test
    fun `同时间戳时墓碑胜过记录`() {
        val record = SyncMerge.recordOrder(timestamp = 100, videoPosition = 500, playTime = 100, url = "u")
        val tombstone = SyncMerge.tombstoneOrder(deletedAt = 100)

        assertTrue(tombstone > record)
        assertTrue(record < tombstone)
    }

    @Test
    fun `墓碑早于记录的更新时记录胜出`() {
        val rerecorded = SyncMerge.recordOrder(timestamp = 101, videoPosition = 500, playTime = 101, url = "u")
        val tombstone = SyncMerge.tombstoneOrder(deletedAt = 100)

        assertTrue(rerecorded > tombstone)
    }

    @Test
    fun `同时间戳的记录按播放位置决定且两端一致`() {
        val shallow = SyncMerge.recordOrder(timestamp = 100, videoPosition = 10, playTime = 100, url = "u")
        val deep = SyncMerge.recordOrder(timestamp = 100, videoPosition = 20, playTime = 100, url = "u")

        assertTrue(deep > shallow)
        assertTrue(shallow < deep)
    }

    @Test
    fun `同时间戳同播放位置时按播放时间再按 url`() {
        val early = SyncMerge.recordOrder(timestamp = 100, videoPosition = 10, playTime = 100, url = "u1")
        val late = SyncMerge.recordOrder(timestamp = 100, videoPosition = 10, playTime = 200, url = "u1")
        val laterUrl = SyncMerge.recordOrder(timestamp = 100, videoPosition = 10, playTime = 200, url = "u2")

        assertTrue(late > early)
        assertTrue(laterUrl > late)
    }

    @Test
    fun `完全等价的记录互不覆盖`() {
        val one = SyncMerge.recordOrder(timestamp = 100, videoPosition = 10, playTime = 100, url = "u")
        val other = SyncMerge.recordOrder(timestamp = 100, videoPosition = 10, playTime = 100, url = "u")

        assertEquals(0, one.compareTo(other))
        assertEquals(0, other.compareTo(one))
    }

    @Test
    fun `任意一对候选恰有一方获胜或完全等价`() {
        val samples = listOf(
            SyncMerge.recordOrder(timestamp = 100, videoPosition = 10, playTime = 100, url = "u1"),
            SyncMerge.recordOrder(timestamp = 100, videoPosition = 10, playTime = 200, url = "u1"),
            SyncMerge.recordOrder(timestamp = 100, videoPosition = 20, playTime = 100, url = "u1"),
            SyncMerge.recordOrder(timestamp = 200, videoPosition = 0, playTime = 0, url = ""),
            SyncMerge.tombstoneOrder(deletedAt = 100),
            SyncMerge.tombstoneOrder(deletedAt = 200),
        )
        for (first in samples) {
            for (second in samples) {
                val firstWins = first > second
                val secondWins = second > first
                assertTrue("同一对不应双向获胜: $first / $second", !(firstWins && secondWins))
                assertTrue("不同候选必须分出胜负: $first / $second", firstWins || secondWins || first == second)
            }
        }
    }
}
