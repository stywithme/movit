// AGP 9 migration: replace android.library with android.kmp.library when upgrading.
plugins {
    id("movit.kmp.feature")
}

movitKmp {
    namespace = "com.movit.feature.library"
    unitTestsReturnDefaultValues = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:model"))
            implementation(project(":core:data"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:network"))
            implementation(project(":core:resources"))
            implementation(project(":core:training-engine"))
            movitComposeUi()
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.camera.view)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
        sourceSets.named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
