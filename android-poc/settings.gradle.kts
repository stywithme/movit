pluginManagement {
    includeBuild("build-logic")
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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Movit"
include(":app")
include(":shared")
include(":core:model")
include(":core:resources")
include(":core:designsystem")
include(":core:network")
include(":core:data")
include(":core:training-engine")
include(":core:pose-capture")
include(":feature:explore")
include(":feature:home")
include(":feature:train")
include(":feature:reports")
include(":feature:library")
include(":feature:training")
include(":feature:account")
include(":feature:shell")
include(":feature:billing")
