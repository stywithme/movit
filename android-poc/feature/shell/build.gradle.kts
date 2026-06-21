@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

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
            // Swift iosApp bridges (MediaPipe, StoreKit, Google Sign-In, camera) call InstallKt
            // entry points and implement Kotlin protocols — export() puts them in MovitApp.h.
            export(project(":core:pose-capture"))
            export(project(":core:data"))
            export(project(":feature:account"))
            transitiveExport = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            movitComposeUi(includeResources = true)
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
            // Swift bridges in iosApp implement protocols from pose-capture (exported in framework).
            implementation(project(":core:pose-capture"))
            // Account effect types (e.g. MovitProfileEffect) are part of shell's public API
            // (MovitAppShellEvent) — api() keeps them visible to the iOS framework compile.
            api(project(":feature:account"))
            implementation(libs.compose.navigationevent)
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
