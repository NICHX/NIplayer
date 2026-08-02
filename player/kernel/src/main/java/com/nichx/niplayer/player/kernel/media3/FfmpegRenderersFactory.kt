package com.nichx.niplayer.player.kernel.media3

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * 自定义 RenderersFactory：在 MediaCodec 硬解之前插入 FFmpeg 软解。
 *
 * 替代 v1 旧仓库 com.google.android.exoplayer2.ext.FfmpegRenderersFactory。
 *
 * 使用 EXTENSION_RENDERER_MODE_PREFER：
 * - FFmpeg 优先尝试软解（TrueHD / E-AC-3 JOC / DTS-HD 等 MediaCodec 不支持的格式）
 * - MediaCodec 作为兜底（AAC / MP3 / FLAC 等硬件支持的格式由硬件解码，省电）
 *
 * 注意：FFmpeg 扩展仅处理音频，视频仍由 MediaCodec 硬件解码。
 *
 * M-36 修复：父类 [DefaultRenderersFactory] 在 [EXTENSION_RENDERER_MODE_PREFER] 下
 * 已自动通过反射添加 FfmpegAudioRenderer，原实现再手动 `out.add(FfmpegAudioRenderer())`
 * 会导致重复注册。ExoPlayer 按顺序选第一个支持的，第二个永远不启用，浪费对象与初始化成本。
 * 现移除手动 add，仅依赖父类的 EXTENSION_RENDERER_MODE_PREFER 自动注册。
 */
class FfmpegRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    }

    // M-36 修复：不重写 buildAudioRenderers。
    // 父类 DefaultRenderersFactory 在 EXTENSION_RENDERER_MODE_PREFER 下会自动通过反射
    // 加载 androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer 并按 PREFER 顺序插入，
    // 无需手动添加。重写并手动 add 会导致重复注册（两个 FFmpeg 渲染器实例）。
}
