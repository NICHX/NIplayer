# Add project specific ProGuard rules here.

# ARouter
-keep class com.alibaba.android.arouter.** { *; }
-keep class com.xyoye.** { *; }
-keep public class * extends com.alibaba.android.arouter.facade.template.IProvider
-keep public class * implements com.alibaba.android.arouter.facade.template.IProvider
-keep class * implements com.alibaba.android.arouter.facade.template.IProvider {
    public <methods>;
}
-keepclassmembers class * implements com.alibaba.android.arouter.facade.template.IProvider {
    public <methods>;
}
-keep class com.alibaba.android.arouter.core.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# MMKV
-keep class com.tencent.mmkv.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes
-keep class com.xyoye.data_component.entity.** { *; }
-keep class com.xyoye.data_component.bean.** { *; }
-keep class com.xyoye.data_component.enums.** { *; }

# VLC
-keep class org.videolan.** { *; }

# Sardine WebDAV
-keep class com.xyoye.sardine.** { *; }
-dontwarn com.xyoye.sardine.**

# FFmpeg
-keep class org.ffmpeg.** { *; }

# OpenCC
-keep class com.xyoye.open_cc.** { *; }

# If you keep the line number information, uncomment this to hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
