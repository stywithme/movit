package com.movit.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

open class MovitKmpExtension(
    private val project: Project,
) {
    /** Android namespace — required; triggers Android KMP library wiring when set. */
    var namespace: String? = null
        set(value) {
            field = value
            if (!value.isNullOrBlank()) {
                project.configureMovitKotlinAndroid(this)
            }
        }

    var unitTestsReturnDefaultValues: Boolean = false
    var includeIosX64: Boolean = false

    /** Gradle-generated Android constants (replaces BuildConfig on AGP KMP library plugin). */
    val buildConfigStrings: MutableMap<String, String> = linkedMapOf()
    val buildConfigInts: MutableMap<String, Int> = linkedMapOf()
}

internal fun Project.configureMovitKotlinAndroid(movit: MovitKmpExtension) {
    if (!plugins.hasPlugin("com.android.kotlin.multiplatform.library")) {
        pluginManager.apply("com.android.kotlin.multiplatform.library")
    }

    val libs = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java).named("libs")
    extensions.extraProperties.apply {
        set("movitAndroidNamespace", movit.namespace)
        set("movitAndroidCompileSdk", libs.findVersion("compile-sdk").get().requiredVersion.toInt())
        set("movitAndroidMinSdk", libs.findVersion("min-sdk").get().requiredVersion.toInt())
    }
    apply(mapOf("from" to rootProject.file("gradle/movit-kmp-android.gradle")))

    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.named("androidMain") {
            kotlin.srcDir(layout.buildDirectory.dir("generated/movitAndroidBuild/kotlin"))
        }
    }
}
