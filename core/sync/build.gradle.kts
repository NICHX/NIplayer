plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.sync"
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

    // 设置配置（同步开关 / WebDAV 服务器 / 游标）
    implementation(project(":core:datastore"))

    // 播放历史 DAO 与删除日志 DAO
    implementation(project(":core:database"))

    // WebDAV 传输（Storage / StorageFactory / WebDavHttpException）
    implementation(project(":core:storage"))

    // Hilt：提供 PlayHistorySyncManager 单例注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines（Flow / Mutex / Dispatchers）
    implementation(libs.kotlinx.coroutines.android)

    // Moshi（同步文件 JSON 序列化）
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    // Test
    testImplementation(libs.junit)
}
