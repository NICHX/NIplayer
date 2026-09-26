package com.nichx.niplayer.metadata.cache

import com.nichx.niplayer.metadata.model.AudioTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 标签缓存测试。
 *
 * 重点是 LRU 语义：容量满时淘汰**最久未使用**项，而读取也算使用。
 * 这条语义决定了「浏览期读到的标签会不会在播放前就被挤掉」。
 */
class AudioTagCacheTest {

    @After
    fun tearDown() {
        AudioTagCache.clear()
    }

    @Test
    fun `存取与 key 隔离`() {
        AudioTagCache.put("local:/a.mp3", AudioTags(title = "A", artist = "X"))
        AudioTagCache.put("local:/b.mp3", AudioTags(title = "B", artist = "Y"))

        assertEquals("A", AudioTagCache.get("local:/a.mp3")?.title)
        assertEquals("B", AudioTagCache.get("local:/b.mp3")?.title)
        assertNull(AudioTagCache.get("local:/c.mp3"))
    }

    @Test
    fun `超出容量时淘汰最久未使用项`() {
        val total = AudioTagCache.MAX_ENTRIES + 1
        repeat(total) { index ->
            AudioTagCache.put("key-$index", AudioTags(title = "t$index"))
        }

        assertNull("最早的条目应被淘汰", AudioTagCache.get("key-0"))
        assertEquals("t${total - 1}", AudioTagCache.get("key-${total - 1}")?.title)
        assertEquals(AudioTagCache.MAX_ENTRIES, AudioTagCache.size)
    }

    @Test
    fun `读取会把条目移到队尾从而免于淘汰`() {
        repeat(AudioTagCache.MAX_ENTRIES) { index ->
            AudioTagCache.put("key-$index", AudioTags(title = "t$index"))
        }
        // 读一次 key-0，把它移到队尾
        assertEquals("t0", AudioTagCache.get("key-0")?.title)

        // 再插一条触发淘汰：队首此时是 key-1，而不是刚被读过的 key-0
        AudioTagCache.put("key-new", AudioTags(title = "tnew"))

        assertEquals("t0", AudioTagCache.get("key-0")?.title)
        assertNull(AudioTagCache.get("key-1"))
    }

    @Test
    fun `clear 清空全部条目`() {
        AudioTagCache.put("local:/a.mp3", AudioTags(title = "A"))
        AudioTagCache.clear()

        assertNull(AudioTagCache.get("local:/a.mp3"))
        assertEquals(0, AudioTagCache.size)
    }

    @Test
    fun `keyFor 按媒体库与本地两种约定生成`() {
        assertEquals("sid:3:/music/a.mp3", AudioTagCache.keyFor(3, "/music/a.mp3"))
        assertEquals("local:content://media/1", AudioTagCache.keyFor(null, "content://media/1"))
    }
}
