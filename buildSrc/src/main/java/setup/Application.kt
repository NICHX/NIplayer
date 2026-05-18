package setup

import com.android.build.gradle.AppExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByName
import setup.utils.setupDefaultDependencies
import setup.utils.setupKotlinOptions
import setup.utils.setupOutputApk
import setup.utils.setupSignConfigs

@Suppress("UnstableApiUsage")
fun Project.applicationSetup() {
    extensions.getByName<AppExtension>("android").apply {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        buildFeatures.apply {
            dataBinding.isEnabled = true
            buildConfig = true
        }

        setupSignConfigs(this@applicationSetup)
        setupOutputApk()
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