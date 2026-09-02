# Add project specific ProGuard rules here.
# :player:mpv 当前仅含 Kotlin 接入骨架，暂无需要 keep 的 native/JNI 规则。
# 后续接入 MPVLib (is.xyz.mpv) 与 libmpv.so 时，需保留 ——keep class is.xyz.mpv.** { *; }
# 并关闭 mpv 相关类的混淆收缩（R8 会裁剪外部 JNI 反射入口）。