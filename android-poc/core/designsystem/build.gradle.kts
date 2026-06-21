// AGP 9 migration: replace android.library with android.kmp.library and move
// the android {} block into kotlin { android {} }. Requires AGP 9.0+ / Gradle 9.1+.
plugins {
    id("movit.kmp.feature")
}

movitKmp {
    namespace = "com.movit.designsystem"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            movitComposeUi(includeResources = true)
            api(project(":core:model"))
            implementation(project(":core:resources"))
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
