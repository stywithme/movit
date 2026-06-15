package com.movit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

class MovitKmpCoreConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")

            val extension = extensions.create("movitKmp", MovitKmpExtension::class.java, target)
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val iosMinVersion = libs.findVersion("ios-deployment-target").get().requiredVersion

            extensions.configure<KotlinMultiplatformExtension> {
                iosArm64()
                iosSimulatorArm64()
            }

            afterEvaluate {
                val namespace = extension.namespace
                    ?: error("movitKmp { namespace = \"...\" } is required for $path")

                extensions.configure<KotlinMultiplatformExtension> {
                    targets.withType(KotlinAndroidTarget::class.java).configureEach {
                        compilerOptions {
                            jvmTarget.set(JvmTarget.JVM_17)
                        }
                    }
                    targets.withType(KotlinNativeTarget::class.java).configureEach {
                        binaries.configureEach {
                            freeCompilerArgs += "-Xoverride-konan-properties=minVersion.ios=$iosMinVersion"
                        }
                    }
                    if (extension.includeIosX64) {
                        iosX64()
                    }
                }

                registerMovitAndroidBuildMetadataTask(namespace, extension)
            }
        }
    }

    private fun Project.registerMovitAndroidBuildMetadataTask(
        namespace: String,
        extension: MovitKmpExtension,
    ) {
        val generateTask = tasks.register("generateMovitAndroidBuildMetadata") {
            val outputDir = layout.buildDirectory.dir("generated/movitAndroidBuild/kotlin")
            outputs.dir(outputDir)
            doLast {
                val outRoot = outputDir.get().asFile
                val packageName = "$namespace.buildconfig"
                val packagePath = packageName.replace('.', '/')
                val outDir = outRoot.resolve(packagePath)
                outDir.mkdirs()

                val isDebug = !gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
                val stringFields = extension.buildConfigStrings.entries.joinToString("\n") { (key, value) ->
                    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                    "    const val $key: String = \"$escaped\""
                }
                val intFields = extension.buildConfigInts.entries.joinToString("\n") { (key, value) ->
                    "    const val $key: Int = $value"
                }

                val content = buildString {
                    appendLine("package $packageName")
                    appendLine()
                    appendLine("internal object MovitGeneratedBuildConfig {")
                    appendLine("    const val DEBUG: Boolean = $isDebug")
                    if (stringFields.isNotEmpty()) appendLine(stringFields)
                    if (intFields.isNotEmpty()) appendLine(intFields)
                    appendLine("}")
                }
                outDir.resolve("MovitGeneratedBuildConfig.kt").writeText(content)
            }
        }

        tasks.matching {
            it.name.startsWith("compile") && (it.name.contains("Kotlin") || it.name.contains("Android"))
        }.configureEach {
            dependsOn(generateTask)
        }
    }
}
