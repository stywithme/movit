plugins {
    `kotlin-dsl`
}

group = "com.movit.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("movitKmpCore") {
            id = "movit.kmp.core"
            implementationClass = "com.movit.buildlogic.MovitKmpCoreConventionPlugin"
        }
        register("movitKmpFeature") {
            id = "movit.kmp.feature"
            implementationClass = "com.movit.buildlogic.MovitKmpFeatureConventionPlugin"
        }
    }
}
