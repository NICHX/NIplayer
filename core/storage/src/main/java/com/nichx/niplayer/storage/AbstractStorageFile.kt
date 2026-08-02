package com.nichx.niplayer.storage

/**
 * [StorageFile] 的抽象基类，提供通用字段实现。
 *
 * 子类只需提供构造参数，无需重复实现 getter。
 */
abstract class AbstractStorageFile(
    override val path: String,
    override val name: String,
    override val isDirectory: Boolean,
    override val length: Long = 0L,
    override val lastModified: Long = 0L,
    override val isHidden: Boolean = false,
) : StorageFile {

    override fun toString(): String =
        "${if (isDirectory) "[DIR]" else "[FILE]"} $path ($length bytes)"
}
