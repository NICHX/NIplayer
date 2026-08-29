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
 * 生命周期：由 Hilt 管理，应用级单例。生产者（文件浏览页/下载管理页）[set] 后立即导航到
 * 图片查看页，消费者（[ImageViewerViewModel]）[peek] 读取。
 *
 * 请求不做"取即清空"处理：数据仅为三个基本类型（storageId/目录/文件路径），体积可忽略；
 * 每次都整份覆盖写入新值。保留最后一次请求可让因系统返回手势（predictive back）等原因被
 * 重建的 [ImageViewerViewModel] 在 [peek] 时仍能拿到请求，避免误报"无效的查看请求"。
 */
@Singleton
class ImageViewerRequestHolder @Inject constructor() {

    @Volatile
    private var request: ImageViewerRequest? = null

    /** 生产者调用：缓存图片查看请求（覆盖旧值），随后导航到图片查看页。 */
    fun set(request: ImageViewerRequest) {
        this.request = request
    }

    /** 消费者调用：读取最近一次请求，不消费（不清空），供 ViewModel 重建后重新加载。 */
    fun peek(): ImageViewerRequest? = request
}
