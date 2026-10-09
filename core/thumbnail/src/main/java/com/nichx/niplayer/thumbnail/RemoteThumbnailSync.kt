package com.nichx.niplayer.thumbnail

import android.util.Log
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.StorageFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.FileOutputStream

/**
 * 服务端缩略图/封面同步：`{目录}/.thumb/{名}-thumb.jpg` 与 `{目录}/.cover/{名}-cover.jpg`。
 *
 * 负责预加载（复用服务端已有图）、上传、孤儿清理、删除/重命名时的同步。全部为 best-effort，
 * 单个失败不影响其余项、不影响本地显示。
 */
internal class RemoteThumbnailSync(private val store: ThumbnailStore) {

    // ---------- 视频缩略图 ----------

    /** 上传已生成的视频缩略图到服务端 `.thumb/`（受写回开关门控）。 */
    suspend fun uploadThumbnail(storage: Storage, file: StorageFile) = withContext(Dispatchers.IO) {
        if (!ThumbnailSettings.effectiveWriteBack(storage.library.id)) return@withContext
        val cacheFile = store.fileFor(store.videoDir, storage.library.id, file.path)
        if (!cacheFile.exists()) return@withContext
        try {
            val thumbDirPath = buildThumbDirPath(file.path)
            storage.createDirectory(thumbDirPath)
            val bytes = cacheFile.readBytes()
            // 与刮削工具约定一致：movie.mp4 → movie-thumb.jpg
            val videoBaseName = file.name.substringBeforeLast('.')
            val thumbPath = "$thumbDirPath/$videoBaseName-thumb.jpg"
            if (storage.fileExists(thumbPath)) {
                Log.d(TAG, "uploadThumbnail skip: 服务端已存在 $thumbPath")
                return@withContext
            }
            storage.saveFile(thumbPath, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "uploadThumbnail failed: ${e.message}")
        }
    }

