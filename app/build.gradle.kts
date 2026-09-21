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
        versionCode = 21
        versionName = "2.5.9"
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
    // 项目模块：仅声明 :app 源码**真正直接 import 类型**的 9 个。
    // - :core:common（AppCoroutineScope / CrashHandler / AppMessageController）
    // - :core:database（NiApplication 校验本地媒体库：MediaLibraryDao / MediaLibraryEntity）
    // - :core:datastore（ThemeSettings / GlassSettings / LanguageSettings / IconSettings）
    // - :core:navigation（NiNavHost / Routes）
    // - :core:sync（PlayHistorySyncManager）
    // - :core:designsystem（NiTheme 与全部玻璃组件 / CompositionLocal）
    // - :player:kernel（di/AudioWiringModule 装配 EqualizerConfigProvider，A1 修复引入）
    // - :feature:home / :feature:player（两个功能模块的 UI 入口）
    //
    // A2 修复（2026-09-21）：原先还直接声明 :core:network / :core:storage / :core:subtitle /
    // :core:thumbnail 共 4 个「全模块零 import」的依赖，它们已由上面各模块传递引入，删除后
    // 经 assembleDebug / assembleRelease 与 Hilt 组件树比对验证，注入图未受影响。
    // （:player:kernel 起初一并删除，随即被 :app:kspDebugKotlin 报出 EqualizerConfigProvider
    //   无法解析 —— 编译期验证确实拦住了这次误删，故保留。）
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:navigation"))
    implementation(project(":core:sync"))
    implementation(project(":core:designsystem"))
    implementation(project(":player:kernel"))
    implementation(project(":feature:home"))
    implementation(project(":feature:player"))

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

    // Network：:app 自身**不使用** OkHttp / Retrofit / Moshi（全模块无相关 import），
    // 使用方各自声明（:core:network / :core:database / :core:sync）。
    // 原先在此重复声明属冗余，A5 于 2026-09-21 清理。
    // moshi-kotlin-codegen 由 :core:network 通过 KSP 统一处理。

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // KV：:app 的 NiApplication 直接调用 MMKV.initialize()，此声明**必需**（勿删）
    implementation(libs.mmkv)

    // Baseline Profile：安装后由 profileinstaller / 系统（Android 15+）AOT 编译启动热路径
    implementation(libs.androidx.profileinstaller)

    // Image
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)

    // Test
    testImplementation(libs.junit)
}
