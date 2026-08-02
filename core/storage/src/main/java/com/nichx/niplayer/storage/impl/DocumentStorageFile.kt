package com.nichx.niplayer.storage.impl

import android.net.Uri
import com.nichx.niplayer.storage.AbstractStorageFile

/**
 * [com.nichx.niplayer.storage.StorageFile] 的 DocumentFile（SAF）实现。
 *
 * 额外持有 [uri]，用于 [android.content.ContentResolver] 打开输入流。
 * media3 可通过 `content://` URI 直接播放（ContentDataSource）。
 */
class DocumentStorageFile(
    path: String,
    name: String,
    isDirectory: Boolean,
    length: Long = 0L,
    lastModified: Long = 0L,
    val uri: Uri,
) : AbstractStorageFile(path, name, isDirectory, length, lastModified)
