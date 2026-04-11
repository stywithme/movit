import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read API config from local.properties (like .env for Android)
val localProps = rootProject.file("local.properties")
val apiProps = Properties()
if (localProps.exists()) apiProps.load(localProps.inputStream())
val apiMode = apiProps.getProperty("api.mode", "local")
val apiPort = apiProps.getProperty("api.port", "4000")
val apiPhysicalIp = apiProps.getProperty("api.physical_device_ip", "192.168.1.18")
val apiServerUrl = apiProps.getProperty("api.server_url", "https://back.mongz.online/")

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

        buildConfigField("String", "API_MODE", "\"$apiMode\"")
        buildConfigField("int", "API_PORT", apiPort)
        buildConfigField("String", "API_PHYSICAL_IP", "\"$apiPhysicalIp\"")
        buildConfigField("String", "API_SERVER_URL", "\"$apiServerUrl\"")
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

    androidResources {
        noCompress += listOf("tflite", "task")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/** LiteRT artifacts — keep version in one place */
val litertVersion = "1.4.0"

dependencies {
    // Core Android — March 2026
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Lifecycle, Activity & Fragment — March 2026
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    val lifecycleVersion = "2.10.0"
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")

    // CameraX — Feb 2026 stable
    val cameraxVersion = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // MediaPipe Pose Landmarker — Apr 2026 (0.10.33 ships 16KB-aligned native libs)
    implementation("com.google.mediapipe:tasks-vision:0.10.33") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-gpu")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-gpu-api")
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }
    // LiteRT replaces legacy TFLite — 16KB page-size aligned.
    // Provides org.tensorflow.lite.* (Interpreter, etc.) for MediaPipe + Posture/Elbow MLP classifiers.
    // Do NOT add org.tensorflow:tensorflow-lite alongside — duplicate classes at merge time.
    // litert-support-api shares manifest namespace with litert-support (AGP warns / AGP 9 can fail);
    // exclude the API artifact when pulled transitively — implementation stays in litert-support.
    implementation("com.google.ai.edge.litert:litert:$litertVersion") {
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }
    implementation("com.google.ai.edge.litert:litert-api:$litertVersion") {
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }
    implementation("com.google.ai.edge.litert:litert-support:$litertVersion") {
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // JSON
    implementation("com.google.code.gson:gson:2.13.2")

    // Image Loading — Coil 2.x (View-based; Coil 3.x is Compose-only)
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Networking — Retrofit 3 + OkHttp 4 (stable)
    val retrofitVersion = "3.0.0"
    val okhttpVersion = "4.12.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // CardView
    implementation("androidx.cardview:cardview:1.0.0")

    // Google Sign-In / Credential Manager
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Media3 (ExoPlayer) — Feb 2026 stable
    val media3Version = "1.9.2"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
