package com.nichx.niplayer.database.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件夹密码哈希器（PBKDF2WithHmacSHA256）。
 *
 * 与存储源密码（明文存储，便于备份恢复跨设备迁移）不同：
 * 文件夹密码只有"验证"语义、无需还原明文，故采用不可逆哈希。即使数据库被
 * 导出，也无法据此还原密码（不提供找回密码）。
 *
 * 安全参数：
 * - 16 字节随机盐（每文件夹独立）
 * - 120,000 次 PBKDF2 迭代（SHA-256，输出 256-bit）
 * - 验证用 [MessageDigest.isEqual] 常量时间比较，防时序侧信道
 */
@Singleton
class FolderPasswordHasher @Inject constructor() {

    /** 生成的密码摘要（哈希 + 盐 + 迭代次数），存储到 DB 前调用方转 Base64。 */
    data class PasswordDigest(
        val hash: ByteArray,
        val salt: ByteArray,
        val iterations: Int,
    )

    /**
     * 生成新密码摘要（随机盐 + PBKDF2 哈希）。
     *
     * @param password 明文密码
     * @return 摘要（盐随机生成，迭代次数取 [DEFAULT_ITERATIONS]）
     */
    fun create(password: String): PasswordDigest {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt, DEFAULT_ITERATIONS)
        return PasswordDigest(hash = hash, salt = salt, iterations = DEFAULT_ITERATIONS)
    }

    /**
     * 验证密码是否正确。
     *
     * @param password 待验证明文密码
     * @param digest DB 中存储的摘要（调用方从 Base64 解码）
     * @return true 密码匹配
     */
    fun verify(password: String, digest: PasswordDigest): Boolean {
        if (digest.iterations <= 0 || digest.salt.isEmpty() || digest.hash.isEmpty()) return false
        val candidate = pbkdf2(password, digest.salt, digest.iterations)
        return MessageDigest.isEqual(candidate, digest.hash)
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH_BYTES = 16
    }
}
