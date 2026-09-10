package com.nichx.niplayer.network.subtitle

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssrtModelsTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun `搜索响应完整反序列化`() {
        val json = """
            {
              "sub": {
                "subs": [
                  {
                    "id": 12345,
                    "videoname": "Test Movie",
                    "native_name": "测试电影",
                    "upload_time": "2024-01-01 12:00:00",
                    "subtype": "ass",
                    "lang": { "desc": "简体中文" },
                    "url": "https://example.com/sub.zip",
                    "filelist": [
                      { "url": "https://example.com/a.ass", "f": "a.ass", "s": "1024" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val response = moshi.adapter(AssrtSearchResponse::class.java).fromJson(json)

        assertEquals(1, response!!.sub!!.subs!!.size)
        val detail = response.sub.subs[0]
        assertEquals(12345, detail.id)
        assertEquals("Test Movie", detail.videoname)
        assertEquals("测试电影", detail.native_name)
        assertEquals("ass", detail.subtype)
        assertEquals("简体中文", detail.lang?.desc)
        assertEquals("https://example.com/sub.zip", detail.url)
        assertEquals(1, detail.filelist!!.size)
        assertEquals("a.ass", detail.filelist[0].f)
        assertEquals("1024", detail.filelist[0].s)
    }

    @Test
    fun `空响应字段为 null`() {
        val json = "{}"
        val response = moshi.adapter(AssrtSearchResponse::class.java).fromJson(json)
        assertNull(response!!.sub)
    }

    @Test
    fun `sub 容器为空列表`() {
        val json = """{ "sub": { "subs": [] } }"""
        val response = moshi.adapter(AssrtSearchResponse::class.java).fromJson(json)
        assertTrue(response!!.sub!!.subs!!.isEmpty())
    }

    @Test
    fun `可选字段缺失时为 null`() {
        val json = """{ "sub": { "subs": [ { "id": 1 } ] } }"""
        val response = moshi.adapter(AssrtSearchResponse::class.java).fromJson(json)
        val detail = response!!.sub!!.subs!![0]
        assertEquals(1, detail.id)
        assertNull(detail.videoname)
        assertNull(detail.lang)
        assertNull(detail.filelist)
    }

    @Test
    fun `序列化后再反序列化保持数据`() {
        val detail = AssrtSubDetail(
            id = 999,
            videoname = "Movie",
            lang = AssrtLang(desc = "English"),
        )
        val json = moshi.adapter(AssrtSubDetail::class.java).toJson(detail)
        val restored = moshi.adapter(AssrtSubDetail::class.java).fromJson(json)

        assertEquals(999, restored!!.id)
        assertEquals("Movie", restored.videoname)
        assertEquals("English", restored.lang?.desc)
    }
}
