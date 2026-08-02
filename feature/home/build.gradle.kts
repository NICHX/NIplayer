plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.feature.home"
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
    // 阶段 5 接入 :core:designsystem（NiTheme / NiColorScheme / NiTypography）
    implementation(project(":core:designsystem"))
    // 阶段 2 接入 :core:navigation（路由常量 Routes.Home.* / Routes.Stream.*）
    implementation(project(":core:navigation"))
    // 阶段 5 接入 :core:database（MediaLibraryDao 注入，LibraryScreen / StorageFileScreen 读取存储源）
    implementation(project(":core:database"))
    // 阶段 5 接入 :core:storage（StorageFactory / Storage / StorageDataSource，文件浏览与播放源构造）
    implementation(project(":core:storage"))
    // 阶段 5 接入 :player:kernel（NxMediaSource / PlaybackRequestHolder，构造播放请求并跨模块传递）
    implementation(project(":player:kernel"))
    // 阶段 5 P1 接入 :core:datastore（SubtitleSettings，播放器设置页字幕配置）
    implementation(project(":core:datastore"))
    // 阶段 5 P1 接入 :core:thumbnail（ThumbnailManager，视频缩略图生成与磁盘缓存）
    implementation(project(":core:thumbnail"))

    implementation(libs.androidx.core.ktx)
    // DocumentFile：StorageFileScreen 下载目标目录选择时读取 SAF tree URI 显示名
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // Navigation（嵌套 NavHost 切换三 Tab + 文件浏览页带参路由）
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image（AsyncImage 加载缩略图 + coil-video 视频帧）
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)

    // 拖拽排序（快速访问页编辑模式下长按拖动重排）
    implementation(libs.compose.reorderable)

    // media3-datasource：编译期可见 androidx.media3.datasource.DataSource.Factory 类型
    // （NxMediaSource.DataSource 构造参数引用该类型；:player:kernel / :core:storage 均
    // implementation 引入不传递，:feature:home 构造 StorageDataSource.Factory 需直接依赖）
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)

    testImplementation(libs.junit)
}
