plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.feature.player"
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
    // Phase 1 接入 :core:common（AppCoroutineScope 统一协程作用域 O-13）
    implementation(project(":core:common"))
    // 阶段 3 接入 :player:kernel（NxPlayer 抽象 + NxMedia3Player 实现）
    implementation(project(":player:kernel"))
    // 阶段 2 接入 :core:navigation（路由常量 Routes.Player.PLAYER）
    implementation(project(":core:navigation"))
    // 阶段 5 P1 接入 :core:database（PlayHistoryDao 注入，PlayerViewModel 记录/更新播放历史）
    implementation(project(":core:database"))
    // 播放列表连播：PlayerViewModel 通过 StorageFactory 重建 Storage 构造播放源
    implementation(project(":core:storage"))
    // 阶段 5 P1 接入 :core:network（ASSRT 字幕搜索 API + OkHttpClient 下载字幕）
    implementation(project(":core:network"))
    // OkHttp：SubtitleSearchViewModel 直接注入 OkHttpClient 下载字幕文件
    implementation(libs.okhttp)
    // 阶段 5 P1 接入 :core:datastore（SubtitleSettings 持久化 assrtToken）
    implementation(project(":core:datastore"))
    // 阶段 5 接入 :core:designsystem（NiPopupMenu 等播放器菜单组件）
    implementation(project(":core:designsystem"))
    // 外挂字幕自渲染：SubtitleEngine + AssOverrideParser + RenderableCaption
    implementation(project(":core:subtitle"))
    // 缩略图生成：ThumbnailManager，音频封面 + 进度条帧预览
    implementation(project(":core:thumbnail"))
    // 播放历史云同步：播放器退出后自动同步
    implementation(project(":core:sync"))
    // Media3：media3-ui(SubtitleView) + media3-common(Cue/MimeTypes)，外挂字幕渲染所需
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.session)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // MediaStyle notification support
    implementation("androidx.media:media:1.7.0")
    // Coil（AsyncImage 加载音频封面缩略图）
    implementation(libs.coil.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Test
    testImplementation(libs.junit)
}
