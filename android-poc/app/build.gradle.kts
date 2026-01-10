plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.trainingvalidator.poc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.trainingvalidator.poc"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0-poc"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core Android - Updated for January 2026
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    
    // Lifecycle & Activity - Modern Architecture
    implementation("androidx.activity:activity-ktx:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")

    // CameraX - Stable 2025 Release
    val cameraxVersion = "1.5.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // MediaPipe - Pose Landmarker
    // Updated to 0.10.29 for 16KB page size support (required for Android 15+)
    // This version is compatible with existing task files
    implementation("com.google.mediapipe:tasks-vision:0.10.29")

    // Coroutines - Updated for Kotlin 2.0
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // JSON Parsing
    implementation("com.google.code.gson:gson:2.13.2")
    
    // ViewPager2 for Report tabs
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    
    // CardView
    implementation("androidx.cardview:cardview:1.0.0")

    // ExoPlayer (Media3) - For video playback and analysis
    val media3Version = "1.9.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
