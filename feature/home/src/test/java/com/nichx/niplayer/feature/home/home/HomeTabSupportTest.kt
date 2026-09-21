package com.nichx.niplayer.feature.home.home

import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页纯辅助函数的回归测试（**`:feature:home` 首批测试之一**）。
 *
 * 覆盖 [formatTime] / [buildThumbnailModel] / [isHistoryReachable] —— 三者都不依赖
 * Android 框架，可在纯 JVM 上跑；也是后续拆分 `HomeTabScreen` / `StorageFileViewModel`
 * 时的特征测试。
 */
class HomeTabSupportTest {

    // ── formatTime：注意这是**第三个**时长变体 ─────────────────────────

    @Test
    fun `formatTime 非正数返回固定零值`() {
        // 与 feature:player 的两个变体都不同：这里 0 和负数统一给 "00:00"
        assertEquals("00:00", formatTime(0))
        assertEquals("00:00", formatTime(-1))
        assertEquals("00:00", formatTime(-999_999))
    }

    @Test
    fun `formatTime 不足一小时时分钟补零`() {
        // 与 feature:player 的 formatDurationShort（"0:59"）**不同**：这里是两位补零 "00:59"
        assertEquals("00:59", formatTime(59_000))
        assertEquals("01:00", formatTime(60_000))
        assertEquals("59:59", formatTime(3_599_000))
    }

    @Test
    fun `formatTime 满一小时进位`() {
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("1:30:30", formatTime(5_430_000))
    }

    // ── buildThumbnailModel ────────────────────────────────────────────

    @Test
    fun `命中缓存时直接返回缓存路径`() {
        val model = buildThumbnailModel(
            url = "/movies/a.mp4",
            mediaType = MediaType.LOCAL_STORAGE,
            thumbnailUrls = mapOf("/movies/a.mp4" to "/cache/x.jpg"),
        )
        assertEquals("/cache/x.jpg", model)
    }

    @Test
    fun `本地视频路径补 file 前缀`() {
        assertEquals(
            "file:///movies/a.mp4",
            buildThumbnailModel("/movies/a.mp4", MediaType.LOCAL_STORAGE),
        )
        // 已是 URL 形态则原样返回
        assertEquals(
            "file:///movies/a.mp4",
            buildThumbnailModel("file:///movies/a.mp4", MediaType.LOCAL_STORAGE),
        )
    }

    @Test
    fun `本地音频不生成缩略图`() {
        // 音频走封面流程，这里必须返回 null，否则会拿音频文件当视频取帧
        assertNull(buildThumbnailModel("/music/song.mp3", MediaType.LOCAL_STORAGE))
        assertNull(buildThumbnailModel("/music/song.flac", MediaType.EXTERNAL_STORAGE))
    }

    @Test
    fun `远程存储不返回本地文件模型`() {
        assertNull(buildThumbnailModel("/movies/a.mp4", MediaType.SMB_SERVER))
        assertNull(buildThumbnailModel("/movies/a.mp4", MediaType.WEBDAV_SERVER))
    }

    @Test
    fun `空路径返回 null`() {
        assertNull(buildThumbnailModel("", MediaType.LOCAL_STORAGE))
    }

    // ── isHistoryReachable ─────────────────────────────────────────────

    private fun history(storageId: Int?) = PlayHistoryEntity(
        videoName = "a.mp4",
        url = "/movies/a.mp4",
        mediaType = MediaType.LOCAL_STORAGE,
        storageId = storageId,
    )

    @Test
    fun `无 storageId 视为可达`() {
        // 本地播放没有 storageId，不应被判为不可达
        assertTrue(isHistoryReachable(history(null), emptyMap()))
    }

    @Test
    fun `未验证的 storageId 视为可达`() {
        // 关键语义：map 里没有该 id（null）时**乐观视为可达**，只有明确 false 才不可达
        assertTrue(isHistoryReachable(history(7), emptyMap()))
    }

    @Test
    fun `明确 false 判为不可达`() {
        assertFalse(isHistoryReachable(history(7), mapOf(7 to false)))
    }

    @Test
    fun `明确 true 判为可达`() {
        assertTrue(isHistoryReachable(history(7), mapOf(7 to true)))
    }

    @Test
    fun `只依据自己的 storageId 判定`() {
        // 别的 storageId 不可达不应影响本条目
        assertTrue(isHistoryReachable(history(7), mapOf(8 to false)))
    }
}
