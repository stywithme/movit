package org.gradle.kotlin.dsl

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

private fun Project.movitComposeLibs(): VersionCatalog =
    extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

/** runtime, foundation, material3, ui; optional material icons extended + compose resources. */
fun KotlinDependencyHandler.movitComposeUi(
    includeMaterialIcons: Boolean = true,
    includeResources: Boolean = false,
) {
    val libs = project.movitComposeLibs()
    implementation(libs.findLibrary("compose-runtime").get())
    implementation(libs.findLibrary("compose-foundation").get())
    implementation(libs.findLibrary("compose-material3").get())
    if (includeMaterialIcons) {
        implementation(libs.findLibrary("compose-material-icons-extended").get())
    }
    implementation(libs.findLibrary("compose-ui").get())
    if (includeResources) {
        implementation(libs.findLibrary("compose-components-resources").get())
    }
}

/** Lighter stack for modules that only need runtime, foundation, ui, and compose resources. */
fun KotlinDependencyHandler.movitComposeResourcesStack() {
    val libs = project.movitComposeLibs()
    implementation(libs.findLibrary("compose-runtime").get())
    implementation(libs.findLibrary("compose-foundation").get())
    implementation(libs.findLibrary("compose-ui").get())
    implementation(libs.findLibrary("compose-components-resources").get())
}
