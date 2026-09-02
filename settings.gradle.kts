pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NIplayer-v2"
include(":app")
include(":core:subtitle")
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:datastore")
include(":core:navigation")
include(":core:storage")
include(":core:designsystem")
include(":core:thumbnail")
include(":core:sync")
include(":player:kernel")
include(":player:ffmpeg")
include(":player:mpv")
include(":feature:player")
include(":feature:home")

