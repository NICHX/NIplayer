plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // FFmpeg 扩展依赖 media3 解码器基础设施（SimpleDecoder / DecoderAudioRenderer 等）
    implementation(libs.media3.common)
    implementation(libs.media3.decoder)
    implementation(libs.media3.exoplayer)

    // 注解依赖（compileOnly，运行时由 media3 传递）
    compileOnly("androidx.annotation:annotation:1.6.0")
    compileOnly("org.checkerframework:checker-qual:3.42.0")
}
