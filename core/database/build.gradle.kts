plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nichx.niplayer.database"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        // Room schema 导出目录（用于 Migration 单元测试与版本审计）
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
        // MigrationTestHelper 从 assets 读 `<DB 类全限定名>/<version>.json`，
        // 因此 JVM 单元测试（Robolectric）也必须把 schemas/ 挂进 test 的 assets。
        getByName("test").assets.srcDirs("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            // Robolectric 需要真实资源/资产：schemas 目录下的 JSON 要能被 AssetManager 打开
            isIncludeAndroidResources = true

            // Robolectric 的 AndroidInterceptors 要反射 jdk.internal.access.SharedSecrets
            // 来设置 FileDescriptor 的原始 fd（ApplicationSharedMemory.create），
            // JDK 17 的模块封装默认拒绝，必须显式开放。少了这一段，所有 Robolectric 用例
            // 都会在 AndroidTestEnvironment.setUpApplicationState 阶段抛 IllegalAccessException。
            all {
                it.jvmArgs(
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/java.nio=ALL-UNNAMED",
                )
            }
        }
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

    // A1 修复：原先为导出 MMKV 设置而依赖 :core:datastore（Room 模块反向依赖设置层）。
    // AppSettingsBackup 已迁至 :core:datastore 并自注册，该依赖已删除；
    // 这里只需备份 SPI（BackupItem / RestoreMode）。
    implementation(project(":core:common"))

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt：提供 Database 与 Dao 注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines（Flow 返回类型）
    implementation(libs.kotlinx.coroutines.android)

    // Moshi（备份/恢复 JSON 序列化）
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    // Test
    testImplementation(libs.junit)
    // Room 迁移的数据库级验证（E3）：MigrationTestHelper 在真实 SQLite 上跑迁移并校验 schema
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
}
