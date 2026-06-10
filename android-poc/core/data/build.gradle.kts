plugins {
    id("movit.kmp.core")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.movit.core.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:model"))
            implementation(project(":core:network"))
            implementation(project(":core:resources"))
            implementation(project(":core:designsystem"))
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
        }
    }
}
