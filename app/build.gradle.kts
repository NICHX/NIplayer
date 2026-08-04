import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 正式版签名配置，优先级：CI 环境变量（GitHub Secrets） > 本地 keystore.properties
// keystore.properties 仅用于本地构建，不入库（见 .gitignore）
val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        FileInputStream(propertiesFile).use { load(it) }
    }
}

val releaseStoreFile: String? = System.getenv("RELEASE_STORE_FILE")
    ?: keystoreProperties.getProperty("storeFile")
val releaseStorePassword: String? = System.getenv("RELEASE_STORE_PASSWORD")
    ?: keystoreProperties.getProperty("storePassword")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
    ?: keystoreProperties.getProperty("keyAlias")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
    ?: keystoreProperties.getProperty("keyPassword")

android {
    namespace = "com.nichx.niplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nichx.niplayer"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "2.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // 仅当完整提供 keystore 信息时注册 release 签名，否则产出未签名 APK/AAB
        if (releaseStoreFile != null && releaseStorePassword != null &&
            releaseKeyAlias != null && releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // O-19：启用 R8 代码裁剪/混淆 + 资源压缩，配合 proguard-rules.pro 的 keep 规则
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    // O-19：lint 配置
    // 临时关闭 release 构建的 lint 检查：AGP 9.3.0 的 lint 工具内部依赖
    // Java 21+ 的 List.removeLast()（JDK 21 引入），而当前 Gradle 运行在 JDK 17，
    // 导致 lintVitalAnalyzeRelease 抛出 NoSuchMethodError（lint 工具自身 bug，非代码问题）。
    // R8 混淆/裁剪本身不受影响。待 Gradle JVM 升级到 JDK 21+ 后可恢复 checkReleaseBuilds = true。
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // 项目模块：
    // - 阶段 1 接入 :core:database（含 Hilt Module 提供 5 表 Dao 注入）
    // - 阶段 2 接入 :core:navigation（统一导航宿主）
    // - 阶段 3 接入 :player:kernel（media3 单一内核，替代旧仓库 exo/ijk/vlc 三套实现）
    // - 阶段 3 接入 :feature:player（PlayerViewModel + PlayerScreen，端到端验证 NxPlayer 注入链路）
    // - 阶段 4 接入 :core:storage（Storage 抽象 + 4 套协议实现 Local/WebDAV/SMB/FTP）
    // - 阶段 5 接入 :core:designsystem（NiTheme / NiColorScheme / NiTypography）
    // - 阶段 5 接入 :feature:home（主页底部导航 + 三 Tab Composable）
    implementation(project(":core:subtitle"))
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:navigation"))
    implementation(project(":core:storage"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:thumbnail"))
    implementation(project(":core:sync"))
    implementation(project(":player:kernel"))
    implementation(project(":feature:player"))
    implementation(project(":feature:home"))

    // Core / Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Media3：媒体3 实际依赖由 :player:kernel 提供，:app 仅以传递依赖形式引入
    // UI / session 等组件（如 PlayerView、MediaSession）仍由 :app 直接依赖，供后续 UI 层使用

    // Network（实际使用由 :core:network 提供，:app 仅传递依赖即可；
    // 保留是为了未来 :app 直接构造 Retrofit/OkHttp 时无需额外配置）
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.moshi)
    // moshi-kotlin-codegen 由 :core:network 通过 KSP 统一处理；
    // :app 当前无 @JsonClass 类，不在本模块配置 ksp(moshi-kotlin-codegen)
    // 以避免 moshi 的 kapt deprecation 警告（hiltJavaCompileDebug 任务触发）

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // KV
    implementation(libs.mmkv)

    // Image
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)

    // Other
    implementation(libs.jsoup)

    // Test
    testImplementation(libs.junit)
}
