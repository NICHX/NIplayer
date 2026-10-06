# :core:thumbnail 消费者 ProGuard 规则
# 本模块仅使用 Android 平台 API（MediaMetadataRetriever / Bitmap），无反射调用，默认 R8 处理即可。
# 例外：FfmpegFrameDecoder 含 JNI 方法 nativeDecode，其类名必须与 C++ 侧符号
# Java_com_nichx_niplayer_thumbnail_FfmpegFrameDecoder_nativeDecode 一致，故不可混淆/裁剪。
-keep class com.nichx.niplayer.thumbnail.FfmpegFrameDecoder { *; }
