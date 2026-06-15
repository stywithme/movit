plugins {

    id("movit.kmp.core")

}



movitKmp {
    namespace = "com.movit.core.posecapture"
}



kotlin {

    sourceSets {

        commonMain.dependencies {

            implementation(project(":shared"))

            implementation(project(":core:training-engine"))

            implementation(libs.kotlinx.coroutines.core)

        }

        androidMain.dependencies {

            implementation(project(":core:data"))

            implementation(libs.okhttp)

            implementation(libs.androidx.core.ktx)

            implementation(libs.camera.core)

            implementation(libs.camera.camera2)

            implementation(libs.camera.lifecycle)

            implementation(libs.camera.view)

            implementation(libs.mediapipe.tasks.vision)

            implementation(libs.koin.core)

        }

        commonTest.dependencies {

            implementation(kotlin("test"))

        }

    }

}


