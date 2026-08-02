plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.common"
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

    // Hilt：提供 AppCoroutineScope / DispatcherProvider / CrashHandler 注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines：AppCoroutineScope 与结构化并发基础
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
