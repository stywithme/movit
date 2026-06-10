plugins {
    id("movit.kmp.core")
}

android {
    namespace = "com.movit.core.model"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
        }
    }
}
