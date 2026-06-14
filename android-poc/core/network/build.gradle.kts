plugins {
    id("movit.kmp.core")
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties

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
    namespace = "com.movit.core.network"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "API_MODE", "\"$apiMode\"")
        buildConfigField("int", "API_PORT", apiPort)
        buildConfigField("String", "API_PHYSICAL_IP", "\"$apiPhysicalIp\"")
        buildConfigField("String", "API_SERVER_URL", "\"$apiServerUrl\"")
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
