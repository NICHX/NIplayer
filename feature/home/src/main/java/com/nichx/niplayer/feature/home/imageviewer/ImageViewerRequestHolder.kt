package com.nichx.niplayer.feature.home.imageviewer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片查看请求的载体。
 *
 * 由文件浏览页（[com.nichx.niplayer.feature.home.library.StorageFileViewModel]）在用户
 * 点击图片文件时构造，经 [ImageViewerRequestHolder] 传递给图片查看页
 * （[ImageViewerViewModel]）消费。
 *
 * 仅携带可序列化的基本类型（storageId / 目录路径 / 初始文件路径），不持有 Storage 实例
 * 或 StorageFile 对象——[ImageViewerViewModel] 消费后通过 [com.nichx.niplayer.storage
 * .StorageFactory] 重建 Storage 并重新列目录定位图片。
 *
 * 设计参照 [com.nichx.niplayer.player.kernel.PlaybackRequestHolder]。
 *
 * @param storageId 存储源 ID，用于重建 Storage
 * @param directoryPath 当前目录路径（根目录为空字符串），用于列出同目录下所有图片
 * @param initialFilePath 初始显示的图片文件路径
 */
data class ImageViewerRequest(
    val storageId: Int,
    val directoryPath: String,
    val initialFilePath: String,
)

/**
 * 跨 ViewModel 传递 [ImageViewerRequest] 的 @Singleton 持有者。
 *
 * 生命周期：由 Hilt 管理，应用级单例。生产者（文件浏览页）[set] 后立即导航到图片查看页，
 * 消费者（[ImageViewerViewModel]）[consume] 后立即清空内部引用，避免泄漏。
 */
@Singleton
class ImageViewerRequestHolder @Inject constructor() {

    @Volatile
    private var request: ImageViewerRequest? = null

    /** 生产者调用：缓存图片查看请求，随后导航到图片查看页。 */
    fun set(request: ImageViewerRequest) {
        this.request = request
    }

    /** 消费者调用：取出并清空。[ImageViewerViewModel] init 时调用一次。 */
    fun consume(): ImageViewerRequest? {
        val current = request
        request = null
        return current
    }
}
