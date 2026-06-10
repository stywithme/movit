// AGP 9 migration: replace android.library with android.kmp.library and move
// the android {} block into kotlin { android {} }. Requires AGP 9.0+ / Gradle 9.1+.
// https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
plugins {
    id("movit.kmp.core")
}

movitKmp {
    includeIosX64 = true
}

android {
    namespace = "com.movit.shared"
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
