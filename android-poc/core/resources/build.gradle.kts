plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(compose.ui)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.movit.resources"
}

val generateMovitEnglishStrings by tasks.registering {
    val stringsXml = layout.projectDirectory.file("src/commonMain/composeResources/values/strings.xml")
    val outputKt = layout.projectDirectory.file("src/commonMain/kotlin/com/movit/resources/MovitEnglishStrings.kt")
    inputs.file(stringsXml)
    outputs.file(outputKt)
    doLast {
        val xml = stringsXml.asFile.readText()
        val entryRegex = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        val lines = buildList {
            add("package com.movit.resources")
            add("")
            add("// Generated from composeResources/values/strings.xml — do not edit manually.")
            add("internal val movitEnglishStrings: Map<String, String> = mapOf(")
            entryRegex.findAll(xml).forEach { match ->
                val name = match.groupValues[1]
                val raw = match.groupValues[2]
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("$", "\\$")
                add("    \"$name\" to \"$raw\",")
            }
            add(")")
        }
        outputKt.asFile.writeText(lines.joinToString("\n") + "\n")
    }
}

tasks.named("preBuild") {
    dependsOn(generateMovitEnglishStrings)
}

android {
    namespace = "com.movit.resources"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
