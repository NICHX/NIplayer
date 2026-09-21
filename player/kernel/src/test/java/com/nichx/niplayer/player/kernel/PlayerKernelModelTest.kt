package com.nichx.niplayer.player.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSizeTest {

    @Test
    fun `标准 16比9 宽高比`() {
        val size = VideoSize(width = 1920, height = 1080)
        assertTrue(size.isValid)
        assertEquals(16f / 9f, size.aspectRatio, 0.001f)
    }

    @Test
    fun `90 度旋转交换宽高比`() {
        // 竖屏视频：width=1080 height=1920，旋转 90° 后显示比例应交换
        val size = VideoSize(width = 1080, height = 1920, unappliedRotationDegrees = 90)
        assertEquals(1920f / 1080f, size.aspectRatio, 0.001f)
    }

    @Test
    fun `270 度旋转同样交换宽高比`() {
        val size = VideoSize(width = 1080, height = 1920, unappliedRotationDegrees = 270)
        assertEquals(1920f / 1080f, size.aspectRatio, 0.001f)
    }

    @Test
    fun `180 度旋转不交换宽高比`() {
        val size = VideoSize(width = 1920, height = 1080, unappliedRotationDegrees = 180)
        assertEquals(16f / 9f, size.aspectRatio, 0.001f)
    }

    @Test
    fun `像素宽高比参与计算`() {
        // displayW = width × PAR，宽高比 = displayW / height
        val size = VideoSize(width = 480, height = 720, pixelWidthHeightRatio = 1.5f)
        assertEquals(480f * 1.5f / 720f, size.aspectRatio, 0.001f)
        // PAR=1 时结果与原始宽高比一致
        val square = VideoSize(width = 480, height = 720)
        assertEquals(480f / 720f, square.aspectRatio, 0.001f)
    }

    @Test
    fun `高度为零时宽高比归零`() {
        val size = VideoSize(width = 0, height = 0)
        assertFalse(size.isValid)
        assertEquals(0f, size.aspectRatio, 0.001f)
    }
}

class PlaylistHolderTest {

    private val item = PlaylistItem(
        libraryId = 1,
        filePath = "/movies/a.mp4",
        fileName = "a.mp4",
        mediaTypeValue = "local_storage",
        fileSize = 1024L,
    )

    @Test
    fun `set 后 consume 返回列表与起始索引`() {
        val holder = PlaylistHolder()
        holder.set(listOf(item), startIndex = 2)

        val result = holder.consume()
        assertEquals(listOf(item), result!!.first)
        assertEquals(2, result.second)
    }

    @Test
    fun `consume 只消费一次`() {
        val holder = PlaylistHolder()
        holder.set(listOf(item), startIndex = 0)

        val first = holder.consume()
        val second = holder.consume()
        assertTrue(first != null)
        assertNull(second)
    }

    @Test
    fun `未 set 时 consume 返回 null`() {
        val holder = PlaylistHolder()
        assertNull(holder.consume())
    }

    @Test
    fun `重新 set 覆盖旧值`() {
        val holder = PlaylistHolder()
        holder.set(listOf(item), startIndex = 0)
        val newer = item.copy(filePath = "/movies/b.mp4")
        holder.set(listOf(newer), startIndex = 5)

        val result = holder.consume()
        assertEquals(listOf(newer), result!!.first)
        assertEquals(5, result.second)
    }
}
