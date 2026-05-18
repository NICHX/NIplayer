package setup.utils

import Dependencies
import Versions
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApkVariantOutput
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

fun Project.setupKotlinOptions() {
    tasks.withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

fun Project.setupDefaultDependencies() {
    dependencies.apply {
        add("implementation", fileTree("libs") {
            include("*.jar")
        })

        add("testImplementation", Dependencies.Junit.junit)
        add("androidTestImplementation", Dependencies.AndroidX.junit_ext)
        add("androidTestImplementation", Dependencies.AndroidX.espresso)
    }
}

fun Project.currentCommit(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine = "git log --pretty=format:%h -1".split(" ")
        standardOutput = stdout
    }
    return stdout.toString()
}

fun AppExtension.setupSignConfigs(project: Project) = apply {
    signingConfigs {
        named("debug") {
            SignConfig.debug(project, this)
        }

        create("release") {
            SignConfig.release(project, this)
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.findByName(this.name)
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("release") {
            signingConfig = signingConfigs.findByName(this.name)
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("beta") {
            initWith(getByName("release"))
        }
    }
}

fun AppExtension.setupOutputApk() = apply {
    applicationVariants.all {
        outputs.filter { it is ApkVariantOutput }
            .map { it as ApkVariantOutput }
            .onEach {
                it.outputFileName = "NIplayer_v${Versions.versionName}_${it.name}.apk"
            }
    }
}