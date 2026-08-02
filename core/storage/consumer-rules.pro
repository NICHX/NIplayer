# :core:storage 消费者 ProGuard 规则
# WebDAV 使用 OkHttp 原生实现，默认 R8 处理即可

# codelibs/jcifs 内部大量使用反射（NTLM 认证、传输层、协议协商等）
-keep class org.codelibs.jcifs.** { *; }
-dontwarn org.codelibs.jcifs.**
