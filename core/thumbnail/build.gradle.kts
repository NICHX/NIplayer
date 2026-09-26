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
    // MediaFileTypes：媒体扩展名权威来源（A1 修复后位于 :core:common，
    // 从而消除「:core:thumbnail 依赖 :player:kernel」的 core→player 依赖倒置）
    implementation(project(":core:common"))
    // 音频标签：提取内嵌封面时顺手读取 title/artist/album 并写入 AudioTagCache，
    // 使浏览期就能把标签预置好（含 SMB/WebDAV 远程文件），播放期零延迟命中
    implementation(project(":core:metadata"))

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
