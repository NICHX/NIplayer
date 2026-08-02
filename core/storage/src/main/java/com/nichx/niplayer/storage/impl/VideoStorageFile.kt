package com.nichx.niplayer.storage.impl

import com.nichx.niplayer.storage.AbstractStorageFile

/**
 * [com.nichx.niplayer.storage.StorageFile] 的本地视频库实现。
 *
 * 包装 [com.nichx.niplayer.database.bean.FolderBean]（目录）或
 * [com.nichx.niplayer.database.entity.VideoEntity]（文件）的元信息。
 *
 * 额外持有 [filePath]（绝对路径，用于 FileInputStream / folderPath 分组）与
 * [fileId]（MediaStore `_ID`，> 0 时 [VideoStorage.createPlayUrl] 返回 `content://` URI）。
 */
class VideoStorageFile(
    path: String,
    name: String,
    isDirectory: Boolean,
    length: Long = 0L,
    lastModified: Long = 0L,
    val filePath: String,
    val fileId: Long = 0L,
) : AbstractStorageFile(path, name, isDirectory, length, lastModified)
