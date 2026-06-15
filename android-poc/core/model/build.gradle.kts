plugins {
    id("movit.kmp.core")
}

movitKmp {
    namespace = "com.movit.core.model"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
        }
    }
}
