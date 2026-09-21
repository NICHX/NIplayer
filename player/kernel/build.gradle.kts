plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.player.kernel"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // MediaFileTypes：媒体扩展名权威来源（A1 修复后由 :core:common 提供）
    implementation(project(":core:common"))

    // A1 修复：原先为读 AudioSettings 而依赖 :core:datastore（内核 → 设置持久化层的依赖倒置）。
    // 现改为由上层经 EqualizerConfigProvider 注入配置，该依赖已删除。

    // 模块依赖：共享 :core:network 的 OkHttpClient（替代旧 NxMedia3Player 内部 new OkHttpClient）
    // :core:network 原先在此声明但**全模块零引用**（仅 KDoc 提及；OkHttpClient 类型经
    // libs.media3.datasource.okhttp 传递获得）。A5 同类清理，2026-09-21。

    // 模块依赖：MediaSourceBuilder 桥接 Storage + NxMediaSource（播放列表连播重建播放源）
    implementation(project(":core:storage"))

    // Media3 单一内核（取代 exo/ijk/vlc 三套实现）
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)

    // FFmpeg 音频软解扩展（TrueHD / E-AC-3 JOC / DTS-HD 等格式依赖此扩展）
    implementation(project(":player:ffmpeg"))

    // Hilt：@Binds NxMedia3Player → NxPlayer
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines（StateFlow / SharedFlow）
    implementation(libs.kotlinx.coroutines.android)

    // Test
    testImplementation(libs.junit)
}
