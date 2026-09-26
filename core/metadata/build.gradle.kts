plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nichx.niplayer.metadata"
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

    // P0 阶段只放纯函数（解析 / 判定 / 候选），不引入 storage / database / hilt / okhttp。
    // 后续 P1 落地 readAudioTags 与 Provider 时，再按需补 project(":core:storage") 等依赖。

    testImplementation(libs.junit)
}
