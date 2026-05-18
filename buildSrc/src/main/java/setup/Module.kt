package setup

import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByName
import setup.utils.setupDefaultDependencies
import setup.utils.currentCommit
import setup.utils.setupKotlinOptions

@Suppress("UnstableApiUsage")
fun Project.moduleSetup() {
    extensions.getByName<LibraryExtension>("android").apply {
        compileSdk = Versions.compileSdkVersion
        defaultConfig {
            minSdk = Versions.minSdkVersion
            targetSdk = Versions.targetSdkVersion
        }

        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }

            getByName("debug") {
                initWith(buildTypes.getByName("debug"))
            }

            create("beta") {
                initWith(buildTypes.getByName("beta"))
            }

            buildTypes.forEach {
                it.buildConfigField("String", "BUILD_COMMIT", "\"${currentCommit()}\"")
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        buildFeatures {
            dataBinding = true
            buildConfig = true
        }
    }

    setupKotlinOptions()
    setupDefaultDependencies()

    afterEvaluate {
        tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
            val generateBuildConfig = tasks.findByName("generateBuildConfig")
            if (generateBuildConfig != null) {
                dependsOn(generateBuildConfig)
            }
        }
    }
}