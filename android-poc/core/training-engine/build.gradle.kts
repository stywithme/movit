plugins {
    id("movit.kmp.core")
}

android {
    namespace = "com.movit.core.training"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
