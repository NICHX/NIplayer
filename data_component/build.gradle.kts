import setup.moduleSetup

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
}

moduleSetup()

ksp {
    arg("AROUTER_MODULE_NAME", name)
}

dependencies {
    implementation(Dependencies.Kotlin.stdlib_jdk7)

    implementation(Dependencies.AndroidX.core)
    implementation(Dependencies.AndroidX.room)

    api(Dependencies.Alibaba.arouter_api)
    api(Dependencies.Square.moshi)

    ksp(Dependencies.Alibaba.arouter_ksp)
}
android {
    namespace = "com.xyoye.data_component"
}
