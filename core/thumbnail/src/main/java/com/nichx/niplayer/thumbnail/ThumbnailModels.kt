package com.nichx.niplayer.thumbnail

/** 缩略图生成结果。 */
sealed class ThumbnailResult {
    /** 生成成功，path 为本地 JPEG 文件绝对路径。 */
    data class Success(val path: String) : ThumbnailResult()

    /** 生成失败（IO 错误、解码失败等）。 */
    data object Failed : ThumbnailResult()

    /**
     * 永久失败：401/403 凭证错误等不可重试场景。
     *
     * 调用方据此可避免在凭据未变时反复重试同一文件。
     */
    data object PermanentFailure : ThumbnailResult()
}

/**
 * 远程缩略图生成请求。
 *
 * 抽象输入，使 [ThumbnailManager.generateRemoteThumbnails] 不依赖 PlayHistoryEntity，
 * HomeTabViewModel / PlayHistoryViewModel 各自负责实体转换。
 *
 * @param storageId 媒体库 id
 * @param filePath 文件在存储中的路径
 * @param fileName 文件名（含扩展名）
 * @param url 作为回调 key 返回给调用方，通常为 PlayHistoryEntity.url
 * @param isAudio 是否为音频文件（true 走音频封面流程）
 * @param isImage 是否为图片文件（true 走图片缩略图流程）；音频与图片均为 false 时走视频取帧
 */
data class RemoteThumbnailRequest(
    val storageId: Int,
    val filePath: String,
    val fileName: String,
    val url: String,
    val isAudio: Boolean,
    val isImage: Boolean = false,
)
