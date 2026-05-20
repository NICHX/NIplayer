import setup.moduleSetup

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
}

moduleSetup()

android {
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("libs")
        }
    }
    namespace = "com.xyoye.player_component"
}

kapt {
    arguments {
        arg("AROUTER_MODULE_NAME", name)
    }
}

dependencies {

    implementation(project(":common_component"))
    implementation(project(":repository:panel_switch"))

    implementation(Dependencies.Github.keyboard_panel)

    // TODO 暂时移除，编译出64位后再考虑重新添加
    //implementation "com.github.ctiao:ndkbitmap-armv7a:0.9.21"

    implementation(Dependencies.Google.exoplayer)
    implementation(Dependencies.Google.exoplayer_core)
    implementation(Dependencies.Google.exoplayer_dash)
    implementation(Dependencies.Google.exoplayer_hls)
    implementation(Dependencies.Google.exoplayer_smoothstraming)
    implementation(Dependencies.Google.exoplayer_rtmp)

    implementation(Dependencies.VLC.vlc)

    //GSYVideoPlayer ex_so: 更新 IJKPlayer 原生库，适配 Android 16+ 与 16K Page Size
    implementation("io.github.carguo:gsyvideoplayer-ex_so:13.0.0")

    kapt(Dependencies.Alibaba.arouter_compiler)

    implementation("com.github.wangchenyan:lrcview:2.2.2")

    // Media3 - 仅音频播放器使用
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")

    // Media compat - 通知按钮和MediaStyle使用
    implementation("androidx.media:media:1.6.0")
}
