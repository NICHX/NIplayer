package com.nichx.niplayer.database.security

import android.util.Base64
import com.nichx.niplayer.database.dao.EncryptedFolderDao
import com.nichx.niplayer.database.entity.EncryptedFolderEntity
import com.nichx.niplayer.database.sync.PlayHistorySyncDeleter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 加密文件夹管理器（@Singleton）。
 *
 * 统一封装文件夹访问加密的增删查、密码解锁与解锁会话缓存。
 * 供文件浏览页（进入目录门禁）与播放器（历史抑制）复用。
 *
 * ## 解锁会话
 * 解锁结果缓存在内存 [unlockedRoots]（进程存活期内免重复验证，应用重启后失效）。
 * 判定"已解锁"采用祖先匹配：进入已解锁加密目录的子目录无需再次验证。
 *
 * ## 前缀匹配
 * - [isWithinEncrypted]：路径落在任一加密目录（含子目录）内 → 历史抑制判定
 * - [isUnlocked]：路径自身或其任一祖先已解锁 → 目录门禁放行判定
 */
@Singleton
class EncryptedFolderManager @Inject constructor(
    private val dao: EncryptedFolderDao,
    private val syncDeleter: PlayHistorySyncDeleter,
    private val hasher: FolderPasswordHasher,
) {

    /** 已解锁加密根目录集合：key = "storageId:folderPath"，value = 解锁时间戳。 */
    private val unlockedRoots = ConcurrentHashMap<String, Long>()

    // ──── 查询 ────

    /** 指定路径是否为加密根目录（精确匹配）。 */
    suspend fun isEncrypted(storageId: Int, folderPath: String): Boolean =
        dao.getByPath(storageId, folderPath.trimEnd('/')) != null

    /** 指定存储源的所有加密配置（文件浏览页锁定角标用）。 */
    fun getEncryptedFlow(storageId: Int) = dao.getByStorageIdFlow(storageId)

    /** 指定路径是否已解锁（自身或任一祖先为已解锁的加密根目录）。 */
    fun isUnlocked(storageId: Int, path: String): Boolean {
        var current = path.trimEnd('/')
        while (true) {
            if (unlockedRoots.containsKey("$storageId:$current")) return true
            if (current.isEmpty()) break
            val idx = current.lastIndexOf('/')
            if (idx < 0) {
                current = ""
            } else {
                current = current.substring(0, idx)
            }
        }
        return false
    }

    /**
     * 路径是否落在任一加密目录内（含子目录，前缀边界对齐 `/`）。
     * 供播放历史抑制判定：加密文件夹内的文件不写历史。
     */
    suspend fun isWithinEncrypted(storageId: Int, path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val normalized = path.trimEnd('/')
        if (normalized.isEmpty()) return false
        return dao.getByStorageId(storageId).any { enc ->
            val root = enc.folderPath.trimEnd('/')
            normalized == root || normalized.startsWith("$root/")
        }
    }

    // ──── 解锁 ────

    /** 密码解锁：PBKDF2 验证通过后写入解锁会话。 */
    suspend fun unlockWithPassword(storageId: Int, folderPath: String, password: String): Boolean {
        val entity = dao.getByPath(storageId, folderPath.trimEnd('/')) ?: return false
        val digest = FolderPasswordHasher.PasswordDigest(
            hash = Base64.decode(entity.passwordHash, Base64.NO_WRAP),
            salt = Base64.decode(entity.passwordSalt, Base64.NO_WRAP),
            iterations = entity.iterations,
        )
        // PBKDF2 12 万次迭代是 CPU 密集计算：切到 Default 线程池，避免阻塞主线程（弹窗卡顿/ANR）
        val ok = withContext(Dispatchers.Default) { hasher.verify(password, digest) }
        if (!ok) return false
        markUnlocked(storageId, entity)
        return true
    }

    private fun markUnlocked(storageId: Int, entity: EncryptedFolderEntity) {
        unlockedRoots["$storageId:${entity.folderPath.trimEnd('/')}"] = System.currentTimeMillis()
    }

    /**
     * 重新锁定：当前目录 [currentPath] 不再覆盖的已解锁加密根目录立即上锁。
     *
     * 目录浏览退出加密文件夹（返回其父级 / 跳到其他分支）时调用，
     * 实现"离开加密区域自动重新上锁"，避免解锁会话在进程存活期内一直有效。
     *
     * @param currentPath 变化后的当前目录路径；为空（根目录）时锁定全部
     */
    fun reLockUncovered(storageId: Int, currentPath: String?) {
        val normalized = currentPath?.trimEnd('/').orEmpty()
        unlockedRoots.keys.removeIf { key ->
            key.startsWith("$storageId:") && run {
                val root = key.substringAfter(':').trimEnd('/')
                // 当前路径仍在该加密根目录内（等于或为其后代）→ 保持解锁；否则重新上锁
                normalized != root && !normalized.startsWith("$root/")
            }
        }
    }

    // ──── 设置 / 取消加密 ────

    /** 为文件夹设置密码（加密）。 */
    suspend fun setPassword(
        storageId: Int,
        folderPath: String,
        password: String,
    ) {
        val path = folderPath.trimEnd('/')
        val digest = withContext(Dispatchers.Default) { hasher.create(password) }
        val existing = dao.getByPath(storageId, path)
        val entity = existing ?: EncryptedFolderEntity(
            storageId = storageId,
            folderPath = path,
            passwordHash = "",
            passwordSalt = "",
        )
        entity.passwordHash = Base64.encodeToString(digest.hash, Base64.NO_WRAP)
        entity.passwordSalt = Base64.encodeToString(digest.salt, Base64.NO_WRAP)
        entity.iterations = digest.iterations
        entity.updatedAt = System.currentTimeMillis()
        if (existing != null) {
            dao.update(entity)
        } else {
            dao.insert(entity)
        }

        // 加密生效：清理该文件夹前缀下已有的播放历史（隐私保护，与"不记历史"语义一致），
        // 并记录同步 tombstone 使删除传播到其他设备
        syncDeleter.deleteByStoragePathPrefixAndStorageId(storageId, path)
    }

    /**
     * 取消加密（需验证当前密码，防止他人解除保护）。
     *
     * @return true 密码正确且已移除加密配置
     */
    suspend fun removePassword(storageId: Int, folderPath: String, password: String): Boolean {
        val entity = dao.getByPath(storageId, folderPath.trimEnd('/')) ?: return false
        val digest = FolderPasswordHasher.PasswordDigest(
            hash = Base64.decode(entity.passwordHash, Base64.NO_WRAP),
            salt = Base64.decode(entity.passwordSalt, Base64.NO_WRAP),
            iterations = entity.iterations,
        )
        if (!withContext(Dispatchers.Default) { hasher.verify(password, digest) }) return false
        dao.delete(entity.id)
        unlockedRoots.remove("$storageId:${entity.folderPath.trimEnd('/')}")
        return true
    }

    /**
     * 修改访问密码（需验证当前密码）。仅更新哈希，不清除播放历史
     * （历史抑制以"目录是否加密"为准，与密码内容无关）。
     *
     * @return true 旧密码正确且已更新
     */
    suspend fun changePassword(
        storageId: Int,
        folderPath: String,
        oldPassword: String,
        newPassword: String,
    ): Boolean {
        val entity = dao.getByPath(storageId, folderPath.trimEnd('/')) ?: return false
        val digest = FolderPasswordHasher.PasswordDigest(
            hash = Base64.decode(entity.passwordHash, Base64.NO_WRAP),
            salt = Base64.decode(entity.passwordSalt, Base64.NO_WRAP),
            iterations = entity.iterations,
        )
        if (!withContext(Dispatchers.Default) { hasher.verify(oldPassword, digest) }) return false
        val newDigest = withContext(Dispatchers.Default) { hasher.create(newPassword) }
        entity.passwordHash = Base64.encodeToString(newDigest.hash, Base64.NO_WRAP)
        entity.passwordSalt = Base64.encodeToString(newDigest.salt, Base64.NO_WRAP)
        entity.iterations = newDigest.iterations
        entity.updatedAt = System.currentTimeMillis()
        dao.update(entity)
        return true
    }

    // ──── 联动清理 ────

    /** 存储源删除：级联清理该存储源全部加密配置。 */
    suspend fun deleteByStorageId(storageId: Int) {
        dao.deleteByStorageId(storageId)
        unlockedRoots.keys.removeIf { it.startsWith("$storageId:") }
    }

    /** 文件夹重命名：同步更新加密配置的 folder_path 前缀。 */
    suspend fun renameFolderPrefix(storageId: Int, oldPrefix: String, newPrefix: String) {
        val oldRoot = oldPrefix.trimEnd('/')
        val newRoot = newPrefix.trimEnd('/')
        dao.getByStorageId(storageId)
            .filter { it.folderPath.trimEnd('/') == oldRoot || it.folderPath.trimEnd('/').startsWith("$oldRoot/") }
            .forEach { entity ->
                entity.folderPath = newRoot + entity.folderPath.trimEnd('/').removePrefix(oldRoot)
                entity.updatedAt = System.currentTimeMillis()
                dao.update(entity)
            }
    }

    /** 文件夹删除：清理该目录（含子目录）前缀下的全部加密配置。 */
    suspend fun deleteFolderPrefix(storageId: Int, folderPath: String) {
        val root = folderPath.trimEnd('/')
        dao.getByStorageId(storageId)
            .filter { it.folderPath.trimEnd('/') == root || it.folderPath.trimEnd('/').startsWith("$root/") }
            .forEach { entity -> dao.delete(entity.id) }
        unlockedRoots.keys.removeIf { it.startsWith("$storageId:") && (it.substringAfter(':').let { p ->
            p == root || p.startsWith("$root/")
        }) }
    }
}
