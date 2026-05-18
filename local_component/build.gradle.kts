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

    implementation(Dependencies.Github.jsoup)

    ksp(Dependencies.Alibaba.arouter_ksp)
}
android {
    namespace = "com.xyoye.local_component"
}