    /**
     * 预加载服务端已生成的视频缩略图到本地缓存（`.thumb/` 优先，其次同目录刮削图）。
     *
     * 按目录分组，同目录共享一次 listFiles；并发按 [Storage.thumbnailConcurrency]。
     */
    suspend fun preloadThumbnails(
        storage: Storage,
        storageId: Int,
        files: List<StorageFile>,
        onLoaded: (String, String) -> Unit,
        sameDirFiles: List<StorageFile>? = null,
    ) = withContext(Dispatchers.IO) {
        val videoFiles = files.filter { !it.isDirectory }
        if (videoFiles.isEmpty()) return@withContext

        val pending = mutableListOf<StorageFile>()
        for (file in videoFiles) {
            val cached = store.cachedPath(store.videoDir, storageId, file.path)
            if (cached != null) onLoaded(file.path, cached) else pending.add(file)
        }
        if (pending.isEmpty()) return@withContext

        val byDir: Map<String, List<StorageFile>> = pending.groupBy { it.path.substringBeforeLast('/', "") }
        val sameDirFilesForGroup: (String) -> List<StorageFile>? = { dirPath ->
            if (sameDirFiles != null && byDir.size == 1) sameDirFiles else null
        }

        val concurrency = minOf(storage.thumbnailConcurrency, pending.size)
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            for ((dirPath, filesInDir) in byDir) {
                val thumbDirPath = if (dirPath.isEmpty()) ".thumb" else "$dirPath/.thumb"
                val thumbFiles = try {
                    storage.listFiles(ThumbDirFile(thumbDirPath))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }

                val thumbMap = mutableMapOf<String, StorageFile>()
                for (tf in thumbFiles) {
                    val name = tf.name.removeSuffix("-thumb.jpg").removeSuffix("-thumb.jpeg")
                    if (name.isNotEmpty()) thumbMap[name] = tf
                }

                val dirFiles = sameDirFilesForGroup(dirPath) ?: try {
                    storage.listFiles(
                        if (dirPath.isEmpty()) StorageFactory.ROOT
                        else object : AbstractStorageFile(path = dirPath, name = "", isDirectory = true) {}
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }

                val sameDirThumbMap = mutableMapOf<String, StorageFile>()
                for (f in dirFiles) {
                    if (f.isDirectory || f.length == 0L) continue
                    val name = f.name.removeSuffix("-thumb.jpg").removeSuffix("-thumb.jpeg")
                    if (name != f.name && name.isNotEmpty()) sameDirThumbMap[name] = f
                }

                cleanUpOrphanThumbs(storage, dirFiles, thumbFiles)

                // 诊断：同目录侧车 `-thumb.jpg` 的发现情况（为 0 说明同目录清单没带上侧车）
                Log.d(
                    TAG,
                    "preload dir=$dirPath 同目录侧车=${sameDirThumbMap.size} .thumb目录=${thumbMap.size} 待取帧=${filesInDir.size}",
                )

                for (file in filesInDir) {
                    val videoBaseName = file.name.substringBeforeLast('.')
                    val source = thumbMap[videoBaseName] ?: sameDirThumbMap[videoBaseName] ?: continue
                    launch {
                        semaphore.withPermit {
                            try {
                                val path = downloadThumbnail(storage, storageId, file, source)
                                if (path != null) onLoaded(file.path, path)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }

    /** 惰性清理服务端孤立缩略图（复用已列出清单，零额外网络往返；受写回开关门控）。 */
    private suspend fun cleanUpOrphanThumbs(
        storage: Storage,
        dirFiles: List<StorageFile>,
        thumbFiles: List<StorageFile>,
    ) {
        try {
            if (!ThumbnailSettings.effectiveWriteBack(storage.library.id)) return
            val validBaseNames = dirFiles.mapNotNull { f ->
                if (f.isDirectory) return@mapNotNull null
                val n = f.name
                if (n.endsWith("-thumb.jpg") || n.endsWith("-thumb.jpeg") ||
                    n.endsWith("-cover.jpg") || n.endsWith("-cover.jpeg")
                ) null else n.substringBeforeLast('.')
            }.toHashSet()
            if (validBaseNames.isEmpty()) return
            for (tf in thumbFiles) {
                val matched = tf.name.removeSuffix("-thumb.jpg").removeSuffix("-thumb.jpeg")
                if (matched.isEmpty() || matched == tf.name) continue
                if (matched !in validBaseNames) {
                    try {
                        storage.deleteFile(tf)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "cleanUpOrphanThumbs failed: ${e.message}")
        }
    }

    private suspend fun downloadThumbnail(
        storage: Storage,
        storageId: Int,
        videoFile: StorageFile,
        thumbFile: StorageFile,
    ): String? {
        val cacheFile = store.fileFor(store.videoDir, storageId, videoFile.path)
        if (cacheFile.exists()) return cacheFile.absolutePath
        var written = false
        return try {
            val input = storage.openInputStream(thumbFile)
            cacheFile.parentFile?.mkdirs()
            BufferedOutputStream(FileOutputStream(cacheFile), BUFFER_SIZE).use { out ->
                input.use { it.copyTo(out) }
            }
            written = true
            cacheFile.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "downloadThumbnail failed for ${videoFile.name}: ${e.message}")
            null
        } finally {
            if (!written) cacheFile.delete()
        }
    }

    /** 删除服务端 `.thumb/` 下由本应用上传的缩略图（不动同目录用户原图）。 */
    suspend fun deleteServerThumbnail(storage: Storage, file: StorageFile) = withContext(Dispatchers.IO) {
        val thumbName = "${file.name.substringBeforeLast('.')}-thumb.jpg"
        val thumbPath = buildThumbDirPath(file.path) + "/$thumbName"
        try {
            storage.deleteFile(
                object : AbstractStorageFile(path = thumbPath, name = thumbName, isDirectory = false) {},
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "deleteServerThumbnail failed: path=$thumbPath, ${e.message}")
        }
    }

    /** 删除视频对应的本地缓存 + 服务端 `.thumb/`（用户删除视频文件时调用）。 */
    suspend fun deleteThumbnailsForVideo(storage: Storage, storageId: Int, file: StorageFile) {
        withContext(Dispatchers.IO) {
            store.clearFor(storageId, listOf(file))
            try {
                if (!file.isDirectory && MediaFileTypes.isVideoFile(file.name)) {
                    val thumbPath =
                        "${buildThumbDirPath(file.path)}/${file.name.substringBeforeLast('.')}-thumb.jpg"
                    if (storage.fileExists(thumbPath)) {
                        storage.deleteFile(
                            object : AbstractStorageFile(
                                path = thumbPath,
                                name = thumbPath.substringAfterLast('/'),
                                isDirectory = false,
                            ) {}
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "deleteThumbnailsForVideo failed: ${e.message}")
            }
        }
    }

    /**
     * 删除服务端 `.cover/` 下由本应用上传的音频封面（手动强制刷新单文件封面时调用）。
     *
     * 不动同目录用户自带的 cover/folder 图，仅删本应用以 `{文件名}-cover.jpg` 约定上传的缓存。
     */
    suspend fun deleteServerAudioCover(storage: Storage, file: StorageFile) = withContext(Dispatchers.IO) {
        val coverName = "${file.name}-cover.jpg"
        val coverPath = "${buildCoverDirPath(file.path)}/$coverName"
        try {
            if (storage.fileExists(coverPath)) {
                storage.deleteFile(
                    object : AbstractStorageFile(path = coverPath, name = coverName, isDirectory = false) {},
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "deleteServerAudioCover failed: path=$coverPath, ${e.message}")
        }
    }

    /** 重命名视频后同步重命名服务端缩略图（受写回门控，目标已存在则保留现状）。 */
    suspend fun renameServerThumbnail(storage: Storage, oldFile: StorageFile, newFileName: String) =
        withContext(Dispatchers.IO) {
            if (!ThumbnailSettings.effectiveWriteBack(storage.library.id)) return@withContext
            try {
                val thumbDir = buildThumbDirPath(oldFile.path)
                val oldThumbPath = "$thumbDir/${oldFile.name.substringBeforeLast('.')}-thumb.jpg"
                val newBasename = newFileName.substringBeforeLast('.')
                val newThumbPath = "$thumbDir/$newBasename-thumb.jpg"
                if (oldThumbPath == newThumbPath) return@withContext
                if (!storage.fileExists(oldThumbPath)) return@withContext
                if (storage.fileExists(newThumbPath)) return@withContext
                storage.rename(
                    object : AbstractStorageFile(
                        path = oldThumbPath,
                        name = "${oldFile.name.substringBeforeLast('.')}-thumb.jpg",
                        isDirectory = false,
                    ) {},
                    "$newBasename-thumb.jpg",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "renameServerThumbnail failed: ${e.message}")
            }
        }

    // ---------- 音频封面 ----------

    /** 上传已生成的音频封面到服务端 `.cover/`（用完整文件名避免同名异扩展名冲突）。 */
    suspend fun uploadAudioCover(storage: Storage, file: StorageFile) = withContext(Dispatchers.IO) {
        if (!ThumbnailSettings.effectiveWriteBack(storage.library.id)) return@withContext
        val cacheFile = store.fileFor(store.audioDir, storage.library.id, file.path)
        if (!cacheFile.exists()) return@withContext
        try {
            val coverDirPath = buildCoverDirPath(file.path)
            storage.createDirectory(coverDirPath)
            val bytes = cacheFile.readBytes()
            val coverPath = "$coverDirPath/${file.name}-cover.jpg"
            if (storage.fileExists(coverPath)) {
                Log.d(TAG, "uploadAudioCover skip: 服务端已存在 $coverPath")
                return@withContext
            }
            storage.saveFile(coverPath, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "uploadAudioCover failed: ${e.message}")
        }
    }

    /** 预加载服务端音频封面：先按目录 cover.jpg/folder.jpg 复用，再查 `.cover/`。 */
    suspend fun preloadAudioCovers(
        storage: Storage,
        storageId: Int,
        files: List<StorageFile>,
        onLoaded: (String, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val audioFiles = files.filter { !it.isDirectory }
        if (audioFiles.isEmpty()) return@withContext

        val pending = mutableListOf<StorageFile>()
        for (file in audioFiles) {
            val cached = store.cachedPath(store.audioDir, storageId, file.path)
            if (cached != null) onLoaded(file.path, cached) else pending.add(file)
        }
        if (pending.isEmpty()) return@withContext

        val byDir: Map<String, List<StorageFile>> = pending.groupBy { it.path.substringBeforeLast('/', "") }

        // 目录封面（cover/folder/album）命中后一次复制给该目录所有音频
        val loadedFromDirCover = mutableSetOf<String>()
        for ((dirPath, filesInDir) in byDir) {
            val matchedCandidate = DIR_COVER_CANDIDATES.firstOrNull { candidate ->
                val checkPath = if (dirPath.isEmpty()) candidate else "$dirPath/$candidate"
                try {
                    storage.fileExists(checkPath)
                } catch (_: Exception) {
                    false
                }
            } ?: continue

            val coverPath = if (dirPath.isEmpty()) matchedCandidate else "$dirPath/$matchedCandidate"
            val coverStorageFile = object : AbstractStorageFile(
                path = coverPath,
                name = matchedCandidate,
                isDirectory = false,
            ) {}
            val coverBytes = try {
                storage.openInputStream(coverStorageFile).use { it.readBytes() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: continue

            for (file in filesInDir) {
                val cacheFile = store.fileFor(store.audioDir, storageId, file.path)
                cacheFile.parentFile?.mkdirs()
                try {
                    BufferedOutputStream(FileOutputStream(cacheFile), BUFFER_SIZE).use { it.write(coverBytes) }
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        onLoaded(file.path, cacheFile.absolutePath)
                        loadedFromDirCover.add(file.path)
                    }
                } catch (_: Exception) {
                }
            }
        }
        pending.removeAll { loadedFromDirCover.contains(it.path) }
        if (pending.isEmpty()) return@withContext

        val concurrency = minOf(storage.thumbnailConcurrency, pending.size)
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            for ((dirPath, filesInDir) in byDir) {
                if (filesInDir.all { it.path in loadedFromDirCover }) continue
                val coverDirPath = if (dirPath.isEmpty()) ".cover" else "$dirPath/.cover"
                val coverFiles = try {
                    storage.listFiles(CoverDirFile(coverDirPath))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }

                val coverMap = mutableMapOf<String, StorageFile>()
                for (cf in coverFiles) {
                    val name = cf.name.removeSuffix("-cover.jpg").removeSuffix("-cover.jpeg")
                    if (name.isNotEmpty()) coverMap[name] = cf
                }

                for (file in filesInDir) {
                    val coverFile = coverMap[file.name] ?: continue
                    launch {
                        semaphore.withPermit {
                            try {
                                val path = downloadAudioCover(storage, storageId, file, coverFile)
                                if (path != null) onLoaded(file.path, path)
                            } catch (e: Exception) {
                                Log.w(TAG, "preloadAudioCovers download failed: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun downloadAudioCover(
        storage: Storage,
        storageId: Int,
        audioFile: StorageFile,
        coverFile: StorageFile,
    ): String? {
        val cacheFile = store.fileFor(store.audioDir, storageId, audioFile.path)
        if (cacheFile.exists()) return cacheFile.absolutePath
        var written = false
        return try {
            val input = storage.openInputStream(coverFile)
            cacheFile.parentFile?.mkdirs()
            BufferedOutputStream(FileOutputStream(cacheFile), BUFFER_SIZE).use { out ->
                input.use { it.copyTo(out) }
            }
            written = true
            cacheFile.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "downloadAudioCover failed for ${audioFile.name}: ${e.message}")
            null
        } finally {
            if (!written) cacheFile.delete()
        }
    }

    private companion object {
        const val TAG = "RemoteThumbnailSync"
        const val BUFFER_SIZE = 64 * 1024
        val DIR_COVER_CANDIDATES = listOf(
            "cover.jpg", "cover.jpeg", "cover.png",
            "folder.jpg", "folder.jpeg", "folder.png",
            "album.jpg", "album.jpeg", "album.png",
        )
    }
}

/** 用于 listFiles `.thumb/` 目录的占位 StorageFile。 */
private class ThumbDirFile(override val path: String) : StorageFile {
    override val name = ".thumb"
    override val isDirectory = true
    override val length = 0L
    override val lastModified = 0L
    override val etag = null
    override val isHidden = false
}

/** 用于 listFiles `.cover/` 目录的占位 StorageFile。 */
private class CoverDirFile(override val path: String) : StorageFile {
    override val name = ".cover"
    override val isDirectory = true
    override val length = 0L
    override val lastModified = 0L
    override val etag = null
    override val isHidden = false
}
