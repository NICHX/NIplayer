package com.nichx.niplayer.common.media

/**
 * 媒体文件类型判断（基于扩展名）。
 *
 * BUG-1 修复：v2 原存在三套独立的扩展名表（`:player:kernel` 的 PlaybackRequestHolder 内部
 * private、`:feature:home` 的 `MediaFileTypes`、`:core:thumbnail` 的 `VIDEO_EXTENSIONS`），
 * 其中 `amr`/`m4s`/`vob`/`f4v`/`m2ts` 在不同表里存在性不一致，导致：
 * - `.amr`/`.m4s` 文件路由到错误的播放页（AudioPlayerScreen vs PlayerScreen）
 * - `.vob`/`.f4v`/`.m2ts` 文件不预加载服务端缩略图
 *
 * 此对象作为**唯一权威来源**。
 *
 * A1 架构修复（2026-09-21）：原放在 `:player:kernel`，使 `:core:thumbnail` 不得不依赖
 * `:player:kernel`（core 层依赖 player 层的**依赖倒置**）。现下移到 `:core:common` ——
 * 本模块无任何工程依赖，是各层唯一都能看到的最低点，倒置随之消除。
 *
 * **注意**：`m4s`（分片 MP4 流）实为视频，从音频扩展名表中移除；
 * `pcm` 在音频表中保留（无压缩音频）。
 */
object MediaFileTypes {

    /** 视频文件扩展名集合。 */
    val VIDEO_EXTENSIONS: Set<String> = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "ts", "webm", "3gp",
        "mpeg", "mpg", "m4v", "rmvb", "rm", "vob", "wmv", "f4v", "m2ts", "m4s",
    )

    /** 音频文件扩展名集合。 */
    val AUDIO_EXTENSIONS: Set<String> = setOf(
        "mp3", "wav", "flac", "ogg", "aac", "ape", "wma", "ac3",
        "m4a", "opus", "amr", "pcm",
    )

    /** 图片文件扩展名集合。 */
    val IMAGE_EXTENSIONS: Set<String> = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heif", "heic",
    )

    /** 是否为视频文件（按扩展名判断）。 */
    fun isVideoFile(name: String): Boolean =
        extensionOf(name) in VIDEO_EXTENSIONS

    /** 是否为音频文件（按扩展名判断）。 */
    fun isAudioFile(name: String): Boolean =
        extensionOf(name) in AUDIO_EXTENSIONS

    /** 是否为图片文件（按扩展名判断）。 */
    fun isImageFile(name: String): Boolean =
        extensionOf(name) in IMAGE_EXTENSIONS

    /** 是否为可播放的媒体文件（视频或音频）。 */
    fun isMediaFile(name: String): Boolean =
        isVideoFile(name) || isAudioFile(name)

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        // dot == 0 视为隐藏文件（如 .mp4），不当作媒体处理
        return if (dot > 0 && dot < name.length - 1) {
            name.substring(dot + 1).lowercase()
        } else {
            ""
        }
    }
}
