plugins {
    id("movit.kmp.feature")
}

movitKmp {
    namespace = "com.movit.feature.trainingdebug"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:data"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:resources"))
            implementation(project(":core:training-engine"))
            implementation(project(":core:pose-capture"))
            movitComposeUi()
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            implementation(project(":feature:training"))
            implementation(libs.koin.core)
            implementation(libs.androidx.activity.compose)
            implementation(libs.camera.core)
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.common)
            implementation(libs.androidx.exifinterface)
            implementation(libs.mediapipe.tasks.vision)
        }
        iosMain.dependencies {
            implementation(project(":feature:training"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":core:data"))
            implementation(project(":core:network"))
            implementation(libs.koin.core)
        }
    }
}
