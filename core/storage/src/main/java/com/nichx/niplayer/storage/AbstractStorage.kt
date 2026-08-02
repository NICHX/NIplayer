package com.nichx.niplayer.storage

import com.nichx.niplayer.database.entity.MediaLibraryEntity

/**
 * [Storage] 的抽象基类，持有 [library] 引用并提供 close 默认空实现。
 *
 * 子类按协议实现具体的 listFiles / openInputStream / createPlayUrl 等方法。
 */
abstract class AbstractStorage(
    override val library: MediaLibraryEntity,
) : Storage {

    override suspend fun close() {
        // 默认空实现，子类按需覆盖
    }
}
