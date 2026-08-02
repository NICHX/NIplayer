# NIplayer ProGuard / R8 规则（O-19）
#
# 大部分第三方库（Room / Media3 / OkHttp / Retrofit / Moshi / Hilt / Compose / Coil / MMKV）
# 自带 consumer-rules，启用 R8 后自动应用。本文件仅补充项目自身与少量第三方需要的手动规则。

# -----------------------------------------------------------------------------
# Kotlin / Coroutines
# -----------------------------------------------------------------------------
# Kotlin 元数据：反射型库（kotlinx.serialization 等）依赖 @Metadata，保留以避免运行时缺失
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod,Deprecated,AnnotationDefault
-keep class kotlin.Metadata { *; }

# 协程：keep 内部状态机类，避免 DebugProbes 等被裁剪导致栈丢失
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }

# -----------------------------------------------------------------------------
# Hilt / Dagger
# -----------------------------------------------------------------------------
# Hilt 生成的类与 @Inject 构造由 hilt-compiler 处理，但需保留 @HiltViewModel 与 @AndroidEntryPoint
# 子类（Activity/Fragment/Service/Application）以供反射实例化。
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# -----------------------------------------------------------------------------
# Room
# -----------------------------------------------------------------------------
# Room 自带 consumer-rules 保留生成实现，补充保留 @Entity / @Dao / @Database 注解类，
# 避免 DAO 方法返回的实体被裁剪字段（部分查询依赖反射映射列名）。
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# -----------------------------------------------------------------------------
# Compose / Compose Compiler
# -----------------------------------------------------------------------------
# Compose 自带 consumer-rules。补充保留 Composable 函数的 @Composable 注解元数据。
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------------
# MMKV
# -----------------------------------------------------------------------------
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# -----------------------------------------------------------------------------
# Coil 3
# -----------------------------------------------------------------------------
-dontwarn coil3.**
-keep class coil3.** { *; }

# -----------------------------------------------------------------------------
# Moshi（备份/恢复 JSON 序列化）
# -----------------------------------------------------------------------------
# codegen 生成的适配器在运行时通过反射调用 @FromJson/@ToJson 方法（如 BackupManager 的
# DateAdapter），需保留这些方法及其宿主类，否则 Moshi 序列化备份数据时找不到适配器。
-keepclasseswithmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonQualifier *;
}

# -----------------------------------------------------------------------------
# 第三方杂项
# -----------------------------------------------------------------------------
# BouncyCastle（jcifs SMB：NTLM 认证 MD4、SMB3 签名 AES-CMAC、SMB3 加密 AES-CCM/GCM）
# Android 系统内置 BC 为裁剪版（无 MD4），jcifs 依赖完整版 bcprov 的 JCA provider 与
# low-level crypto API（AESEngine/CCMBlockCipher/HMac 等直接 new），必须完整保留，
# 否则 SMB 连接抛 NoSuchAlgorithmException: MD4 / AESCMAC 或 NoClassDefFoundError。
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
# jsoup（字幕/歌词 HTML 解析）
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
# juniversalchardet（编码探测）
-keep class org.mozilla.universalchardet.** { *; }
-dontwarn org.mozilla.universalchardet.**
# compose-reorderable（快速访问拖拽排序）
-dontwarn com.burnoutcrew.**

# -----------------------------------------------------------------------------
# 字幕自渲染引擎
# -----------------------------------------------------------------------------
# SubtitleEngine / AssOverrideParser 解析 ASS/SSA override tags，保留包名避免裁剪
-keep class com.nichx.niplayer.subtitle.** { *; }

# -----------------------------------------------------------------------------
# JNI / Native 方法
# -----------------------------------------------------------------------------
# FFmpeg JNI keep 规则已在 :player:ffmpeg consumer-rules 提供，此处保留通用 native 方法声明
-keepclasseswithmembernames class * {
    native <methods>;
}

# -----------------------------------------------------------------------------
# 枚举
# -----------------------------------------------------------------------------
# 枚举的 values()/valueOf() 依赖反射，保留以避免 EnumFieldNotFoundException。
# 枚举常量字段（<fields>）同样必须保留：Moshi EnumJsonAdapter 会按源码常量名
# （Class.getField）反射解析枚举（如 MediaType.LOCAL_STORAGE），R8 若重命名常量
# 会在启动时抛 NoSuchFieldException: LOCAL_STORAGE 导致闪退。
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
