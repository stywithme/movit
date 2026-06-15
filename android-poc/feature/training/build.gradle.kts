// AGP 9 migration: replace android.library with android.kmp.library when upgrading.
plugins {
    id("movit.kmp.feature")
}

val throughputProfile =
    (project.findProperty("movit.training.throughput.profile") as String?)?.trim().orEmpty()
        .ifEmpty { "stable" }

movitKmp {
    namespace = "com.movit.feature.training"
    buildConfigStrings["TRAINING_THROUGHPUT_PROFILE"] = throughputProfile
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:data"))
            implementation(project(":core:network"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:resources"))
            implementation(project(":core:training-engine"))
            implementation(project(":core:pose-capture"))
            implementation(project(":feature:reports"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.camera.view)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
