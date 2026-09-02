# :player:mpv R8 / ProGuard 规则。

# MPVLib (is.xyz.mpv) 通过 JNI external 方法名 (Java_is_xyz_mpv_MPVLib_*) 与自编译 libmpv.so/libplayer.so
# 硬绑定；R8 混淆/裁剪会改类名或方法名导致 UnsatisfiedLinkError，必须整体保留。
-keep class is.xyz.mpv.** { *; }
-keepnames class is.xyz.mpv.** { *; }

# NxMpvPlayer 作为 NxPlayerBackend 多绑定成员由 Hilt/反射解析，保留类与构造。
-keep class com.nichx.niplayer.player.mpv.** { *; }