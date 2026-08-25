import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChinaUnicomAndroid"

include(":app")
include(":core:design")
include(":core:model")
include(":core:parser")
include(":core:network")
include(":core:security")
include(":core:login")
include(":core:storage")
include(":data:account")
include(":data:settings")
include(":data:refresh")
include(":data:balance")
include(":data:orderedbusiness")
