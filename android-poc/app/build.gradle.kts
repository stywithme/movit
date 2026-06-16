import java.util.Properties



plugins {

    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.android)

    alias(libs.plugins.kotlin.compose)

}



// Read API config from local.properties (machine-specific) with optional api.properties defaults.

val localProps = rootProject.file("local.properties")

val apiPropsFile = rootProject.file("api.properties")

val apiProps = Properties()

if (apiPropsFile.exists()) apiProps.load(apiPropsFile.inputStream())

if (localProps.exists()) apiProps.load(localProps.inputStream())

val apiMode = apiProps.getProperty("api.mode", "local")

val apiPort = apiProps.getProperty("api.port", "4000")

val apiPhysicalIp = apiProps.getProperty("api.physical_device_ip", "192.168.1.18")

val apiServerUrl = apiProps.getProperty("api.server_url", "https://back.mongz.online/")



android {

    namespace = "com.trainingvalidator.poc"

    compileSdk = libs.versions.compile.sdk.get().toInt()



    defaultConfig {

        applicationId = "com.trainingvalidator.poc"

        minSdk = libs.versions.min.sdk.get().toInt()

        targetSdk = libs.versions.target.sdk.get().toInt()

        versionCode = 1

        versionName = "1.0-poc"



        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"



        buildConfigField("String", "API_MODE", "\"$apiMode\"")

        buildConfigField("int", "API_PORT", apiPort)

        buildConfigField("String", "API_PHYSICAL_IP", "\"$apiPhysicalIp\"")

        buildConfigField("String", "API_SERVER_URL", "\"$apiServerUrl\"")

    }



    buildTypes {

        debug {

            // F8 — emulators (x86/x86_64) stay available in debug only.

            ndk {

                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

            }

        }

        release {

            isMinifyEnabled = true

            isShrinkResources = true

            proguardFiles(

                getDefaultProguardFile("proguard-android-optimize.txt"),

                "proguard-rules.pro"

            )

            // F8 — real devices only; no x86/x86_64 in release APK/AAB.

            ndk {

                abiFilters += listOf("arm64-v8a", "armeabi-v7a")

            }

        }

    }



    bundle {

        abi {

            enableSplit = true

        }

        language {

            enableSplit = true

        }

    }



    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17

        targetCompatibility = JavaVersion.VERSION_17

    }



    androidResources {

        noCompress += listOf("tflite", "task", "onnx")

        localeFilters += listOf("en", "ar")

    }



    buildFeatures {

        viewBinding = true

        buildConfig = true

        compose = true

    }



    packaging {

        jniLibs {

            useLegacyPackaging = false

        }

    }



    testOptions {

        unitTests {

            isIncludeAndroidResources = true

        }

    }



    sourceSets {

        getByName("main").java.srcDirs(

            "src/movitShellEnabled/java",

            "src/movitShellHost/java",

        )

    }

}



kotlin {

    compilerOptions {

        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)

    }

}



dependencies {

    // Secure auth tokens (EncryptedSharedPreferences) — production path via AuthManager

    implementation(project(":core:data"))

    // LegacyWorkoutSyncDrain (B3 migrator) + MovitData types

    implementation(project(":shared"))

    implementation(project(":core:pose-capture"))

    implementation(libs.koin.core)



    val movitShellProjects = listOf(

        ":shared",

        ":core:model",

        ":core:resources",

        ":core:network",

        ":core:designsystem",

        ":feature:explore",

        ":feature:home",

        ":feature:train",

        ":feature:library",

        ":feature:training",

        ":feature:reports",

        ":feature:account",

        ":feature:shell",

    )

    movitShellProjects.forEach { path -> implementation(project(path)) }

    implementation(libs.androidx.activity.compose)

    implementation(libs.jetbrains.lifecycle.viewmodel.compose)

    debugImplementation(libs.compose.ui.tooling)

    debugImplementation(project(":feature:training-debug"))



    implementation(project(":core:training-engine"))

    implementation(project(":feature:billing"))



    // Core Android (SubscriptionActivity view binding)

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.appcompat)

    implementation(libs.material)

    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.swiperefreshlayout)



    implementation(libs.androidx.activity.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.lifecycle.process)



    // CameraX

    implementation(libs.camera.core)

    implementation(libs.camera.camera2)

    implementation(libs.camera.lifecycle)

    implementation(libs.camera.view)



    // MediaPipe Pose Landmarker — excludes legacy TFLite (LiteRT below)

    implementation(libs.mediapipe.tasks.vision) {

        exclude(group = "org.tensorflow", module = "tensorflow-lite")

        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")

        exclude(group = "org.tensorflow", module = "tensorflow-lite-gpu")

        exclude(group = "org.tensorflow", module = "tensorflow-lite-gpu-api")

        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")

    }

    // LiteRT replaces legacy TFLite — 16KB page-size aligned.

    implementation(libs.litert) {

        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")

    }

    implementation(libs.litert.api) {

        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")

    }

    implementation(libs.litert.support) {

        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")

    }



    // Portrait matting (MODNet / U²-Net ONNX) — debug only until D9 dynamic delivery (F8).

    debugImplementation(libs.onnxruntime.android)



    // Coroutines

    implementation(libs.kotlinx.coroutines.android)



    // kotlinx.serialization — billing DTOs (JsonElement) read by SubscriptionActivity
    implementation(libs.kotlinx.serialization.json)

    // Gson — deep-link WorkoutFlowConfig parsing + legacy AnalyticsStorage drain (WS-C).
    implementation(libs.gson)



    // Subscriptions: MyFatoorah return URL (Custom Tabs) + Google Play Billing

    implementation(libs.androidx.browser)

    implementation(libs.billing.ktx)



    // Testing

    testImplementation(libs.junit)

    testImplementation(libs.robolectric)

    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.espresso.core)

}


