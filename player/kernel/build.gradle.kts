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

    // F-02：AudioSettings（均衡器配置持久化）
    implementation(project(":core:datastore"))

    // 模块依赖：共享 :core:network 的 OkHttpClient（替代旧 NxMedia3Player 内部 new OkHttpClient）
    implementation(project(":core:network"))

    // 模块依赖：MediaSourceBuilder 桥接 Storage + NxMediaSource（播放列表连播重建播放源）
    implementation(project(":core:storage"))

    // Media3 单一内核（替代旧仓库 exo/ijk/vlc 三套实现）
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
