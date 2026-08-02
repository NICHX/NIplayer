plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.storage"
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
    implementation(libs.androidx.documentfile)

    // 模块依赖
    // api：Storage.library 返回 MediaLibraryEntity，依赖 :core:storage 的模块
    // （如 :player:kernel 的 MediaSourceBuilder）需要访问该类型
    api(project(":core:database"))
    // VideoScanner 读取 VideoExtensionSettings 判断视频扩展名
    implementation(project(":core:datastore"))
    // 共享 :core:network 的 OkHttpClient（WebDavStorage 注入）
    implementation(project(":core:network"))

    // 协议实现依赖（SMB 协议客户端 codelibs/jcifs，WebDAV 使用 OkHttp 原生实现）
    implementation(libs.jcifs) {
        exclude(group = "jakarta.servlet")
        exclude(group = "jakarta.annotation")
    }
    // jcifs 的 NTLM 认证（MD4）、SMB3 加密（AES-CCM/AES-CMAC）依赖完整版 BouncyCastle：
    // Android 系统内置的 BC 是裁剪版（无 MD4），且 R8 会裁剪传递依赖的 bcprov 类，
    // 此处显式引入并配合 proguard 规则完整保留 org.bouncycastle.**，否则 SMB 连接在
    // 认证阶段抛 NoSuchAlgorithmException: MD4 / AESCMAC。
    implementation(libs.bcprov) {
        exclude(group = "org.bouncycastle", module = "bcutil")
        exclude(group = "org.bouncycastle", module = "bcpkix")
    }
    runtimeOnly(libs.slf4j.nop)

    // OkHttp API（WebDavStorage 直接构造 Request / Interceptor）
    implementation(libs.okhttp)

    // media3 DataSource：实现 StorageDataSource，让 SMB 等非 HTTP 协议接入 media3 播放
    // 替代旧仓库 SmbPlayServer 的 NanoHTTPD 本地代理
    // media3-datasource 将 media3-common 列为 runtime scope，需额外引入 media3-common
    // 才能在编译期访问 DataReader / C 等类型
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)

    // Hilt：提供 StorageFactory 注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines（suspend 函数）
    implementation(libs.kotlinx.coroutines.android)

    // Test
    testImplementation(libs.junit)
}
