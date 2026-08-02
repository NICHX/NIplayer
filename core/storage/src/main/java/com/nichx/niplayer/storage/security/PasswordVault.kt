package com.nichx.niplayer.storage.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码加解密保险库，基于 Android Keystore AES-GCM。
 *
 * BUG-33 修复：原实现 [com.nichx.niplayer.database.entity.MediaLibraryEntity.password]
 * 以明文存入 Room 数据库，root 设备或备份提取可直接读取密码。
 * 现使用 Android Keystore 生成的 AES-256-GCM 密钥加密密码字段，密钥永不离开 Keystore，
 * 即使数据库被导出也无法解密。
 *
 * ## 存储格式
 * 加密后的密码以 `enc:v1:` 前缀 + Base64(IV + ciphertext) 存入 DB：
 * ```
 * enc:v1:eJx1...（Base64）
 * ```
 * 无前缀的视为历史明文（向后兼容），[decrypt] 直接返回原值，便于平滑迁移。
 *
 * ## 密钥丢失场景
 * Keystore 密钥在以下情况丢失：卸载应用、清除应用数据、恢复出厂设置。
 * 密钥丢失后已加密的密码无法解密，[decrypt] 返回 null，调用方（Storage）认证失败，
 * 用户需在存储源设置中重新输入密码。这是安全权衡——避免明文存储的风险。
 */
@Singleton
class PasswordVault @Inject constructor() {
    /**
     * 加密明文密码。
     *
     * @param plain 明文密码，null 或空字符串原样返回（不加密空值）
     * @return 加密后的字符串（`enc:v1:` 前缀 + Base64(IV+ciphertext)），
     *         或原值（当 plain 为 null/空 或 Keystore 不可用时回退明文）
     */
    fun encrypt(plain: String?): String? {
        if (plain.isNullOrEmpty()) return plain
        // 已加密的不重复加密
        if (plain.startsWith(PREFIX)) return plain
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            // IV + ciphertext 拼接后 Base64
            val combined = iv + cipherBytes
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Keystore 不可用（如 ROM 缺失 Keystore）时回退明文，避免功能不可用
            Log.e(TAG, "encrypt failed, fallback to plaintext", e)
            plain
        }
    }

    /**
     * 解密密码。
     *
     * @param ciphered DB 中存储的密码字段（可能带 `enc:v1:` 前缀，也可能是历史明文）
     * @return 明文密码，或 null（密钥丢失无法解密，或输入为 null）
     */
    fun decrypt(ciphered: String?): String? {
        if (ciphered.isNullOrEmpty()) return ciphered
        // 无前缀视为历史明文，直接返回（向后兼容已有数据）
        if (!ciphered.startsWith(PREFIX)) return ciphered
        return try {
            val payload = ciphered.substring(PREFIX.length)
            val combined = Base64.decode(payload, Base64.NO_WRAP)
            // GCM IV 固定 12 字节
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val key = getKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            // 密钥丢失（KeyPermanentlyInvalidatedException）或解密失败
            Log.e(TAG, "decrypt failed, password unrecoverable", e)
            null
        }
    }

    /**
     * 获取或创建 Keystore 中的 AES 密钥。
     * 密钥不存在时创建，已存在时直接返回。
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        // 密钥不存在，生成新密钥
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** 仅获取已存在的密钥，不创建（解密时密钥应已存在）。 */
    private fun getKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private companion object {
        const val TAG = "PasswordVault"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "enc:v1:"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_ALIAS = "niplayer_password_v1"
    }
}
