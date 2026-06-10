package com.movit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class MovitKmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("movit.kmp.core")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        }
    }
}
