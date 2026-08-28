package com.nichx.niplayer.storage.util

import java.io.InputStream

/**
 * 包装 [InputStream] 并累计已读取字节数，经 [onRead] 回调上报。
 *
 * 用于上传进度统计：上传实现无论底层如何读流（流式循环 / OkHttp RequestBody），
 * 只要用本类包装输入流，就能得到"已读完多少字节"的累计值。
 *
 * [onRead] 为非挂起回调（可能在 IO / 网络线程触发），调用方需保证线程安全且低开销
 * （例如写入原子计数器或节流后的 StateFlow）。
 */
class CountingInputStream(
    private val delegate: InputStream,
    private val onRead: (total: Long) -> Unit,
) : InputStream() {

    private var total: Long = 0

    override fun read(): Int =
        delegate.read().also { if (it >= 0) { total++; onRead(total) } }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        delegate.read(b, off, len).also { if (it > 0) { total += it; onRead(total) } }

    override fun skip(n: Long): Long = delegate.skip(n)

    override fun available(): Int = delegate.available()

    override fun markSupported(): Boolean = delegate.markSupported()

    override fun mark(readlimit: Int) = delegate.mark(readlimit)

    override fun reset() = delegate.reset()

    override fun close() = delegate.close()
}