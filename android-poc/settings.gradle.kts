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
include(":core:designsystem")
include(":feature:explore")
include(":feature:home")
include(":feature:train")
include(":feature:shell")
