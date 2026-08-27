plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.nichx.niplayer.designsystem"
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Phase 1 接入 :core:common（NiMessage 统一消息模型，供 NiSnackbarHost 渲染 O-25）
    implementation(project(":core:common"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    // api: LocalHazeState 等暴露 HazeState 类型，需传递给下游模块（feature:home 等）
    api(libs.haze)
    // 液态玻璃悬浮底栏：backdrop 提供背景捕获/模糊（HomeScreen 需 layerBackdrop），api 暴露其类型
    api(libs.backdrop)
    implementation(libs.capsule)
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
}
