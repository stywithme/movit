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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Movit"
include(":app")
include(":shared")
include(":core:resources")
include(":core:designsystem")
include(":core:network")
include(":core:data")
include(":core:training-engine")
include(":feature:explore")
include(":feature:home")
include(":feature:train")
include(":feature:reports")
include(":feature:library")
include(":feature:account")
include(":feature:shell")
