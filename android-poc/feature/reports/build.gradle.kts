// AGP 9 migration: replace android.library with android.kmp.library when upgrading.
plugins {
    id("movit.kmp.feature")
}

movitKmp {
    namespace = "com.movit.feature.reports"
    unitTestsReturnDefaultValues = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:data"))
            implementation(project(":core:training-engine"))
            implementation(project(":core:network"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:resources"))
            implementation(compose.components.resources)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        sourceSets.named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
