package com.nichx.niplayer.common.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileTypesTest {

    @Test
    fun `常见视频扩展名识别`() {
        listOf(
            "movie.mp4", "movie.MKV", "movie.avi", "movie.mov", "movie.flv",
            "movie.ts", "movie.webm", "movie.3gp", "movie.mpeg", "movie.mpg",
            "movie.m4v", "movie.rmvb", "movie.rm", "movie.vob", "movie.wmv",
            "movie.f4v", "movie.m2ts",
        ).forEach { name ->
            assertTrue("应识别为视频: $name", MediaFileTypes.isVideoFile(name))
            assertTrue("视频同时是媒体: $name", MediaFileTypes.isMediaFile(name))
        }
    }

    @Test
    fun `常见音频扩展名识别`() {
        listOf(
            "song.mp3", "song.wav", "song.flac", "song.ogg", "song.aac",
            "song.ape", "song.wma", "song.ac3", "song.m4a", "song.opus",
            "song.amr", "song.pcm",
        ).forEach { name ->
            assertTrue("应识别为音频: $name", MediaFileTypes.isAudioFile(name))
            assertTrue("音频同时是媒体: $name", MediaFileTypes.isMediaFile(name))
        }
    }

    @Test
    fun `常见图片扩展名识别`() {
        listOf(
            "photo.jpg", "photo.jpeg", "photo.png", "photo.gif", "photo.bmp",
            "photo.webp", "photo.heif", "photo.heic",
        ).forEach { name ->
            assertTrue("应识别为图片: $name", MediaFileTypes.isImageFile(name))
        }
    }

    @Test
    fun `大小写不敏感`() {
        assertTrue(MediaFileTypes.isVideoFile("MOVIE.MP4"))
        assertTrue(MediaFileTypes.isAudioFile("SONG.FLAC"))
        assertTrue(MediaFileTypes.isImageFile("PHOTO.PNG"))
        assertTrue(MediaFileTypes.isVideoFile("movie.Mp4"))
    }

    @Test
    fun `无扩展名与点开头文件不识别`() {
        assertFalse(MediaFileTypes.isVideoFile("movie"))
        assertFalse(MediaFileTypes.isAudioFile("song"))
        assertFalse(MediaFileTypes.isImageFile("photo"))
        assertFalse(MediaFileTypes.isMediaFile(".mp4"))
        assertFalse(MediaFileTypes.isVideoFile("movie."))
    }

    @Test
    fun `非媒体扩展名不识别`() {
        assertFalse(MediaFileTypes.isVideoFile("doc.txt"))
        assertFalse(MediaFileTypes.isAudioFile("archive.zip"))
        assertFalse(MediaFileTypes.isImageFile("script.sh"))
        assertFalse(MediaFileTypes.isMediaFile("readme.md"))
    }

    @Test
    fun `m4s 不作为音频处理`() {
        // BUG-1 修复：m4s（分片 MP4 流）实为视频，从音频扩展名表中移除
        assertFalse(MediaFileTypes.isAudioFile("segment.m4s"))
        assertTrue(MediaFileTypes.isMediaFile("segment.m4s"))
    }

    @Test
    fun `空字符串不识别`() {
        assertFalse(MediaFileTypes.isVideoFile(""))
        assertFalse(MediaFileTypes.isMediaFile(""))
    }
}
