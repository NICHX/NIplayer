rootProject.name="DanDanPlayForAndroid"

include(":app")
include(":local_component")
include(":user_component")
include(":storage_component")
include(":player_component")
include(":common_component")
include(":data_component")

include(":repository:panel_switch")
include(":repository:seven_zip")

pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}