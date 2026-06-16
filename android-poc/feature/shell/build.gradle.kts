// AGP 9 migration: replace android.library with android.kmp.library when upgrading.
plugins {
    id("movit.kmp.feature")
}

movitKmp {
    namespace = "com.movit.feature.shell"
}

kotlin {
    // Compose Multiplatform 1.11 — no iosX64 artifacts; use arm64 targets only.
    // Shell is the iOS app entry point: it produces a static framework (MovitApp)
    // consumed by iosApp/ (Swift). It already aggregates designsystem + explore + home.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "MovitApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:model"))
            implementation(project(":core:resources"))
            implementation(project(":core:data"))
            implementation(project(":core:network"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:explore"))
            implementation(project(":feature:home"))
            implementation(project(":feature:train"))
            implementation(project(":feature:reports"))
            implementation(project(":core:training-engine"))
            implementation(project(":feature:library"))
            implementation(project(":feature:training"))
            implementation(project(":feature:training-debug"))
            // Account effect types (e.g. MovitProfileEffect) are part of shell's public API
            // (MovitAppShellEvent) — api() keeps them visible to the iOS framework compile.
            api(project(":feature:account"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
