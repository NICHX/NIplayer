package com.nichx.niplayer.database.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPasswordHasherTest {

    private val hasher = FolderPasswordHasher()

    @Test
    fun `verify 正确密码返回 true`() {
        val digest = hasher.create("my-secret-password")
        assertTrue(hasher.verify("my-secret-password", digest))
    }

    @Test
    fun `verify 错误密码返回 false`() {
        val digest = hasher.create("my-secret-password")
        assertFalse(hasher.verify("wrong-password", digest))
    }

    @Test
    fun `相同密码生成的盐不同（随机性）`() {
        val a = hasher.create("same-password")
        val b = hasher.create("same-password")
        assertFalse(a.salt.contentEquals(b.salt))
        // 盐不同 → 哈希必然不同
        assertFalse(a.hash.contentEquals(b.hash))
    }

    @Test
    fun `空盐或空哈希的摘要验证返回 false`() {
        assertFalse(
            hasher.verify(
                "anything",
                FolderPasswordHasher.PasswordDigest(byteArrayOf(), byteArrayOf(), 120_000),
            )
        )
    }

    @Test
    fun `非法迭代次数返回 false`() {
        val digest = hasher.create("pwd")
        val invalid = FolderPasswordHasher.PasswordDigest(digest.hash, digest.salt, 0)
        assertFalse(hasher.verify("pwd", invalid))
    }

    @Test
    fun `UTF-8 中文密码可验证`() {
        val digest = hasher.create("中文密码测试123")
        assertTrue(hasher.verify("中文密码测试123", digest))
        assertFalse(hasher.verify("中文密码测试124", digest))
    }

    @Test
    fun `PBKDF2 输出长度稳定为 256-bit`() {
        val digest = hasher.create("fixed-salt-test")
        assertEquals(32, digest.hash.size)
        assertEquals(16, digest.salt.size)
    }
}
