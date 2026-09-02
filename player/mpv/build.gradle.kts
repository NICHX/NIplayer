plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.player.mpv"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        // 自编译产物仅 arm64-v8a（libmpv + ffmpeg 共享库），后续如有 v7 需求再补
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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
    // 多内核契约：NxPlayerBackend / NxPlayer / NxPlayerProvider
    implementation(project(":player:kernel"))

    // NxPlayer.cues 暴露 androidx.media3.common.text.Cue（kernel 用 implementation 非传递，需显式引入）
    implementation(libs.media3.common)

    // Hilt：@Binds @IntoSet 把 NxMpvPlayer 注册进 NxPlayerBackend 集合，供解析器消费
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines（StateFlow / SharedFlow）
    implementation(libs.kotlinx.coroutines.android)
}