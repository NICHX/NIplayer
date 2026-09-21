plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.datastore"
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
    implementation(libs.mmkv)
    // A1 修复：原先因 ThemeSettings 持有 NiScheme 类型而依赖 :core:designsystem（数据层依赖 UI 层）。
    // 改为只存序号后该依赖已删除。
    // A1 修复：备份 SPI（BackupItem / RestoreMode）位于 :core:common，AppSettingsBackup 在本模块自注册
    implementation(project(":core:common"))
    // 备份/恢复 JSON 序列化（AppSettingsData 走 @JsonClass 代码生成）
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    // Hilt：@IntoSet 自注册 AppSettingsBackup
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
