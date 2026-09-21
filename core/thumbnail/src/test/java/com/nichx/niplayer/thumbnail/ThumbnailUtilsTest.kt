package com.nichx.niplayer.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 缩略图纯函数回归测试（**A4 拆分 `ThumbnailManager` 时新增**）。
 *
 * `:core:thumbnail` 此前**没有任何测试**。这些函数在拆分中从 `ThumbnailManager` 的
 * 类内 `private fun` 提为同包顶层 `internal fun`（行为逐字未变），本测试把行为钉死。
 *
 * 其中 [md5] 尤其重要：它是缓存文件名的唯一来源（`File(dir, "${'$'}{md5(...)}.jpg")`），
 * 一旦摘要算法或大小写/补零方式被改动，**全部已缓存缩略图会立刻失效**（文件名不匹配，
 * 等于缓存被静默清空）。故用固定向量钉住。
 */
class ThumbnailUtilsTest {

    // ── md5：缓存文件名唯一来源，必须钉死 ────────────────────────────────

    @Test
    fun `md5 固定向量`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5(""))
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5("hello"))
    }

    @Test
    fun `md5 真实缓存键固定向量`() {
        assertEquals("396b40fc22d889d5edd941d0cb7a2cc1", md5("a/b/c.mp4"))
        assertEquals("eb0211da624987c45cc80cf350052f78", md5("/movies/film.mkv"))
        assertEquals("b3ba91ed867f9a6a9442974d22f9e7df", md5("song.mp3"))
    }

    @Test
    fun `md5 恒为 32 位小写十六进制`() {
        listOf("", "a", "中文路径/视频.mkv", "x".repeat(500)).forEach { input ->
            val digest = md5(input)
            assertEquals("长度应为 32: $input", 32, digest.length)
            assertEquals("应为小写十六进制: $digest", digest, digest.lowercase())
            assertEquals("应只含十六进制字符: $digest", true, digest.all { it in "0123456789abcdef" })
        }
    }

    @Test
    fun `md5 大小写敏感`() {
        // 缓存键区分大小写，避免 "A.mp4" 与 "a.mp4" 撞同一缩略图
        assertEquals(false, md5("A.mp4") == md5("a.mp4"))
    }

    // ── buildThumbDirPath / buildCoverDirPath ───────────────────────────

    @Test
    fun `buildThumbDirPath 取父目录并追加点目录`() {
        assertEquals("/a/b/.thumb", buildThumbDirPath("/a/b/c.mp4"))
        assertEquals("/movies/.thumb", buildThumbDirPath("/movies/film.mkv"))
        assertEquals("a/b/.thumb", buildThumbDirPath("a/b/c.mp4"))
    }

    @Test
    fun `buildThumbDirPath 无父目录时退化为相对目录名`() {
        assertEquals(".thumb", buildThumbDirPath("c.mp4"))
        assertEquals(".thumb", buildThumbDirPath(""))
        // "/c.mp4" 的最后一个 '/' 在开头，substringBeforeLast 得空串 -> 退化
        assertEquals(".thumb", buildThumbDirPath("/c.mp4"))
    }

    @Test
    fun `buildCoverDirPath 取父目录并追加点目录`() {
        assertEquals("/a/b/.cover", buildCoverDirPath("/a/b/song.mp3"))
        assertEquals("a/b/.cover", buildCoverDirPath("a/b/song.mp3"))
        assertEquals(".cover", buildCoverDirPath("song.mp3"))
        assertEquals(".cover", buildCoverDirPath(""))
    }

    @Test
    fun `thumb 与 cover 目录名不冲突`() {
        val path = "/a/b/x"
        assertEquals("/a/b/.thumb", buildThumbDirPath(path))
        assertEquals("/a/b/.cover", buildCoverDirPath(path))
    }

    // ── computeInSampleSize（BitmapFactory 下采样倍率） ─────────────────

    @Test
    fun `computeInSampleSize 尺寸已在阈值内返回 1`() {
        assertEquals(1, computeInSampleSize(480, 480, 480))
        assertEquals(1, computeInSampleSize(1, 1, 480))
        assertEquals(1, computeInSampleSize(100, 480, 480))
    }

    @Test
    fun `computeInSampleSize 超过阈值时按 2 的幂递增`() {
        // 960/1 > 480 -> 2；960/2 = 480 不再大于 -> 停
        assertEquals(2, computeInSampleSize(960, 960, 480))
        // 1920/2=960>480 -> 4；1920/4=480 停
        assertEquals(4, computeInSampleSize(1920, 1080, 480))
        assertEquals(8, computeInSampleSize(3840, 2160, 480))
    }

    @Test
    fun `computeInSampleSize 任一边超阈值即继续下采样`() {
        // 宽已达标但高仍超 -> 必须继续
        assertEquals(4, computeInSampleSize(480, 1920, 480))
        assertEquals(4, computeInSampleSize(1920, 480, 480))
    }

    @Test
    fun `computeInSampleSize 返回值恒为 2 的幂`() {
        listOf(1, 7, 100, 999, 4096, 10000).forEach { size ->
            val s = computeInSampleSize(size, size, 480)
            assertEquals("$size 的倍率应为 2 的幂: $s", 0, s and (s - 1))
        }
    }
}
