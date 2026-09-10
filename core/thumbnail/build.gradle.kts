plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.thumbnail"
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

    // 模块依赖
    // Storage/StorageFile 抽象：createPlayUrl / 文件路径
    implementation(project(":core:storage"))
    // MediaLibraryEntity：Storage.library 引用
    implementation(project(":core:database"))
    // ThumbnailSettings：缩略图开关（generateForVideo/saveInSameDir 等）
    implementation(project(":core:datastore"))
    // MediaFileTypes：媒体扩展名权威来源（ARCH-3 require 断言用）
    implementation(project(":player:kernel"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // OkHttp：lrcapi 远程音乐元数据获取（封面回退）
    implementation(libs.okhttp)

    // Test
    testImplementation(libs.junit)
}
