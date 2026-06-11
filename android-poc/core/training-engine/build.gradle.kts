plugins {
    id("movit.kmp.core")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.movit.core.training"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
