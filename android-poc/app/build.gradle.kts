import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val movitShellLauncherEnabled =
    providers.gradleProperty("movit.shell.launcher.enabled").orNull?.toBoolean() ?: false

val movitTrainingKmpEnabled =
    providers.gradleProperty("movit.training.kmp.enabled").orNull?.toBoolean() ?: false

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
        buildConfigField(
            "boolean",
            "MOVIT_SHELL_LAUNCHER_ENABLED",
            movitShellLauncherEnabled.toString(),
        )
        buildConfigField(
            "boolean",
            "MOVIT_TRAINING_KMP_ENABLED",
            movitTrainingKmpEnabled.toString(),
        )

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // F8 — real devices only; debug keeps x86/x86_64 for emulators.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    bundle {
        abi {
            enableSplit = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += listOf("tflite", "task", "onnx")
        // F8 — ship ar/en only (Compose resources + legacy res).
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
        val movitShellHostDir = "src/movitShellHost/java"
        if (movitShellLauncherEnabled) {
            getByName("main").java.srcDirs("src/movitShellEnabled/java", movitShellHostDir)
        } else {
            getByName("main").java.srcDir("src/movitShellDisabled/java")
            getByName("debug").java.srcDir(movitShellHostDir)
        }
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
    // LegacyWorkoutSyncDrain + MovitData types on release classpath (shell modules are debug-only when launcher off).
    implementation(project(":shared"))
    implementation(project(":core:pose-capture"))
    implementation(libs.koin.core)

    // Movit KMP modules — release classpath only when launcher flag is on (Phase 06 G-5).
    // core:data stays implementation unconditionally (AuthManager / secure tokens in legacy path).
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
    val movitShellDeps: org.gradle.kotlin.dsl.DependencyHandlerScope.() -> Unit = {
        movitShellProjects.forEach { path -> add("implementation", project(path)) }
        add("implementation", libs.androidx.activity.compose)
        add("implementation", libs.jetbrains.lifecycle.viewmodel.compose)
    }
    val movitShellDebugDeps: org.gradle.kotlin.dsl.DependencyHandlerScope.() -> Unit = {
        movitShellProjects.forEach { path -> add("debugImplementation", project(path)) }
        add("debugImplementation", libs.androidx.activity.compose)
        add("debugImplementation", libs.jetbrains.lifecycle.viewmodel.compose)
        add("debugImplementation", libs.compose.ui.tooling)
    }
    if (movitShellLauncherEnabled) {
        movitShellDeps()
        debugImplementation(libs.compose.ui.tooling)
    } else {
        movitShellDebugDeps()
        // Satisfy Compose compiler on release without pulling Movit into releaseRuntimeClasspath.
        releaseCompileOnly(
            "org.jetbrains.compose.runtime:runtime:${libs.versions.compose.multiplatform.get()}",
        )
    }

    implementation(project(":core:training-engine"))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.exifinterface)

    // Lifecycle, Activity & Fragment
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
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

    // JSON
    implementation(libs.gson)

    // Image Loading — Coil 2.x (View-based; Coil 3.x is Compose-only)
    implementation(libs.coil)
    implementation(libs.coil.gif)

    // Networking — Retrofit 3 + OkHttp 4 (stable)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Subscriptions: MyFatoorah return URL (Custom Tabs) + Google Play Billing
    implementation(libs.androidx.browser)
    implementation(libs.billing.ktx)

    // Charts
    implementation(libs.mpandroidchart)

    // ViewPager2
    implementation(libs.androidx.viewpager2)

    // CardView
    implementation(libs.androidx.cardview)

    // Google Sign-In / Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
