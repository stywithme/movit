// AGP 9 migration: replace android.library with android.kmp.library when upgrading.
plugins {
    id("movit.kmp.feature")
}

movitKmp {
    namespace = "com.movit.feature.account"
    unitTestsReturnDefaultValues = true
}

kotlin {
    sourceSets {
        iosMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/movitAndroidBuild/kotlin"))
        }
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:data"))
            implementation(project(":core:network"))
            implementation(project(":core:training-engine"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:resources"))
            implementation(compose.components.resources)
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
        iosMain.dependencies {
            implementation(project(":core:pose-capture"))
        }
        androidMain.dependencies {
            implementation(project(":core:pose-capture"))
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.camera.view)
            implementation(libs.googleid)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":core:network"))
            implementation(project(":core:resources"))
        }
        sourceSets.named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
