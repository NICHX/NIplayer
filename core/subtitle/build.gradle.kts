plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nichx.niplayer.subtitle"
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
    // 编码检测（ASS/SRT/TTML 解析器依赖）
    implementation(libs.juniversalchardet)

    // Coroutines（SubtitleEngine 使用 StateFlow 暴露渲染状态）
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
