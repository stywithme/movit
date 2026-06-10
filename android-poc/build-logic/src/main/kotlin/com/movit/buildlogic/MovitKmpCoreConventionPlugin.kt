package com.movit.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

class MovitKmpCoreConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.library")

            val extension = extensions.create("movitKmp", MovitKmpExtension::class.java)
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            val iosMinVersion = libs.findVersion("ios-deployment-target").get().requiredVersion

            extensions.configure<KotlinMultiplatformExtension> {
                androidTarget {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
                iosArm64()
                iosSimulatorArm64()
            }

            extensions.configure<LibraryExtension> {
                compileSdk = libs.findVersion("compile-sdk").get().requiredVersion.toInt()
                defaultConfig {
                    minSdk = libs.findVersion("min-sdk").get().requiredVersion.toInt()
                }
                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
                }
            }

            afterEvaluate {
                extensions.configure<KotlinMultiplatformExtension> {
                    targets.withType(KotlinNativeTarget::class.java).configureEach {
                        binaries.configureEach {
                            freeCompilerArgs += "-Xoverride-konan-properties=minVersion.ios=$iosMinVersion"
                        }
                    }
                }
                if (extension.includeIosX64) {
                    extensions.configure<KotlinMultiplatformExtension> {
                        iosX64()
                    }
                }
                if (extension.unitTestsReturnDefaultValues) {
                    extensions.configure<LibraryExtension> {
                        testOptions {
                            unitTests.isReturnDefaultValues = true
                        }
                    }
                }
            }
        }
    }
}
