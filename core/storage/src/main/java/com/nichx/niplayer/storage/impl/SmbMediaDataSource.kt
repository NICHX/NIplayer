package com.nichx.niplayer.storage.impl

import android.media.MediaDataSource
import android.util.Log
import org.codelibs.jcifs.smb.SmbRandomAccess
import org.codelibs.jcifs.smb.impl.SmbFile

/**
 * SMB 协议的 [MediaDataSource] 实现，用于 [android.media.MediaMetadataRetriever] 生成缩略图。
 *
 * 解决问题：SMB 的 [createPlayUrl][com.nichx.niplayer.storage.impl.SmbStorage.createPlayUrl] 返回 null，
 * MediaMetadataRetriever 无法通过 URL 取帧。本类通过 codelibs/jcifs [SmbFile.openRandomAccess] 获取
 * [SmbRandomAccess] 实例，在 [readAt] 中使用 [SmbRandomAccess.seek] 定位后读取。
 *
 * 与 [SmbParallelInputStream] 的区别：
 * - SmbParallelInputStream：顺序预读流，用于播放（media3 DataSource）
 * - SmbMediaDataSource：随机读，用于缩略图生成（MediaMetadataRetriever readAt）
 *
 * 性能：使用 [SmbRandomAccess.seek]（SMB 协议级 seek，不产生数据下载），
 * 避免 [InputStream.skip] 读取-丢弃导致的带宽浪费。
 *
 * @param fileProvider 目标文件的 [SmbFile] 工厂（重试时会重新打开，故每次调用返回新实例）。
 *   不用 URL 字符串是因为 jcifs 拼 URL 时文件名里的 `?` 会被当作 query 截断（#12）。
 * @param fileSize 文件大小
 */
class SmbMediaDataSource(
    private val fileProvider: () -> SmbFile,
    private val fileSize: Long,
) : MediaDataSource() {

    @Volatile
    private var raf: SmbRandomAccess? = null
    private val lock = Object()
    private var firstReadLogged = false

    private fun ensureOpen(): SmbRandomAccess {
        raf?.let { return it }
        synchronized(lock) {
            // 用局部变量做二次检查：直接 `if (raf != null) return raf!!` 会对可变属性
            // 连续读取两次，Kotlin 无法保证两次读到同一个值（detekt NullCheckOnMutableProperty）。
            raf?.let { return it }
            return fileProvider().openRandomAccess("r").also { raf = it }
        }
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || offset < 0 || size < 0 || offset + size > buffer.size) {
            return -1
        }
        if (fileSize > 0 && position >= fileSize) {
            return -1
        }
        val toRead = if (fileSize > 0) {
            minOf(size.toLong(), fileSize - position).toInt()
        } else {
            size
        }
        if (toRead == 0) return 0

        var lastError: Exception? = null
        repeat(MAX_READ_AT_RETRIES + 1) {
            if (Thread.currentThread().isInterrupted) {
                Thread.currentThread().interrupt()
                return -1
            }
            try {
                val st = ensureOpen()
                // seek() 是 SMB 协议级操作，不产生数据下载
                st.seek(position)
                val read = st.read(buffer, offset, toRead)
                if (!firstReadLogged) {
                    Log.d(TAG, "First readAt: pos=$position size=$toRead read=$read")
                    firstReadLogged = true
                }
                return if (read <= 0) -1 else read
            } catch (e: Exception) {
                lastError = e
                close()
            }
        }
        Log.w(TAG, "readAt exhausted retries at $position: ${lastError?.message}")
        return -1
    }

    override fun getSize(): Long = fileSize

    override fun close() {
        synchronized(lock) {
            try { raf?.close() } catch (_: Exception) {}
            raf = null
        }
    }

    private companion object {
        const val TAG = "SmbMediaDataSource"
        const val MAX_READ_AT_RETRIES = 2
    }
}
