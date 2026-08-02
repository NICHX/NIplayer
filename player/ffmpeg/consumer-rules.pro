# 保持 FFmpeg JNI 类名（JNI 函数名绑定 Java 包名，混淆会导致 UnsatisfiedLinkError）
-keep class androidx.media3.decoder.ffmpeg.** { *; }
