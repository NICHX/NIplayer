plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation(kotlin("gradle-plugin", "2.2.20"))
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.2.20-2.0.4")
}
