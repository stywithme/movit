plugins {
    id("movit.kmp.core")
    alias(libs.plugins.kotlin.serialization)
    // ponytail: atomicfu *library* only — plugin fails on androidMainClasses (WP-03/WP-14).
}

movitKmp {
    namespace = "com.movit.core.training"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
