import setup.moduleSetup

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")
    kotlin("kapt")
}

moduleSetup()

ksp {
    arg("AROUTER_MODULE_NAME", name)
}

dependencies {
    implementation(project(":common_component"))

    ksp(Dependencies.Alibaba.arouter_ksp)
}
android {
    namespace = "com.xyoye.storage_component"
}
